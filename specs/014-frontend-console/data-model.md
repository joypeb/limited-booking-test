# Data Model: 로컬 브라우저 예약 콘솔

콘솔은 원본 데이터를 갖지 않는다. 아래는 화면·전달 계층이 다루는 값의 형태다. 백엔드 응답 형태는 `docs/openapi.yaml`이 기준이다.

## 백엔드 응답(읽기 전용, 계약 그대로)

| 이름 | 필드 |
|---|---|
| ResourceView | `id: string(uuid)`, `name: string`, `capacity: number`, `available: number` |
| CreatedReservationView | `id`, `resourceId`, `status: 'CONFIRMED'`, `createdAt: string(UTC ISO)` |
| ReservationView | `id`, `resourceId`, `status: 'CONFIRMED' \| 'CANCELLED'`, `createdAt`, `cancelledAt: string \| null` |
| Problem | `type?`, `title?`, `status?`, `detail?`, `instance?`, `code?: string` |

## Account / Credentials (탭 메모리)

- `account: 'ADMIN' | 'USER_A' | 'USER_B'`, `password: string`.
- `generation: number` — 자격 증명 적용·지우기·계정 변경 시 1 증가. 요청은 시작 시 세대를 기록하고, 응답 시 세대가 다르면 결과를 버린다.
- 영속화 금지(localStorage·sessionStorage·쿠키·URL·로그).

## ApiResult

| 필드 | 설명 |
|---|---|
| `status: number` | HTTP 상태(콘솔 오류 포함). 클라이언트 시간 초과·네트워크 실패는 0 |
| `ok: boolean` | 2xx |
| `body?: unknown` | 성공 JSON |
| `problem?: Problem` | problem+json 해석 결과 |
| `rawText?: string` | JSON이 아닌 응답의 앞 200자 |
| `headers` | `location?`, `idempotentReplayed?: boolean`, `retryAfter?: string`, `upstreamWwwAuthenticate?: string` |
| `source: 'backend' \| 'console'` | 콘솔 자체 오류(`CONSOLE_*`)·클라이언트 실패면 `console` |

## 오류 코드 표(설명 문구 대상)

- 백엔드 10종: `INVALID_REQUEST`(400), `UNAUTHENTICATED`(401), `FORBIDDEN`(403), `NOT_FOUND`(404), `SOLD_OUT`(409), `IDEMPOTENCY_CONFLICT`(409), `PAYLOAD_TOO_LARGE`(413), `UNSUPPORTED_MEDIA_TYPE`(415), `RATE_LIMITED`(429), `DEPENDENCY_UNAVAILABLE`(503).
- 콘솔 7종: `CONSOLE_ROUTE_NOT_ALLOWED`(404), `CONSOLE_FORBIDDEN_ORIGIN`(403), `CONSOLE_PAYLOAD_TOO_LARGE`(413), `CONSOLE_UPSTREAM_UNREACHABLE`(502), `CONSOLE_UPSTREAM_TIMEOUT`(504), `CONSOLE_MISCONFIGURED`(500), `CONSOLE_CLIENT_TIMEOUT`(클라이언트, status 0).
- 같은 409라도 `SOLD_OUT`과 `IDEMPOTENCY_CONFLICT`는 다른 문구. 503(백엔드)과 502/504(콘솔)는 다른 문구.

## ReservationAttempt (탭 메모리)

- `resourceId: string`(입력 원문), `idempotencyKey: string`(입력 원문), `account`, `generation`.
- "같은 키로 다시 보내기"는 마지막 시도를 그대로 재전송. 현재 계정·세대가 다르면 비활성.

## HistoryEntry (localStorage `limited-booking-console:history:v1`)

- `kind: 'resource' | 'reservation'`, `id: string(uuid)`, `name?: string`(대상만), `recordedAt: string(ISO)`, `account`.
- 최대 50개, 최신순, `(kind, id)` 중복은 최신으로 갱신. 저장 시 위 필드만 직렬화. 파싱 실패·형식 불일치 항목은 버린다.
- 기록 시점: 대상 생성·조회 성공, 예약 생성·조회·취소 성공(2xx).

## 입력 규칙(전송 차단 조건만)

| 입력 | 콘솔이 막는 경우 | 나머지 |
|---|---|---|
| 대상 이름 | 없음 | JSON 문자열로만 인코딩, 다듬지 않음 |
| 수량 | 원문이 JSON number 문법이 아님(빈 값 포함) | 원문 그대로 삽입 |
| 경로 식별자(대상·예약 조회, 취소) | UUID 8-4-4-4-12 형식이 아님 | 원문 그대로 |
| 예약 본문 resourceId | 없음 | JSON 문자열로 인코딩 |
| 생성 키 | 없음(빈 값이면 헤더 생략 → 백엔드 400 관찰) | 원문 그대로 |
