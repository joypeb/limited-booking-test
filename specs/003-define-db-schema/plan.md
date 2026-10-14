# 구현 계획: 예약 데이터 제약과 테스트 초기화

**브랜치**: `docs/define-db-schema` | **날짜**: 2026-10-14 | **명세**: [spec.md](spec.md)

**입력**: `specs/003-define-db-schema/spec.md`. Phase 0 조사·Phase 1 설계 완료, 업무 구현·실행 검증 전이다. setup-plan의 BRANCH 값 `003-define-db-schema`는 기능 경로에서 파생되며 실제 Git 브랜치는 위 값이다.

## 요약

계획서 5.1절의 resources·reservations와 제약·인덱스를 Flyway V1로 작성하고, 두 읽기 Repository와 전체 저장 예약 record를 제공한다. 실제 PostgreSQL에 직접 제약 위반을 넣는 SchemaIT, 격리된 TestData 초기화, 단일 SELECT 수량 보존 검사를 구현한다. 예약 생성·취소 Service/Writer·HTTP·보안·Redis 제한은 후속 단계다.

조사 결정은 [research.md](research.md), 저장 모델은 [data-model.md](data-model.md), 후속 계층에 제공하는 읽기·테스트 지원 계약은 [계약](contracts/persistence-contract.md), 실행 절차는 [quickstart.md](quickstart.md)에 둔다. 이번 명령은 tasks.md를 만들지 않는다.

## 기술 맥락

| 항목 | 선택과 실제 상태 |
| --- | --- |
| 언어·버전 | 기존 Java 25, preview 제외. 기록된 JDK Corretto 25.0.4+7-LTS |
| 의존성 | Boot 4.1.1·Gradle Wrapper 9.7.1 유지. Spring JDBC NamedParameterJdbcTemplate, Flyway 12.4.0·pgJDBC 42.7.13은 기존 BOM 기록값 |
| 저장소 | PostgreSQL 18.6, 기존 ContainerTestSupport의 태그·digest 유지. 업무 마이그레이션은 main resources의 db/migration |
| 검증 | Jupiter 6.0.3·AssertJ·Testcontainers 2.0.5. SchemaIT·RepositoryIT·DatabaseSupportIT은 일반 IT, Spring 컨텍스트 없이 실제 DB 사용 |
| 실행 플랫폼 | localhost CLI, Docker 사용. 동적 포트·격리 DB·UTF-8·UTC |
| 프로젝트 유형 | 기존 단일 backend 애플리케이션의 저장 계층·테스트 기반 |
| 성능 목표 | 이 단계는 처리량·지연 목표를 추가하지 않는다. 수량 관측은 SELECT 하나, 실제 부하는 11단계 |
| 제약 | 수량 0~1000, available 0~capacity, 성공 사용자·키 유일성, 상태·취소 시각 일치. 테스트 DB 이외 초기화 금지 |
| 규모·범위 | 소규모 고정 테스트 데이터와 두 마이그레이션 준비 조건. 경쟁·장애·HTTP 검증은 후속 |

버전은 [기존 기록](../../docs/versions.md)을 읽은 값이며 이번 계획에서 호환성을 다시 실행 검증한 값은 아니다. 추가 의존성·도구 변경은 필요하지 않다.

## 헌법 확인

| 게이트 | 설계 전 | 설계 후 근거 |
| --- | --- | --- |
| I. 서비스 경계·API 호환성 | 통과 | 기능별 Repository·DTO만 추가. 기존 OpenAPI·HTTP 계약 변경 없음. Redis는 수량 원본이 아님 |
| II. 도메인 근거 | 통과 | knowledge 안내·규칙 전체·resource/reservation과 계획서 2~5절·7절 2단계를 대조. 신규 업무 도메인 없음 |
| III. 검증·증거 | 통과 | 제약별 RED→GREEN, 실제 PostgreSQL, 0건/skip/실패 게이트 유지. 단일 관측·검사 탐지력·재적용 포함 |
| IV. 문서 동기화 | 통과 | 업무 규칙 변경 없음. 구현 시 실제 파일·검증 결과에 맞춰 도메인 문서·README·docs를 갱신 |
| V. 미확정 정책 | 통과 | clarify에서 필수 속성 문장 교정. 미결정 업무 정책 없음. 문서 설계와 실행 완료를 구분 |
| 로컬·비밀정보·Git | 통과 | 현재 docs 브랜치·기존 미커밋 변경 보존. 외부 실행·개발 DB 연결·volume 삭제 없음 |

