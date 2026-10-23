# reservation 도메인 지식

## 1. 기준과 현재 상태

- 패키지: `com.limitedbooking.backend.reservation`. 작성: 2026-10-13, 재확인: 2026-10-22.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절, [API 규칙](../rules/api-and-security.md), [트랜잭션 규칙](../rules/domain-and-transactions.md), [요청 제한 규칙](../rules/rate-limit.md).
- 설계: 기존 확정 규칙. V1·StoredReservation·ReservationStatus·읽기 Repository와 저장 IT는 구현·실행 검증됨. 5단계 생성 Controller/Service/Writer·멱등 재현·정확한 유일 위반 복구·장애 롤백을 구현·검증했다. 소유자 GET/취소를 6단계에서 구현하고 HTTP·실제 DB 취소/롤백을 검증했다. 실제 경쟁 반복·Redis 제한도7~8단계에서 구현·검증했다.


**9단계 계약 검증 상태(2026-10-23):** 010 T001~T042 구현·실행 인수 완료(PR #12 리뷰 보완 포함). T042 `./gradlew build verifyContract verifyContractDependencies` 종료0, Unit298·일반IT315·Contract102(업무96+프로브6), 실패/오류/skip0. T032 당시 Unit294·IT312·Contract96은 historical 값이다. 제품 변경은 PR #12 리뷰 보완의 `ResourceController` 엄격 UUID binder 1건이며 SQL·Gradle·OpenAPI·정책 변경은 없다. 고정 OpenAPI와 실제 HTTP의 정상 5작업·적용표 전체 오류·응답 사본 훼손44종·실제 DTO 타입 변이 RED→원복 GREEN을 확인했다. Qodana는 사용자 지시로 건너뛴 미실행 후속이다. [010 작업](../../specs/010-openapi-contract/tasks.md)·[실행 근거](../../docs/test-evidence.md)를 따른다. 아래 8단계 상태와 날짜별 이력은 해당 시점 기록이다.

**8단계 검증 상태(2026-10-22):** 2026-10-22 8단계 구현·실행 인수 완료: `./gradlew build verify verifyContractDependencies` 종료0, 일반 Unit193·IT312, 실패/오류/skip0. IT에는 기본 Redis5·HTTP제한37·실제장애12·경쟁110회와 종료지원fixture5가 포함된다. T001~T036/T038 37개 완료, Qodana T037만 사용자 “나중에 할거야” 지시에 따른 미실행 후속이며 이번 완료·PR·병합 게이트에서 제외한다. 이 요약은 구현·실행 인수 시점의 기록이다. 이후 PR·독립 리뷰·병합 결과는 해당 PR과 최종 보고에서 확인한다. [실행 근거](../../docs/test-evidence.md)·[009 작업](../../specs/009-redis-rate-limit/tasks.md)을 따른다. 아래 날짜별 이력은 해당 시점 기록이다.

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
| reservation-001~003 | capacity=1 예약·재요청·취소·재요청·중복 취소 | 추가 예약 없음, 복구 1회, 수량식 유지 | ReservationOwnershipIT·ReservationCancelIT HTTP/DB 검증 |
| reservation-002 | 마지막 1개 동일 키 / 다른 대상 동일 키 경쟁 | 한 성공 행 / 한 성공과 충돌, 패자 수량 롤백 | ReservationConcurrencyIT C02~C10 실제 HTTP/DB 반복 검증 |
| reservation-003 | 중복 취소·생성/취소 경쟁 | 동일 취소 시각·수량식 유지 | ReservationConcurrencyIT C02~C10 실제 HTTP/DB 반복 검증 |
| 롤백 | UPDATE 뒤 INSERT 실패 | 행·수량 부분 반영 없음, 같은 키 재시도 | ReservationRollbackIT 실제 HTTP/DB 검증 |

실제 검토·실행 기록은 [증거 문서](../../docs/test-evidence.md)에서 구분한다. 생성·재현·롤백은 5단계, 소유권·취소·롤백은 6단계에서 실행했다. 실제 경쟁 반복과 Redis 연결 회귀까지 검증했다.

## 8. 구현 연결과 변경 영향

| 책임 | 실제 파일·예정 위치 | 상태 |
| --- | --- | --- |
| 앱 골격 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java) | 생성 기능 포함 |
| 읽기 DTO·Repository | [ReservationRepository](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationRepository.java)·StoredReservation·ReservationStatus | 구현·실행 검증됨 |
| 순수 키·취소 판단 | IdempotencyKey·CancellationDecision 및 대응 Unit | 구현·Unit 검증됨 |
| 생성 Controller·Service·Writer | reservation 패키지, 아래5단계 구현 링크 | 구현·실제Redis/HTTP/DB 검증됨 |
| 업무 V1·저장/조회 테스트 | [V1](../../backend/src/main/resources/db/migration/V1__create_reservation_schema.sql)·SchemaIT·ReservationRepositoryIT | 구현·실행 검증됨 |
| 생성 쓰기 SQL·HTTP | ResourceRepository/ReservationRepository, 생성3IT | 구현·실제경쟁110회·Redis연결 회귀 검증됨 |
| 고정 계약·결정 기록 | [고정 OpenAPI](../../docs/openapi.yaml)·[결정 기록](../../docs/decisions.md) | 고정 계약 대조·실제 HTTP 인수 완료; 2026-10-23 9단계 업무 전체 자동 OpenAPI 대조 완료 |

