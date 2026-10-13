# 조사: 1단계 도구와 실행 계약

작성일: 2026-10-13. 조사 에이전트 두 개가 빌드·연동과 심화 검증을 독립 조사하고 결과를 통합했다. 이번 조사에서 설치·의존성 해석·컴파일·컨테이너·테스트는 실행하지 않았다. 아래 버전은 실행할 후보이며 호환 확인 완료가 아니다.

## 1. 기존 골격과 빌드 유지

- **결정**: 기존 Java 25, Boot 4.1.1, Gradle Wrapper 9.7.1, `backend/`, `com.limitedbooking.backend`를 유지한다. 새 Initializr 골격이나 Maven 저장소를 만들지 않는다.
- **이유**: 사용자 지침이 기존 구성을 우선한다. 공식 Gradle 표는 Java 25 실행·도구 체인을 9.1.0 이상에서 지원한다고 안내한다. 이는 개별 플러그인·현재 Wrapper의 실행 증거는 아니다. [호환 표](https://docs.gradle.org/current/userguide/compatibility.html)
- **대안**: Maven 전환은 불필요한 구성 변경이므로 제외한다. Wrapper 취득·실행이 실패하면 배포 URL·체크섬·실제 JDK·네트워크를 먼저 확인하고 원인 없이 버전을 낮추지 않는다.

## 2. 의존성 구성

- **결정**: Boot BOM 관리 좌표는 수동 버전을 추가하지 않는다. 기본 starter를 필요한 기능 starter로 확장하고 `dependencyInsight`로 실제 해석값을 기록한다.
- **후보 좌표**: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-jdbc`, `spring-boot-starter-data-redis`, `spring-boot-starter-flyway`, `spring-boot-starter-actuator`, 기존 `spring-boot-starter-test`, 필요한 `spring-boot-starter-webmvc-test`; PostgreSQL driver와 `flyway-database-postgresql`을 포함한다. 신규 기능 테스트 starter는 실제 사용 API가 필요할 때만 추가한다.
- **이유**: Boot 4의 모듈별 starter 이름과 관리표를 기준으로 하고 과거 예제를 복사하지 않는다. 관리표 조사값은 Jupiter 6.0.3, Testcontainers 2.0.5, Flyway 12.4.0, PostgreSQL JDBC 42.7.13이다. 실제 Gradle 해석값이 우선이며 이 표를 별도 버전 override로 사용하지 않는다. [starter](https://docs.spring.io/spring-boot/reference/using/build-systems.html), [관리표](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- **대안**: JPA·H2·독립 JUnit 버전 고정은 기존 기술·검증 계약과 맞지 않아 제외한다.

## 3. 실제 저장소와 Flyway 확인

- **결정**: Testcontainers 2의 `testcontainers-junit-jupiter`, `testcontainers-postgresql`, core `testcontainers`를 사용한다. Redis는 GenericContainer, 연결은 실제 Lettuce 경로다. `getHost()`와 시작 후 mapped port를 사용한다.
- **이유**: 2.x의 artifact 이름을 사용하며 PostgreSQL driver를 별도로 넣는다. 테스트 클래스 병렬 실행·Gradle 다중 fork는 비활성화한다. [PostgreSQL 모듈](https://java.testcontainers.org/modules/databases/postgres/), [네트워크](https://java.testcontainers.org/features/networking/), [Jupiter 제한](https://java.testcontainers.org/test_framework_integration/junit_5/)
- **추가 결정**: 테스트 리소스의 전용 Flyway migration으로 작은 `toolchain_probe` 테이블을 만들고 적용 메타데이터 및 insert/select를 확인한다. 업무 migration 경로·버전과 분리한다. 일반 JDBC 성공을 Flyway 성공으로 대체하지 않는다.
- **대안**: Compose 개발 DB를 테스트에 재사용하거나 mock 저장소로 연동을 대신하지 않는다. 속성 도구 확인은 별도 순수 메모리 프로브로 수행한다.

## 4. Compose와 보존

- **결정**: 우선 이미지 후보는 `postgres:18.6-bookworm`, `redis:8.10.2-trixie`다. 취득 가능한 정확한 tag·아키텍처·digest를 구현 시 확인하고 Compose와 테스트에 같은 식별값을 사용한다. `latest`·주버전만의 가변 tag는 제외한다.
- **이유**: 조사 당시 공식 이미지 목록에서 확인한 정확한 후보다. 이미지를 pull하거나 로컬 CPU에서 실행하지 않았으므로 사용 가능·실행 호환 확정은 아니다. [PostgreSQL 이미지](https://hub.docker.com/_/postgres), [Redis 이미지](https://hub.docker.com/_/redis)
- **보존 결정**: 사용자 답변 A에 따라 PostgreSQL만 종료·재시작 후 보존 검증한다. PostgreSQL 18의 named volume 대상은 `/var/lib/postgresql`, 기본 PGDATA는 `/var/lib/postgresql/18/docker`다. Redis는 저장·조회만 확인하고 영속화 설정을 요구하지 않는다. [18 이미지 경로](https://hub.docker.com/_/postgres#pgdata)
- **환경 결정**: ports는 127.0.0.1에 바인딩하고 named volume을 기본 종료에서 제거하지 않는다. 비밀번호는 환경 변수에서 주입하며 `.env.example`에는 변수명만 둔다. Compose 보간과 호스트 JVM 환경은 별개로 설정한다. 비밀값이 보간된 `docker compose config` 전체 결과는 증거에 저장하지 않는다. [Compose 보간](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/), [종료](https://docs.docker.com/reference/cli/docker/compose/down/)
- **대안**: PostgreSQL 17용 마운트 경로 재사용, 익명 volume, 기본 `down -v`는 보존 목표와 맞지 않는다.

## 5. 테스트 태스크와 실패 판정

- **결정**: 동일 test source set에서 이름·태그로 기본/연동/계약/속성을 분리하고 각 `Test` 태스크의 classpath와 testClassesDirs를 명시한다. 조합 태스크와 세부 범위는 [실행 계약](contracts/validation-contract.md)을 따른다.
- **이유**: `*IT` 이름만으로 자동 발견을 보장하지 않는다. `failOnNoDiscoveredTests`와 `failOnNoMatchingTests`를 설정하되 실제 실행 결과의 총수·실패·skip 및 필수 그룹별 수를 추가 검사한다. [Gradle 테스트](https://docs.gradle.org/current/userguide/java_testing.html), [Test DSL](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.testing.Test.html), [필터](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/testing/TestFilter.html)
- **검증 결정**: 최상위 suite listener로 현재 실행의 수를 수집하고 결과 검증 태스크가 NO-SOURCE·보고서 없음·대상 0건·skip을 실패로 처리한다. 태그로 모든 사례를 제외하는 고의 설정과 비활성화 사례도 일시 적용·실패 확인·원복한다. 첫 실행 증거는 `--rerun-tasks`로 캐시와 구분한다. 이후 변경 없는 무거운 검증은 반복하지 않는다. 오래된 XML만으로 통과하지 않는다.
- **대안**: 일반 `test`/`build`를 모든 검증으로 간주하거나 엔진을 불필요하게 Jupiter만으로 제한하는 방식은 제외한다.

## 6. PIT 최소 검증

- **결정**: 첫 후보는 `info.solidsoft.pitest` 1.19.0, 해당 플러그인 기본 PIT 1.22.1, Jupiter 지원 plugin 1.2.3이다. BOM 밖이므로 명시적으로 고정한다. 순수 int 프로브 한 클래스와 대응 Unit에만 적용한다.
- **이유**: 플러그인 공식 기본 조합을 시작점으로 삼는다. 최소 Java17/Gradle8.4 조건과 Jupiter plugin의 PIT1.19.4+ 조건은 문서상 맞지만 Java25·Gradle9.7.1·Platform6 전체 실행 호환성을 보장하지 않는다. [Gradle 플러그인](https://github.com/szpak/gradle-pitest-plugin/releases), [Jupiter 지원](https://github.com/pitest/pitest-junit5-plugin)
- **대안**: PIT engine 1.30.0은 최신 변경 기록의 대안이다. 원인 분석 결과 engine 지원 문제라면 명시적 override 후보로 비교한다. 초기 조합은 최신 engine이라는 뜻이 아니다. Java25 BigDecimal/BigInteger 관련 수정이 후속 engine에 있으므로 int 프로브 성공을 전체 기능 호환으로 일반화하지 않는다. [engine 변경 기록](https://github.com/hcoles/pitest)
- **판정**: XML/HTML에서 생성≥1·탐지≥1·NO_COVERAGE=0·도구 예외 없음. 생존 변이는 이유를 기록한다. 후속 업무 규칙의 80% 기준과 SQL·권한 탐지 검증은 이 smoke 점수로 대체하지 않는다.

## 7. 속성 도구

- **결정**: 기존 후보 `org.quicktheories:quicktheories:0.26`을 Jupiter `@Test`에서 실행한다. `property` 태그를 사용하며 별도 엔진은 추가하지 않는다. 0/100 명시 사례와 0~100 생성 입력 최소100회로 정수 저장·조회 일치를 검사한다.
- **이유**: seed·`withFixedSeed`·자동 shrinking이 문서화되어 있다. 0.26은 기존 후보의 게시 좌표이며 실패를 피하기 위한 버전 하향이 아니다. [공식 README](https://github.com/quicktheories/QuickTheories), [좌표](https://central.sonatype.com/artifact/org.quicktheories/quicktheories)
- **실패 확인**: 입력이 50보다 클 때 저장값을 바꾸는 일시적 경계 결함을 주입한다. 최초 반례가 51보다 크고 축소된 반례가 51이 된 실행과 고정 seed 재현을 기록한다. 처음부터 51이 발견되었다는 사실만으로 축소를 입증하지 않는다. 초기 반례를 기록할 수 있는 도구 API/출력도 실제 확인하며, seed 탐색 과정은 정상 재시도로 숨기지 않고 재현값 선정으로 기록한다. 원복 후 정상 통과를 확인한다.
- **대안**: jqwik의 별도 엔진 방식은 후보 실패 원인이 확인될 때 비교한다. 도구를 바꾸어도 seed·shrinking·실패 종료 계약은 유지한다.

## 8. 계약 검증

- **결정**: `com.atlassian.oai:swagger-request-validator-core:2.46.1`을 첫 후보로 고정한다. Spring 어댑터 대신 core의 SimpleRequest/SimpleResponse에 실제 HTTP method/path/header/body/status를 전달한다.
- **이유**: 테스트 전용 localhost 동적 포트 fixture와 JDK HTTP client로 프레임워크 의존성을 줄이고 정상 JSON 및 필수 id 누락 탐지를 입증한다. [core 사용법](https://github.com/atlassian/openapi-request-validator/blob/master/openapi-request-validator-core/README.md), [API](https://javadoc.io/doc/com.atlassian.oai/swagger-request-validator-core/2.46.1)
- **대안**: upstream 3.0.0은 artifact가 `openapi-request-validator-core`로 바뀐 경로다. Boot4/Spring7 연동이 필요하거나 core 후보의 실제 결함이 확인되면 대안으로 평가한다. 이전 이름과 새 이름을 혼용하지 않는다. [변경 기록](https://github.com/atlassian/openapi-request-validator/blob/master/RELEASE-NOTES.md)
- **판정**: 정상 응답 허용, 필수 id 누락 거부를 각각 assertion으로 확인한다. 거부를 예상한 음성 사례 자체는 정상 테스트 성공이고, 고의로 잘못된 응답을 정상이라 주장할 때는 테스트 비성공 종료를 확인한다. 기존 `docs/openapi.yaml`도 읽기·참조 처리 결과를 따로 검사한다. 실제 업무 API 준수는 후속 단계다.

## 조사 완료와 실행 게이트

도구·구조·실행 범위의 설계 질문은 위 결정으로 해소했다. exact JDK 패치·Docker 메모리·설치된 k6 버전·pull digest·실제 BOM 해석값은 추측하지 않고 구현 시 환경 목록으로 측정한다. 이는 사용자 정책의 미확정이 아니라 실행 증거를 생산하는 작업이다. 후보가 실행되지 않으면 해당 도구를 미검증으로 남기고 원인·대안·선택 영향을 결정 기록에 남긴다. 설치나 특정 조합의 성공을 이 조사에서 선언하지 않는다.
