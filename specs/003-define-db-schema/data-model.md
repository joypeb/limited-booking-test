# 데이터 모델: 예약 저장 기반

설계 문서이며 아래 업무 테이블·record는 아직 구현하지 않았다. 기준은 [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 5.1절과 [명세](spec.md)다.

## resources

| 컬럼 | 저장 타입 | null | 규칙 |
| --- | --- | --- | --- |
| id | uuid | 불가 | PK, 생성 후 불변 |
| name | varchar(100) | 불가 | 저장 길이 상한 100. 공백 제거·최소 길이는 후속 입력 규칙 |
| capacity | integer | 불가 | 0~1000, 최초 수량 불변 |
| available | integer | 불가 | 0~capacity |
| created_at | timestamptz | 불가 | 생성 시각, UTC·마이크로초 |

수량 CHECK는 `capacity BETWEEN 0 AND 1000`, `available BETWEEN 0 AND capacity`다. 최초 정상 대상 준비는 available=capacity다. capacity=0도 허용한다. 자동 UUID·시각 생성 기본값 대신 호출 측이 명시 값을 제공한다.

## reservations

| 컬럼 | 저장 타입 | null | 규칙 |
| --- | --- | --- | --- |
| id | uuid | 불가 | PK |
| resource_id | uuid | 불가 | resources.id 참조, 생성 후 불변 |
| user_id | uuid | 불가 | 인증 사용자 UUID, 생성 후 불변 |
| idempotency_key | varchar(64) | 불가 | 성공 생성 키, 변환 없이 저장, 생성 후 불변 |
| status | varchar(16) | 불가 | CONFIRMED 또는 CANCELLED |
| created_at | timestamptz | 불가 | 생성 후 불변, UTC·마이크로초 |
| cancelled_at | timestamptz | 가능 | 상태 조합에 따라 필수 여부 결정 |

- `uq_reservations_user_key(user_id,idempotency_key)`는 대상·상태와 무관한 전체 행 유일성이다. 부분 인덱스나 resource_id를 포함한 유일성으로 바꾸지 않는다.
- FK는 예약의 고아 참조를 거부한다. ON DELETE CASCADE를 추가하지 않는다. users 테이블·회원 관리는 범위 밖이므로 user_id에 새 FK를 만들지 않는다.
- CHECK는 `(status = 'CONFIRMED' AND cancelled_at IS NULL) OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)`이다. status 자체의 NOT NULL도 유지한다.
- `idx_reservations_resource_id(resource_id)` 인덱스를 둔다. UNIQUE/PK가 제공하는 인덱스를 중복 생성하지 않는다.
- 주요 검증 SQLSTATE는 CHECK 23514, UNIQUE/PK 23505, FK 23503, NOT NULL 23502, 비공백 문자 길이 초과 22001이다. 사용자/키 UNIQUE는 이름도 비교한다. 세부 CHECK/FK 이름은 migration에서 일관되게 명명하고 해당 기대값으로 검증한다.
- 길이는 바이트가 아닌 문자 수이며 이름의 😀는 1자로 센다. 직접 SQL 길이 사례는 비공백 ASCII 100/101·64/65를 사용하고 이름 😀 100/101개도 확인한다. 이름은 비공백100자+일반공백1자, 키는 비공백64자+일반공백1자를 직접 바인딩해 각각100자·64자로 잘린 저장 값을 기대하는 별도 정상 사례를 둔다. 초과분이 일반 공백이라는 예외를 NBSP 등에 확대하지 않는다. 거부 시험에 명시적 varchar(n) cast를 사용하면 비공백 초과도 절단될 수 있으므로 사용하지 않는다. 문자열의 DB 특수 변환을 입력 검증으로 간주하지 않는다. 이름 strip·키 정규식·최소 길이 검증은 후속 단계다.

## 읽기 모델

- `resource.ResourceView(UUID id, String name, int capacity, int available)` — 조회의 4개 컬럼. created_at은 저장되지만 이 모델에 포함하지 않는다.
- `reservation.StoredReservation(UUID id, UUID resourceId, UUID userId, String idempotencyKey, ReservationStatus status, Instant createdAt, Instant cancelledAt)` — 예약의 7개 저장 컬럼. cancelledAt은 null 허용이다.
- `ReservationStatus`는 CONFIRMED/CANCELLED 두 값만 갖는다. 저장된 현재 상태를 읽고, 취소 예약의 상태를 조회 과정에서 CONFIRMED로 바꾸지 않는다.
- timestamptz는 JDBC OffsetDateTime→Instant로 변환한다. 고정 입력은 마이크로초로 맞추고 반환은 실제 저장 값을 사용한다. 생성 응답 재현용 모델은 여기서 추가하지 않는다.

## 관계와 상태 전이 경계

resources 1개에 reservations 여러 개가 연결된다. 동일 사용자도 다른 키로 여러 개를 보유한다. 성공 예약은 취소 후에도 남아 키를 점유한다. 업무 상태 전이는 CONFIRMED→CANCELLED이며 재활성화·행 삭제는 제공하지 않는다. 이번 DB CHECK는 현재 상태·시각 일치만 보장하고 전이 경로·불변 컬럼 UPDATE까지 강제하는 트리거는 만들지 않는다.

## 테스트 데이터와 수량 관측

TestData는 컨테이너가 만든 DataSource를 생성 시 전달받고 대상·예약을 명시 값으로 준비한다. 정상 준비는 트랜잭션 커밋 후 반환하며 다른 연결의 조회로 확인한다. 의도적 위반은 자동 보정하지 않는다. 테스트 초기화의 삭제는 업무 행 삭제 API와 별개이며 전용 DB에서만 수행한다.

DatabaseAssertions.assertInventoryConserved(UUID resourceId)는 하나의 SELECT로 resources의 capacity·available과 해당 CONFIRMED 예약 수를 집계한다. LEFT JOIN에서 상태 조건을 ON에 두고 `COUNT(reservation.id)`를 사용하거나 동등한 단일 문장 집계를 사용한다. 예약 없는 대상도 0으로 관측한다. 대상 없음을 capacity=0으로 대체하지 않는다.

관측한 `0≤available≤capacity` 및 `available+confirmedCount=capacity`를 함께 검사한다. 정상 10/8/확정2/취소1, 0/0/예약0, 전부 취소된 대상은 통과한다. 개별 수량 제약은 유효한 10/9/확정2와 없는 대상은 실패한다. 이 검사로 저장 제약만의 보장 범위를 확대해 주장하지 않는다.

## 실패 시 보존해야 하는 상태

업무 V1 사본의 고의 SQL 실패 뒤 resources·reservations·업무 인덱스는 존재하지 않고 V1 성공 적용 이력은0건이어야 한다. Flyway 이력 테이블 자체는 존재할 수 있다. 초기화 중간 SQL 실패 뒤에는 삭제 전 두 테이블의 모든 행과 값·적용 이력·업무 구조가 동일해야 한다. 실패 상태 확인은 롤백 후 별도 연결에서 하고, 정상 재준비는 새 실행소유 DB·초기화 재요청은 장애 제거 후 같은 DB의 새 트랜잭션을 사용한다. 관측 불가능한 연결 장애는 불명이며 이 보존식 검증의 통과가 아니다. 세부 범위는 [CHK022 계약](contracts/persistence-contract.md)에 정의한다.