헌법의 과거 골격·테스트 미구현 기록은 현재 파일 및 1단계 실행 기록과 구분한다. 헌법의 채택일·외부 CI TODO는 이 단계의 저장 계약 결정에 영향을 주지 않으므로 헌법 자체를 변경하지 않는다. 정당화가 필요한 설계 위반은 없다.

## 프로젝트 구조

### 이번 기능 문서

```text
specs/003-define-db-schema/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/persistence-contract.md
  checklists/requirements.md
  tasks.md                         # 작업 목록 생성됨, 아직 미수행
```

### 구현 단계의 파일 배치

아래 업무 파일은 예정이며, ContainerTestSupport·build.gradle·문서들은 기존 파일이다.

```text
backend/src/main/resources/db/migration/V1__create_reservation_schema.sql
backend/src/main/java/com/limitedbooking/backend/
  resource/ResourceRepository.java
  resource/ResourceView.java
  reservation/ReservationRepository.java
  reservation/StoredReservation.java
  reservation/ReservationStatus.java
backend/src/test/java/com/limitedbooking/backend/
  support/ContainerTestSupport.java   # 무인자 호환 유지 + PostgreSQL-only 경로
  support/SchemaDatabase.java         # 실행 소유 업무 V1 DB·스냅샷
  support/TestData.java
  support/DatabaseAssertions.java
  common/SchemaIT.java
  resource/ResourceRepositoryIT.java
  reservation/ReservationRepositoryIT.java
  support/DatabaseSupportIT.java
```

제약별 개발 중 `backend/src/test/resources/db/schema-candidate/schema.sql`을 일시적으로 사용한다. 후보는 Flyway migration이 아니며 마지막 CREATE DDL을 V1로 그대로 복사한 뒤 파일·선택 분기를 제거한다. 최종 트리에는 이 경로가 없다.

현재 `db/toolchain`·`db/toolchain-failure` 시험 migration과 기존 IT의 명시적 Flyway locations는 유지한다. 업무 V1과 프로브 V1을 한 Flyway 위치에 합치지 않는다. build.gradle의 테스트 태스크·게이트를 이 단계 때문에 완화하지 않는다.

## Phase 0: 조사와 결정

[조사 결과](research.md)에 결정·근거·대안을 기록했다. 기존 코드 읽기와 공식 문서 확인으로 계획 결정을 마쳤다. 읽기 전용 조사 에이전트는 Gradle 필터·지원 코드·프로브 위치를 검토했다. 미해결 설계 질문은 없으며 실제 실행 호환성과 DB 결과는 구현 단계에서 확인한다.

## Phase 1: 데이터·인터페이스 설계

- 저장 길이는 문자 수 기준 이름100·키64·상태16이다. 직접 비공백 초과를 거부하고 초과분이 모두 일반 공백인 경우는 절단 저장을 별도 사례로 확인한다. 이름 strip·키 문자 규칙과 원본 요청 거부는 후속 입력 검증 범위다.
- V1은 두 테이블·기본키·NOT NULL·수량 CHECK·예약 FK·상태 CHECK·이름 있는 사용자/키 UNIQUE·대상 검색 인덱스를 생성한다. 테이블 간 보존식이나 재활성화 방지용 트리거를 추가하지 않는다.
- Repository는 바인딩된 명명 파라미터와 명시적 컬럼을 사용한다. Optional.empty는 행 없음만 뜻하며 SQL·연결 실패를 empty로 숨기지 않는다. 조회는 수량·상태를 바꾸거나 트랜잭션 Writer 역할을 수행하지 않는다.
- 테스트 JDBC 시각 입력은 고정 Instant를 마이크로초로 맞춘 UTC OffsetDateTime으로 전달하고, 조회는 OffsetDateTime→Instant로 읽는다. null 취소 시각을 보존한다. 실제 저장된 시각을 기대값과 비교한다.
- TestData·DatabaseAssertions는 컨테이너 실행이 소유한 DataSource를 명시적으로 받는다. 환경의 local URL을 기본값으로 쓰지 않는다. 초기화는 DELETE reservations→resources만 수행하고 Flyway 이력을 남긴다.
- ContainerTestSupport는 기존 무인자 생성자의 PostgreSQL+Redis 시작/정리 계약을 유지한다. SchemaIT 경로에만 PostgreSQL-only 선택을 추가하고, 시작하지 않은 Redis는 정리 대상에서 제외한다. 시작 실패·정리 상한 계약은 유지한다.

