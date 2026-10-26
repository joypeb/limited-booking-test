import { expect, test, type Locator } from "@playwright/test";
import { assertBackendReady, dialogGuard, login, passwords, uniqueName } from "./support/console";

const HISTORY_KEY = "limited-booking-console:history:v1";
const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

function field(result: Locator, name: string) {
  return result.locator("dt").filter({ hasText: new RegExp(`^${name}$`) })
    .locator("xpath=following-sibling::dd[1]");
}

test.describe("US4 실제 백엔드 최근 기록", () => {
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

  test("새로고침 뒤 자격 증명 없이 기록을 유지하고 재로그인 후 새로 조회하며 기록을 지운다", async ({ page }) => {
    // ADMIN 대상 생성은 예약 쓰기 제한 대상이 아니며 USER_A·USER_B 쓰기는 0회다.
    await login(page, "ADMIN");
    const name = uniqueName("US4 기록 대상");
    const resource = page.getByRole("region", { name: "예약 대상", exact: true });
    const result = page.getByRole("status", { name: "예약 대상 결과", exact: true });
    const history = page.getByRole("region", { name: "최근 기록", exact: true });
    const credentials = page.getByRole("region", { name: "자격 증명", exact: true });
    await resource.getByRole("textbox", { name: "대상 이름", exact: true }).fill(name);
    await resource.getByRole("textbox", { name: "최초 수량", exact: true }).fill("3");
    await resource.getByRole("button", { name: "대상 생성", exact: true }).click();
    await expect(result.getByText("HTTP 201", { exact: true })).toBeVisible();
    await expect(field(result, "name")).toHaveText(name);
    await expect(field(result, "id")).toHaveText(UUID);
    const id = await field(result, "id").innerText();
    const historyButton = history.getByRole("button", { name: `대상 ${id} 다시 조회`, exact: true });
    await expect(historyButton).toBeVisible();
    await expect(history).toContainText(`대상 · ${id} · ${name} · ADMIN ·`);

    await page.reload();
    await expect(credentials.getByText("자격 증명 없음", { exact: true })).toBeVisible();
    await expect(credentials.getByLabel("비밀번호", { exact: true })).toHaveValue("");
    await expect(resource.getByRole("button", { name: "대상 조회", exact: true })).toBeDisabled();
    await expect(historyButton).toBeVisible();
    await expect(history).toContainText(`대상 · ${id} · ${name} · ADMIN ·`);

    await login(page, "ADMIN");
    const [response] = await Promise.all([
      page.waitForResponse((response) => response.request().method() === "GET" &&
        new URL(response.url()).pathname === `/api/backend/resources/${id}`),
      historyButton.click(),
    ]);
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id, name, capacity: 3, available: 3 });
    await expect(resource.getByRole("textbox", { name: "대상 식별자", exact: true })).toHaveValue(id);
    await expect(result.getByText("HTTP 200", { exact: true })).toBeVisible();
    await expect(field(result, "id")).toHaveText(id);
    await expect(field(result, "name")).toHaveText(name);

    const storage = await page.evaluate(() => Object.fromEntries(Object.entries(localStorage)));
    // 단언 실패 메시지에 실제 비밀번호나 저장소 원문을 출력하지 않는다.
    expect(JSON.stringify(storage).includes(passwords().ADMIN), "localStorage 전체에 ADMIN 비밀번호가 없어야 합니다").toBe(false);
    const forbiddenKey = /"(?:available|status|password)"\s*:/;
    expect(Object.values(storage).some((raw) => forbiddenKey.test(raw)), "저장 값에 available·status·password 키가 없어야 합니다").toBe(false);
    expect(typeof storage[HISTORY_KEY], "정의된 localStorage 키에 기록이 저장되어야 합니다").toBe("string");
    const entries: unknown = JSON.parse(storage[HISTORY_KEY]);
    expect(Array.isArray(entries)).toBe(true);
    if (!Array.isArray(entries)) throw new Error("최근 기록 저장 값이 배열이 아닙니다");
    expect(entries).toHaveLength(1);
    // HistoryEntry 허용 필드만 저장하며 중복 조회는 같은 항목을 갱신한다.
    expect(Object.keys(entries[0]).sort()).toEqual(["account", "id", "kind", "name", "recordedAt"]);
    expect(entries[0]).toMatchObject({ kind: "resource", id, name, account: "ADMIN" });
    expect(Number.isFinite(Date.parse(entries[0].recordedAt))).toBe(true);

    await history.getByRole("button", { name: "기록 지우기", exact: true }).click();
    await expect(history.getByText("기록 없음", { exact: true })).toBeVisible();
    await expect(historyButton).toHaveCount(0);
  });
});
