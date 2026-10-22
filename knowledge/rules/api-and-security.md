# API와 보안 규칙

근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2·4절, 7절 4·6단계. 예약의 응답 재현은 [도메인과 트랜잭션](domain-and-transactions.md)을 따른다.

## 엔드포인트와 응답

| API | 인증·권한·입력 | 성공 |
|---|---|---|
| `POST /api/resources` | ADMIN, name·capacity | 201 + Location + ResourceView |
| `GET /api/resources/{id}` | 인증 사용자 | 200 + ResourceView |
| `POST /api/reservations` | 인증 사용자, Idempotency-Key, resourceId | 201 + Location + CreatedReservationView |
| `GET /api/reservations/{id}` | 소유자 | 200 + ReservationView |
| `POST /api/reservations/{id}/cancel` | 소유자, 본문 없음 | 200 + ReservationView |

- ResourceView 필드는 id·name·capacity·available이다.
- CreatedReservationView 필드는 id·resourceId·status·createdAt이며 status는 항상 CONFIRMED다. 최초·재요청은 `Idempotent-Replayed` 헤더로 구분한다.
- ReservationView 필드는 id·resourceId·status·createdAt·cancelledAt이다. CONFIRMED의 cancelledAt은 null, CANCELLED는 값이 있다.
- UUID·UTC 시각·enum·필수 필드·알 수 없는 필드·헤더를 고정 OpenAPI 3.0.3 계약에 명시한다. 자동 생성 명세만을 기대값으로 삼지 않는다.

## 엄격한 입력과 처리 순서

- 두 생성 API는 `application/json`만 허용한다. 미지원 Content-Type은 415다.
- 예약 본문은 resourceId만 받는다. userId·quantity 등 미정의 필드, 중복 JSON 키, 잘못된 JSON·UUID, capacity의 문자열·소수 입력은 400이다. 숫자의 암묵적 형 변환을 허용하지 않는다.
- 취소 요청의 비어 있지 않은 본문은 400이다. 키·이름·수량의 경계값은 도메인 규칙대로 검증한다.
- 모든 요청 본문의 상한은 8KiB이며 초과는 413이다. Content-Length뿐 아니라 실제 읽기 바이트·길이 헤더 없는 요청·chunked 요청을 검사한다.
- 본문 크기 제한은 인증 앞에서 적용한다. 상한 이하 요청은 인증 → 대상 경로의 요청 제한 → 입력 검증 → 서비스 순서다. 읽은 본문은 후속 처리에서 재사용할 수 있어야 한다.

- 이름은 Java `String.strip()`으로 전처리 후 코드 포인트 1~100개를 검사한다. 내부 공백과 NBSP(U+00A0)·U+2007·U+202F는 유지한다. 제거 후 빈 문자열은 400이며 NBSP만 1개인 이름은 허용한다.

## 인증과 소유권

- localhost 비브라우저 API에서 Basic 인증, 고정 UUID의 ADMIN·USER_A·USER_B를 사용한다. 비밀번호는 환경 변수로 받아 BCrypt로 처리하고 서버 세션을 사용하지 않는다.
- 사용자 UUID는 인증 Principal에서만 얻는다. body·임의의 `X-User-Id`를 신뢰하지 않는다.
- 타인 예약의 조회·취소는 관리자에게도 404다. 없는 예약과 구분해 존재 여부를 노출하지 않는다.
- 미인증·잘못된 비밀번호는 401, 인증된 USER의 관리자 API 사용은 403이다.
- CSRF 비활성화에는 localhost 비브라우저 API라는 전제를 기록한다. CORS 전체 허용은 추가하지 않는다. 브라우저·외부 공개로 확장할 때 인증·TLS·CSRF를 다시 설계한다.

## 오류 계약

- 오류는 `application/problem+json`과 ProblemDetail 표준 필드·`code`로 통일한다. 필드별 상세 계약과 API별 허용 실패는 계획서 4절을 확인한다.
- ControllerAdvice뿐 아니라 AuthenticationEntryPoint·AccessDeniedHandler·본문 제한 필터·요청 제한 필터에도 같은 형식을 적용한다.

| HTTP | code | 조건 |
|---|---|---|
| 400 | INVALID_REQUEST | 입력 계약 위반 |
| 401 | UNAUTHENTICATED | 인증 실패; WWW-Authenticate 포함 |
| 403 | FORBIDDEN | 관리자 권한 없음 |
| 404 | NOT_FOUND | 대상 없음 또는 예약 소유자 아님 |
| 409 | SOLD_OUT | 예약 가능 수량 없음 |
| 409 | IDEMPOTENCY_CONFLICT | 같은 사용자·키로 다른 대상 |
| 413 | PAYLOAD_TOO_LARGE | 본문 8KiB 초과 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 생성 API의 미지원 Content-Type |
| 429 | RATE_LIMITED | 요청 제한 초과; Retry-After 포함 |
| 503 | DEPENDENCY_UNAVAILABLE | DB·Redis 의존성 장애 또는 정해진 timeout |

- SQL·스택 트레이스·비밀번호를 응답에 노출하지 않는다.
- 분류하지 못한 내부 결함은 500으로 탐지한다. 계획서에 없는 500 code를 임의로 계약에 추가하거나 모든 예외를 409·503으로 바꾸지 않는다.

### 2026-10-22 US2 필터 체인 적용

실제체인의 순서는 본문상한→Basic→Authorization→RateLimitFilter→CancelRequestValidationFilter→DispatcherServlet이다. 두 후속필터는 서블릿자동등록하지 않으며 MVC기본 PathPatternRequestMatcher를 쓴다. 인코딩경로·ERROR dispatch·8KiB바이트상한·429표준6필드/판정PTTL헤더를 RateLimitApiIT37건으로 지정인수했다. US3실제장애503·DB보호/독립API는추가인수했고 US4기존회귀·최종게이트도실행인수했다.


### 2026-10-22 최종 적용 확인

기본20/60000ms·만료전용3/1000ms·경쟁전용10000/60000ms·연결/명령200ms를 유지한다. 기존HTTP 회귀·경쟁110회·종료 후 다음Redis/DB reset0을 인수했다. `build verify verifyContractDependencies`의 실제 수·결과는 [실행 근거](../../docs/test-evidence.md)를 따른다. Qodana T037은 사용자지정 미실행 후속이다. 오케스트레이터 최종검토는 별도다.
