import { randomUUID } from "node:crypto";
import { expect, test, type APIResponse } from "@playwright/test";
import { e2eBackendBaseUrl } from "./support/console";

const CONSOLE_URL = "http://127.0.0.1:3000";

async function expectProblem(response: APIResponse, status: number, code: string) {
  expect(response.status()).toBe(status);
  expect(response.headers()["content-type"]).toMatch(/^application\/problem\+json(?:;|$)/);
  expect(await response.json()).toMatchObject({ status, code });
}

test.describe("실제 Next HTTP 경계의 메서드·경로·출처 거부", () => {
  test.beforeAll(() => {
    // 개발 백엔드에 연결하는 설정은 읽기 전용 경계 검사에서도 거부한다.
    e2eBackendBaseUrl();
  });

  test("OPTIONS는 자동 204 대신 경로 거부 404 problem을 반환한다", async ({ request }) => {
    const response = await request.fetch(`${CONSOLE_URL}/api/backend/resources`, {
      method: "OPTIONS",
      maxRedirects: 0,
    });
    await expectProblem(response, 404, "CONSOLE_ROUTE_NOT_ALLOWED");
  });

  test("HEAD는 GET을 자동 실행하지 않고 본문 없는 404를 반환한다", async ({ request }) => {
    const response = await request.head(`${CONSOLE_URL}/api/backend/resources/${randomUUID()}`, {
      maxRedirects: 0,
    });
    expect(response.status()).toBe(404);
    // HEAD는 HTTP 의미에 따라 본문을 보내지 않으므로 JSON code를 읽지 않는다.
    expect((await response.body()).byteLength).toBe(0);
  });

  test("내보내지 않은 DELETE는 프레임워크 405를 반환한다", async ({ request }) => {
    const response = await request.delete(`${CONSOLE_URL}/api/backend/resources/${randomUUID()}`, {
      maxRedirects: 0,
    });
    expect(response.status()).toBe(405);
  });

  test("GET 대상 목록은 허용 목록 밖이므로 404 problem을 반환한다", async ({ request }) => {
    const response = await request.get(`${CONSOLE_URL}/api/backend/resources`, {
      maxRedirects: 0,
    });
    await expectProblem(response, 404, "CONSOLE_ROUTE_NOT_ALLOWED");
  });

  test("루프백 URL이라도 조작한 Host는 403 출처 오류로 거부한다", async ({ request }) => {
    const response = await request.get(`${CONSOLE_URL}/api/backend/resources/${randomUUID()}`, {
      headers: { Host: "evil.example:3000" },
      maxRedirects: 0,
    });
    await expectProblem(response, 403, "CONSOLE_FORBIDDEN_ORIGIN");
  });
});
