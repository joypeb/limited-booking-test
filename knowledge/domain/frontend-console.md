# frontend-console 도메인 지식

## 1. 기준과 현재 상태

- 도메인 이름·패키지: 로컬 브라우저 예약 콘솔 / `frontend/`(Next.js 16.4.3 App Router + TypeScript, Tailwind CSS 4.3.3). 앱 코드 `frontend/src/`, 테스트 `frontend/tests/`·`frontend/e2e/`.
- 작성·마지막 확인일: 2026-10-26
- 설계 근거: 2026-10-26 사용자 지시, [헌법](../../.specify/memory/constitution.md) 1.1.0 원칙 VI, [specs/014 명세](../../specs/014-frontend-console/spec.md)·[계획](../../specs/014-frontend-console/plan.md)·[전달 계약](../../specs/014-frontend-console/contracts/console-proxy.md)·[화면 계약](../../specs/014-frontend-console/contracts/console-ui.md), [API와 보안 규칙](../rules/api-and-security.md), 계획서 4절(백엔드 API·오류 계약).
- 설계 상태: 확정(spec Clarifications 2026-10-26). 업무 규칙은 백엔드 도메인 문서가 기준이며 이 도메인은 새 업무 규칙을 만들지 않는다.
- 구현 상태: 전달 계층·자격 증명·결과 표시·대상 생성/조회·예약 생성/재전송·예약 조회/취소·최근 기록 구현(specs/014 tasks 참조).
- 실행 검증 상태: Vitest(Unit·Proxy·Component)와 실제 백엔드 대상 Playwright E2E 실행. 수치·명령은 [test-evidence 14단계 절](../../docs/test-evidence.md).

## 2. 목적·책임·경계

- 목적과 담당 책임: 완료된 백엔드 API 5개를 사람이 브라우저에서 호출·관찰하게 한다. 응답 상태·필드·보존 헤더·오류 code를 원문 그대로 보여 준다.
- 포함하는 기능: 계정·비밀번호 입력(메모리 전용), 대상 생성·조회, 예약 생성·같은 키 재전송, 예약 조회·취소, 결과의 대상 다시 조회, 이 브라우저의 최근 식별자 기록.
- 제외하는 기능: 백엔드 변경(CORS·목록 API·새 엔드포인트), 외부 공개·TLS·배포, 회원가입·비밀번호 변경, 서버 측 저장, 자동 재시도, 프런트엔드 CI(사용자 결정 대기), chunked 요청 재현.
- 연관 도메인과 의존 방향: 콘솔 → 백엔드 HTTP 계약([resource](resource.md)·[reservation](reservation.md)·[security](security.md)·[ratelimit](ratelimit.md)). 백엔드는 콘솔을 알지 못한다.
- 데이터 원본과 변경 책임: 수량·예약·멱등성의 원본은 PostgreSQL(백엔드). 콘솔은 원본 데이터를 갖지 않는다. 브라우저 `localStorage`의 최근 기록은 식별자 메모일 뿐 사실의 원본이 아니다.

## 3. 용어와 데이터

| 용어·필드 | 의미·타입 | 허용값·정규화·불변 여부 | 원본·근거 |
|---|---|---|---|
| 자격 증명 | `{ account: 'ADMIN'\|'USER_A'\|'USER_B', password }` | React 상태에만. 저장소·쿠키·URL·로그 금지. 새로고침·지우기·계정 변경 시 사라짐 | spec FR-002 |
| 세대(generation) | 정수 | 자격 증명 적용·지우기·계정 변경마다 증가. 응답 시 요청 시작 세대와 다르면 결과 폐기 | spec FR-012 |
| 전달 경로 | `/api/backend/<path>` | C1.1의 5개만. `{uuid}`는 8-4-4-4-12 원문 | console-proxy C1.1 |
| ApiResult | status·ok·body·problem·rawText·headers·source | headers: location·idempotentReplayed(boolean)·retryAfter·upstreamWwwAuthenticate. source `console`은 `CONSOLE_*`·클라이언트 실패 | data-model |
| 최근 기록 | `{ kind, id, name?, recordedAt, account }` | 키 `limited-booking-console:history:v1`, 최대 50, 최신순, (kind,id) 중복 갱신, 허용 필드만 직렬화 | data-model HistoryEntry |
| 콘솔 오류 code | `CONSOLE_ROUTE_NOT_ALLOWED`(404)·`CONSOLE_FORBIDDEN_ORIGIN`(403)·`CONSOLE_PAYLOAD_TOO_LARGE`(413)·`CONSOLE_UPSTREAM_UNREACHABLE`(502)·`CONSOLE_UPSTREAM_TIMEOUT`(504)·`CONSOLE_MISCONFIGURED`(500)·`CONSOLE_CLIENT_TIMEOUT`(클라이언트) | 백엔드 code 10종과 구분 | console-proxy C1.5 |

