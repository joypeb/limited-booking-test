import { randomUUID } from "node:crypto";
import { expect, test, type Locator, type Page } from "@playwright/test";
import { assertBackendReady, dialogGuard, login, uniqueName } from "./support/console";

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const UTC_TIMESTAMP = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z(?:\s+\(.*\))?$/;
const ownedPanel = (page: Page) => page.getByRole("region", { name: "내 예약", exact: true });
const ownedResult = (page: Page) => page.getByRole("status", { name: "내 예약 결과", exact: true });
const createPanel = (page: Page) => page.getByRole("region", { name: "예약 생성", exact: true });
const createResult = (page: Page) => page.getByRole("status", { name: "예약 생성 결과", exact: true });
const resourcePanel = (page: Page) => page.getByRole("region", { name: "예약 대상", exact: true });
const resourceResult = (page: Page) => page.getByRole("status", { name: "예약 대상 결과", exact: true });

function field(result: Locator, name: string) {
  return result.locator("dt").filter({ hasText: new RegExp(`^${name}$`) })
    .locator("xpath=following-sibling::dd[1]");
}

// 요청은 화면 버튼으로만 보내며, 매 클릭의 새 응답을 기다려 이전 결과와 혼동하지 않는다.
async function clickRequest(page: Page, button: Locator, method: string, path: string) {
  const [response] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === method &&
      new URL(response.url()).pathname === `/api/backend/${path}`),
    button.click(),
  ]);
  return response;
}

async function reservationAction(page: Page, id: string, cancel: boolean) {
  const panel = ownedPanel(page);
  await panel.getByRole("textbox", { name: "예약 식별자", exact: true }).fill(id);
  return clickRequest(page,
    panel.getByRole("button", { name: cancel ? "예약 취소" : "예약 조회", exact: true }),
    cancel ? "POST" : "GET", `reservations/${id}${cancel ? "/cancel" : ""}`);
}

async function expectOwnedState(page: Page, id: string, state: string) {
  const result = ownedResult(page);
  await expect(result.getByText("HTTP 200", { exact: true })).toBeVisible();
  await expect(field(result, "id")).toHaveText(id);
  await expect(field(result, "status")).toHaveText(state);
}

async function expectNotFound(page: Page) {
  const result = ownedResult(page);
  await expect(result.getByText("HTTP 404", { exact: true })).toBeVisible();
  await expect(result.getByText("NOT_FOUND", { exact: true })).toBeVisible();
  await expect(result.getByText("대상을 찾을 수 없거나 접근할 수 없습니다.", { exact: true })).toBeVisible();
}

async function expectAvailable(page: Page, id: string, available: number) {
  const panel = resourcePanel(page);
  await panel.getByRole("textbox", { name: "대상 식별자", exact: true }).fill(id);
  const response = await clickRequest(page,
    panel.getByRole("button", { name: "대상 조회", exact: true }), "GET", `resources/${id}`);
  expect(response.status()).toBe(200);
  expect(await response.json()).toMatchObject({ id, capacity: 2, available });
  const result = resourceResult(page);
  await expect(result.getByText("HTTP 200", { exact: true })).toBeVisible();
  await expect(field(result, "id")).toHaveText(id);
  await expect(field(result, "capacity")).toHaveText("2");
  await expect(field(result, "available")).toHaveText(String(available));
}

