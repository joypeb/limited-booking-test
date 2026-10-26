import { expect, test, type Locator, type Page } from "@playwright/test";
import { assertBackendReady, dialogGuard, login, uniqueName } from "./support/console";

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const reservationPanel = (page: Page) => page.getByRole("region", { name: "예약 생성", exact: true });
const reservationResult = (page: Page) => page.getByRole("status", { name: "예약 생성 결과", exact: true });
const resourcePanel = (page: Page) => page.getByRole("region", { name: "예약 대상", exact: true });
const resourceResult = (page: Page) => page.getByRole("status", { name: "예약 대상 결과", exact: true });

function field(result: Locator, name: string) {
  return result.locator("dt").filter({ hasText: new RegExp(`^${name}$`) })
    .locator("xpath=following-sibling::dd[1]");
}

async function createResource(page: Page, capacity: number, prefix: string) {
  const name = uniqueName(prefix);
  const panel = resourcePanel(page);
  await panel.getByRole("textbox", { name: "대상 이름", exact: true }).fill(name);
  await panel.getByRole("textbox", { name: "최초 수량", exact: true }).fill(String(capacity));
  await panel.getByRole("button", { name: "대상 생성", exact: true }).click();
  const result = resourceResult(page);
  await expect(result.getByText("HTTP 201", { exact: true })).toBeVisible();
  // 연속 생성에서 이전 201 결과를 읽지 않도록 고유 이름까지 기다린다.
  await expect(field(result, "name")).toHaveText(name);
  await expect(field(result, "capacity")).toHaveText(String(capacity));
  await expect(field(result, "available")).toHaveText(String(capacity));
  await expect(field(result, "id")).toHaveText(UUID);
  return field(result, "id").innerText();
}

async function expectAvailable(page: Page, id: string, expected: number) {
  const panel = resourcePanel(page);
  await panel.getByRole("textbox", { name: "대상 식별자", exact: true }).fill(id);
  // 같은 대상을 다시 조회할 때도 실제 새 응답을 기다린다.
  const [response] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === "GET" &&
      new URL(response.url()).pathname === `/api/backend/resources/${id}`),
    panel.getByRole("button", { name: "대상 조회", exact: true }).click(),
  ]);
  expect(response.status()).toBe(200);
  expect(await response.json()).toMatchObject({ id, available: expected });
  const result = resourceResult(page);
  await expect(result.getByText("HTTP 200", { exact: true })).toBeVisible();
  await expect(field(result, "id")).toHaveText(id);
  await expect(field(result, "available")).toHaveText(String(expected));
}

async function reserve(page: Page, resourceId: string) {
  const panel = reservationPanel(page);
  await panel.getByRole("textbox", { name: "예약할 대상 식별자", exact: true }).fill(resourceId);
  await panel.getByRole("button", { name: "예약", exact: true }).click();
}

async function expectCreated(page: Page, resourceId: string, replayed: boolean) {
  const result = reservationResult(page);
  await expect(result.getByText("HTTP 201", { exact: true })).toBeVisible();
  await expect(field(result, "id")).toHaveText(UUID);
  await expect(field(result, "resourceId")).toHaveText(resourceId);
  await expect(field(result, "status")).toHaveText("CONFIRMED");
  await expect(result.getByText(`재현 여부: ${replayed ? "예" : "아니오"}`, { exact: true })).toBeVisible();
  // 계약이 허용한 보조 로컬 시각은 제외하고 UTC 원문을 기록·비교한다.
  await expect(field(result, "createdAt")).toHaveText(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z(?:\s+\(.*\))?$/);
  return {
    id: await field(result, "id").innerText(),
    createdAt: (await field(result, "createdAt").innerText()).split(/\s/)[0],
  };
}

async function expectConflict(page: Page, code: string, description: string) {
  const result = reservationResult(page);
  await expect(result.getByText("HTTP 409", { exact: true })).toBeVisible();
  await expect(result.getByText(code, { exact: true })).toBeVisible();
  await expect(result.getByText(description, { exact: true })).toBeVisible();
}

test.describe("US2 실제 백엔드 예약 생성·재전송·충돌", () => {
  let checkDialogs: (() => Promise<void>) | undefined;
  let resourceX: string;
  let resourceY: string;

  test.beforeAll(async () => {
    await assertBackendReady();
  });

  test.beforeEach(async ({ page }) => {
    checkDialogs = dialogGuard(page);
    await page.goto("/");
    await login(page, "ADMIN");
    // 각 테스트는 독립된 X(수량 1), Y(수량 5)를 화면에서 생성한다.
    resourceX = await createResource(page, 1, "US2 대상 X");
    resourceY = await createResource(page, 5, "US2 대상 Y");
  });

  test.afterEach(async () => {
    await checkDialogs?.();
    checkDialogs = undefined;
  });

  test("예약 재전송은 최초 응답과 잔여 0을 유지하고 다른 사용자의 새 예약은 품절이다", async ({ page }) => {
    // 사용자별 쓰기: USER_A 2회(생성·재전송), USER_B 1회(품절).
    await login(page, "USER_A");
    const panel = reservationPanel(page);
    const key = await panel.getByRole("textbox", { name: "생성 키", exact: true }).inputValue();
    expect(key).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
    await reserve(page, resourceX);
    const first = await expectCreated(page, resourceX, false);
    await expect(reservationResult(page).getByText(`생성 키: ${key}`, { exact: true })).toBeVisible();
    await expectAvailable(page, resourceX, 0);

    await panel.getByRole("button", { name: "같은 키로 다시 보내기", exact: true }).click();
    const replay = await expectCreated(page, resourceX, true);
    expect(replay).toEqual(first);
    await expect(reservationResult(page).getByText(`생성 키: ${key}`, { exact: true })).toBeVisible();
    await expect(reservationResult(page).getByText(
      "재현 응답은 최초 CONFIRMED 응답입니다. 현재 상태는 예약 조회로 확인하세요.", { exact: true },
    )).toBeVisible();
    await expectAvailable(page, resourceX, 0);

    await login(page, "USER_B");
    await panel.getByRole("button", { name: "새 키", exact: true }).click();
    const otherKey = await panel.getByRole("textbox", { name: "생성 키", exact: true }).inputValue();
    expect(otherKey).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
    expect(otherKey).not.toBe(key);
    await reserve(page, resourceX);
    await expectConflict(page, "SOLD_OUT", "잔여 수량이 없어 예약할 수 없습니다(품절).");
  });

  test("성공한 생성 키로 다른 대상을 예약하면 충돌하며 그 대상의 수량 5가 보존된다", async ({ page }) => {
    // 사용자별 쓰기: USER_A 2회(생성·다른 대상 충돌), USER_B 0회.
    await login(page, "USER_A");
    const panel = reservationPanel(page);
    const key = await panel.getByRole("textbox", { name: "생성 키", exact: true }).inputValue();
    expect(key).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
    await reserve(page, resourceX);
    await expectCreated(page, resourceX, false);
    await expect(reservationResult(page).getByText(`생성 키: ${key}`, { exact: true })).toBeVisible();

    await panel.getByRole("textbox", { name: "생성 키", exact: true }).fill(key);
    await reserve(page, resourceY);
    await expectConflict(page, "IDEMPOTENCY_CONFLICT", "같은 생성 키가 다른 대상의 예약에 이미 사용되었습니다.");
    await expectAvailable(page, resourceY, 5);
  });
});