## 4. 불변식과 상태 전이

| 규칙 ID | 반드시 유지할 규칙 | 적용 시점·경계 | 근거 |
|---|---|---|---|
| FC-001 | 계약 밖 경로·메서드, UUID가 아닌 식별자, 허용 목록 밖 Host, 다른 Origin, 교차 사이트 요청은 백엔드에 도달하지 않는다 | 전달 계층, 매 요청 | C1.1·C1.2, SC-004 |
| FC-002 | 요청 본문은 원바이트, 허용 헤더 4개만 전달. 사용자 식별 헤더를 만들지 않는다 | 전달 계층 | C1.3, 헌법 VI |
| FC-003 | 응답 상태·본문·보존 헤더 4개를 바꾸지 않는다. `WWW-Authenticate`는 `X-Upstream-WWW-Authenticate`로만 전달 | 전달 계층 | C1.4, FR-006 |
| FC-004 | 비밀번호는 브라우저 저장소·쿠키·최근 기록에 저장되지 않는다 | 화면 | FR-002, SC-003 |
| FC-005 | 잔여 수량·예약 상태는 백엔드 응답만 표시한다 | 화면 | FR-013, 헌법 VI |
| FC-006 | 어떤 실패도 자동 재시도하지 않는다. 같은 키 재전송은 명시적 버튼만 | 전달 계층·화면 | FR-009·FR-011 |
| FC-007 | 이전 세대 요청의 응답은 표시하지 않는다 | 화면 | FR-012 |

상태 전이: 콘솔 자체의 업무 상태는 없다(예약 상태 전이는 [reservation](reservation.md)). 화면 상태는 `자격 증명 없음 → 현재 계정: <계정>`(적용) → `자격 증명 없음`(지우기·새로고침)뿐이다.

## 5. 유스케이스와 API 계약

| 유스케이스·API | 인증·권한·입력 | 사전 조건 | 성공·헤더·반환값 | 실패·HTTP·code | 데이터 변경 |
|---|---|---|---|---|---|
| 대상 생성 `POST /api/backend/resources` | 화면의 계정(ADMIN만 성공), 이름·수량 원문 | 수량 원문이 JSON 숫자 문법 | 201·Location·ResourceView 원문 | 400·401·403·413·415·503, 콘솔 오류 | 백엔드만 |
| 대상 조회 `GET …/resources/{uuid}` | 인증 사용자 | UUID 형식 | 200·ResourceView | 401·404·503 | 없음 |
| 예약 생성 `POST …/reservations` | 인증 사용자, `Idempotency-Key` 원문(빈 값이면 생략) | 없음 | 201·Location·`Idempotent-Replayed`·재현 여부 표시 | 400·401·404·409(SOLD_OUT/IDEMPOTENCY_CONFLICT)·429(Retry-After)·503 | 백엔드만 |
| 예약 조회 `GET …/reservations/{uuid}` | 소유자 | UUID 형식 | 200·ReservationView | 401·404(타인·관리자 포함)·503 | 없음 |
| 예약 취소 `POST …/reservations/{uuid}/cancel` | 소유자, 본문·Content-Type 없음 | UUID 형식 | 200·CANCELLED(중복 취소도 기존 시각) | 401·404·429·503 | 백엔드만 |

## 6. 멱등성·트랜잭션·경쟁·장애

- 멱등성: 콘솔은 키를 만들고(`crypto.randomUUID()`) 보여 줄 뿐, 재현 판단은 백엔드가 한다. 재현 응답은 최초 `CONFIRMED`이며 현재 상태는 조회로 확인한다고 안내한다.
- 트랜잭션: 해당 없음(콘솔은 DB를 쓰지 않음).
- 동시성: 패널별 진행 중 요청 동안 버튼 비활성, 세대 불일치 응답 폐기. 경쟁 검증은 백엔드 테스트·k6 담당.
- 실패와 재시도: 전달 계층 백엔드 호출 상한 10초(504), 연결 실패 502, 클라이언트 상한 15초. 재시도 없음.
- 외부 응답 유실: 사용자가 같은 키로 다시 보내 재현 여부를 확인한다(백엔드 멱등 계약).
- 요청 제한: 백엔드 Redis 제한(쓰기 20회/60초)을 그대로 관찰한다. 콘솔은 제한하지 않는다.

