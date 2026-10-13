# 도메인과 트랜잭션 규칙

근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2·5절. API 형식은 [API와 보안](api-and-security.md), 검증 방식은 [개발과 검증](development-and-testing.md)을 함께 읽는다.

## 입력과 불변식

- 대상 이름은 앞뒤 공백 제거 후 1~100자, 최초 수량은 정수 0~1,000이다. 수량 0인 대상도 생성할 수 있고 최초 `available = capacity`다.
- 예약 요청당 항상 1개다. `quantity`·`userId`를 입력받지 않고 사용자 UUID는 인증 결과에서 얻는다.
- 상태 전이는 `CONFIRMED → CANCELLED`만 허용한다. 재활성화와 예약 행 삭제를 하지 않는다.
- 대상별 `0 ≤ available ≤ capacity`, `available + CONFIRMED 예약 수 = capacity`를 유지한다.
- 사용자·생성 키 하나에 예약 행은 최대 1개다. 같은 사용자는 다른 키로 추가 예약할 수 있다.
- 취소의 수량 복구는 예약당 최대 1개다. 실패한 트랜잭션은 예약과 수량을 부분 반영하지 않는다.

## 생성 키와 응답 재현

- 키는 `^[A-Za-z0-9_-]{1,64}$`다. 공백 제거·대소문자 변환 없이 검증하고 보관한다.
- 범위는 `(인증 사용자 UUID, 생성 키)` 전체이며 대상이 달라도 공유한다. 요청 동일성은 `resourceId`의 UUID 값으로 비교한다.
- 성공한 예약 행을 키 기록으로 사용한다. TTL로 삭제하지 않고 실패한 생성 요청의 키는 보존하지 않는다.
- 같은 사용자·키·대상은 최초 201·Location·JSON 필드 값을 재현하고 예약·수량을 추가 변경하지 않는다. 최초 `Idempotent-Replayed: false`, 재요청은 `true`다.
- 같은 사용자·키·다른 대상은 409 `IDEMPOTENCY_CONFLICT`다. 경쟁에서 실패한 대상의 수량 차감도 롤백한다.
- 취소 후 생성 재요청도 최초 `CONFIRMED` 결과를 재현한다. 현재 `CANCELLED` 상태와 `cancelledAt`은 GET에서 확인한다.
- 이는 성공한 키의 추가 예약 방지 보장이다. 실패 응답의 영구 재현이나 네트워크의 한 번 전달을 보장하지 않는다. 재요청도 인증·제한·Redis 장애에 먼저 차단될 수 있다.

## 스키마와 시각

- `resources`는 UUID PK, 이름, capacity, available, created_at을 저장한다. 이름은 `varchar(100) NOT NULL`, 수량은 정수 NOT NULL이며 capacity 0~1000·available 0~capacity CHECK를 둔다.
- `reservations`는 UUID PK, 대상 UUID FK, 사용자 UUID, 키 `varchar(64)`, 상태 `varchar(16)`, created_at, cancelled_at을 저장한다. cancelled_at 이외에는 NOT NULL이다.
- `uq_reservations_user_key(user_id, idempotency_key)` 유일 제약과 `resource_id` 검색 인덱스를 둔다. status는 두 상태만 허용하며 CONFIRMED는 cancelled_at이 null, CANCELLED는 값이 있어야 한다.
- 예약의 resource_id·user_id·key·created_at은 생성 후 변경하지 않는다. 소유자 조회는 예약 PK와 user 조건을 사용한다.
- DB는 `timestamptz`, API는 UTC ISO-8601이다. Instant를 마이크로초로 정규화하고 INSERT/UPDATE RETURNING으로 저장된 값을 읽어 응답에 사용한다. 최초·재요청 시각을 일치시킨다. Unit에서는 고정 Clock을 쓴다.
- 테이블 간 수량 보존식은 CHECK만으로 보장되지 않는다. 트랜잭션과 실제 PostgreSQL 통합 테스트로 검증한다.

## 생성의 트랜잭션 경계

1. 생성 Service는 트랜잭션 없이 사용자·키를 조회하고, 기존 행이 있으면 대상 UUID를 비교해 재현 또는 충돌 처리한다.
2. 없으면 별도 Writer Bean을 호출해 READ COMMITTED 트랜잭션을 시작한다. `UPDATE resources SET available = available - 1 WHERE id = :resourceId AND available > 0`으로 차감한다.
3. 갱신 0건이면 새 SELECT로 사용자·키를 재조회한다. 선행 성공을 재현하거나 키 충돌을 반환하고, 키가 없으면 대상 없음 404와 품절 409 `SOLD_OUT`을 구분한다.
4. 갱신 1건이면 예약 INSERT와 차감을 함께 커밋한다.
5. 유일 위반이면 Writer 전체를 롤백한 뒤 Service에서 SQLSTATE `23505`와 `uq_reservations_user_key` 이름을 함께 확인한다. 해당 위반만 새 조회로 재현·충돌 처리한다.

- 자기 호출이나 호출자의 상위 트랜잭션으로 Writer 경계를 무효화하지 않는다.
- 실패한 트랜잭션 안에서 예외를 삼키고 계속 조회하지 않는다. 다른 PK·FK·연결 오류를 재요청으로 분류하지 않는다.
- 경쟁 이후 기존 예약을 조회할 수 없으면 내부 불변식 위반이다. 성공 처리하거나 무제한 재시도하지 않는다.

## 취소와 잠금

1. 한 Writer 트랜잭션에서 예약 ID와 소유자로 조회한다. 없으면 404, 이미 CANCELLED면 기존 상태·시각을 그대로 반환한다.
2. CONFIRMED이면 대상 행을 `FOR UPDATE`로 잠근 뒤 예약을 소유자·`status='CONFIRMED'` 조건으로 CANCELLED로 갱신한다.
3. 상태 갱신 1건일 때만 `available < capacity` 조건으로 수량 1개를 복구한다. 복구 0건은 내부 불변식 위반으로 전체 롤백한다.
4. 상태 갱신 0건이면 선행 취소가 완료됐으므로 추가 복구하지 않는다. 다시 조회한 CANCELLED 결과를 반환한다.

- 생성과 취소의 갱신 잠금 순서는 `resource → reservation`이다. 취소의 최초 소유자 조회로 예약 행을 먼저 잠그지 않는다.
- 중복 취소는 200과 동일한 취소 시각을 반환한다. 상태·수량 복구는 함께 커밋한다.
- 초기값은 lock timeout 2초, statement timeout 5초, 풀 최대 10개, 연결 획득 대기 3초다. 각 트랜잭션 시작 시 DB timeout을 `SET LOCAL`로 적용한다.
- DB 의존성 장애·잠금 시간 초과는 503과 전체 롤백이다. 분류하지 못한 내부 결함을 409·503으로 숨기지 않는다.
