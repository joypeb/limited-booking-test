# 구현 계획: 고정 OpenAPI와 실제 업무 API 계약 검증

**브랜치**: `feature/010-openapi-contract` | **작성일**: 2026-10-23 | **명세**: [spec.md](spec.md)

**범위**: 9단계의 Phase 0 조사·Phase 1 설계만 완료한다. 승인된 명세·체크리스트를 보존한다. tasks 생성, 구현, 테스트 실행, 커밋·push·PR은 이번 범위 밖이다.

## Summary

`docs/openapi.yaml`의 OpenAPI 3.0.3을 독립 기대값으로 확정하고 테스트 전용 `ApiContractValidator`와 실제 HTTP `ApiContractIT`를 추가할 계획이다. 기존 프로브·라이브러리·Gradle 리소스 복사와 실행 게이트를 재사용한다. 정상 요청/응답, 의도적으로 무효인 요청의 정확한 예상 위반, 항상 완전히 유효해야 하는 오류 응답을 분리한다. 스키마 외 의미 검사는 UTC·필수 헤더·Location-id·Replay·Retry-After·취소 관계를 보완한다. 제품 API·SQL·스키마·정책은 변경하지 않는다.

## Technical Context

**Language/Version**: Java 25, preview 없음. 현재 worktree Wrapper 9.7.1·Boot 4.1.1 유지. 원본의 사용자 Wrapper 9.8 변경과 별개이며 자동 수정·하향 금지.

**Primary Dependencies**: Spring MVC/Security/JDBC, Flyway, Redis, 기존 `openapi-request-validator-core:3.0.0`/networknt 2.0.1, OpenAPIV3Parser. 계약 라이브러리는 test classpath에만 사용한다. 기존 Jackson 2 테스트 BOM 2.21.8과 애플리케이션 Jackson 3 경계를 유지한다.

**Storage**: 기존 PostgreSQL 18.6-bookworm·Redis 8.10.2-trixie의 ContainerTestSupport 고정 digest/동적 포트. 새 저장 구조 없음.

**Testing**: JUnit·AssertJ, RANDOM_PORT 실제 HTTP·Basic 인증·실제 PostgreSQL/Redis. 순수 검사기 `*Test`, 실제 업무 `*IT` + `contract` 태그. 기존 실행 listener/XML·0건/skip 거부·의존성 게이트 재사용.

**Target Platform**: localhost CLI/API, UTF-8/UTC. Docker 소유 컨테이너, 개발 DB 접속 금지.

**Project Type**: 단일 Spring Boot 백엔드의 테스트·고정 명세 확정.

**Performance Goals**: 새 운영 처리량 목표 없음. 명세 파싱/참조 해석은 검사기 인스턴스 생성 시 한 번, 매 교환은 본문 길이에 비례한 스키마/원문/의미 검사 비용. 실제 DB/Redis 기동·장애·기존 경쟁 회귀가 실행 시간의 주비용이다. 8KiB 경계 전후의 유한 fixture만 유지하며 무제한 본문·재시도·sleep을 만들지 않는다. 단계별 시간·메모리 관측은 docs/performance.md에 실제 측정한 경우만 기록한다.

**Constraints**: 제공된 void 인터페이스 유지, 모든 API별 정상/실패, DB·Redis 503 구분, chunked 413·인증 전 크기 제한, strict JSON 표현 한계 보완. Qodana는 최신 사용자 지시로 이번 완료·PR·병합 게이트 제외·미실행 후속.

**Scale/Scope**: 업무 작업 5개, 명세 적용표 전체, 409 두 code, 재현 시퀀스, 독립 응답 훼손 및 별도 실제 DTO 타입 변이. 10단계 Property/PIT·부하·배포·CI 확장 제외. 미해결 업무 정책 없음.

## Constitution Check

