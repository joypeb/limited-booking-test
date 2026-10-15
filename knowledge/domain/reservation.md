# reservation 도메인 지식

## 1. 기준과 현재 상태

- 패키지: `com.limitedbooking.backend.reservation`. 작성: 2026-10-13, 재확인: 2026-10-15.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절, [API 규칙](../rules/api-and-security.md), [트랜잭션 규칙](../rules/domain-and-transactions.md), [요청 제한 규칙](../rules/rate-limit.md).
- 설계: 기존 확정 규칙. V1·StoredReservation·ReservationStatus·읽기 Repository와 저장 IT는 구현·실행 검증됨. 업무 Writer·HTTP·보안/제한은 미구현.

## 2. 목적·책임·경계

예약의 생성·소유자 조회·취소를 담당한다. 수량 변경은 resource와 하나의 PostgreSQL 트랜잭션으로 반영한다. 결제·만료·대기열·목록·재활성화·행 삭제는 제외한다. Redis는 진입 제한만 담당한다.

## 3. 용어와 데이터

| 필드 | 의미·타입 | 허용값·불변 여부 | 근거 |
| --- | --- | --- | --- |
| id / resourceId | UUID 예약/대상 | 필수·불변, resourceId는 존재하는 대상 FK | 계획서 5.1 |
| userId | 인증 UUID | 클라이언트 입력·응답 아님, 불변 | 계획서 2 |
| idempotencyKey | 문자열 | ASCII 영문·숫자·밑줄·하이픈 1~64, 변환 없음 | 계획서 2 |
| status | 문자열 | CONFIRMED 또는 CANCELLED | 계획서 5.1 |
| createdAt | UTC Instant / timestamptz | 저장된 마이크로초 값·불변 | 계획서 5.1 |
| cancelledAt | UTC 시각/null | CONFIRMED는 null, CANCELLED는 필수 시각 | 계획서 4.1 |

설계 제약은 `uq_reservations_user_key(user_id,idempotency_key)`, 상태/취소 시각 CHECK, resource_id 인덱스다. 성공 행·키를 TTL로 삭제하지 않는다. 업무 V1을 구현·검증했다. 상태 전이 경로·불변 컬럼 변경까지 강제하는 트리거는 두지 않는다.

## 4. 불변식과 상태 전이

| 규칙 | 적용 경계 | 기대값 |
| --- | --- | --- |
| reservation-001 | 커밋된 상태 | available + CONFIRMED 수 = capacity, 0≤available≤capacity |
| reservation-002 | 사용자·키 | 최대 예약 1개, 다른 키로 추가 예약 가능 |
| reservation-003 | 취소 | 예약당 복구 최대 1회 |

| 현재 상태 | 명령 | 다음 상태 | 수량 변화 | 응답 |
| --- | --- | --- | --- | --- |
| 없음 | 생성 성공 | CONFIRMED | -1 | 201 |
| CONFIRMED | 소유자 취소 | CANCELLED | +1 | 200 |
| CANCELLED | 중복 취소 | CANCELLED | 0 | 200, 동일 취소 시각 |
| 어느 성공 상태든 | 같은 키·대상 생성 | 유지 | 0 | 최초 CONFIRMED·201 재현 |
| 어느 성공 상태든 | 같은 키·다른 대상 | 유지 | 0 | 409 IDEMPOTENCY_CONFLICT |

## 5. 유스케이스와 API 계약

| API | 인증·입력 | 성공 | 실패 | 변경 |
| --- | --- | --- | --- | --- |
| POST /api/reservations | 인증 UUID, resourceId·Idempotency-Key | 201 CreatedReservationView, Location, Idempotent-Replayed | 400·401·404·409·413·415·429·503 | 수량-1·행 INSERT 함께 |
| GET /api/reservations/{id} | 소유자·UUID | 200 ReservationView | 400·401·404·503, 공통 본문 초과 413 | 없음 |
| POST /api/reservations/{id}/cancel | 소유자·UUID·본문 없음 | 200 ReservationView | 400·401·404·413·429·503 | 최초만 상태·수량+1 함께 |

타인 접근은 관리자도 404다. 생성 응답은 id/resourceId/status/createdAt이며 현재 응답에 필수 cancelledAt가 추가된다. userId·quantity를 입력받지 않는다.

## 6. 멱등성·트랜잭션·경쟁·장애

