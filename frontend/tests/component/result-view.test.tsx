import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ResultView } from "@/components/ResultView";
import type { ApiResult } from "@/lib/api/types";

const success: ApiResult = {
  status: 201, ok: true, source: "backend", headers: {},
  body: { id: "12345678-1234-4234-8234-123456789abc", name: "콘서트 A", capacity: 10, available: 9 },
};

function show(result: ApiResult, idempotencyKey?: string) {
  render(<ResultView title="예약 대상" result={result} idempotencyKey={idempotencyKey} />);
  return screen.getByRole("status", { name: "예약 대상 결과" });
}

function expectField(container: HTMLElement, name: string, value: string) {
  const term = within(container).getByText(name, { selector: "dt" });
  expect(term.closest("dl")).not.toBeNull();
  expect(term.nextElementSibling?.tagName).toBe("DD");
  expect(term.nextElementSibling).toHaveTextContent(value);
}

const explanations = [
  ["INVALID_REQUEST", 400, "요청 형식이나 입력값이 계약에 맞지 않습니다."],
  ["UNAUTHENTICATED", 401, "인증에 실패했습니다. 계정과 비밀번호를 확인하세요."],
  ["FORBIDDEN", 403, "이 계정에는 해당 작업 권한이 없습니다."],
  ["NOT_FOUND", 404, "대상을 찾을 수 없거나 접근할 수 없습니다."],
  ["SOLD_OUT", 409, "잔여 수량이 없어 예약할 수 없습니다(품절)."],
  ["IDEMPOTENCY_CONFLICT", 409, "같은 생성 키가 다른 대상의 예약에 이미 사용되었습니다."],
  ["PAYLOAD_TOO_LARGE", 413, "요청 본문이 백엔드 상한(8KiB)을 넘었습니다."],
  ["UNSUPPORTED_MEDIA_TYPE", 415, "지원하지 않는 Content-Type입니다."],
  ["RATE_LIMITED", 429, "쓰기 요청 제한을 넘었습니다."],
  ["DEPENDENCY_UNAVAILABLE", 503, "백엔드 의존성(DB·Redis) 장애로 처리하지 못했습니다. 변경은 반영되지 않았습니다."],
  ["CONSOLE_ROUTE_NOT_ALLOWED", 404, "콘솔이 허용하지 않는 경로입니다. 백엔드로 전달하지 않았습니다."],
  ["CONSOLE_FORBIDDEN_ORIGIN", 403, "허용되지 않은 출처의 요청입니다. 백엔드로 전달하지 않았습니다."],
  ["CONSOLE_PAYLOAD_TOO_LARGE", 413, "콘솔 전달 상한(1MiB)을 넘었습니다. 백엔드로 전달하지 않았습니다."],
  ["CONSOLE_UPSTREAM_UNREACHABLE", 502, "백엔드에 연결할 수 없습니다. 백엔드 실행 여부를 확인하세요."],
  ["CONSOLE_UPSTREAM_TIMEOUT", 504, "백엔드 응답 시간이 초과되었습니다."],
  ["CONSOLE_MISCONFIGURED", 500, "콘솔의 백엔드 주소 설정이 올바르지 않습니다."],
  ["CONSOLE_CLIENT_TIMEOUT", 0, "콘솔 서버 응답이 없어 요청을 중단했습니다."],
  ["FUTURE_UNKNOWN_CODE", 500, "알 수 없는 오류 코드입니다. 원문을 확인하세요."],
] as const;

