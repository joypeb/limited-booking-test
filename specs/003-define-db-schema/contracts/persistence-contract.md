# 저장 계층·테스트 지원 계약

계획서 7절 2단계의 제공 인터페이스를 후속 애플리케이션 계층이 사용할 수 있도록 정의한다. 새 외부 HTTP API는 추가하지 않으며 [기존 OpenAPI](../../../docs/openapi.yaml)는 유지한다. 아래 인터페이스는 모두 구현 예정이다.

## 읽기 Repository

| 인터페이스 | 입력 | 정상 결과 | 행 없음 |
| --- | --- | --- | --- |
| ResourceRepository.find(UUID id): Optional<ResourceView> | 대상 UUID | id·name·capacity·available 저장 값 | Optional.empty |
| ReservationRepository.findByUserAndKey(UUID userId, String key): Optional<StoredReservation> | 사용자 UUID·원문 키 | 예약의 7개 저장 컬럼과 현재 상태 | Optional.empty |

- `NamedParameterJdbcTemplate`을 생성자로 전달받는 별도 기능 Bean으로 둔다. UUID/키를 SQL 문자열에 이어 붙이지 않고 바인딩한다.
- 대상 조회는 id를, 예약 조회는 user_id와 idempotency_key를 모두 조건으로 사용한다. 다른 사용자·같은 키는 반환하지 않는다. 키의 대소문자 변경·trim은 하지 않는다.
- 취소된 예약도 조회한다. 최초 생성 응답의 고정 CONFIRMED 재구성은 후속 Service의 책임이다.
- SQL·매핑·연결 오류는 호출자에게 예외로 전달한다. 오류를 행 없음이나 멱등 재요청으로 분류하지 않는다. 예상 0행은 empty, 예상 1행은 값이며 비정상 복수 행을 임의 첫 행으로 숨기지 않는다.
- 결과 타입의 세부 속성과 시각은 [데이터 모델](../data-model.md)을 따른다. 조회는 쓰기·수량 변경·상태 전이·권한 판단을 수행하지 않는다. public HTTP의 소유자 검증을 이 사용자/키 조회로 대체하지 않는다.

## TestData

테스트 생성 시 컨테이너가 소유한 DataSource를 명시 전달한다. 생성·초기화의 연결은 이 DataSource에서만 얻으며 local 프로필·환경 URL fallback이 없다. 일반 애플리케이션 컴포넌트로 등록하지 않는다.

- 대상·예약의 식별자·수량·상태·고정 시각을 지정해 준비할 수 있다. 정상 준비는 보존식과 상태 조합을 호출 측이 명확히 구성한다.
- 준비가 완료됐다고 반환하기 전에 커밋한다. 다른 연결에서 읽을 수 있음을 시험한다.
- 초기화는 reservations 전체 DELETE 후 resources 전체 DELETE를 하나의 트랜잭션으로 커밋한다. 외래키 제약·Flyway 이력을 삭제하지 않는다. 반복 두 번 실행 후 두 테이블 0행을 요구한다.
- 제약 위반과 수량 불일치 시험은 직접 SQL/명시적 fixture로 준비한다. 지원 코드가 값을 자동 보정하거나 제약 오류를 삼키지 않는다.

## DatabaseAssertions

`assertInventoryConserved(UUID resourceId)`를 제공하는 테스트 전용 객체는 TestData와 동일한 실행 소유 DataSource를 받는다.

- SELECT 1회에서 capacity·available·CONFIRMED 수를 관측한다. 상세 집계 방식은 [데이터 모델](../data-model.md)을 따른다.
- 대상 있음과 두 수량 불변식을 모두 검사한다. 불일치·대상 부재는 AssertionError로 실패하고 진단에 대상 UUID·관측 수량을 포함한다. 연결 오류는 검사 통과로 처리하지 않는다.
- 개인정보·비밀번호·연결 자격증명을 출력하지 않는다. 취소 예약과 타 대상의 예약은 확정 수에 포함하지 않는다.

## 테스트 발견·증거 계약

| 검증 클래스 | 범위 | 연결 요구사항 |
| --- | --- | --- |
| SchemaIT | 실제 저장 거부/허용, fresh/reapplied, 이력·인덱스 | FR-001~004·007(실패/새DB복구 포함) |
| ResourceRepositoryIT | 대상 조회·없음 | FR-005 |
| ReservationRepositoryIT | 사용자/키 분리·현재 상태·전체 속성·시각 | FR-006 |
| DatabaseSupportIT | 커밋된 준비·반복 초기화·검사 탐지력·대상 부재 | FR-008~010(초기화 실패/재요청 포함) |

각 테스트는 이름 *IT·일반 태그로 integrationTest에서 발견한다. 실제 발생 SQLSTATE·해당 제약 이름과 실패 뒤 행 없음/기존 행 보존을 확인한다. SchemaIT의 같은 제약 사례는 fresh/reapplied 모드 각각 실행한다. JUnit 클래스 병렬 실행 및 테스트 전체 @Transactional은 사용하지 않는다.

SchemaIT에서 작성할 검증 이름·데이터·기대값은 다음과 같다. 각 행의 여러 입력은 분리된 인수 사례로 실행하며 실제 실행 수는 구현 후 보고서로 기록한다.