계약·업무 변경은 계획서·관련 도메인·공통 규칙·OpenAPI·테스트를 함께 검토한다. 서비스 경계·키·수량·권한·실패 무변경은 관련 도메인에 함께 영향을 준다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 예약 GET/취소 구현 | 소유권·수량 복구·롤백 IT 검증 | 6단계 최종 인수는 실행 증거 참조 |
| 실제 경쟁/Redis | 지정110회·장애·최종인수 완료, 모든순서의 증명은 아님 | 오케스트레이터 최종검토; 자동계약/심화는 후속 |

기존 규칙 이외의 새 정책을 추가하지 않았다.

### 2026-10-14 저장 길이 보장 범위 명확화

계획서 5.1의 varchar 저장 상한과 요청 입력 거부를 구분한다. 길이는 문자 수이고 이름의 😀는1자다. 비공백 이름101자·키65자는 직접 저장에서 거부하지만 비공백 이름100자/키64자 뒤 일반 공백만 초과하면 상한으로 절단 저장될 수 있다. 이 예외와 명시적 cast의 절단을 원본 요청 검증 보장으로 확대하지 않는다. 이름 strip·최소 길이·키 문자 규칙은 기존 후속 입력 검증에서 수행한다. 기존 스키마 타입·외부 API 계약은 유지하며 관련 직접 저장 사례는 003 명세·데이터 모델에 정의했다. SchemaIT에서 실제 업무 V1의 저장 길이를 검증했다. 입력 API의 인수는 후속이다.

### CHK022 실패 처리 검증 연결

2단계의 정상 연결 고의 SQL 실패에서는 업무 V1의 두 테이블·인덱스가 부분 생성돼 남지 않아야 하고 성공 적용 이력은0건이다. 실패 DB의 증거/종료 후 새 테스트 DB에서 원본 V1로 준비를 재개한다. 테스트 초기화는 대상·예약의 부분 삭제를 남기지 않고 전체 롤백하며, 장애 제거 후 같은 DB의 새 트랜잭션으로 재요청한다. 기존 행/값·구조·이력을 별도 연결에서 비교한다. 업무 예약 생성/취소의 장애·경쟁 보장과는 별개의 테스트 지원 계약이며 SchemaIT/DatabaseSupportIT에서 실행 검증했다. [003 실패 처리 계약](../../specs/003-define-db-schema/contracts/persistence-contract.md)에 근거와 검증 사례를 연결했다.

### 현재 저장 기반 구현·검증 상태 (2026-10-15)

V1·읽기 Repository와 대응 IT는 구현·실행 검증됨이다. 직접 저장 제약의 보장 범위는 스키마에 한정하며 입력 strip/키 문자 순수 규칙과 취소 판단은3단계에서 구현·Unit 검증했다. HTTP·권한·Writer·경쟁·응답 재현은 미구현이다. [TestData](../../backend/src/test/java/com/limitedbooking/backend/support/TestData.java)는 실행 소유 DataSource의 독립 커밋과 한 트랜잭션 삭제를 사용한다. [DatabaseAssertions](../../backend/src/test/java/com/limitedbooking/backend/support/DatabaseAssertions.java)는 단일SELECT에서 capacity/available/확정 수를 검사한다. 보존식 불일치를 스키마가 막는다는 보장은 없으며 실제 업무 쓰기의 보장은 후속이다.