## 구현 순서와 검증 경계

1. **제약별 테스트 우선 개발**: resources의 수량→필수 값→PK→이름 길이, reservations의 사용자/키 UNIQUE→FK→상태/취소 시각→필수 값→PK→키 길이→대상 인덱스를 한 검증 대상씩 처리한다. 각 대상은 테스트 작성→제약 없는 후보에서 실제 잘못된 저장 허용/인덱스 부재로 RED→후보 CREATE DDL에 현재 제약만 추가→같은 테스트 GREEN 순서다. 이름/키 제한 전 컬럼은 text, NOT NULL 추가 전 속성은 nullable 등 현재 검증 대상이 실제 부재한 초기 구조를 사용한다. 이미 검증한 제약은 유지하고 새 제약 구현 전에 해당 RED를 확인한다. 후보는 테스트 전용 db/schema-candidate/schema.sql을 JDBC로 직접 적용하며 각 후보 시험마다 새 소유 DB를 쓴다. 42P01·컴파일·컨테이너 실패는 기능 RED 증거가 아니다.
2. **완성된 DDL과 업무 V1**: US1은 후보 resources DDL의 인수까지, US2는 후보 reservations DDL의 제약별 인수까지 진행한다. 후보에는 CREATE 정의만 유지하며 ALTER 변경 이력을 누적하지 않는다. 두 테이블의 검증된 최종 후보 내용을 수정 없이 main db/migration/V1__create_reservation_schema.sql로 복사해 동일성을 확인한다. 그 뒤 후보 파일·선택 분기를 제거하고 새 DB에 실제 Flyway V1을1건 적용해 같은 전체 제약 사례를 검증한다. 후보 DDL의 RED/GREEN은 SQL 제약 증거이고 최종 V1의 GREEN은 Flyway 적용 증거다. V1은 처음 적용한 뒤 편집하지 않는다. 기존 도구 프로브 V1은 별도 위치를 유지한다.
3. **두 준비 조건**: SchemaIT의 각 제약 사례를 fresh/reapplied 두 조건에서 실행한다. 클래스가 소유하는 독립 DB 두 개를 순차 준비하고 fresh는 V1 1건 적용, reapplied는 1건 적용 후 표식 데이터 저장→validate→migrate 0건→표식 보존 확인을 수행한다. 같은 제약 사례를 각 DB에서 데이터 초기화 후 실행한다. 테스트 수와 각 모드 실행 수를 구분해 기록한다.
4. **읽기 저장소**: ResourceRepositoryIT·ReservationRepositoryIT을 먼저 작성해 없는 행·사용자/키 분리·CONFIRMED/CANCELLED·7개 예약 컬럼·저장 시각을 검증한다. 컴파일용 최소 시그니처 다음 실제 값 불일치 RED를 확인하고 최소 조회 구현으로 통과시킨다.
5. **준비·초기화·검사**: DatabaseSupportIT에서 반복 초기화·이력 보존·다른 연결에서 준비 데이터 조회·정상/불일치 보존식·대상 부재를 검증한다. 정상 준비는 커밋 완료 후 검증하며 비정상 데이터는 각 테스트에서 명시적으로 만든다.
6. **영향 검증과 기록**: 선택 IT들과 기존 Unit·ToolchainSmokeIT·BackendApplicationIT을 실행한다. 지원 코드 변경에 영향을 받는 일반 IT는 전체 integrationTest로 한 번 확인한다. 새 문제나 변경이 없다면 Contract/Property/PIT/부하를 반복하지 않는다. 기록·지식 문서의 상태를 갱신한 뒤 검증 대상별 커밋으로 저장한다. 문서 브랜치 통합·구현 브랜치 분리는 구현 시작 시 Git 상태와 함께 수행하며 이번 계획에서 수행됐다고 표시하지 않는다.