| 예정 테스트 | 준비·입력 | 기대 결과 |
| --- | --- | --- |
| rejectsInvalidAvailability | capacity=10/available=-1·11, capacity=-1·1001 | 23514, 실패 행 없음 |
| acceptsAvailabilityBoundaries | 0/0·1000/1000·10/0·10/10 | 저장 성공, 저장 값 조회 일치 |
| enforcesUserKeyUniqueness | A/key1 동일·다른 대상·취소 기존행에 중복, B/key1·A/key2 | 중복23505+uq_reservations_user_key, 다른 조합 성공 |
| rejectsUnknownResource | 존재하지 않는 resource_id, 나머지 속성 정상 | 23503, 실패 예약 없음 |
| enforcesStatusAndCancellationTime | 두 정상 조합, 두 비정상 조합, 알 수 없는 상태 | 정상 성공, 비정상23514 |
| rejectsMissingRequiredValues | 각 NOT NULL 속성을 한 번씩 null | 23502, 기존 행 유지 |
| enforcesPrimaryKeys | 기존 대상·예약 PK에 중복 INSERT | 23505, 기존 행 유지 |
| enforcesStoredStringLengths | 이름100/101·키64/65, 비공백 문자·이름 😀 | 문자 수 기준 상한 성공, 초과22001 |
| documentsTrailingSpaceTruncation | 이름 비공백100자+일반공백1자·키 비공백64자+일반공백1자 | 저장 허용, 각각100자·64자로 잘린 값 |
| exposesResourceLookupIndex | pg_indexes/pg_index에서 resource_id 인덱스 확인 | 유효한 비부분 대상 검색 인덱스 존재 |

마이그레이션 준비 자체는 별도 assertion으로 1→0 적용·validate·표식/이력 보존을 확인한다. NOT NULL·FK·UNIQUE 시험은 나머지 조건을 유효하게 두어 다른 제약 위반이 먼저 발생하지 않도록 한다.

시작·종료 실패, 0건, skip, 예상 밖 SQLSTATE는 실패로 기록한다. 개발 DB에 접속해 격리를 검증하지 않고, 컨테이너 동적 접속값만 사용한 코드 경로와 current_database()의 실행 소유 DB 이름을 확인한다. 실행 로그에 비밀번호를 남기지 않는다.

제약 DDL 작성은 각 사례의 테스트·제약 부재 RED 이후에 시작한다. 임시 schema-candidate/schema.sql을 실제 PostgreSQL에 직접 적용하며, 검증한 최종 CREATE DDL을 수정 없이 업무 V1로 복사한 뒤 임시 경로를 제거한다. 후보 DDL의 GREEN과 실제 V1의 Flyway 적용 GREEN은 별도로 기록한다. 저장 길이 사례는 직접 파라미터 바인딩 기준이며, 명시적 cast 또는 요청 입력 검증의 보장으로 확대하지 않는다.

## CHK022 실패 처리와 복구 계약

### 업무 마이그레이션의 고의 SQL 실패

- 최종 V1은 트랜잭션에서 실행 가능한 CREATE TABLE/INDEX·제약 DDL만 포함한다. 명시 COMMIT·트랜잭션 밖 SQL·CONCURRENTLY를 넣지 않는다. 업무 검증의 Flyway executeInTransaction은true다.
- SchemaIT.rollsBackFailedMigrationAndRecoversInNewDatabase는 실제 main V1 내용을 읽어 테스트가 소유한 임시 경로의 사본 끝에 `SELECT 1 / 0`을 붙여 적용한다. 실패 사본은 테스트 전용이며 실제 V1 파일·체크섬을 수정하지 않는다. 두 테이블 DDL 이후 오류가 발생해야 하며 Flyway 오류와 원인 SQLSTATE22012를 기대한다.
- 실패 후 별도 정상 연결로 resources·reservations·해당 업무 인덱스 부재와 V1 성공 적용 이력0건을 확인한다. 이력 테이블 자체는 존재할 수 있으며 존재 여부부터 검사한다. 해당 실패를 성공 적용이나 단순0매칭으로 기록하지 않는다.
- 실패한 실행소유 DB/컨테이너의 증거와 종료를 확인한다. clean/repair·원본 V1 편집·같은 실패 DB의 무한 재시도 없이, 다른 실행소유 DB에서 변경하지 않은 실제 V1을1건 적용하고 제약 사례가 정상인지 확인한다. 원인과 실패→새 환경 복구를 각각 기록한다.

### 초기화 중간 SQL 실패와 명시적 재요청

- TestData 초기화의 두 DELETE는 한 트랜잭션이다. 예약 DELETE 성공 뒤 대상 DELETE가 실패해도 오류를 전달하고 전체를 롤백한다. 준비된 양쪽 테이블의 모든 행과 값·Flyway 이력·업무 구조가 초기화 전과 동일해야 한다.
- DatabaseSupportIT.rollsBackFailedResetAndRetriesAfterFaultRemoval은 데이터 커밋 뒤 전용 DB의 resources DELETE에만 SQLSTATE22012를 내는 테스트 전용 트리거를 설치한다. 생산 migration·TestData 공개 인자·애플리케이션 Bean에 장애 주입 경로를 넣지 않는다.
- 초기화 전 행/값·이력의 스냅샷과 예외 후 별도 연결의 상태를 비교한다. 실패한 트랜잭션 안에서 비교하지 않는다. 고의 트리거 제거 후 같은 실행소유 DB에서 새 초기화 트랜잭션을 명시적으로 요청하면 reservations/resources 각각0건·구조/이력 유지여야 한다. 테스트 후 장애 객체와 자원을 제거한다.
- 자동 무한 재시도나 실패 예외 삼키기는 금지한다. 연결/롤백 문제로 상태를 확인할 수 없으면 불명·검증 실패로 기록하고 해당 환경의 이후 사례를 중단한다. 소유 자원 종료 뒤 새 환경으로 재개하며 불명 상태를 부분 삭제 없음의 증거로 쓰지 않는다.

연결 단절·프로세스 중단·운영 DB 복구는11단계 범위다. 이번 계약은 격리된 정상 연결의 고의 SQL 실패와 실제 롤백 관측에 한정한다. 모든 실행 증거는 후속 구현에서 확보한다.