fresh/reapplied 두 독립 DB에서 같은 제약·조회·초기화/검사 사례를 검증했다. 실패사본 V1 전체 DDL 롤백과 새DB복구, 초기화 중간 실패의 전체 행/값·구조/이력 보존 및 같은DB 명시적 재요청은 [증거](../../docs/test-evidence.md)를 따른다. 연결 단절·프로세스 중단·부하는 미검증이다.

### 3단계 순수 규칙 설계 (2026-10-15)

[004 계획](../../specs/004-validate-domain-rules/plan.md)과 [규칙 계약](../../specs/004-validate-domain-rules/contracts/rules-contract.md)에 검증 생성자를 가진 IdempotencyKey record와 기존 ReservationStatus를 사용하는 CancellationDecision을 설계했다. 무효 키는 IllegalArgumentException, 내부 상태 null은 NullPointerException이며 HTTP 변환은 후속 경계의 책임이다.5단계 생성 키 사용·6단계 이미 취소됨 분기 사용은 별도 인수한다. IdempotencyKey와 대응 Unit56건은 구현·실행 검증됨이다. CancellationDecision·대응 Unit5건도 구현·실행 검증됨이다. 실제 취소/경쟁은 미구현·미검증이다.

생성 키의 순수 검증: [IdempotencyKey](../../backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java)·[IdempotencyKeyTest](../../backend/src/test/java/com/limitedbooking/backend/reservation/IdempotencyKeyTest.java). compact constructor가 parse와 new를 함께 보호하며 원문 equals/hashCode를 제공한다. Repository·StoredReservation의 String은 유지했다. 사용자·키 유일성/대상 충돌/성공 응답 재현·TTL/HTTP 변환과 실제 요청 경로 사용은5단계에서 검증한다.

취소 판단의 순수 검증: [CancellationDecision](../../backend/src/main/java/com/limitedbooking/backend/reservation/CancellationDecision.java)·[CancellationDecisionTest](../../backend/src/test/java/com/limitedbooking/backend/reservation/CancellationDecisionTest.java). CONFIRMED→CANCEL_NOW, CANCELLED→ALREADY_CANCELLED, 내부 null→NPE이며 필드·I/O·Clock·저장 의존성 없이 반복 판단한다. 실제 상태 갱신·수량 복구·동일 취소 시각과6단계 Writer 사용은 미완료다.

## 5단계 생성 설계 연결 (2026-10-19)

[006 구현 계획](../../specs/006-reservation-create/plan.md)과 [생성 계약](../../specs/006-reservation-create/contracts/reservation-create-contract.md)에 트랜잭션 밖 Service·별도 READ COMMITTED Writer·조건부 차감/INSERT RETURNING·정확한 사용자/키 유일 위반 분류를 설계했다. Service는 상위 활성 트랜잭션을 거부하고, Writer 유일 위반은 전체 롤백 후 새 조회로만 복구한다. UPDATE0 뒤 정상 트랜잭션 안 새 SELECT와 구분한다. 생성 시각은 MICROS 정규화·저장 반환값을 최초/재현 모두에 사용한다. 실제 UPDATE 뒤 테스트 전용 저장 실패 주입·별도 연결의 불변식 검사·동일 키 재시도를 계획했다.

기존 업무 규칙·외부 계약·스키마 변경은 없다. 설계 당시 신규 Controller/Service/Writer/분류기/쓰기 SQL과 지정 세 IT는 미구현이었으며 아래 구현 기록에서 완료했다. 준비된 CANCELLED 행의 최초 생성 재현은 5단계에서 다루고 실제 조회/취소·경쟁 반복·Redis 제한은 후속이다. 앞의 과거 확인일별 구현/검증 기록은 당시 범위다.


### 2026-10-19 5단계 구현·검증 연결