| 헌법 게이트 | 조사 전 판정·근거 | 설계 후 재검토 |
| --- | --- | --- |
| I 경계·호환성 | 통과: 기존 5 API·계층·SQL 보존 | 통과: 검사기는 테스트 전용, DTO 변이는 반드시 원복 |
| II 근거 대조 | 통과: AGENTS, knowledge README·rules 전체·4 domain, 계획서 2~5절·7절9단계·헌법·실제 코드 확인 | 통과: FR별 추적과 기존 파일 재사용 명시 |
| III 실제 검증·증거 | 통과: 계획과 실행 구분 | 통과: TDD·실제 HTTP·고의 훼손/DTO RED→원복 GREEN·최종 게이트 설계; 이번 실행 없음 |
| IV 문서 동기화 | 통과: 기존 docs/knowledge 체계 유지 | 통과: 구현 시 동기화할 경로 명시, 새 업무 도메인 없음 |
| V 미확정 정책 | 통과: 승인 명세 우선, 새 정책 없음 | 통과: 도구 특성의 탐지력은 후속 실행으로 확인; 정책 질문 없음 |

헌법의 2026-10-13 골격/태스크 부재 표시는 당시 확인 기록이다. 현재 build.gradle·업무 코드가 실제 기준이다. 헌법의 채택일/CI_CONTEXT TODO는 이번 로컬 계약 계획의 미확정 업무 정책이 아니며 임의 수정하지 않는다. 최신 Spec Kit 지시가 과거 superpowers 절차보다 우선한다. 위반 예외 없음.

## Project Structure

### Documentation (this feature)

```text
specs/010-openapi-contract/
├── spec.md                         # 승인본 유지
├── checklists/requirements.md      # 승인본 유지
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/validation-contract.md
```

`tasks.md`는 후속 speckit-tasks 산출물이며 이번에는 만들지 않는다.

### Source Code (repository root)

```text
docs/openapi.yaml                                      # 기존, 확정 예정
backend/build.gradle                                   # 기존 연결/게이트 재사용
backend/src/test/java/com/limitedbooking/backend/
├── support/ApiContractValidator.java                   # 신규 예정
├── support/ApiContractHttpSupport.java                 # 신규 예정
├── support/ApiContractValidatorTest.java                # 신규 예정
└── common/ApiContractIT.java                            # 신규 예정
```

**Structure Decision**: `ApiContractIT`에 정상·오류·재현을 둔다. 파괴적 의존성 중단은 별도 `ApiContractDependencyIT.java` 파일(신규 예정, contract 태그, 별도 컨테이너/컨텍스트)에 격리할 수 있다. 응답 사본 훼손은 실제 정상 교환을 확보한 뒤 실행하며 순수 검사기 Unit과 별개다. 기존 package-private `ReservationApiTestSupport`/`RateLimitHttpTestSupport`를 무리하게 공개하거나 상속하지 않는다. 테스트 전용 관측/유한 종료 패턴을 작은 새 HTTP 지원에 재사용하고 일반 IT의 의미를 바꾸지 않는다.

## Phase 0: 조사 결과

[research.md](research.md)에 선택·이유·대안을 기록했다. 기존 도구 채택을 유지하며 새 라이브러리 조사를 위한 교체는 하지 않는다. 본문 없는 GET 프로브인 ToolchainContractValidator를 업무 전체 검증 완료로 보지 않는다. setup-plan.sh --json은 정상 종료했고 BRANCH 값 `010-openapi-contract`는 Spec Kit 선택 식별자다. 실제 Git 브랜치는 `feature/010-openapi-contract`, HEAD는 `8f088ef`이며 변경하지 않았다. 확장 설정 `.specify/extensions.yml`은 없어 전후 훅이 없다.

## Phase 1: 설계와 검증 경계

세부 인터페이스·적용표는 [검증 계약](contracts/validation-contract.md), 값·관계는 [data-model.md](data-model.md), 명령/실패 탐지/원복은 [quickstart.md](quickstart.md)를 따른다.

1. 고정 명세 확정·parser/ref 오류 차단과 검사기 정상/무효 분리의 Unit을 먼저 작성한다. 스키마·필수 헤더·UTC·정확한 예상 위반을 한 종류씩 RED→최소 구현→GREEN으로 진행한다.
2. 정상 5 API의 실제 HTTP 교환을 고정 계약과 대조한다. 이름 strip 후 유효/100코드포인트·capacity0, 헤더 대소문자/JSON 순서 동등성을 포함한다.
3. 적용표의 모든 오류 조합과 strict JSON·바이트 경계·chunked·실제 DB/Redis 장애를 검증한다. 무효 요청마다 허용할 요청 위반을 명시하고 응답은 항상 전체 검증한다.
4. 생성→재전송→취소→중복취소→취소후재전송→GET을 연결해 최초 불변 결과/현재 상태/최종 DB 수량을 검증한다.
5. 정상 응답의 독립 훼손 및 실제 DTO 타입 변이의 HTTP 실패→원복 통과를 각각 증거로 남긴다.
6. 최종 `build verifyContract verifyContractDependencies`를 실행한다. verifyContract가 일반 verify를 포함하므로 기존 HTTP·경쟁110회·실패/종료 회귀도 빠짐없이 발견됐는지 XML/실제 실행 수로 확인한다.

