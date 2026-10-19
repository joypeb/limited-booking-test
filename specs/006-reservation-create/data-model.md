# 데이터 모델: 예약 생성·성공한 키

작성일: 2026-10-19. [명세](spec.md)·[기존 V1](../../backend/src/main/resources/db/migration/V1__create_reservation_schema.sql)의 설계를 유지한다. 신규 스키마·키 테이블·마이그레이션은 없다. 아래 record·쓰기 메서드는5단계에서 구현했다. 실행 근거는 docs/test-evidence.md를 따른다.

## 예약 대상 — resources

| 속성 | 타입/규칙 | 이번 책임 |
| --- | --- | --- |
| id | UUID PK, 불변 | 요청 대상 식별 |
| name | varchar(100), 기존 입력 규칙 | 변경 없음 |
| capacity | 정수 0~1000, 불변 | 보존식 기준 |
| available | 정수 0~capacity | 성공한 생성당 -1 |
| created_at | timestamptz | 변경 없음 |

예약 전 available>0일 때만 한 개 감소한다. 대상 없음과 수량 0을 새 키 재조회 후 구분한다. 해당 처리가 실패하면 감소도 취소한다.

## 예약 — reservations / StoredReservation

| 속성 | 타입/규칙 |
| --- | --- |
| id | UUID PK, 생성 후 불변 |
| resource_id | UUID FK resources(id), 필수·불변 |
| user_id | 인증 UUID, 필수·불변; 응답에 포함하지 않음 |
| idempotency_key | varchar(64), 필수·불변; IdempotencyKey에서 ASCII 영문/숫자/_/- 1~64 검증 |
| status | varchar(16), CONFIRMED 또는 CANCELLED |
| created_at | timestamptz, 필수·불변, UTC 마이크로초 |
| cancelled_at | CONFIRMED이면 null, CANCELLED이면 필수 시각 |

기존 `StoredReservation(UUID id, UUID resourceId, UUID userId, String idempotencyKey, ReservationStatus status, Instant createdAt, Instant cancelledAt)`는 유지한다. 키의 저장 타입 String은 유지하며 서비스 입력은 검증된 IdempotencyKey를 받는다.

유일성은 `uq_reservations_user_key(user_id, idempotency_key)`이며 대상 ID가 유일성 범위에 들어가지 않는다. 성공한 예약 행 자체가 키의 기록이다. 실패한 키·별도 실패 결과·TTL은 저장하지 않는다. 같은 사용자·다른 키와 다른 사용자·같은 키는 별개다. 생성 후 id/resource/user/key/createdAt 변경·예약 삭제·재활성화는 없다.

## 입력·출력 값 (구현됨)

- `CreateReservationRequest(UUID resourceId)`: 유일한 본문 필드이며 null/누락/형식 오류는 거부한다. 엄격 역직렬화에 의해 userId·quantity·알 수 없는 필드·중복 JSON 키를 거부한다.
- 기존 `IdempotencyKey(String value)`: 원문을 변환하지 않는 키 값 객체. 누락/무효 입력의 예외를 HTTP 경계에서 400으로 변환한다.
- `CreatedReservationView(UUID id, UUID resourceId, ReservationStatus status, Instant createdAt)`: 최초/재현 모두 CONFIRMED. userId·key·cancelledAt는 포함하지 않는다.
- `CreateReservationResult(CreatedReservationView body, boolean replayed)`: 최초 false, 기존 성공 재현 true. HTTP Location은 body.id에서 만든다.

현재 CANCELLED 예약도 불변 생성 정보와 고정 CONFIRMED로 CreatedReservationView를 재구성한다. 현재 상태를 나타내는 ReservationView/GET/취소는 6단계다.

## 상태·수량 변화

| 기존 상태 | 요청 결과 | 예약/수량 |
| --- | --- | --- |
| 성공 키 없음 | 생성 성공 | CONFIRMED 행 1개, available -1 |
| 성공 키 있음·같은 대상 | 생성 재현 | 행·현재 상태·수량 모두 불변 |
| 성공 키 있음·다른 대상 | 키 충돌 | 양쪽 대상의 요청별 수량 변경 없음 |
| 성공 키 없음 | 품절/없음/입력 오류/저장 실패 | 예약·성공 키 없음, 수량 부분 반영 없음 |
| CANCELLED 성공 키·같은 대상 | 최초 생성 재현 | CANCELLED·cancelledAt·available 그대로 |

취소 실행의 유일 상태 전이는 CONFIRMED→CANCELLED지만 이번 단계에 그 경로를 구현하지 않는다. 취소된 준비 데이터는 상태 CHECK와 수량 보존식을 함께 만족시켜 만든다.

## 트랜잭션 관측과 SQL 책임

- ResourceRepository: `decrementIfAvailable(UUID): int`, 기존 `find(UUID): Optional<ResourceView>`.
- ReservationRepository: 기존 `findByUserAndKey(UUID, String): Optional<StoredReservation>`, 신규 `insert(UUID id, UUID resourceId, UUID userId, IdempotencyKey key, Instant createdAt): StoredReservation`.
- 차감 SQL은 `UPDATE resources SET available = available - 1 WHERE id = :resourceId AND available > 0`이다. 영향 행 수는 0 또는 1이며 그 밖은 내부 오류다.
- INSERT는 CONFIRMED/null 취소 시각과 불변 속성을 쓰고 전체 예약 속성을 RETURNING으로 읽는다. 시각은 MICROS 절삭 후 OffsetDateTime UTC로 바인딩하고 DB 반환값을 Instant로 읽는다.
- Writer 시작 시 같은 트랜잭션 연결에 `SET LOCAL lock_timeout = '2s'`, `SET LOCAL statement_timeout = '5s'`를 적용한다. ResourceRepository의 현재 find와 ReservationRepository의 키 조회는 READ COMMITTED의 서로 다른 문장 관측이다.
- 수량 보존 `available + CONFIRMED 수 = capacity`는 테이블 CHECK만으로 보장되지 않는다. 작업 종료 후 별도 연결에서 기존 DatabaseAssertions의 단일 SELECT로 검증한다.
