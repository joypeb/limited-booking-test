# 실행 계약: 1단계 검증

## 상태와 입력

아래 Gradle 태스크·테스트·Compose 설정은 구현 예정이며 현재 실행할 수 있다고 주장하지 않는다. 현재는 `backend/gradlew`, `build.gradle`, 기존 기동 검사만 존재한다. 명령은 구현 후 `backend/`에서 수행한다. 원 계획 6.2절의 Maven 실행 범위를 Gradle로 대응한다.

## 발견 규칙

| 태스크 | 클래스·태그 | 역할 |
| --- | --- | --- |
| `test` | `*Test`, contract/property 태그 제외 | Spring 없는 순수 Unit |
| `integrationTest` | `*IT`, contract/property 태그 제외 | 실제 PostgreSQL/Redis·Spring 기동 |
| `contractTest` | `*IT`, contract 태그만 포함 | 실제 HTTP fixture 계약·기존 명세 읽기 |
| `propertyTest` | `*Test`, property 태그만 포함 | 순수 정수 프로브 속성·seed·shrinking |
| `pitest` | main의 ToolchainProbe와 ToolchainSmokeTest만 | 자동 변형 최소 생성·탐지 |

각 Test 태스크는 명시적 classpath/testClassesDirs, JUnit Platform, 기본 UTF-8/UTC, fork1과 클래스 순차 실행을 사용한다. 별도 엔진이 없는 초기 Property는 Jupiter에서 실행하지만 불필요한 engine 제한은 넣지 않는다. 일반 `build`/`check`는 순수 `test` 범위만 유지하며 전체 심화 성공을 뜻하지 않는다.

## 실행 조합

| 구현 후 명령 | 필수 실행 그룹 |
| --- | --- |
| `./gradlew test` | Unit |
| `./gradlew verify` | Unit + 일반 Integration |
| `./gradlew test --tests '*ToolchainSmokeTest'` | 지정 Unit |
| `./gradlew test integrationTest --tests '*ToolchainSmokeIT'` | Unit + 지정 일반 IT; 필터는 integrationTest에 적용 |
| `./gradlew verifyContract` | Unit + 일반 IT + Contract, Property 제외 |
| `./gradlew verifyProperty` | Unit + 일반 IT + Property, Contract 제외 |
| `./gradlew deepTest` | Unit + 일반 IT + Contract + Property |
| `./gradlew mutationSmoke` | PIT + PIT 결과 판정, 심화와 별도 |

동일 Test source set을 재사용한다. 태그·이름 필터는 실행 대상을 고르는 규칙이며 컴파일 대상을 줄이지 않는다. 심화 라이브러리·계약 fixture·validator 어댑터를 준비하고 전체 test 컴파일이 성공한 뒤 Property/PIT/Contract를 실행한다. 각 보고서는 `build/test-results/<태스크>`와 `build/reports/tests/<태스크>`로 분리한다. 조합 태스크는 필요한 세부 태스크와 그 결과 검증에 의존한다. `--tests`는 Test 태스크의 옵션이므로 지정 IT 명령에서는 integrationTest 뒤에 둔다. Property 태스크는 선택한 QT_SEED 환경값을 테스트 JVM에 명시적으로 전달한다. 일반 IT 뒤 Contract/Property 순서로 실행해 컨테이너 자원을 공유하지 않는다. PIT 보고서는 `build/reports/pitest`의 XML·HTML이다.

## 성공과 비성공

- 정상 Test 실행은 필수 그룹별 실행≥1, 실패0, skipped0이다. `--tests` 불일치·빈 source·태그 전체 제외는 비성공이다.
- `failOnNoDiscoveredTests`, `failOnNoMatchingTests`와 현재 실행 listener의 실제 결과 및 결과 검증 태스크를 함께 사용한다. NO-SOURCE·결과 없음은 별도 결과 검증에서 실패한다. 이전 보고서가 있는 경우에도 새 실행 여부를 확인한다.
- 첫 증거 실행은 `--rerun-tasks`를 사용한다. UP-TO-DATE/FROM-CACHE는 재사용임을 표시하며 새 실행 증거로 계산하지 않는다.
- 계약 음성 사례의 예상 거부는 assertion 성공이다. 고의 결함으로 정상 결과의 assertion을 깨뜨리면 비성공 종료가 발생해야 한다.
- Property 정상은 0/100 경계와 생성입력 최소100회다. 실패 확인은 seed·최초 반례·더 작은 반례·동일 seed 재현을 포함한다. JUnit 테스트 수와 내부 생성 시도 수는 별도 기록한다.
- Mutation은 생성≥1·killed≥1·NO_COVERAGE0이며 도구 예외·0개 결과·보고서 누락은 실패한다. 생존 결과는 이유를 기록한다.
- 실패를 무시하는 ignoreFailures, disabled 테스트, 의존성의 임의 하향, 무제한 재시도는 완료 수단이 아니다. 고의 skip 확인은 별도 실패 시연 후 원복한다.

