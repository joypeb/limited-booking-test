import { randomUUID } from "node:crypto";
import { expect, type Dialog, type Page } from "@playwright/test";

export type Account = "ADMIN" | "USER_A" | "USER_B";

export function passwords(): Record<Account, string> {
  const values = {
    ADMIN: process.env.ADMIN_PASSWORD,
    USER_A: process.env.USER_A_PASSWORD,
    USER_B: process.env.USER_B_PASSWORD,
  };
  const missing = Object.entries(values)
    .filter(([, value]) => !value)
    .map(([account]) => `${account}_PASSWORD`);
  if (missing.length > 0) {
    throw new Error(`E2E 필수 환경 변수가 없습니다: ${missing.join(", ")}. 전용 백엔드 계정 비밀번호를 주입하세요.`);
  }
  return values as Record<Account, string>;
}

export function e2eBackendBaseUrl(): URL {
  if (process.env.E2E_ISOLATED_BACKEND !== "1") {
    throw new Error("E2E는 npm run test:e2e(실행기)로만 실행합니다: 전용 DB 백엔드를 보장하려면 실행기 표식 E2E_ISOLATED_BACKEND=1이 필요합니다.");
  }
  let url: URL;
  try {
    url = new URL(process.env.E2E_BACKEND_BASE_URL ?? "http://127.0.0.1:18080");
  } catch {
    throw new Error("E2E_BACKEND_BASE_URL이 유효한 URL이 아닙니다. 전용 백엔드의 루프백 주소를 지정하세요.");
  }
  if (url.port === "8080") {
    throw new Error("개발 DB 보호: E2E 백엔드 포트 8080은 사용할 수 없습니다. 전용 백엔드 포트 18080을 사용하세요.");
  }
  if (!["127.0.0.1", "localhost", "[::1]"].includes(url.hostname)) {
    throw new Error("개발 DB 보호: E2E_BACKEND_BASE_URL의 호스트는 루프백(127.0.0.1·localhost·[::1])이어야 합니다.");
  }
  return url;
}

export async function assertBackendReady() {
  // 목록 수집 시에는 실행하지 않고 beforeAll에서 호출한다.
  const baseUrl = e2eBackendBaseUrl();
  passwords();
  let response: Response;
  try {
    response = await fetch(new URL("/actuator/health", baseUrl), {
      signal: AbortSignal.timeout(5000),
      redirect: "manual",
    });
  } catch {
    throw new Error("E2E 백엔드 health에 연결할 수 없습니다. 전용 DB·Redis로 백엔드를 먼저 기동하세요.");
  }
  if (!response.ok) {
    throw new Error(`E2E 백엔드 health가 성공하지 않았습니다(HTTP ${response.status}).`);
  }
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new Error("E2E 백엔드 health 응답이 JSON이 아닙니다.");
  }
  if (typeof body !== "object" || body === null || !("status" in body) || body.status !== "UP") {
    throw new Error("E2E 백엔드 health 상태가 UP이 아닙니다.");
  }
}

export async function login(page: Page, account: Account, password = passwords()[account]) {
  const credentials = page.getByRole("region", { name: "자격 증명", exact: true });
  await credentials.getByRole("combobox", { name: "계정", exact: true }).selectOption(account);
  await credentials.getByLabel("비밀번호", { exact: true }).fill(password);
  await credentials.getByRole("button", { name: "자격 증명 적용", exact: true }).click();
  await expect(credentials.getByText(`현재 계정: ${account}`, { exact: true })).toBeVisible();
}

export function dialogGuard(page: Page) {
  let count = 0;
  const pending: Promise<void>[] = [];
  const listener = (dialog: Dialog) => {
    count += 1;
    pending.push(dialog.dismiss());
  };
  page.on("dialog", listener);
  return async () => {
    try {
      await Promise.all(pending);
      expect(count, "브라우저 기본 인증창을 포함한 대화상자는 0회여야 합니다").toBe(0);
    } finally {
      page.off("dialog", listener);
    }
  };
}

export function uniqueName(prefix = "콘서트 A") {
  return `${prefix} ${Date.now()}-${randomUUID()}`;
}