제약 예외는 가장 안쪽 SQLException의 SQLSTATE·해당 제약 이름을 확인한다. 실패 SQL 뒤의 잔류 행 검사는 autocommit의 새 statement 또는 롤백 이후 새 연결에서 수행한다. 실패한 트랜잭션 안에서 계속 조회하지 않는다. 초기 SchemaIT는 직접 SQL 저장 제약 검증이며 공개 API·멱등 응답·원자성의 검증으로 보고하지 않는다.

## CHK022 실패 처리 설계

- 업무 V1은 트랜잭션 가능한 DDL만 사용하고 Flyway executeInTransaction=true로 검증한다. SchemaIT에서 원본 V1을 수정하지 않은 테스트 전용 사본 뒤에22012 오류를 붙여 전체 DDL 이후 실패를 재현한다. 별도 연결에서 두 업무 테이블·인덱스 부재와 V1 성공 이력0건을 확인한다. 이력 테이블 자체의 존재는 허용하고 성공 적용 여부와 구분한다.
- 실패 DB의 증거·소유 자원 종료 후 다른 실행소유 DB에서 원본 V1을1건 적용하고 제약을 검증한다. 실패 DB에 clean/repair·변경 V1 적용·무한 재시도로 통과를 만들지 않는다. 시험 이름은 SchemaIT.rollsBackFailedMigrationAndRecoversInNewDatabase다.
- 초기화는 한 트랜잭션이므로 예약 삭제 후 대상 삭제의 고의 오류에서도 기존 양쪽 행/값·구조·이력이 모두 보존돼야 한다. TestData 구현 전 DatabaseSupportIT.rollsBackFailedResetAndRetriesAfterFaultRemoval을 작성해 이 기대값의 실패를 확인한다. 테스트 전용 resources DELETE 트리거는 실제 DB에만 설치하며 생산 코드·migration에는 넣지 않는다.
- 오류 후 롤백이 끝난 별도 연결에서 초기화 전 스냅샷과 비교한다. 장애 트리거 제거 후 같은 DB의 새 트랜잭션으로 명시적 초기화를 재요청해 두 테이블0건·구조/이력 유지와 장애 객체 정리를 확인한다.
- 상태 조회 불가 시 검증 실패·불명으로 기록하고 다음 사례를 중단한다. 연결 단절·프로세스 중단의 복구 시험은11단계이며 이번에는 정상 연결의 고의 SQL 오류를 다룬다. 상세 기대값은 [계약](contracts/persistence-contract.md)의 CHK022 절을 따른다.

## 완료 판정과 남은 범위

FR-001~004는 SchemaIT, FR-005~006은 두 RepositoryIT, FR-007은 fresh/reapplied 및 적용 이력 검사, FR-008~010은 DatabaseSupportIT, FR-011은 실행 기록으로 연결한다. 실행 테스트 수>0·실패0·skip0과 최종 업무 V1 사용을 확인해야 구현 완료다. 단일 SELECT 방식은 코드 검토와 실제 불일치 탐지로 확인한다.

현재 완료는 설계 문서와 [작업 목록](tasks.md) 작성·대조까지다. 스키마·조회 코드·업무 테스트와 새/재적용 DB 검증은 미실행이다. 다음 작업은 `$speckit-analyze`로 산출물 간 일관성을 검토하는 것이며, 구현은 한 검증 대상씩 진행한다.
