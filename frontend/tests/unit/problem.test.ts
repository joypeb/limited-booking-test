import { describe, expect, it } from "vitest";
import { describeCode, parseResponse } from "../../src/lib/api/problem";

const descriptions = [
  ["INVALID_REQUEST", "요청 형식이나 입력값이 계약에 맞지 않습니다."],
  ["UNAUTHENTICATED", "인증에 실패했습니다. 계정과 비밀번호를 확인하세요."],
  ["FORBIDDEN", "이 계정에는 해당 작업 권한이 없습니다."],
  ["NOT_FOUND", "대상을 찾을 수 없거나 접근할 수 없습니다."],
  ["SOLD_OUT", "잔여 수량이 없어 예약할 수 없습니다(품절)."],
  ["IDEMPOTENCY_CONFLICT", "같은 생성 키가 다른 대상의 예약에 이미 사용되었습니다."],
  ["PAYLOAD_TOO_LARGE", "요청 본문이 백엔드 상한(8KiB)을 넘었습니다."],
  ["UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다."],
  ["RATE_LIMITED", "쓰기 요청 제한을 넘었습니다."],
  ["DEPENDENCY_UNAVAILABLE", "백엔드 의존성(DB·Redis) 장애로 처리하지 못했습니다. 변경은 반영되지 않았습니다."],
  ["CONSOLE_ROUTE_NOT_ALLOWED", "콘솔이 허용하지 않는 경로입니다. 백엔드로 전달하지 않았습니다."],
  ["CONSOLE_FORBIDDEN_ORIGIN", "허용되지 않은 출처의 요청입니다. 백엔드로 전달하지 않았습니다."],
  ["CONSOLE_PAYLOAD_TOO_LARGE", "콘솔 전달 상한(1MiB)을 넘었습니다. 백엔드로 전달하지 않았습니다."],
  ["CONSOLE_UPSTREAM_UNREACHABLE", "백엔드에 연결할 수 없습니다. 백엔드 실행 여부를 확인하세요."],
  ["CONSOLE_UPSTREAM_TIMEOUT", "백엔드 응답 시간이 초과되었습니다."],
  ["CONSOLE_MISCONFIGURED", "콘솔의 백엔드 주소 설정이 올바르지 않습니다."],
  ["CONSOLE_CLIENT_TIMEOUT", "콘솔 서버 응답이 없어 요청을 중단했습니다."],
];

describe("오류 코드 설명", () => {
  it.each(descriptions)("%s에 계약의 고정 한국어 문구를 반환한다", (code, description) => {
    expect(describeCode(code)).toBe(description);
  });
  it.each(["UNKNOWN_CODE", undefined])("미정의 코드 %j에는 원문 확인을 안내한다", (code) => {
    expect(describeCode(code)).toBe("알 수 없는 오류 코드입니다. 원문을 확인하세요.");
  });
});

describe("실제 HTTP Response 해석", () => {
  it("201 성공 JSON의 전체 본문과 상태를 반환한다", async () => {
    const body = { id: "reservation-id", resourceId: "resource-id", status: "CONFIRMED", createdAt: "2026-10-26T00:00:00.123456Z" };
    const result = await parseResponse(new Response(JSON.stringify(body), {
      status: 201, headers: { "Content-Type": "application/json" },
    }));
    expect(result).toMatchObject({ status: 201, ok: true, body, source: "backend" });
    expect(result.problem).toBeUndefined();
  });
  it.each([true, false])("재현 헤더 %s를 boolean으로 바꾸고 Location을 보존한다", async (replayed) => {
    const result = await parseResponse(new Response('{"status":"CONFIRMED"}', {
      status: 201,
      headers: { "Content-Type": "application/json", Location: "/api/reservations/reservation-id", "Idempotent-Replayed": String(replayed) },
    }));
    expect(result.headers.location).toBe("/api/reservations/reservation-id");
    expect(result.headers.idempotentReplayed).toBe(replayed);
  });
  it("429의 Retry-After 문자열과 문제 상세를 보존한다", async () => {
    const problem = { type: "about:blank", title: "요청 제한", status: 429, detail: "잠시 후 재시도", instance: "/api/reservations", code: "RATE_LIMITED" };
    const result = await parseResponse(new Response(JSON.stringify(problem), {
      status: 429, headers: { "Content-Type": "application/problem+json", "Retry-After": "17" },
    }));
    expect(result).toMatchObject({ status: 429, ok: false, problem, source: "backend" });
    expect(result.headers.retryAfter).toBe("17");
    expect(result.body).toBeUndefined();
  });
  it("401의 이름이 바뀐 인증 요구 헤더 원문을 보존한다", async () => {
    const challenge = 'Basic realm="limited-booking", charset="UTF-8"';
    const result = await parseResponse(new Response('{"code":"UNAUTHENTICATED"}', {
      status: 401, headers: { "Content-Type": "application/problem+json", "X-Upstream-WWW-Authenticate": challenge },
    }));
    expect(result.headers.upstreamWwwAuthenticate).toBe(challenge);
    expect(result.problem?.code).toBe("UNAUTHENTICATED");
  });
  it.each([
    ["CONSOLE_UPSTREAM_UNREACHABLE", "console"], ["CONSOLE_NEW_CODE", "console"],
    ["DEPENDENCY_UNAVAILABLE", "backend"], ["UNKNOWN_CODE", "backend"], ["X_CONSOLE_ERROR", "backend"],
  ])("problem 코드 %s의 출처를 %s로 구분한다", async (code, source) => {
    const problem = { type: "about:blank", title: "오류", status: 503, detail: "실패", code };
    const result = await parseResponse(new Response(JSON.stringify(problem), {
      status: 503, headers: { "Content-Type": "application/problem+json; charset=utf-8" },
    }));
    expect(result.problem).toEqual(problem);
    expect(result.source).toBe(source);
    expect(result.ok).toBe(false);
  });
  it.each(["<html>짧은 오류</html>", `<html>${"가".repeat(240)}</html>`])("비JSON 응답 %j는 앞 200자까지만 원문으로 보관한다", async (text) => {
    const result = await parseResponse(new Response(text, { status: 502, headers: { "Content-Type": "text/html" } }));
    expect(result).toMatchObject({ status: 502, ok: false, rawText: text.slice(0, 200) });
    expect(result.problem).toBeUndefined();
    expect(result.body).toBeUndefined();
  });
});
