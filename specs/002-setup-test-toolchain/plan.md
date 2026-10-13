# 구현 계획: 1단계 환경과 테스트 도구 최소 실행 검증

**실제 구현 Git 브랜치**: `chore/setup-test-toolchain` (설계: `docs/setup-test-toolchain`) | **작성일**: 2026-10-13 | **명세**: [spec.md](spec.md)

**입력**: `specs/002-setup-test-toolchain/spec.md`. setup-plan의 JSON은 `BRANCH=002-setup-test-toolchain`을 출력하지만 이는 활성 기능 식별값이다. 실제 Git 브랜치는 위 값을 사용한다.

## 요약

기존 Gradle 애플리케이션을 유지하며 로컬 의존 서비스, 실제 연동, 테스트 발견·실패 종료, Property의 seed·shrinking, PIT, 실제 HTTP 최소 계약을 한 검증 대상씩 준비한다. PostgreSQL만 기본 종료·재시작 후 데이터 보존을 필수 확인한다. Redis는 저장·조회 연동을 확인한다.

계획 산출물과 tasks.md를 작성한 뒤 2026-10-13 구현을 수행했다. 실제 환경·최소 실행·실패 탐지·원복은 [실행 증거](../../docs/test-evidence.md), [버전](../../docs/versions.md)에 기록했다. 업무 기능은 범위 밖이다.

## 기술 맥락

- **언어·버전**: Java25, preview 제외. 현재 설정은 Boot4.1.1·Gradle9.7.1이며 유지한다. 호스트는 Corretto25.0.4+7-LTS, 실제 BOM은 Jupiter6.0.3/TC2.0.5/Flyway12.4.0/JDBC42.7.13이다.
- **주요 의존성**: Boot MVC/Validation/Security/JDBC/Redis/Flyway/Actuator starter와 PostgreSQL driver·Flyway PostgreSQL 모듈. 관리 버전은 Boot BOM 우선. 필요한 테스트 starter와 Testcontainers2 artifact는 조사 좌표를 실제 해석해 확인한다.
- **저장소**: Compose PostgreSQL18.x(named volume)·Redis와 별도 동적 테스트 컨테이너. 우선 이미지 후보는 postgres18.6-bookworm·redis8.10.2-trixie; 취득 digest·arm64 내부 실행을 확인하고 두 설정에 고정했다. PostgreSQL18 volume 대상은 `/var/lib/postgresql`다.
- **검증**: Jupiter·AssertJ·Testcontainers, 순수 int 프로브 PIT, QuickTheories0.26, validator core2.46.1과 JDK HTTP fixture. PIT plugin1.19.0/engine1.22.1/Jupiter지원1.2.3은 첫 최소 검증 후보. 버전 근거·대안·실행 게이트는 research에 정리했다.
- **대상 플랫폼**: 현재 macOS 호스트의 localhost CLI/API, Linux 컨테이너. 실제 arm64/Docker메모리8,216,915,968 bytes를 versions에 기록했다.
- **프로젝트 유형**: 단일 Spring Boot 백엔드의 환경·검증 기반 준비.
- **성능 목표**: 부하·운영 지연 목표 없음. k6는 존재·정확한 버전 확인만 한다. 환경 대기는 Compose/컨테이너120초·앱60초·HTTP 연결3초/요청10초·시험 자원 종료10초로 제한하고 Test 태스크는 초기 fork1·클래스 순차 실행한다.
- **제약**: 기본 테스트는 Spring 없이 수행한다. 실제 저장소·HTTP를 검사하며 개발 DB를 IT에서 재사용하지 않는다. 모든 필수 범위0건·skip은 실패다. 비밀값·개발 데이터는 보존하고 업무 계약을 바꾸지 않는다.
- **규모·범위**: 기본1건 이상, PostgreSQL/Redis 각 연동1건 이상, 정수 생성최소100회·양끝값·축소/재현, 계약 정상/음성 각각1건 이상, 변이생성/탐지 각각1개 이상. 실제 스키마·예약·UUID 인증·요청 제한·경쟁·업무 속성·부하·외부CI는 후속 범위다.

## 헌법 검토

| 게이트 | 조사 전 | 설계 후 | 근거 |
| --- | --- | --- | --- |
| I. 책임 경계·API 호환성 | 통과 | 통과 | 기존 단일앱·패키지 유지. fixture는 테스트에서만 실행. 업무 API·SQL 변경 없음 |
| II. 근거·도메인 기록 | 통과 | 통과 | 지식 규칙 전체, resource·ratelimit 문서와 계획서 확인. 업무 도메인 신규 개발 없음 |
| III. 발견·검증·실행 증거 | 통과 | 통과 | 명시적 이름·태그·조합·0건/skip·실패 종료·원복·보고서 계약. 실행 성공은 아직 없음 |
| IV. 정책 동기화 | 통과 | 통과 | A 답변 유지. 설계·구현·실행 확인 분리. 구현 시 원계획의 Maven/경로 예시와 기록을 실제 Gradle 상태로 맞출 작업 포함 |
| V. 미확정 정책 | 통과 | 통과 | 보존 범위 확정, 도구 결정·대안 조사 완료. 실제 환경값은 실행 측정 대상. 헌법 채택일/외부 CI TODO는 이번 로컬 작업의 선행 조건 아님 |

