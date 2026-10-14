# 조사 결과: 예약 저장 제약과 검증 기반

확인일: 2026-10-14. 기존 코드·문서 읽기 및 공식 자료 확인 결과다. 실제 업무 SQL·테스트는 아직 실행하지 않았다. 이 단계에서 해결이 필요한 정책 질문은 없다.

## 1. 기존 도구 조합 유지

- **결정**: Java25·Boot4.1.1·Gradle9.7.1과 기존 BOM/이미지 고정을 유지한다. 정확한 기존 채택값은 [versions.md](../../docs/versions.md), 실제 선언은 [build.gradle](../../backend/build.gradle)·[컨테이너 지원](../../backend/src/test/java/com/limitedbooking/backend/support/ContainerTestSupport.java)을 따른다.
- **근거**: 1단계에서 실행 확인한 기반이 있고 이번 단계는 저장 규칙 검증이다. 새 라이브러리가 필요하지 않다.
- **검토한 대안**: Maven 전환·H2 대체·JPA 자동 스키마 생성은 프로젝트 기준과 맞지 않는다.

## 2. 마이그레이션과 저장 제약

- **결정**: 계획서 5.1 그대로 업무 V1을 main/resources/db/migration에 둔다. PK·NOT NULL·CHECK·FK·UNIQUE와 resource_id 인덱스를 사용한다. 성공 사용자/키 UNIQUE는 정확히 `uq_reservations_user_key`다. CHECK는 두 상태 및 취소 시각 조합을 명시한다.
- **근거**: CHECK는 null을 별도 거부하지 않으므로 필수 값은 NOT NULL로 명시한다. CHECK로 다른 행·테이블의 집계를 보장하지 않는다. [PostgreSQL 18 제약 문서](https://www.postgresql.org/docs/18/ddl-constraints.html)
- **검토한 대안**: 트리거로 수량 보존·상태 전이를 강제하거나 별도 키 테이블·TTL을 추가하지 않는다. 앱 입력 검증만으로 DB 저장 거부를 대체하지 않는다.

## 3. 제약별 테스트 우선과 적용된 V1 보존

- **결정**: resources 다음 reservations의 각 제약을 테스트 작성→제약 부재 RED→후보 CREATE DDL 최소 추가→같은 테스트 GREEN 순서로 진행한다. 후보는 테스트 전용 `db/schema-candidate/schema.sql`이고 명명된 임시 Flyway migration이 아니다. 각 실행은 새 소유 DB에 후보 DDL을 직접 적용한다. 검증한 마지막 후보 내용을 수정 없이 업무 V1에 복사하고 후보·분기를 제거한 뒤 실제 Flyway 검증을 수행한다.
- **근거**: 예약 제약을 테스트보다 먼저 구현하는 순서를 없애고 한 검증 대상씩 인수한다. 문자열은 초기 text·필수 속성은 초기 nullable 등 대상 제약을 추가하기 전에는 그 위반을 실제로 허용하는 준비 구조가 필요하다. 이미 통과한 제약은 후보에 유지하며 각 CREATE 정의를 갱신하고 새 DB에서 시험한다. ALTER 누적 이력을 V1에 재작성하지 않아 최종 복사 내용의 동일성을 확인할 수 있다.
- **검토한 대안**: 구현 후 테스트 전용 구조의 제약을 제거하는 방식은 최초 구현의 TDD 증거가 아니다. 컴파일/42P01만의 RED, 적용된 V1 수정, clean/repair는 사용하지 않는다. 후보에서의 RED/GREEN과 실제 V1 적용 증거는 구분한다.

## 4. 새 DB와 재적용 DB

- **결정**: 동일 제약 사례를 fresh/reapplied 두 DB 준비 조건에서 실행한다. 최초 migrate 1건, validate 성공·재실행 migrate 0건과 저장 표식/이력 보존을 확인한다. 재적용 전에 clean을 호출하지 않는다.
- **근거**: Flyway migrate는 아직 적용되지 않은 migration을 적용한다. 두 조건을 분리해야 최초 생성과 재사용 안정성을 함께 확인할 수 있다. [Flyway migrate](https://documentation.red-gate.com/flyway/reference/commands/migrate)
- **검토한 대안**: 반복할 때마다 DB를 삭제하거나 테이블을 다시 만드는 경우 재적용 안정성의 증거가 아니다.

## 5. JDBC 시각·조회

- **결정**: 명시적 컬럼·바인딩 파라미터로 읽는다. UUID는 UUID로, 상태는 ReservationStatus로, timestamptz는 OffsetDateTime을 읽고 Instant로 변환한다. 테스트 입력은 고정 Instant를 마이크로초로 맞춰 UTC OffsetDateTime으로 전달한다.
- **근거**: pgJDBC가 timestamptz와 대응하는 타입으로 OffsetDateTime을 지원하고 Instant 직접 JDBC 매핑은 지원하지 않는다고 설명한다. [pgJDBC 조회·시각 매핑](https://jdbc.postgresql.org/documentation/query/)
- **검토한 대안**: 문자열 시각 비교, 시스템 기본 시간대 사용, 지원을 확인하지 않은 getObject(Instant.class), SELECT *는 피한다. 저장소 예외를 empty로 바꾸지 않는다.

## 6. 컨테이너 지원과 기존 프로브

- **결정**: 무인자 PostgreSQL+Redis 생성·public 필드·시작 실패 정리·종료 상한을 유지하고 PostgreSQL-only 경로를 추가한다. 새 업무 IT는 Spring 전체 기동 없이 실제 PostgreSQL·Flyway·NamedParameterJdbcTemplate로 실행한다.
- **근거**: 현재 지원 코드는 두 컨테이너를 항상 시작한다. 기존 ToolchainSmokeIT·BackendApplicationIT는 db/toolchain을 명시하므로 업무 V1과 프로브 V1은 별도 경로를 유지할 수 있다. 이는 코드 대조 결과이며 실행은 후속이다.
- **검토한 대안**: 업무 DB 검증에도 Redis를 필수로 기동하는 방법은 불필요한 의존성을 만든다. 기존 지원 클래스를 교체하거나 무인자 동작을 바꾸면 프로브 호환성을 깨뜨린다.

## 7. Gradle 필터와 결과 게이트

- **결정**: `./gradlew test integrationTest --tests '*SchemaIT'`를 그대로 사용한다. 이때 test는 기존 Unit을, integrationTest는 선택된 SchemaIT를 실행한다. 0건·실패·skip 및 listener/XML 일치 게이트를 유지한다.
- **근거**: --tests는 Test 태스크 옵션이고 태스크 이름 뒤에 지정한다. 현재 빌드와 기존 README·1단계 실행 증거의 같은 명령 형태를 대조했다. [Gradle 명령행](https://docs.gradle.org/current/userguide/command_line_interface.html), [테스트 필터](https://docs.gradle.org/current/userguide/java_testing.html)
- **검토한 대안**: Unit에 *SchemaIT 필터를 붙이거나 게이트를 완화하는 방법은 0매칭 또는 거짓 통과를 만든다. 이번 계획에서 Gradle 명령을 실행한 것은 아니다.

## 8. 준비·초기화와 수량 관측

- **결정**: 컨테이너가 소유한 DataSource만 명시 전달한다. 초기화는 reservations→resources DELETE, Flyway 이력 보존이다. 정상 준비 데이터는 명시적인 트랜잭션을 커밋한 뒤 전달한다. 의도적 비정상 저장은 별도 직접 SQL 사례다.
- **근거**: 실패 트랜잭션 조회를 피하고 후속 독립 연결에서 데이터를 읽을 수 있어야 한다. 수량 관측은 SELECT 하나에서 대상과 CONFIRMED 집계를 얻는다. 취소 예약은 제외하며 행 없음·불일치는 AssertionError로 탐지한다.
- **검토한 대안**: 개발 DB 기본값·환경 URL fallback, 테스트 전체 @Transactional, 여러 SELECT의 관측값 합치기, count(*)로 예약 없는 LEFT JOIN 행을 1개로 세는 방식은 사용하지 않는다.

## 9. 저장 길이와 요청 검증의 범위

- **결정**: FR-002의 저장 상한과 직접 비공백 길이 거부를 구분한다. 일반 공백 초과분 절단은 별도 저장 사례로 문서화하며 API 입력의 거부·이름 strip·키 문자 규칙은 기존 후속 범위를 유지한다.
- **근거**: PostgreSQL varchar(n)은 문자 수를 제한하고 초과 부분이 모두 공백이면 절단한다. 명시적 cast 역시 절단할 수 있다. [PostgreSQL 18 문자열 타입](https://www.postgresql.org/docs/18/datatype-character.html)
- **검토한 대안**: 모든 원본 입력 길이 초과를 DB가 거부한다고 표현하거나 후속 입력 규칙을 새 DB 제약으로 옮기지 않는다. 기존 계획서 5.1의 타입과 외부 API 계약은 변경하지 않는다.

## 10. CHK022 실패 처리 범위

- **결정**: 정상 연결의 고의 SQL 실패만 이 단계에 포함한다. Flyway 트랜잭션 V1 사본 마지막 실패는 전체 업무 DDL 롤백·성공 이력0건을 확인하고 소유 자원 종료 뒤 새 DB에서 원본으로 재개한다. TestData 중간 삭제 실패는 전체 롤백·스냅샷 불변을 확인하고 장애 제거 후 같은 DB의 새 트랜잭션으로 명시적 재요청한다.
- **근거**: PostgreSQL에서 트랜잭션 가능한 DDL을 Flyway 한 migration 트랜잭션으로 적용하면 SQL 실패에 전체 롤백을 요구할 수 있다. 현재 계획의 CREATE DDL은 이 범위다. [Flyway 트랜잭션 처리](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html), [executeInTransaction](https://documentation.red-gate.com/fd/flyway-execute-in-transaction-setting-277578997.html). 실제 업무 실행 검증은 후속이다.
- **대안·한계**: 실패 DB repair/clean·원본 V1 변경·자동 무한 재시도는 사용하지 않는다. 비트랜잭션 DDL은 이 V1에 채택하지 않는다. 연결 단절/프로세스 중단은 이번 시험의 보장 범위가 아니며11단계에서 다룬다. 관측 불가능한 상태를 롤백 성공으로 추정하지 않는다. 기존 도구 프로브를 업무 복구 인수의 대체로 쓰지 않는다.