## 로컬 프로필과 설정

- Compose는 프로젝트 루트 `compose.yaml`, 서비스명 `postgres`, `redis`; `postgres`는 `reservation_local`과 named volume, 127.0.0.1:5432; Redis는 127.0.0.1:6379다. 앱 local은 127.0.0.1:8080이다.
- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `TOOLCHAIN_SMOKE_PASSWORD`는 비밀 설정 포함 여부를 검토하고 환경에서 전달한다. `.env.example`에는 변수명만 제공한다. Compose와 호스트 JVM에 별도 주입한다.
- 이미지 식별값은 구현 시 선정·고정한 Compose와 Testcontainers 설정이 일치해야 한다.
- Boot 자동 보안의 환경용 계정 이름은 `toolchain`; password는 환경 변수다. 임시 기동 확인 목적이며 ADMIN/USER UUID·소유권·상태 없는 인증의 업무 계약 구현이 아니다. 임의 허용 SecurityFilterChain이나 임시 업무 API를 추가하지 않는다. 테스트 기동은 테스트 실행 중 생성한 비밀값을 해당 property로 공급한다.
- Actuator는 health만 노출하고 상세값을 노출하지 않는다. health HTTP 응답과 앱 기동을 확인한다. 사용자·SQL·비밀번호를 기록하지 않는다.
- IT는 동적 컨테이너 연결과 RANDOM_PORT를 사용하고 local 프로필로 실행하지 않는다. 테스트용 Flyway 위치는 `classpath:db/toolchain`이며 업무 migration 위치와 다르다.

## HTTP fixture 계약

[최소 OpenAPI](toolchain-smoke.openapi.yaml)는 독립된 도구 시험 계약이다. 테스트 코드가 loopback 동적 포트 서버에 GET `/toolchain/probe`를 제공하고 JDK HTTP client로 실제 전송한다. 정상 응답은200·JSON·유효 UUID id, 음성 응답은 같은 경로·상태에서 id 제거다. validator는 실제 request/response의 method/path/headers/body/status를 검사한다. 음성 모드는 테스트 서버 내부 설정으로 선택하며 제품 API에 파라미터를 만들지 않는다.

YAML의 유일한 도구 시험 원본은 이 기능의 contracts 파일이다. 구현 시 processTestResources가 테스트 classpath로 복사하고 별도 수작업 사본을 관리하지 않는다. 업무 계약의 유일한 원본은 계속 루트 `docs/openapi.yaml`이다. 기존 문서도 테스트 리소스로 읽어 parser 오류·참조 처리 결과를 확인한다. 두 계약의 성공을 업무 실제 HTTP 준수로 혼동하지 않는다.

## 정지와 데이터 보존

PostgreSQL에 본인이 만든 시험 schema·표식1행을 넣고 commit한 뒤 `docker compose down`, `docker compose up -d --wait`로 컨테이너를 재생성한다. 동일 UUID·값이 유지되는지 조회한다. named volume을 제거하지 않는다. Redis 키의 재시작 후 보존은 요구하지 않는다. 자동 IT는 이 개발 DB 표식에 접근하지 않는다.

## 환경 검증의 대기 상한

| 대상 | 상한 | 초과 판정 |
| --- | --- | --- |
| Compose 준비 대기 | 120초 | up의 비성공 종료, 환경 미준비 기록 |
| 시험 컨테이너 시작 | 컨테이너별 120초 | startup 실패, 테스트 비성공, 시작한 시험 자원 정리 |
| 앱 HTTP 준비 | 앱 시작 후 60초 | 준비 실패, 앱 정상 종료 시도, 환경 미검증 기록 |
| HTTP 연결 | 요청별 3초 | 연결 timeout, 해당 요청/테스트 비성공 |
| HTTP 요청 전체 | 요청별 10초 | 요청 timeout, 해당 요청/테스트 비성공 |
| 시험 서버·클라이언트·컨테이너 종료 | 자원별 10초 | 정리 실패와 잔존 자원 식별값 기록, 인수 보류 |

