# API 계약 작성 설계

[명세](../spec.md) FR-005~FR-012, [모델](../data-model.md), 원 계획 4절을 적용한다. 이 문서는 인터페이스 설계이며 최종 OpenAPI 파일·실행 증거가 아니다. 최종 계약은 후속 작업에서 프로젝트 루트 `docs/openapi.yaml`로 작성한다.

## 기본 계약

- OpenAPI 3.0.3, 서버 `http://127.0.0.1:8080`, HTTP Basic 인증. 고정 UUID ADMIN·USER_A·USER_B, 비밀번호는 환경 변수이며 문서·예시에는 넣지 않는다.
- 두 생성은 application/json만 허용한다. 요청 본문·필수 필드·path UUID·예약 생성 Idempotency-Key를 필수로 선언한다. 입력 userId·quantity는 받지 않는다.
- 본문 상한은 모든 요청에서 실제 8192바이트이고 초과는 413이다. Content-Length 유무·chunked와 무관하다. 취소는 본문을 받지 않으며 상한 이하의 비어 있지 않은 본문은 400이다.
- 크기 제한 → 인증 → 예약 쓰기 제한 → 입력 검증 → 서비스. 재요청도 이 순서를 통과해야 한다.
- 성공 본문은 application/json, 오류는 application/problem+json. 아래 실패 목록은 원 계획 4.1절의 주요 실패이며 공통 본문 제한과 미분류 내부 결함 규칙도 적용한다.

## 다섯 작업

| 메서드·경로 | 권한 | 입력 | 성공·본문 | 필수 성공 헤더 | 주요 실패 상태 |
| --- | --- | --- | --- | --- | --- |
| POST /api/resources | ADMIN | name 문자열, capacity 정수 | 201 ResourceView | Location: /api/resources/{id} | 400·401·403·413·415·503 |
| GET /api/resources/{id} | 인증 사용자 | path id UUID | 200 ResourceView | 없음 | 400·401·404·503 |
| POST /api/reservations | 인증 사용자 | resourceId UUID, 생성 키 헤더 | 201 CreatedReservationView | Location: /api/reservations/{id}, Idempotent-Replayed | 400·401·404·409·413·415·429·503 |
| GET /api/reservations/{id} | 소유자 | path id UUID | 200 ReservationView | 없음 | 400·401·404·503 |
| POST /api/reservations/{id}/cancel | 소유자 | path id UUID, 본문 없음 | 200 ReservationView | 없음 | 400·401·404·413·429·503 |

Location은 생성된 단건 조회 경로를 가리킨다. 타인 예약 조회·취소는 관리자에게도 404다. 본문·임의 헤더의 사용자 UUID는 인증을 대체하지 않는다. 조회·대상 생성은 Redis에 의존하지 않는다.

## 스키마 작성 기준

| 스키마 | 필수 필드·제약 |
| --- | --- |
| CreateResourceRequest | name: string, Java `String.strip()`으로 앞뒤 공백 제거 후 코드 포인트 1~100개; capacity: integer, 0~1000; additionalProperties: false |
| CreateReservationRequest | resourceId: string/uuid; additionalProperties: false |
| ResourceView | id: string/uuid, name: string 길이 1~100, capacity: integer 0~1000, available: integer 0~1000; available≤capacity는 설명·사례로 검토 |
| CreatedReservationView | id·resourceId: string/uuid, status enum [CONFIRMED], createdAt: string/date-time |
| ReservationView | id·resourceId: string/uuid, status enum [CONFIRMED,CANCELLED], createdAt: string/date-time, cancelledAt: string/date-time/nullable true |
| ProblemDetail | type·title·detail·instance: string, status: integer, code: 아래 정의한 코드; 각 필드 존재를 선언 |

Java 25 `String.strip()`의 `Character.isWhitespace(int)` 기준으로 이름 앞뒤 공백을 제거한다. 일반 공백·탭·줄바꿈은 제거하고 내부 공백은 유지한다. U+00A0(NBSP)·U+2007·U+202F는 제거하지 않고 코드 포인트 길이에 포함한다. 제거 후 길이 1~100이며 빈 문자열은 400이다. NBSP만 1개인 입력도 길이1로 허용하며 W01~W07의 문서 기대값을 따른다.

name은 정규화 후 길이이므로 요청 원문에 maxLength 100을 적용해 유효한 앞뒤 공백 입력을 거부하지 않는다. description과 경계 사례에 규칙을 기록한다. cancelledAt는 필드 자체가 필수이며 CONFIRMED이면 null, CANCELLED이면 시각이다. 3.0.3에서는 type 배열이나 type null로 표현하지 않는다.

