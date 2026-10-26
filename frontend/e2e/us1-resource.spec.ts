import { randomUUID } from "node:crypto";
import { expect, test, type Locator, type Page } from "@playwright/test";
import { assertBackendReady, dialogGuard, login, passwords, uniqueName } from "./support/console";

const resourcePanel = (page: Page) => page.getByRole("region", { name: "예약 대상", exact: true });
const resourceResult = (page: Page) => page.getByRole("status", { name: "예약 대상 결과", exact: true });

function field(result: Locator, name: string) {
  return result.locator("dt").filter({ hasText: new RegExp(`^${name}$`) })
    .locator("xpath=following-sibling::dd[1]");
}

async function createResource(page: Page, name: string, capacity: string) {
  const panel = resourcePanel(page);
  await panel.getByRole("textbox", { name: "대상 이름", exact: true }).fill(name);
  await panel.getByRole("textbox", { name: "최초 수량", exact: true }).fill(capacity);
  await panel.getByRole("button", { name: "대상 생성", exact: true }).click();
}

async function lookupResource(page: Page, id: string) {
  const panel = resourcePanel(page);
  await panel.getByRole("textbox", { name: "대상 식별자", exact: true }).fill(id);
  await panel.getByRole("button", { name: "대상 조회", exact: true }).click();
}

async function expectError(page: Page, status: number, code: string, description: string) {
  const result = resourceResult(page);
  await expect(result.getByText(`HTTP ${status}`, { exact: true })).toBeVisible();
  await expect(result.getByText(code, { exact: true })).toBeVisible();
  await expect(result.getByText(description, { exact: true })).toBeVisible();
}