게이트 위반이나 정당화가 필요한 복잡성 예외는 없다. 설계 후 재검토에서도 동일하게 통과했다. 도구의 실제 호환성 미검증은 조사 단계의 숨은 완료가 아니라 구현 단계에서 판정할 필수 게이트다.

## 프로젝트 구조

### 이번 기능 문서

```text
specs/002-setup-test-toolchain/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  checklists/requirements.md
  contracts/validation-contract.md
  contracts/toolchain-smoke.openapi.yaml
```

`tasks.md`는 이번 명령에서 생성하지 않는다.

### 실제 도구 기반과 후속 대체 위치

```text
backend/
  build.gradle                       # 존재, 태스크·의존성 확장 및 최소 실행 확인
  gradlew, gradle/wrapper/            # 존재, 유지
  src/main/java/com/limitedbooking/backend/
    BackendApplication.java          # 존재, 유지
    common/ToolchainProbe.java        # 구현·최소 실행 확인: 무해한 순수 int 프로브, Bean/API 아님
  src/main/resources/
    application.yaml                 # 존재
    application-local.yaml           # 구현·최소 실행 확인
  src/test/java/com/limitedbooking/backend/
    BackendApplicationIT.java        # 구현·최소 실행 확인: 실제 컨테이너+RANDOM_PORT 앱 기동
    support/ContainerTestSupport.java # 구현·최소 실행 확인
    support/ToolchainContractValidator.java # 구현·최소 실행 확인
    support/ToolchainHttpFixture.java # 구현·최소 실행 확인: 테스트 전용 동적 HTTP 서버
    common/ToolchainSmokeTest.java    # 구현·최소 실행 확인: Spring 없는 프로브 Unit, PIT 대상
    common/ToolchainSmokeIT.java      # 구현·최소 실행 확인: Flyway 적용+PG insert/select+Redis set/get
    common/ToolchainPropertyTest.java # 구현·최소 실행 확인: property 태그, 순수 메모리
    common/ToolchainContractIT.java   # 구현·최소 실행 확인: contract 태그, 실제 fixture HTTP
  src/test/resources/
    junit-platform.properties        # 구현·최소 실행 확인: 클래스 순차 실행
    db/toolchain/V1__toolchain_probe.sql # 구현·최소 실행 확인: 격리 테스트 전용 migration
compose.yaml, .env.example, README.md # 구현·최소 실행 확인: 프로젝트 루트
```

프로브는 도구 확인용이며 업무 capacity 규칙을 새로 구현하지 않는다. 실제 규칙 준비 뒤 프로브와 smoke PIT 대상을 대체할 대상으로 기록한다. 별도 서비스·Repository·source set을 먼저 추가하지 않는다. 계약 리소스는 contracts YAML과 루트 docs/openapi.yaml에서 테스트 리소스로 복사해 원본을 중복 관리하지 않는다.

## 실행 설계와 순서

1. **저장소·환경 목록**: 의존성 해석 전 JDK·Wrapper의 실제 존재와 실행 가능 여부를 먼저 확인한다. 독립 저장소·HEAD·작업 트리·JDK·Wrapper·Docker/Compose·k6·CPU·메모리를 확인한다. 부족한 도구만 준비하고 설치/문서확인/실행확인을 구분한다. 실제 구현은 별도 `chore/` 작업 브랜치에서 검증 대상별 커밋을 남긴다.
2. **기본 실행**: 순수 ToolchainProbe와 ToolchainSmokeTest를 준비해 Jupiter 발견·실제 assertion·기본 실패 종료를 확인한다. 기존 Spring 기동 검사는 삭제해 감추지 않고 BackendApplicationIT로 분류를 이동한다. 실패 확인은 값 불일치로 수행하며 원복한다.
3. **로컬·연동**: 필요한 starter·BOM·image를 해석/고정하고 Compose local과 동적 컨테이너 지원을 준비한다. 테스트 전용 Flyway migration 적용·metadata·PG 삽입/조회·Redis 저장/조회를 확인한다. 앱 IT와 local 기동은 별도 결과로 기록한다.
4. **발견·범위**: 이름·태그·classpath와 `test`, `integrationTest`, `contractTest`, `propertyTest`, 조합태스크를 구성한다. 각 그룹에 실제 사례를 둔 뒤 0건/skip/불일치 고의 설정의 비성공 종료를 확인한다. 아직 준비되지 않은 필수 그룹을 빈 채 성공 처리하지 않는다.
5. **심화 도구**: 라이브러리 준비 → 속성/계약 사례 작성 → 계약 fixture·validator·리소스 연결 → 전체 test 컴파일 → Property 정상/경계결함/축소/seed재현/원복 → PIT int 최소 변이 → 계약 실제 HTTP 정상/id제거 거부와 기존 명세 읽기를 각각 수행한다. 동일 test source set이므로 태그 제외만으로 미완성 계약 클래스의 컴파일 문제를 피하지 않는다. 하나씩 독립 검증하고 실패 원인 없이 다른 도구 성공으로 대체하지 않는다.
6. **보존과 인수**: local PostgreSQL 표식 저장·commit → Compose down(볼륨 유지) → up → 동일 표식 조회를 실행한다. IT는 개발 DB에 접속하지 않는다. 마지막 심화 조합을 실행하고 보고서·원복·필수 그룹·버전 근거를 대조한다. PIT은 별도 실행으로 판정한다.
7. **문서 동기화**: 실제 실행 명령은 README, 버전·이미지·해석 결과는 docs/versions.md, 선택은 docs/decisions.md, 실패/정상/seed/축소/변이는 docs/test-evidence.md에 남긴다. 원계획3·6·7절의 환경·명령 설명도 실제 Gradle과 구분되도록 갱신한다. 테스트0건·건너뜀·미원복·미검증 도구가 남으면 1단계 체크박스를 완료로 표시하지 않는다.

