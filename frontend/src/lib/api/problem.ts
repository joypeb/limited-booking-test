import type { ApiResult, Problem } from "./types";

export const CODE_DESCRIPTIONS: Record<string, string> = {
  "INVALID_REQUEST": "요청 형식이나 입력값이 계약에 맞지 않습니다.",
  "UNAUTHENTICATED": "인증에 실패했습니다. 계정과 비밀번호를 확인하세요.",
  "FORBIDDEN": "이 계정에는 해당 작업 권한이 없습니다.",
  "NOT_FOUND": "대상을 찾을 수 없거나 접근할 수 없습니다.",
  "SOLD_OUT": "잔여 수량이 없어 예약할 수 없습니다(품절).",
  "IDEMPOTENCY_CONFLICT": "같은 생성 키가 다른 대상의 예약에 이미 사용되었습니다.",
  "PAYLOAD_TOO_LARGE": "요청 본문이 백엔드 상한(8KiB)을 넘었습니다.",
  "UNSUPPORTED_MEDIA_TYPE": "지원하지 않는 Content-Type입니다.",
  "RATE_LIMITED": "쓰기 요청 제한을 넘었습니다.",
  "DEPENDENCY_UNAVAILABLE": "백엔드 의존성(DB·Redis) 장애로 처리하지 못했습니다. 변경은 반영되지 않았습니다.",
  "CONSOLE_ROUTE_NOT_ALLOWED": "콘솔이 허용하지 않는 경로입니다. 백엔드로 전달하지 않았습니다.",
  "CONSOLE_FORBIDDEN_ORIGIN": "허용되지 않은 출처의 요청입니다. 백엔드로 전달하지 않았습니다.",
  "CONSOLE_PAYLOAD_TOO_LARGE": "콘솔 전달 상한(1MiB)을 넘었습니다. 백엔드로 전달하지 않았습니다.",
  "CONSOLE_UPSTREAM_UNREACHABLE": "백엔드에 연결할 수 없습니다. 백엔드 실행 여부를 확인하세요.",
  "CONSOLE_UPSTREAM_TIMEOUT": "백엔드 응답 시간이 초과되었습니다.",
  "CONSOLE_MISCONFIGURED": "콘솔의 백엔드 주소 설정이 올바르지 않습니다.",
  "CONSOLE_CLIENT_TIMEOUT": "콘솔 서버 응답이 없어 요청을 중단했습니다."
};

export function describeCode(code: string | undefined): string {
  return (code && Object.hasOwn(CODE_DESCRIPTIONS, code) ? CODE_DESCRIPTIONS[code] : undefined)
    ?? "알 수 없는 오류 코드입니다. 원문을 확인하세요.";
}

export async function parseResponse(response: Response): Promise<ApiResult> {
  const headers: ApiResult["headers"] = {};
  const location = response.headers.get("location");
  const replayed = response.headers.get("idempotent-replayed");
  const retryAfter = response.headers.get("retry-after");
  const challenge = response.headers.get("x-upstream-www-authenticate");
  if (location !== null) headers.location = location;
  if (replayed === "true" || replayed === "false") headers.idempotentReplayed = replayed === "true";
  if (retryAfter !== null) headers.retryAfter = retryAfter;
  if (challenge !== null) headers.upstreamWwwAuthenticate = challenge;
  const result: ApiResult = { status: response.status, ok: response.ok, headers, source: "backend" };
  const text = await response.text();
  const contentType = response.headers.get("content-type")?.split(";")[0].trim().toLowerCase();
  if (contentType === "application/json" || contentType === "application/problem+json") {
    try {
      const value: unknown = JSON.parse(text);
      if (contentType === "application/problem+json" && value !== null && typeof value === "object" && !Array.isArray(value)) {
        result.problem = value as Problem;
        if (typeof result.problem.code === "string" && result.problem.code.startsWith("CONSOLE_")) result.source = "console";
      } else {
        result.body = value;
      }
      return result;
    } catch {
      // 잘못된 JSON도 원문 일부를 관찰할 수 있게 한다.
    }
  }
  result.rawText = text.slice(0, 200);
  return result;
}