키 범위는 인증 사용자·키이며 대상 UUID 값으로 비교한다. 실패 키는 남기지 않는다. 취소 후에도 성공 키를 유지하고 최초 201·Location·JSON 필드 값을 재현한다. 최초 Replay=false, 재요청=true다.

Service는 트랜잭션 없이 기존 키를 확인하고 별도 Writer Bean에서 READ COMMITTED로 조건부 available>0 UPDATE와 INSERT를 커밋한다. UPDATE 0이면 새 SELECT로 키·대상·품절을 구분한다. SQLSTATE 23505와 제약 이름을 모두 확인한 유일 위반만 Writer 전체 롤백 후 Service의 새 조회로 처리한다. 다른 제약·접속 오류를 재요청으로 간주하지 않는다.

취소는 소유자 조회 후 resource FOR UPDATE→소유자·CONFIRMED 조건 예약 갱신 순서다. 갱신 1건만 available<capacity 조건으로 복구하며 복구 0건이면 전체 롤백한다. 잠금 순서는 resource→reservation이다. 자기 호출·상위 트랜잭션·실패 트랜잭션 내 계속 조회는 금지한다.

DB timeout은 트랜잭션 시작 SET LOCAL로 lock 2초·statement 5초, 풀 최대 10·획득 3초 설계다. 의존성 장애·timeout은 503·전체 롤백, 내부 불변식 결함은 500으로 탐지한다. 커밋 뒤 응답 유실은 동일 키 재요청으로 복구한다. 크기 제한→인증→예약 쓰기 제한→입력 검증→서비스 순서이며 429/Redis 503은 DB 무변경이다.

## 7. 검증할 사례와 실제 증거

| 규칙 | 사례 | 기대 결과 | 검증 상태 |
| --- | --- | --- | --- |
| reservation-001~003 | capacity=1 예약·재요청·취소·재요청·중복 취소 | 추가 예약 없음, 복구 1회, 수량식 유지 | 수동 사례 작성·대조됨, HTTP/DB 미실행 |
| reservation-002 | 마지막 1개 동일 키 / 다른 대상 동일 키 경쟁 | 한 성공 행 / 한 성공과 충돌, 패자 수량 롤백 | 후속 IT 예정 |
| reservation-003 | 중복 취소·생성/취소 경쟁 | 동일 취소 시각·수량식 유지 | 후속 IT 예정 |
| 롤백 | UPDATE 뒤 INSERT 실패 | 행·수량 부분 반영 없음 | 후속 IT 예정 |

실제 검토·실행 기록은 [증거 문서](../../docs/test-evidence.md)에서 구분한다. 위 후속 테스트는 아직 존재하거나 실행된 것으로 간주하지 않는다.

## 8. 구현 연결과 변경 영향

| 책임 | 실제 파일·예정 위치 | 상태 |
| --- | --- | --- |
| 앱 골격 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java) | 존재, 해당 기능 없음 |
| 읽기 DTO·Repository | [ReservationRepository](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationRepository.java)·StoredReservation·ReservationStatus | 구현·실행 검증됨 |
| 입력 규칙·Controller·Service·Writer·필터 | 해당 기능 패키지 예정 | 미구현 |
| 업무 V1·저장/조회 테스트 | [V1](../../backend/src/main/resources/db/migration/V1__create_reservation_schema.sql)·SchemaIT·ReservationRepositoryIT | 구현·실행 검증됨 |
| 업무 쓰기 SQL·HTTP/경쟁 테스트 | 후속 단계 예정 | 미구현 |
| 고정 계약·결정 기록 | [OpenAPI 초안](../../docs/openapi.yaml)·[결정 기록](../../docs/decisions.md) | 작성·문서 인수됨, 이름 strip 정책 확정·T025 사용자 확인 완료; 독립 재설명/기능 실행 미검증 |

계약·업무 변경은 계획서·관련 도메인·공통 규칙·OpenAPI·테스트를 함께 검토한다. 서비스 경계·키·수량·권한·실패 무변경은 관련 도메인에 함께 영향을 준다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 업무 쓰기·HTTP·필터 미구현 | 응답·경쟁·의존성 장애 미확인 | 3단계 입력 규칙 이후 Writer·HTTP 개발 |
| 실행 미검증 | 계약 선언이 실제 동작 증거가 아님 | 후속 실제 HTTP·의존성 시험 |

기존 규칙 이외의 새 정책을 추가하지 않았다.