Idempotency-Key: string, 필수, 패턴 `^[A-Za-z0-9_-]{1,64}$`, minLength 1, maxLength 64. trim·대소문자 변환 없음. 사용자·키가 유일 범위이고 대상 UUID 값으로 동일 요청을 비교한다.

응답 시각은 UTC이며 저장된 마이크로초 값을 사용한다. UUID format, UTC·정밀도, 중복 JSON 키, capacity의 숫자 강제 변환 금지는 스키마만으로 실동작 검증을 대신하지 않는다.

## 오류와 헤더

| 상태 | code | 의미·필수 헤더 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 입력·JSON·키·UUID·취소 본문 오류 |
| 401 | UNAUTHENTICATED | 인증 실패, WWW-Authenticate Basic |
| 403 | FORBIDDEN | ADMIN 권한 없음 |
| 404 | NOT_FOUND | 대상 없음, 예약 없음 또는 소유자 아님 |
| 409 | SOLD_OUT | 성공 키 없음, 대상 품절 |
| 409 | IDEMPOTENCY_CONFLICT | 성공한 같은 사용자·키로 다른 대상 |
| 413 | PAYLOAD_TOO_LARGE | 실제 본문 8192바이트 초과 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 생성의 미지원 Content-Type |
| 429 | RATE_LIMITED | 생성·취소 합산 고정 창 초과, Retry-After |
| 503 | DEPENDENCY_UNAVAILABLE | DB 장애·timeout 또는 예약 쓰기의 Redis 장애 |

429의 Retry-After는 정수 초로 `max(1, ceil(남은 밀리초 / 1000))`다. 창은 최초 요청부터 60초이며 사용자당 20회, TTL은 요청마다 연장하지 않는다. 인증 뒤 해당 쓰기에 들어온 재요청·입력 오류도 횟수에 포함한다. 거부로 업무 데이터는 변하지 않고 DB 실패가 제한 카운터를 되돌리지 않는다.

필터·인증 오류도 같은 ProblemDetail 계약이다. SQL·스택·비밀번호를 노출하지 않는다. 내부 결함은 500으로 탐지하며 임의의 새 code를 만들지 않는다. 최종 명세의 default 응답 설명에 이를 명시하고 409·503으로 분류하지 않는다는 한계를 남긴다.

## 순차 사례와 재현 기준

[명세의 6행 흐름](../spec.md)을 적용한다. 최초 생성·재요청은 201, 동일 id/resourceId/createdAt/status CONFIRMED와 동일 Location을 반환한다. Idempotent-Replayed만 최초 false·재요청 true다. JSON의 순서·공백까지 같은 바이트일 필요는 없다.

추가 필수 검토 사례:

| 사례 | 초기 상태·행동 | 기대 결과 |
| --- | --- | --- |
| 실패 키 재사용 | capacity 1을 B가 점유, A/F 실패 → B 취소 → A/F 재시도 | 409 SOLD_OUT에서 F 미보존 → 수량 1 → 201, 수량 0·활성 1·F 기록 |
| 취소 뒤 키 충돌 | A/K로 R1 성공·취소 뒤 K로 R2 요청 | 409 IDEMPOTENCY_CONFLICT, 두 대상 수량 무변경 |
| 다른 키의 추가 예약 | capacity 3, A/K1·A/K2 | 서로 다른 예약 2개, 남은 1·활성 2 |
| 사용자별 키 범위 | capacity 2, A/K·B/K | 서로 다른 예약 2개, 남은 0·활성 2 |
| 길이 경계 | name이 😀 100개 또는 101개 | 각각 201 또는 400 INVALID_REQUEST, 실패 시 대상 미생성 |
| 공백과 길이 | 앞뒤 공백 + 코드 포인트 100개 | 정규화 후 100자 이름으로 생성 |
| 품절·의존성·입력 실패 | 미사용 키로 예약 실패 후 원인 해소·동일 키 재시도 | 실패는 키 점유 없음, 유효한 재시도 성공 가능 |
| 취소 재전송 | 소유자가 동일 예약 두 번 취소 | 200, 취소 시각 동일, 합계 수량 복구 1 |
| 쓰기 제한·장애 | 인증 사용자 예약 생성/취소에 429 또는 Redis 503 | 업무 데이터 무변경, 재요청도 차단 가능 |
| 응답 유실 | 생성 커밋 확인 뒤 같은 키 재요청 | 동일 최초 생성 결과, 추가 예약 없음 |

최소 15개 API 정상·비정상 사례는 [명세](../spec.md)의 표를 최종 검토 기록에서 구체화한다. 0단계는 이 사례의 기대 결과를 수동 대조한다. 실제 경쟁·장애·HTTP 자동 검증은 원 계획의 후속 단계에서 수행한다.