앱 준비 조건은 최대1초 간격으로 기다릴 수 있으나 60초 deadline을 넘기지 않는다. 독립 요청의 실패를 성공할 때까지 재시도하지 않는다. 종료 상한은 테스트 후크/실행 도구에서 적용하며 실패를 skip으로 숨기지 않는다. 이미지 취득과 설치는 별도 환경 준비 작업으로 기록한다. 이 값은 환경 검증의 출발점이며 업무 DB lock/statement timeout과 무관하다. 값 변경 시 원인·변경값·실행 결과를 docs/decisions.md와 docs/test-evidence.md에 함께 기록한다.

## 중단·실패 후 정리와 재개

### 격리된 시험 DB의 마이그레이션 실패

- 시험 실행 담당 코드가 DB 이름·컨테이너 식별값·시험 실행 UUID·migration 위치와 오류·적용 메타데이터 확인 가능 여부를 기록한다. 비밀번호·접속 문자열의 비밀값은 기록하지 않는다. 일부 객체가 남았거나 상태 조회가 불가능해도 성공으로 처리하지 않는다. DDL이 모두 롤백되었다고 추정하지 않는다.
- 지원 코드가 실패·성공 모두에서 연결/클라이언트를 닫고 이번 실행이 만든 시험 컨테이너를 정리한다. 프로세스 중단으로 후크가 실행되지 않았다면 실행 담당자가 기록된 식별값으로 이번 시험 자원만 확인·정리한다. 개발 DB·named volume·다른 실행 자원은 삭제하지 않는다.
- 종료10초 상한을 넘거나 정리 여부가 불명확하면 잔존 자원 식별값·이유를 기록하고 인수를 보류한다. 실패 DB에서 자동 repair·clean 또는 같은 migration의 무조건 재실행으로 통과시키지 않는다.
- 재개는 실패 원인 수정과 자원 정리 확인 후 새 격리 컨테이너·새 시험 DB에서 수행한다. 시험용 migration 적용 메타데이터와 insert/select를 처음부터 확인하고, 실패 실행과 정상 재개 실행의 근거를 별도로 남긴다.

### 개발 DB 보존 표식 준비 중단

- 실행 담당자가 준비 전에 접속 DB가 reservation_local인지, 시험 schema/테이블 구조가 예상과 같은지 확인하고 이번 표식 UUID·기대 정수·실행 식별값을 기록한다. 시험 DDL과 표식 삽입은 명시적 트랜잭션으로 준비하고, commit 후 새 연결에서 동일 UUID·값을 읽은 뒤에만 재시작 보존 시험을 시작한다.
- commit 전 오류는 rollback하고 연결을 닫는다. 중단 또는 commit 응답 유실로 결과가 불명확하면 같은 UUID를 새 연결로 조회한다. 행이 없으면 준비 미완료로 기록하고 원인 수정 후 새 표식으로 준비한다. 같은 값의 행이 있으면 준비 commit을 확인한 것으로 기록하고 그 표식을 사용해 보존 시험 전체를 다시 시작한다. UUID는 같지만 값이 다르거나 접속/구조를 확인할 수 없으면 재개하지 않고 불일치·확인 불가를 기록한다.
- 시험 schema나 이전 표식이 남아 있어도 schema 전체 삭제·DB 초기화·volume 제거를 하지 않는다. 실행 담당자는 소유가 확인된 이번 UUID의 표식만 정상 보존 검증 후 정리한다. 실패 표식은 조사·근거 확보 전에 임의로 지우지 않는다. 다른 행이나 기존 개발 데이터를 수정하지 않는다.
- 정지·재시작 도중 중단된 경우 준비 commit·표식·동일 named volume 연결을 확인한 뒤 정지 → 재생성 → 동일 표식 조회의 전체 보존 시험을 재개한다. 중단된 일부 절차나 단순 조회를 완료 근거로 계산하지 않는다.

두 흐름 모두 실행 담당자/지원 코드의 정리 책임, 실패 상태·남은 자원·원인 수정·재개 결과를 docs/test-evidence.md에 남긴다. 재개 조건이 충족되기 전 해당 검증과 1단계 전체 완료를 보류한다. 이는 도구 시험 데이터의 복구 계약이며 업무 예약 트랜잭션·API 계약을 변경하지 않는다.