- [ReservationController](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationController.java) → [ReservationService](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationService.java) → [ReservationWriter](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationWriter.java) → 두 Repository의 조건UPDATE/INSERT RETURNING을 구현했다. DTO는 CreateReservationRequest/CreatedReservationView/CreateReservationResult record다.
- Service는 활성 상위 트랜잭션을 거부한다. Writer는 READ_COMMITTED, 같은 연결에서 SET LOCAL lock_timeout2s·statement_timeout5s를 적용한다. UPDATE0의 새 SELECT와 오류 후 롤백 완료 뒤 Service 새 SELECT는 별도 경로다.
- [분류기](../../backend/src/main/java/com/limitedbooking/backend/common/UniqueViolationClassifier.java)는 동일 PSQLException의23505/정확한 제약명만 확인한다. Cause/nextException은 identity 순환 방지 탐색이다. 55P03 UncategorizedSQLException과 실제 연결 획득 원인만 공통503으로 추가 연결했다. 다른 제약/내부결함500은 유지한다.
- [ReservationCreateIT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationCreateIT.java)16건, [IdempotencyIT](../../backend/src/test/java/com/limitedbooking/backend/reservation/IdempotencyIT.java)5건, [ReservationRollbackIT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationRollbackIT.java)7건과 Service/Writer/Classifier Unit21건을 작성했다. 실제 실행 결과는 [증거](../../docs/test-evidence.md)를 따른다.
- 최초/재현/DB 시각 ...123456Z, CANCELLED 준비 상태 불변, 실패 키 재사용, 실제 차감 후 실패 롤백, 직접 Writer 유일 위반·lock/statement/획득 장애503와 복구를 확인했다. 각 IT 풀10/3000ms·동적 DB·실제 Authorization·단일SELECT 수량식을 사용한다.
- 앞 절의 취소/소유자 GET·실제 경쟁 사례는 설계이며 아직 미구현/미실행이다. 다음 한 작업은6단계 소유자의 예약 조회·취소다.

입력 보완: CreateReservationRequest.fromJson은 원문36자 UUID를 확인 후 값으로 변환한다. Base64 22/24자·문자열 앞뒤공백·축약형은400이며 실패 키를 남기지 않는다. 정상 UUID 영문 대소문자는 모두 허용한다. 관련 HTTP 회귀5건을 추가했다.


## 6단계 조회·취소 구현 연결 (2026-10-20)

[ReservationView](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationView.java)는 현재 상태의 다섯 필드를 반환하고 cancelledAt null도 직렬화한다. [Controller](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationController.java)는 인증 UUID만 Service에 넘기며 UUID 경로는 표준 8-4-4-4-12 텍스트를 검사한다(대소문자 허용). [Service](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationService.java)의 get은 `findByIdAndUser` 단건 SELECT, cancel은 상위 트랜잭션을 거부한 뒤 별도 Writer를 호출한다. 타인·관리자·없는 예약은 동일404다.

[Writer](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationWriter.java)는 READ COMMITTED/SET LOCAL→소유자 조회→`CancellationDecision.ALREADY_CANCELLED` 조기 반환 또는 대상 FOR UPDATE→CONFIRMED 조건 상태UPDATE→1건일 때만 available<capacity 조건 복구→저장값 재조회 순서다. 복구0건은 내부 오류500과 전체 롤백이며 분류된 timeout은503이다. 취소 timestamp는 Clock UTC 마이크로초이며 응답은 DB 재조회 값이다.

[OwnershipIT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationOwnershipIT.java)·[CancelIT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationCancelIT.java)는 실제 Basic HTTP/격리 PostgreSQL·수량 보존식으로 검증한다. 취소 복구 장애는 테스트 Spy에만 있으며 실제 상태 갱신 후 실패·롤백을 확인한다. 중복 취소, 대상 잠금 중 이미 취소된 분기, 낡은 CONFIRMED 조회 뒤 조건UPDATE0 분기를 구분한다. 실제 경쟁 반복은 7단계다. 세부 명령·고의 변경 탐지·실행 수는 [실행 증거](../../docs/test-evidence.md)의 6단계를 따른다.


### PR #6 적대적 리뷰: 인코딩 경로의 취소 본문 (2026-10-20)

취소 본문 검증은 raw URI 기반 보안 필터에만 의존하지 않는다. MVC가 디코딩한 경로로 Handler를 선택한 뒤 Controller에서도 `RequestBodyLimitFilter.getCachedBody`의 실제 바이트가0인지 검사하고 Service 호출 전에 거부한다. 따라서 `%63ancel` 또는 `%72eservations` 경로에도 비어 있지 않은 본문은400이며 상태·수량을 변경하지 않는다. 동일 경로의 빈 본문은 정상 취소한다. 두 경로의 실제 HTTP/DB 회귀를 추가했고, RED에서는200/CANCELLED/available1을 관측한 뒤 수정했다. API·도메인 규칙 변경 없이 기존 계약의 적용 누락을 고쳤다. [실행 증거](../../docs/test-evidence.md)의 PR6 리뷰 기록을 따른다.


