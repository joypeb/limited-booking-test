# Contract C1: 콘솔 전달 계층 (`/api/backend/...`)

브라우저 → 콘솔 서버(`http://127.0.0.1:3000/api/backend/<path>`) → 백엔드(`BACKEND_BASE_URL` + `/api/<path>`).

## C1.1 허용 경로·메서드

| 콘솔 요청 | 백엔드 요청 |
|---|---|
| `POST /api/backend/resources` | `POST /api/resources` |
| `GET /api/backend/resources/{uuid}` | `GET /api/resources/{uuid}` |
| `POST /api/backend/reservations` | `POST /api/reservations` |
| `GET /api/backend/reservations/{uuid}` | `GET /api/reservations/{uuid}` |
| `POST /api/backend/reservations/{uuid}/cancel` | `POST /api/reservations/{uuid}/cancel` |

- `{uuid}`: `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`. 원문 그대로 전달(대소문자 변환 없음).
- 쿼리 문자열은 전달하지 않는다.
- 그 밖의 경로·메서드(예: `GET /api/backend/resources`, `../`, 인코딩된 `%2F`) → 404 `CONSOLE_ROUTE_NOT_ALLOWED`.
- `OPTIONS`·`HEAD`는 프레임워크 자동 응답(OPTIONS 204+Allow 등)에 맡기지 않고 Route Handler가 명시적으로 받아 C1.2 출처 검사 후 404 `CONSOLE_ROUTE_NOT_ALLOWED`로 응답한다(2026-10-26 로컬 리뷰 L1). HEAD 응답은 HTTP 의미에 따라 본문이 없으므로 상태(출처 위반 403, 그 밖 404)와 `Content-Type: application/problem+json` 헤더만 관찰 대상이다. Route Handler가 export하지 않은 그 밖의 메서드(`PUT`·`PATCH`·`DELETE`)는 프레임워크 405. 모두 업스트림 호출 없음.

## C1.2 출처 검사(경로 검사보다 먼저)

1. `Host`가 `127.0.0.1:<port>`, `localhost:<port>`, `[::1]:<port>` 중 하나가 아니면 403 `CONSOLE_FORBIDDEN_ORIGIN`. `<port>`는 서버 환경 변수 `CONSOLE_PORT`(기본 3000)이며 `dev`·`start` 스크립트의 `-p` 값과 같아야 한다.
2. `Origin`이 있으면 `http://<허용 Host>`여야 한다. 아니면 403.
3. `Sec-Fetch-Site`가 있으면 `same-origin` 또는 `none`이어야 한다. 아니면 403.

## C1.3 요청 전달

- 복사하는 요청 헤더: `Authorization`, `Content-Type`, `Idempotency-Key`, `Accept`. 값은 바꾸지 않는다. 그 밖은 전달하지 않는다(특히 `Cookie`, `X-User-Id`, `X-Forwarded-*`).
- 본문: 수신 바이트를 그대로 전달. 0바이트면 본문 없이 전달. 1,048,576바이트 초과면 413 `CONSOLE_PAYLOAD_TOO_LARGE`(업스트림 호출 0회). `Content-Length`가 상한을 넘으면 읽기 전에, 길이가 없거나 틀린 스트림은 누적 중 상한을 넘는 즉시 읽기를 중단하고 413을 반환한다(스트림 종료를 기다리지 않음, 2026-10-26 로컬 리뷰 M2).
- 시간 상한 10초는 요청 본문 읽기 시작부터 백엔드 응답 본문 수신까지 전체에 적용한다. 본문 읽기 중 초과도 같은 504 `CONSOLE_UPSTREAM_TIMEOUT`으로 응답한다(업스트림 호출 0회).
- 재시도·리다이렉트 추종 없음(`redirect: 'manual'`).

## C1.4 응답 전달

- 상태 코드·본문 바이트 그대로.
- 보존 헤더: `Content-Type`, `Location`, `Idempotent-Replayed`, `Retry-After`.
- `WWW-Authenticate` → `X-Upstream-WWW-Authenticate`(값 그대로). 응답에 `WWW-Authenticate`는 없어야 한다.
- 추가: `Cache-Control: no-store`. 그 밖의 업스트림 헤더(`Set-Cookie` 등)는 전달하지 않는다.

## C1.5 콘솔 자체 오류

`Content-Type: application/problem+json`, 본문 `{ "type": "about:blank", "title", "status", "detail", "code" }`. 내부 예외 메시지·스택·백엔드 주소·자격 증명을 포함하지 않는다.

| 상황 | status | code |
|---|---|---|
| 허용되지 않은 경로 | 404 | `CONSOLE_ROUTE_NOT_ALLOWED` |
| 출처 검사 실패 | 403 | `CONSOLE_FORBIDDEN_ORIGIN` |
| 본문 1MiB 초과 | 413 | `CONSOLE_PAYLOAD_TOO_LARGE` |
| 백엔드 연결 실패 | 502 | `CONSOLE_UPSTREAM_UNREACHABLE` |
| 백엔드 응답 10초 초과 | 504 | `CONSOLE_UPSTREAM_TIMEOUT` |
| `BACKEND_BASE_URL`이 루프백이 아니거나 URL이 아님 | 500 | `CONSOLE_MISCONFIGURED` |

## C1.6 검증 방법

- `tests/proxy/`: `node:http` 가짜 업스트림(동적 포트)에서 수신 요청(메서드·경로·헤더·본문 바이트)과 수신 횟수를 기록해 단언한다. 거부 사례는 수신 0회.
- `e2e/`: 실제 백엔드로 401(대화상자 0회·`X-Upstream-WWW-Authenticate` 표시)·201·`Idempotent-Replayed` 전달 확인.