## 7. 검증할 사례와 실제 증거

| 규칙 ID | 사례 | 기대 | 검증 종류·테스트 | 실제 명령·증거 |
|---|---|---|---|---|
| FC-001 | 허용 밖 경로·rebinding Host·다른 Origin·cross-site | 403/404, 업스트림 수신 0회 | Unit `tests/unit/{routes,guard}.test.ts`, Proxy `tests/proxy/*.test.ts` | test-evidence 14단계 |
| FC-002·003 | 중복 키 JSON·8KiB+1 원바이트, 401 헤더 이름 변경, Set-Cookie 미전달 | 바이트·헤더 일치 | Proxy(node:http 가짜 업스트림) | 같음 |
| FC-003 | 실제 401 | 인증창 0회, 헤더 원문 표시 | E2E `e2e/us1-resource.spec.ts` | 같음 |
| FC-004 | 적용·새로고침 후 저장소·쿠키 | 비밀번호 없음 | Component `credentials`·`recent-history`, E2E us1·us4 | 같음 |
| FC-005·006 | 재전송·품절·충돌·취소 복구·429 | 백엔드 값 그대로, fetch 1회 | Component·E2E us2·us3 | 같음 |
| FC-007 | 계정 변경 중 응답 도착 | 미표시 | Component `console-requests` | 같음 |

## 8. 구현 연결과 변경 영향

| 책임 | 실제 파일·심볼 | 구현 상태 |
|---|---|---|
| 전달 계층 | [route.ts](../../frontend/src/app/api/backend/%5B...path%5D/route.ts), [forward.ts](../../frontend/src/lib/proxy/forward.ts), [guard.ts](../../frontend/src/lib/proxy/guard.ts), [routes.ts](../../frontend/src/lib/proxy/routes.ts) | 구현 |
| 요청·해석 | [client.ts](../../frontend/src/lib/api/client.ts), [bodies.ts](../../frontend/src/lib/api/bodies.ts), [problem.ts](../../frontend/src/lib/api/problem.ts), [basic-auth.ts](../../frontend/src/lib/basic-auth.ts), [idempotency.ts](../../frontend/src/lib/idempotency.ts) | 구현 |
| 화면 | [page.tsx](../../frontend/src/app/page.tsx), [components/](../../frontend/src/components/) | 구현 |
| 최근 기록 | [history.ts](../../frontend/src/lib/history.ts), RecentHistory | 구현 |
| 보안 헤더 | [next.config.ts](../../frontend/next.config.ts) `headers()` | 구현 |
| 테스트 | [tests/](../../frontend/tests/), [e2e/](../../frontend/e2e/), 게이트 [assert-vitest-report.mjs](../../frontend/scripts/assert-vitest-report.mjs) | 구현·실행 |

- 변경 시 함께 검토: 백엔드 API·오류 code가 바뀌면 `problem.ts` 문구 표·화면 계약 C2.4·전달 허용 목록 C1.1·E2E를 함께 갱신한다. 백엔드 계약 변경 없이 콘솔만 바꾸는 경우 OpenAPI는 그대로다.
- 결정과 이유·대안: [docs/decisions.md](../../docs/decisions.md) 14단계 항목.

## 9. 미확정·미검증 사항과 다음 작업

| 질문·미검증 항목 | 영향 | 확인 방법·다음 한 작업 |
|---|---|---|
| 프런트엔드 CI 실행 여부 | PR에서 콘솔 회귀가 자동 검출되지 않음 | 사용자 결정 후 verify 워크플로에 job 추가 검토 |
| 스크립트 CSP(nonce) | XSS 방어 심층화 미적용(로컬 전용·외부 입력 렌더 없음) | 외부 공개 설계 시 함께 결정 |
| 백엔드 503(DB·Redis 장애)의 실제 화면 확인 | Component 테스트로만 확인 | 필요 시 Redis 컨테이너 정지 후 수동 확인 기록 |
| chunked 413 | 브라우저 fetch로 재현 불가 | 백엔드 테스트가 담당(범위 밖) |