### 7단계 생성·취소 경쟁 인수 기준 명확화 (2026-10-20)

사용자 선택으로 [7단계 명세](../../specs/008-reservation-concurrency/spec.md)의 일반 생성·취소 경쟁 10회에 더해 두 순서를 각각 제어하여 각 10회 검증한다. 취소 복구 확정 후 생성 판단은 생성 성공·새 확정1·잔여0, 생성 품절 판단 후 취소 복구는 품절·새 예약 없음·잔여1이다. 기존 예약은 두 경우 모두 취소 상태다. 매 회차 의도한 순서의 실제 관측과 모든 작업 종료 후 수량 보존을 확인하며, 미관측을 통과로 처리하지 않는다. 업무·API 계약 변경 없이 인수 범위를 명확히 한 것이며 구현·실행 검증은 후속이다.

### 7단계 경쟁 검증 설계 연결 (2026-10-20)

[구현 계획](../../specs/008-reservation-concurrency/plan.md)·[경쟁 계약](../../specs/008-reservation-concurrency/contracts/concurrency-contract.md)에 실제 HTTP/격리DB·11종 각10회·테스트 Spy 관측·단일 수량 snapshot·모든 작업 종료를 설계했다. 키 강제 경쟁 장벽은 트랜잭션 밖 최초 실제 빈 조회 뒤에 두고, 같은 자원 차감 후 양측 대기를 금지한다. 생성/취소 두 순서도 연결·잠금 보유 전 대기로 제어한다. 업무 계약·스키마 변경 없이 테스트 지원만 확장할 예정이며 아직 신규 테스트·실행 증거는 없다.

### U1 종료 실패 격리 설계 보완

7단계 경쟁 테스트는 클래스 단위 최초 종료 실패 상태를 유지하고 부모 setup의 첫 보호 검사로 reset·준비·요청 제출을 차단하도록 [계획](../../specs/008-reservation-concurrency/plan.md)·[작업](../../specs/008-reservation-concurrency/tasks.md)을 보완했다. 기본 무동작 보호 검사와 null-client 정리로 기존 테스트 동작을 유지하며 다음 회차 세 작업0회·최초 원인 보존의 별도 fixture 검증을 예정한다. 문서 설계 보완이며 코드 구현·실행 증거는 후속이다.


## 7단계 경쟁 검증 구현 연결 (2026-10-20)

[ReservationConcurrencyIT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationConcurrencyIT.java)는 C01~C11 각10회를 실제 HTTP/Basic·PostgreSQL로 검증한다. C07은 차감0→트랜잭션 안 재조회, C03/C04/C08은 실제23505/정확한 제약명→Writer전체롤백→트랜잭션 밖 재조회로 구분한다. 동일키 최초201·Location·필드/UTC정밀도, 패자대상 복구, 중복취소 동일시각/복구1회, 취소후 최초생성재현 계약을 유지했다.

C09/C10은 연결·잠금 보유 전 대기와 선행HTTP/별도DB확인으로 두 순서를 관측한다. C11은 지정키 차감 후 INSERT실패·전체롤백·같은키복구를 정상 경쟁키와 함께 검증한다. 제품후크·SQL·스키마·OpenAPI 변경 없음. 부모setup 첫 보호 검사가 최초종료실패 상태를 확인하고 다음 reset/준비/제출0회를 보장한다. 실제 부모setup fixture와 지원Unit10건을 연결하며 [실행 증거](../../docs/test-evidence.md)·[110회 기록](../../docs/concurrency-runs.json)을 따른다. 지정경로의 반복 증거이며 모든 실행순서/운영성능의 증명이 아니다. 다음은8단계다.


### 2026-10-21 최종 리뷰 보완 확인

최종 일반 verify Unit150·IT257(경쟁110회+지원fixture5 포함), 실패/오류/skip0. 종료/전송실패 차단·모든 오류/결과 보존·호출별 고유ID와 C07 경로기록을 보완했다. 제품·API·스키마는 유지한다. [최종 실행 증거](../../docs/test-evidence.md)·[110회 기록](../../docs/concurrency-runs.json)을 따른다. 이전 날짜의 실행 수는 당시 기록이다. Redis·심화·부하·외부CI는 후속이다.