### 2026-10-14 저장 길이 보장 범위 명확화

계획서 5.1의 varchar 저장 상한과 요청 입력 거부를 구분한다. 길이는 문자 수이고 이름의 😀는1자다. 비공백 이름101자·키65자는 직접 저장에서 거부하지만 비공백 이름100자/키64자 뒤 일반 공백만 초과하면 상한으로 절단 저장될 수 있다. 이 예외와 명시적 cast의 절단을 원본 요청 검증 보장으로 확대하지 않는다. 이름 strip·최소 길이·키 문자 규칙은 기존 후속 입력 검증에서 수행한다. 기존 스키마 타입·외부 API 계약은 유지하며 관련 직접 저장 사례는 003 명세·데이터 모델에 정의했다. SchemaIT에서 실제 업무 V1의 저장 길이를 검증했다. 입력 API의 인수는 후속이다.

### CHK022 실패 처리 검증 연결

2단계의 정상 연결 고의 SQL 실패에서는 업무 V1의 두 테이블·인덱스가 부분 생성돼 남지 않아야 하고 성공 적용 이력은0건이다. 실패 DB의 증거/종료 후 새 테스트 DB에서 원본 V1로 준비를 재개한다. 테스트 초기화는 대상·예약의 부분 삭제를 남기지 않고 전체 롤백하며, 장애 제거 후 같은 DB의 새 트랜잭션으로 재요청한다. 기존 행/값·구조·이력을 별도 연결에서 비교한다. 업무 예약 생성/취소의 장애·경쟁 보장과는 별개의 테스트 지원 계약이며 SchemaIT/DatabaseSupportIT에서 실행 검증했다. [003 실패 처리 계약](../../specs/003-define-db-schema/contracts/persistence-contract.md)에 근거와 검증 사례를 연결했다.

### 현재 저장 기반 구현·검증 상태 (2026-10-15)

V1·읽기 Repository와 대응 IT는 구현·실행 검증됨이다. 직접 저장 제약의 보장 범위는 스키마에 한정하며 입력 strip/키 문자 규칙·HTTP·권한·Writer·경쟁·응답 재현은 설계됨/미구현이다. [TestData](../../backend/src/test/java/com/limitedbooking/backend/support/TestData.java)는 실행 소유 DataSource의 독립 커밋과 한 트랜잭션 삭제를 사용한다. [DatabaseAssertions](../../backend/src/test/java/com/limitedbooking/backend/support/DatabaseAssertions.java)는 단일SELECT에서 capacity/available/확정 수를 검사한다. 보존식 불일치를 스키마가 막는다는 보장은 없으며 실제 업무 쓰기의 보장은 후속이다.

fresh/reapplied 두 독립 DB에서 같은 제약·조회·초기화/검사 사례를 검증했다. 실패사본 V1 전체 DDL 롤백과 새DB복구, 초기화 중간 실패의 전체 행/값·구조/이력 보존 및 같은DB 명시적 재요청은 [증거](../../docs/test-evidence.md)를 따른다. 연결 단절·프로세스 중단·부하는 미검증이다.

### 3단계 순수 규칙 설계 (2026-10-15)

[004 계획](../../specs/004-validate-domain-rules/plan.md)과 [규칙 계약](../../specs/004-validate-domain-rules/contracts/rules-contract.md)에 검증 생성자를 가진 IdempotencyKey record와 기존 ReservationStatus를 사용하는 CancellationDecision을 설계했다. 무효 키는 IllegalArgumentException, 내부 상태 null은 NullPointerException이며 HTTP 변환은 후속 경계의 책임이다.5단계 생성 키 사용·6단계 이미 취소됨 분기 사용은 별도 인수한다. IdempotencyKey와 대응 Unit56건은 구현·실행 검증됨이다. CancellationDecision·실제 취소/경쟁은 아직 미검증이다.

생성 키의 순수 검증: [IdempotencyKey](../../backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java)·[IdempotencyKeyTest](../../backend/src/test/java/com/limitedbooking/backend/reservation/IdempotencyKeyTest.java). compact constructor가 parse와 new를 함께 보호하며 원문 equals/hashCode를 제공한다. Repository·StoredReservation의 String은 유지했다. 사용자·키 유일성/대상 충돌/성공 응답 재현·TTL/HTTP 변환과 실제 요청 경로 사용은5단계에서 검증한다.