describe("공통 결과 표시", () => {
  it("접근 가능한 상태 영역과 HTTP 상태 및 성공 필드 원문을 표시한다", () => {
    const result = show(success);
    expect(result).toHaveAttribute("aria-live", "polite");
    expect(within(result).getByText("HTTP 201", { exact: true })).toBeInTheDocument();
    for (const [key, value] of Object.entries(success.body as Record<string, unknown>)) {
      expectField(result, key, String(value));
    }
  });

  it("예약 필드와 UTC 시각 원문 및 null을 표시한다", () => {
    const body = {
      id: "22345678-1234-4234-8234-123456789abc",
      resourceId: "12345678-1234-4234-8234-123456789abc",
      status: "CONFIRMED", createdAt: "2026-10-26T01:02:03.123456Z", cancelledAt: null,
    };
    const result = show({ ...success, status: 200, body });
    for (const [key, value] of Object.entries(body)) expectField(result, key, String(value));
  });

  it("보존된 응답 헤더 네 종류를 원래 이름으로 표시한다", () => {
    const result = show({ ...success, headers: {
      location: "/api/resources/12345678-1234-4234-8234-123456789abc",
      idempotentReplayed: false, retryAfter: "42", upstreamWwwAuthenticate: 'Basic realm="reservation"',
    } });
    expectField(result, "Location", "/api/resources/12345678-1234-4234-8234-123456789abc");
    expectField(result, "Idempotent-Replayed", "false");
    expectField(result, "Retry-After", "42");
    expectField(result, "X-Upstream-WWW-Authenticate", 'Basic realm="reservation"');
  });

  it.each(explanations)("%s 오류의 원문과 고정 한국어 설명을 표시한다", (code, status, explanation) => {
    const result = show({ status, ok: false, headers: {},
      source: code.startsWith("CONSOLE_") ? "console" : "backend",
      problem: { code, title: "원문 제목", detail: "원문 상세 설명", status },
    });
    const view = within(result);
    for (const text of [code, "원문 제목", "원문 상세 설명", explanation]) {
      expect(view.getByText(text, { exact: true })).toBeInTheDocument();
    }
    expect(view.getByText(status === 0 ? "응답 없음" : `HTTP ${status}`, { exact: true })).toBeInTheDocument();
  });

  it("429 응답의 Retry-After 원문으로 다시 시도할 초를 표시한다", () => {
    const result = show({ status: 429, ok: false, source: "backend", headers: { retryAfter: "42" }, problem: { code: "RATE_LIMITED" } });
    expect(within(result).getByText("42초 후 다시 시도할 수 있습니다", { exact: true })).toBeInTheDocument();
  });

  it("JSON이 아닌 응답의 형식 안내와 HTTP 상태 및 원문을 표시한다", () => {
    const result = show({ status: 502, ok: false, source: "console", headers: {}, rawText: "upstream returned plain text" });
    for (const text of ["알 수 없는 응답 형식", "HTTP 502", "upstream returned plain text"]) {
      expect(within(result).getByText(text, { exact: true })).toBeInTheDocument();
    }
  });

  it("상태 0은 HTTP 0 대신 응답 없음으로 표시한다", () => {
    const result = show({ status: 0, ok: false, source: "console", headers: {} });
    expect(within(result).getByText("응답 없음", { exact: true })).toBeInTheDocument();
    expect(within(result).queryByText("HTTP 0", { exact: true })).not.toBeInTheDocument();
  });

  it.each([true, false])("재현 헤더가 %s이면 생성 키와 재현 여부를 표시한다", (replayed) => {
    const result = show({ ...success, body: {
      id: "22345678-1234-4234-8234-123456789abc",
      resourceId: "12345678-1234-4234-8234-123456789abc",
      status: "CONFIRMED", createdAt: "2026-10-26T01:02:03Z",
    }, headers: { idempotentReplayed: replayed } }, "creation-key-A");
    expect(within(result).getByText(`재현 여부: ${replayed ? "예" : "아니오"}`, { exact: true })).toBeInTheDocument();
    expect(within(result).getByText("생성 키: creation-key-A", { exact: true })).toBeInTheDocument();
    if (replayed) {
      expect(within(result).getByText("재현 응답은 최초 CONFIRMED 응답입니다. 현재 상태는 예약 조회로 확인하세요.", { exact: true })).toBeInTheDocument();
    }
  });
});