### 2026-10-22 US2 Redis 제한 HTTP 연결

생성·취소 합산20/60초가 실제HTTP에 연결됐다. 성공키 재전송·중복취소·취소후 최초CONFIRMED 재현도 각1회 집계하며 초과429에서는 Controller/Service/TX0·모든행/시각/키/수량snapshot동일을 검증했다. 기존Writer/SQL·스키마·성공/소유권 계약은 유지한다. [RateLimitApiIT](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitApiIT.java)37건·[공통 관측](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitHttpTestSupport.java)·[실행 증거](../../docs/test-evidence.md)를 따른다. 실제Redis 장애·503 분류와 기존HTTP/110회 회귀·최종전체게이트는 후속이며 US2 오케스트레이터 중간검토 대기다.


### 2026-10-22 US3 실제 장애·복구 인수

Redis거부의생성/취소/성공키재전송은503·전체행snapshot동일이다. 제한통과뒤실제수량차감후pg_sleep6→기본statement timeout5초로DB503/전체롤백/count1을확인했고같은키복구201/count2였다. 기본Redis창이실제만료돼도성공예약키/최초생성응답은보존된다. [RedisFailureIT 계열](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RedisFailureIT.java)·[공통관측](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitHttpTestSupport.java)·[실행증거](../../docs/test-evidence.md)를따른다. US4 기존HTTP/110회·최종전체게이트는후속이며 US3 오케스트레이터 중간검토 대기다.


### 2026-10-22 US4 회귀·최종 인수

기존 HTTP는 기본20/60초·실제 Redis를 사용하고 사례별 ADMIN/A/B 소유키만 초기화한다. 경쟁은 별도10000/60초로110회 모두 실제 HTTP·Redis·DB를 통과하며 최종 단일SQL 수량 snapshot과 종료를 확인했다. 부모 setup 첫 보호검사 전에는 Redis/DB를 접근하지 않으며 종료/전송불확실 fixture에서 reset0을 검증했다. 취소 입력 검증은 SecurityIT, 순수 대상 API는 postgresOnly ResourceApiIT에 둔다. 업무 SQL/스키마·성공/소유권/취소·OpenAPI 계약은 유지한다. [증거](../../docs/test-evidence.md)·[110회 기록](../../docs/concurrency-runs.json)을 따른다.


### 9단계 US2 의존성 계약 연결 (2026-10-23)

[의존성 계약 시험](../../backend/src/test/java/com/limitedbooking/backend/common/ApiContractDependencyIT.java)은 별도 소유 DB/Redis 중단의5API/두쓰기503과 정상DB에서의업무전체행무변경·독립3API정상을 고정명세와 대조한다. 중단DB의사후snapshot과 기존정상DB의timeout롤백증거를 구분한다. 제품·SQL·정책은 유지하며 [T018~T020 실행증거](../../docs/test-evidence.md)를 따른다. T021이후/전체9단계인수·Qodana는미완료다.


### 9단계 계약 검증 최종 인수 (2026-10-23)

[계약 HTTP 시험](../../backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java)이 예약 생성201·Location·Replay, 소유자 조회·취소200, 409 SOLD_OUT/IDEMPOTENCY_CONFLICT, 타인·ADMIN 404를 고정 OpenAPI로 대조한다(ApiContractIT 94건, T042). 생성→재전송→GET→취소→중복 취소→취소 후 재전송→GET 7교환은 최초 CONFIRMED 4필드·Location을 재현하고 현재 CANCELLED를 반환하며, 최종 단일 SQL snapshot에서 예약1행·복구1회를 확인했다. T032와 T042 전체 게이트에서 기존 경쟁 110회(C01~C11 각10)·종료 fixture5·롤백7·취소13이 다시 통과했고 [110회 기록](../../docs/concurrency-runs.json)을 갱신했다. 앞 절의 미완료 표시는 당시 기록이다. 제품 변경은 PR #12 리뷰 보완의 `ResourceController` 엄격 UUID binder 1건이며 SQL·Gradle·OpenAPI·정책 변경은 없다. [실행 근거](../../docs/test-evidence.md)의 9단계 절과 [010 작업](../../specs/010-openapi-contract/tasks.md)을 따른다. Qodana는 사용자 지시로 건너뛴 미실행 후속이다.