test.describe("US1 실제 백엔드 대상 생성·조회", () => {
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

  test("관리자는 수량 3 대상을 생성하고 같은 대상을 바로 다시 조회한다", async ({ page }) => {
    await login(page, "ADMIN");
    const name = uniqueName();
    await createResource(page, name, "3");
    const result = resourceResult(page);
    await expect(result.getByText("HTTP 201", { exact: true })).toBeVisible();
    await expect(field(result, "name")).toHaveText(name);
    await expect(field(result, "capacity")).toHaveText("3");
    await expect(field(result, "available")).toHaveText("3");
    await expect(field(result, "id")).toHaveText(/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/);
    const id = await field(result, "id").innerText();
    // 백엔드 Location은 상대·절대 URI 모두 가능하다. 경로와 식별자는 같아야 한다.
    const location = await field(result, "Location").innerText();
    expect(new URL(location, "http://127.0.0.1:8080").pathname).toBe(`/api/resources/${id}`);
    await resourcePanel(page).getByRole("button", { name: "대상 다시 조회", exact: true }).click();
    await expect(result.getByText("HTTP 200", { exact: true })).toBeVisible();
    await expect(field(result, "id")).toHaveText(id);
    await expect(field(result, "name")).toHaveText(name);
    await expect(field(result, "capacity")).toHaveText("3");
    await expect(field(result, "available")).toHaveText("3");
  });

  test("관리자는 최초 수량 0인 대상을 생성할 수 있다", async ({ page }) => {
    await login(page, "ADMIN");
    const name = uniqueName();
    await createResource(page, name, "0");
    const result = resourceResult(page);
    await expect(result.getByText("HTTP 201", { exact: true })).toBeVisible();
    await expect(field(result, "name")).toHaveText(name);
    await expect(field(result, "capacity")).toHaveText("0");
    await expect(field(result, "available")).toHaveText("0");
  });

  test("일반 사용자의 대상 생성은 403과 권한 오류 설명을 표시한다", async ({ page }) => {
    await login(page, "USER_A");
    await createResource(page, uniqueName(), "3");
    await expectError(page, 403, "FORBIDDEN", "이 계정에는 해당 작업 권한이 없습니다.");
  });

  test("틀린 비밀번호의 조회는 인증창 없이 401과 인증 요구 헤더를 화면에 표시한다", async ({ page }) => {
    const incorrectPassword = `${passwords().USER_A}__invalid_${randomUUID()}`;
    await login(page, "USER_A", incorrectPassword);
    const responsePromise = page.waitForResponse((response) =>
      response.request().method() === "GET" && response.url().includes("/api/backend/resources/"));
    await lookupResource(page, randomUUID());
    const response = await responsePromise;
    expect(response.status()).toBe(401);
    expect(response.headers()["www-authenticate"]).toBeUndefined();
    expect(response.headers()["x-upstream-www-authenticate"]).toContain("Basic");
    await expectError(page, 401, "UNAUTHENTICATED", "인증에 실패했습니다. 계정과 비밀번호를 확인하세요.");
    await expect(field(resourceResult(page), "X-Upstream-WWW-Authenticate")).toHaveText(response.headers()["x-upstream-www-authenticate"]);
  });

  for (const input of [
    { label: "공백 이름", name: "   ", capacity: "3" },
    { label: "상한 초과 수량", name: undefined, capacity: "1001" },
    { label: "소수 수량", name: undefined, capacity: "1.5" },
  ]) {
    test(`관리자의 ${input.label} 생성은 백엔드 400을 그대로 표시한다`, async ({ page }) => {
      await login(page, "ADMIN");
      await createResource(page, input.name ?? uniqueName(), input.capacity);
      await expectError(page, 400, "INVALID_REQUEST", "요청 형식이나 입력값이 계약에 맞지 않습니다.");
    });
  }

  test("존재하지 않는 대상 UUID 조회는 404와 찾을 수 없음 설명을 표시한다", async ({ page }) => {
    await login(page, "ADMIN");
    await lookupResource(page, randomUUID());
    await expectError(page, 404, "NOT_FOUND", "대상을 찾을 수 없거나 접근할 수 없습니다.");
  });

  test("UUID가 아닌 식별자는 요청 없이 형식 오류를 안내한다", async ({ page }) => {
    await login(page, "ADMIN");
    const requests: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname.startsWith("/api/backend/")) requests.push(request.url());
    });
    await lookupResource(page, "not-a-uuid");
    await expect(resourcePanel(page).getByRole("alert")).toHaveText("식별자는 UUID 형식이어야 합니다");
    expect(requests).toHaveLength(0);
  });

  test("콘솔 HTML 응답은 유형 추측·참조 정보·프레임 삽입을 막는 보안 헤더를 포함한다", async ({ page }) => {
    const response = await page.goto("/");
    expect(response, "콘솔 문서 응답이 있어야 합니다").not.toBeNull();
    const headers = response!.headers();
    expect(headers["x-content-type-options"]).toBe("nosniff");
    expect(headers["referrer-policy"]).toBe("no-referrer");
    expect(headers["x-frame-options"]).toBe("DENY");
    expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'");
  });

  test("적용한 비밀번호는 브라우저 저장소와 모든 쿠키에 남지 않는다", async ({ page }) => {
    await login(page, "ADMIN");
    // 실제 API 호출 뒤에도 저장 여부를 검사한다.
    await lookupResource(page, randomUUID());
    await expectError(page, 404, "NOT_FOUND", "대상을 찾을 수 없거나 접근할 수 없습니다.");
    const secrets = Object.values(passwords());
    const persisted = await page.evaluate(() => ({
      local: Object.values(localStorage),
      session: Object.values(sessionStorage),
      cookie: document.cookie,
    }));
    const cookies = await page.context().cookies();
    // 실패 메시지에도 실제 비밀번호·쿠키 값을 출력하지 않는다.
    expect(secrets.some((secret) => JSON.stringify(persisted).includes(secret)), "웹 저장소와 document.cookie에 비밀번호가 없어야 합니다").toBe(false);
    expect(secrets.some((secret) => JSON.stringify(cookies).includes(secret)), "브라우저 컨텍스트 쿠키에 비밀번호가 없어야 합니다").toBe(false);
  });
});