이 순서는 설계이며 task ID나 실행 증거가 아니다. 태스크 조합의 상세는 실행 계약, 실제 수행 안내는 quickstart를 따른다. 실패가 없고 변경이 없으면 불필요하게 전체 검증을 반복하지 않는다. 다음 명령은 `$speckit-tasks`다.

## 요구사항 추적

| 요구사항 | 계획·인수 근거 |
| --- | --- |
| FR-001·FR-002 | 실행순서1, quickstart1, 환경 목록 |
| FR-003·FR-004 | local·보존 계약, 실행순서3·6, quickstart2·3 |
| FR-005·FR-006 | 순수 Unit·실제 저장소/Flyway·앱 IT, quickstart4 |
| FR-007 | PIT 후보·범위·결과 gate, quickstart5 |
| FR-008 | 정수 생성·경계·고의 결함·seed/축소, quickstart5 |
| FR-009·FR-010 | 실행 계약의 발견·조합·현재 결과 판정, quickstart4·6 |
| FR-011 | 단계별 고의 실패/재현/원복, quickstart4·5·6 |
| FR-012 | test HTTP fixture·최소 계약·기존 명세 읽기, quickstart5 |
| FR-013·FR-014 | 데이터 모델의 증거·조합 상태, 실행순서7, quickstart6 |

SC-001은 보존과 환경 목록, SC-002는 기본·두 저장소 연동, SC-003은 PIT·속성 재현, SC-004는 계약 정상/음성/문서 읽기, SC-005는 각 필수 범위 결과, SC-006은 기록과 최종 인수로 판정한다. 이 대응은 실행 계획이며 현재 성공 횟수를 나타내지 않는다.

## 분석 후 보완 (2026-10-13)

I1의 공유 test 컴파일 선행 조건과 I2의 환경 점검 우선 순서를 반영했다. A1의 구체적인 환경 대기 상한·초과 판정은 [실행 계약](contracts/validation-contract.md)의 “환경 검증의 대기 상한”을 기준으로 한다. 사용자 확정 보존 범위와 업무 API·DB 정책은 유지한다. 구현 작업 수는42개이며 번호 변경은 tasks.md의 현재 목록을 기준으로 한다.

CHK022의 실패·중단 복구 계약은 [실행 계약](contracts/validation-contract.md#중단실패-후-정리와-재개)에 추가했다. 격리 시험 DB 실패는 이번 자원 정리·원인 수정 후 새 DB에서 재검증하고, 개발 보존 표식 중단은 commit 결과·UUID·기대값 확인 후 전체 보존 시험을 재개한다. T012/T018에 정리 책임과 실제 확인 근거를 연결하며 개발 데이터·named volume은 보존한다.

## 실제 구현 결과

1단계 최소 검증은 Unit3/일반IT4/Contract3/Property3(내부101회), PIT1생성1탐지·NO_COVERAGE0이다. PostgreSQL 표식73 보존 및 실패 migration 후 새 격리 DB 재개를 확인했다. QuickTheories0.26의 중복 제거 때문에 생성 예산100→1000을 조정했고 실제100회 하한은 유지했다. tag+digest를 사용하는 공식 PostgreSQL에 Testcontainers compatibility를 명시했다. Spring context 종료 뒤 의존 컨테이너를 정리한다.

프로브는 main/common에 있으나 Bean/API/업무 capacity 규칙이 아니다. 3단계 순수 ResourceRules/IdempotencyKey/CancellationDecision을 작성할 때 해당 실제 사례와 PIT 대상으로 대체한다. 시험 migration은 2단계 업무 migration으로, 시험 HTTP fixture는 실제 업무 계약 사례로 점진 대체한다. toolchain 계정은 4단계 업무 UUID 인증 전에 교체한다. Gradle10/configuration cache·업무 경쟁·부하·외부 CI는 미검증이다.