### 변경 예정 파일·재사용·독립성

| 대상 | 예정 변경·이유 | 재사용·독립 수정 가능성 |
| --- | --- | --- |
| docs/openapi.yaml | UTC 의미·필수 헤더 설명·상태별 관계·default 비통과 명확화, 오래된 미실행 설명 갱신 | 단일 고정 원본. schema는 확정 정책만 반영, 계약 품질 검토와 검사기 설계 후 병합 |
| support/ApiContractValidator.java·Test | 요청/응답 분리·예상위반/의미검사, 각 실패 탐지 | 프로브 parser 방식/라이브러리 재사용, DB 없이 독립 구현 가능 |
| support/ApiContractHttpSupport.java | 실제 원문 capture·헤더 전달·유한 종료 | ContainerTestSupport/HttpClientTestSupport/TestData/DatabaseAssertions/RateLimitTestSupport/ConcurrencyTestSupport 재사용, 검사기와 합의한 교환 경계 후 독립 작성 가능 |
| common/ApiContractIT.java | 정상·오류·재현·응답 훼손 | 지원·명세 안정 후 작성. 파괴적 장애 fixture와 분리 가능하나 실행/공유 기록은 순차 |
| 실제 DTO ResourceView.java | 고의 타입 변경 실험에만 임시 수정 | 최종 diff0 필수, 실험 중 다른 작업 금지 |
| README·계획서 7절9단계·knowledge/README·관련 rules/domain | 구현 파일·검증 계약 연결 및 실제 상태 동기화 | 검증 후 반영. 업무 규칙 변경 없이 완료 체크는 실제 인수 후 |
| docs/decisions.md·versions.md·test-evidence.md·performance.md | 선택·해석 의존성·명령/수/RED/GREEN·비용/미측정 기록 | 기존 기록 형식 유지, 단일 작성자로 순차 정리 |
| backend/build.gradle·ToolchainContractIT/Validator | 기본 수정 없음 | 기존 리소스 business.openapi.yaml·태그·보안 의존성·실행 게이트 보존, 발견 결함 있을 때만 최소 변경 |

### 요구사항 추적과 자가검토

| 요구사항 | 설계/인수 근거 |
| --- | --- |
| FR-001~002 | 고정 docs 원본·전체 원문 전달·정상 요청/응답 각각 검사 |
| FR-003 | 불변 예상위반 설정·정확한 multiset 비교·raw 보완·응답 무조건 검사 |
| FR-004 | 검증 계약 적용표 전 조합·DB5/Redis2 작업 구분 |
| FR-005~007 | data-model·의미검사·헤더 존재·default 거부·오류6필드/민감정보 |
| FR-008 | 연결 시퀀스·최종 DB 불변식 |
| FR-009~010 | 독립 훼손·컴파일가능 실제 ResourceView 타입변이 절차 |
| FR-011~012 | 소유 컨테이너/키·유한 종료·기존 회귀·실행 수/XML·증거 기록 |

승인 명세/체크리스트·헌법·현재 코드를 대조했다. GET 413은 전역 상한이며 새 정책이 아니다. Problem type은 현재 about:blank도 유효하며 예시 urn/title/detail을 바이트 고정하지 않는다. 문서의 default가 있어도 명시 상태만 승인한다. 아직 업무 계약 검사기·실행 증거가 없고 설계만 완료했다. 최종 자가검토에는 구현 탐지력 확인이 남으며 이를 계획 단계 성공으로 대체하지 않는다.

## Complexity Tracking

헌법 위반 없음. 새 제품 계층·DB·런타임 의존성 없이 테스트 경계만 추가한다.