test.describe("US3 실제 백엔드 예약 취소·소유권·요청 제한", () => {
  test.describe.configure({ mode: "serial" });
  let checkDialogs: (() => Promise<void>) | undefined;

  test.beforeAll(async () => {
    await assertBackendReady();
  });

  test.beforeEach(async ({ page }) => {
    checkDialogs = dialogGuard(page);
    await page.goto("/");
  });

  test.afterEach(async () => {
    await checkDialogs?.();
    checkDialogs = undefined;
  });

  test("취소와 재취소는 수량을 한 번 복구하고 타인 접근을 숨기며 생성 재현과 현재 상태를 구분한다", async ({ page }) => {
    // USER_A 쓰기 4회: 생성, 취소, 재취소, 취소 후 생성 재현.
    // USER_B 쓰기 1회, ADMIN 쓰기 1회: 타인 예약 취소 거부(대상 생성은 제한 대상 아님).
    await login(page, "ADMIN");
    const name = uniqueName("US3 취소 대상");
    const resource = resourcePanel(page);
    await resource.getByRole("textbox", { name: "대상 이름", exact: true }).fill(name);
    await resource.getByRole("textbox", { name: "최초 수량", exact: true }).fill("2");
    const createdResource = await clickRequest(page,
      resource.getByRole("button", { name: "대상 생성", exact: true }), "POST", "resources");
    expect(createdResource.status()).toBe(201);
    await expect(field(resourceResult(page), "name")).toHaveText(name);
    await expect(field(resourceResult(page), "id")).toHaveText(UUID);
    const resourceId = await field(resourceResult(page), "id").innerText();

    await login(page, "USER_A");
    const creation = createPanel(page);
    const key = await creation.getByRole("textbox", { name: "생성 키", exact: true }).inputValue();
    expect(key).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
    await creation.getByRole("textbox", { name: "예약할 대상 식별자", exact: true }).fill(resourceId);
    const created = await clickRequest(page,
      creation.getByRole("button", { name: "예약", exact: true }), "POST", "reservations");
    expect(created.status()).toBe(201);
    await expect(createResult(page).getByText("HTTP 201", { exact: true })).toBeVisible();
    await expect(field(createResult(page), "id")).toHaveText(UUID);
    const reservationId = await field(createResult(page), "id").innerText();
    await expect(createResult(page).getByText(`생성 키: ${key}`, { exact: true })).toBeVisible();
    await expectAvailable(page, resourceId, 1);

    const confirmed = await reservationAction(page, reservationId, false);
    expect(confirmed.status()).toBe(200);
    await expectOwnedState(page, reservationId, "CONFIRMED");
    await expect(field(ownedResult(page), "cancelledAt")).toHaveText("null");

    const cancelled = await reservationAction(page, reservationId, true);
    expect(cancelled.status()).toBe(200);
    await expectOwnedState(page, reservationId, "CANCELLED");
    await expect(field(ownedResult(page), "cancelledAt")).toHaveText(UTC_TIMESTAMP);
    const cancelledAt = (await field(ownedResult(page), "cancelledAt").innerText()).split(/\s/)[0];
    const cancellationBody = await cancelled.json();
    expect(cancellationBody).toMatchObject({ id: reservationId, status: "CANCELLED", cancelledAt });
    await expectAvailable(page, resourceId, 2);

    const repeated = await reservationAction(page, reservationId, true);
    expect(repeated.status()).toBe(200);
    expect(await repeated.json()).toEqual(cancellationBody);
    await expectOwnedState(page, reservationId, "CANCELLED");
    expect((await field(ownedResult(page), "cancelledAt").innerText()).split(/\s/)[0]).toBe(cancelledAt);
    await expectAvailable(page, resourceId, 2);

    for (const account of ["USER_B", "ADMIN"] as const) {
      await login(page, account);
      for (const cancel of [false, true]) {
        const response = await reservationAction(page, reservationId, cancel);
        expect(response.status()).toBe(404);
        await expectNotFound(page);
      }
    }

    await login(page, "USER_A");
    await creation.getByRole("textbox", { name: "예약할 대상 식별자", exact: true }).fill(resourceId);
    await creation.getByRole("textbox", { name: "생성 키", exact: true }).fill(key);
    const replay = await clickRequest(page,
      creation.getByRole("button", { name: "예약", exact: true }), "POST", "reservations");
    expect(replay.status()).toBe(201);
    await expect(createResult(page).getByText("HTTP 201", { exact: true })).toBeVisible();
    await expect(field(createResult(page), "id")).toHaveText(reservationId);
    await expect(field(createResult(page), "status")).toHaveText("CONFIRMED");
    await expect(createResult(page).getByText("재현 여부: 예", { exact: true })).toBeVisible();
    await expect(createResult(page).getByText(`생성 키: ${key}`, { exact: true })).toBeVisible();
    await expect(createResult(page).getByText(
      "재현 응답은 최초 CONFIRMED 응답입니다. 현재 상태는 예약 조회로 확인하세요.", { exact: true },
    )).toBeVisible();
    const current = await reservationAction(page, reservationId, false);
    expect(current.status()).toBe(200);
    await expectOwnedState(page, reservationId, "CANCELLED");
    expect((await field(ownedResult(page), "cancelledAt").innerText()).split(/\s/)[0]).toBe(cancelledAt);
    await expectAvailable(page, resourceId, 2);
  });

  // 파일의 마지막 테스트: USER_B의 제한 창을 소비하므로 후속 쓰기 시나리오를 두지 않는다.
  test("USER_B의 없는 예약 취소는 최대 21회 안에 429와 다시 시도할 초를 표시한다", async ({ page }) => {
    test.setTimeout(90_000);
    await login(page, "USER_B");
    let rateLimited = false;
    for (let attempt = 1; attempt <= 21; attempt += 1) {
      const response = await reservationAction(page, randomUUID(), true);
      if (response.status() === 429) {
        const result = ownedResult(page);
        await expect(result.getByText("HTTP 429", { exact: true })).toBeVisible();
        await expect(result.getByText("RATE_LIMITED", { exact: true })).toBeVisible();
        await expect(result.getByText("쓰기 요청 제한을 넘었습니다.", { exact: true })).toBeVisible();
        const retryAfter = response.headers()["retry-after"];
        expect(retryAfter).toMatch(/^[1-9]\d*$/);
        await expect(field(result, "Retry-After")).toHaveText(retryAfter);
        await expect(result.getByText(`${retryAfter}초 후 다시 시도할 수 있습니다`, { exact: true })).toBeVisible();
        rateLimited = true;
        break;
      }
      expect(response.status(), `${attempt}번째 없는 예약 취소는 제한 전 404여야 합니다`).toBe(404);
      await expectNotFound(page);
    }
    expect(rateLimited, "USER_B 취소 요청 최대 21회 안에 429가 발생해야 합니다").toBe(true);
  });
});
