# Phase 0 조사: 소유자의 예약 조회·취소

## R1. 소유권 판정 위치
- **결정**: SQL 조건 `WHERE id = :id AND user_id = :userId`로 조회한다. 결과가 없으면 `ReservationNotFoundException` → 404 `NOT_FOUND`로 처리한다.
- **이유**: 타인 예약과 없는 예약이 같은 경로·같은 응답이 되어 존재 여부가 드러나지 않는다. 관리자 우회 분기도 생기지 않는다.
- **대안**: id로 조회한 뒤 Java에서 소유자를 비교하면 403/404 분기가 섞이고, 응답 시간으로 존재 여부가 드러날 위험이 있어 제외했다.

## R2. 취소 트랜잭션·잠금 순서
- **결정**: 별도 Writer Bean의 `@Transactional(READ_COMMITTED)`에서 계획서 5.3 순서를 따른다. 소유자 예약 조회(잠금 없음) → 대상 `SELECT ... FOR UPDATE` → `UPDATE ... AND status='CONFIRMED'` → 갱신 1건일 때 `UPDATE resources SET available=available+1 WHERE id=:id AND available < capacity` → 재조회.
- **이유**: 생성과 같은 resource → reservation 잠금 순서로 교착을 피한다. 조건부 UPDATE가 동시 취소에서 복구를 한 번으로 제한한다.
- **대안**: 예약 행을 `FOR UPDATE`로 먼저 잠그는 방식은 잠금 순서가 생성과 반대가 되므로 제외했다.

## R3. 조회의 트랜잭션
- **결정**: GET은 Service에서 Repository 단건 SELECT 하나로 처리하고 쓰기 트랜잭션을 두지 않는다.
- **이유**: 문장 하나라 일관성 문제가 없고, Service에 트랜잭션을 두지 않는 기존 규칙과도 맞다. statement_timeout은 쓰기 트랜잭션에만 SET LOCAL로 적용하며, 조회 timeout 정책은 계획서에 추가 요구가 없다.

## R4. 이미 취소된 분기
- **결정**: 조회한 상태를 `CancellationDecision.from`에 넘긴다. `ALREADY_CANCELLED`이면 잠금·갱신 없이 기존 행을 반환한다. 조건부 UPDATE가 0건이면(경쟁으로 이미 취소된 경우) 복구 없이 재조회해 반환한다.
- **이유**: 3단계 순수 규칙을 실제 요청 경로에 연동한다(계획서 3단계 완료 기준).

## R5. 복구 실패 주입
- **결정**: 테스트에서만 `ResourceRepository`의 복구 메서드를 `@MockitoSpyBean`으로 감싼다. 하나는 예외를 던지고, 다른 하나는 0을 반환하게 한다. 실제 상태 UPDATE는 그대로 실행한다.
- **이유**: 상태 UPDATE가 실제로 반영된 뒤 롤백되는지 확인할 수 있다. 5단계 `ReservationRollbackIT`의 방식과 같다.

## R6. 시각
- **결정**: `cancelled_at`은 `Instant.now(clock)`을 마이크로초로 자른 값이다. 응답에는 재조회한 저장값을 사용한다.

## R7. 오류 매핑
- 복구 0건은 `IllegalStateException`으로 처리해 기존 내부 오류 500 경로를 탄다. 409/503으로 숨기지 않는다. lock/statement timeout은 기존 503 `DEPENDENCY_UNAVAILABLE` 매핑을 재사용하며, 구현 시 기존 매핑이 있는지 확인한다.
