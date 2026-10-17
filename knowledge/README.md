# 프로젝트 지식 문서

이 프로젝트는 Java/Spring 개발 감각을 복구하고 AI가 작성한 코드를 직접 이해·검증하기 위한 localhost 전용 한정 수량 예약 백엔드다. PostgreSQL이 수량·예약·성공한 생성 키의 원본이며 Redis는 인증된 사용자의 예약 생성·취소 요청 제한만 담당한다. 핵심은 기능 수보다 수량 보존, 멱등성, 취소의 원자성과 재현 가능한 검증이다.

설계 기준은 [로컬 개발 계획](<../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절이다. 이 폴더는 그 기준을 도메인 지식과 개발 규칙으로 정리한다. 작업 절차와 저장소 주의사항은 [AGENTS.md](../AGENTS.md)도 따른다. 최신 사용자 지시를 우선하고, 기준 간 차이는 확인·기록한 뒤 관련 문서를 함께 갱신한다.

## 작업 전 읽는 순서

1. 이 안내를 읽고 `knowledge/` 하위 Markdown 목록을 확인한다.
2. 아래 개발 규칙을 모두 읽는다.
3. 작업 대상과 연관 도메인의 문서를 읽고, 연결된 계획서 절에서 기대값을 확인한다.
4. 실제 코드·SQL·테스트·설정을 확인해 구현 여부와 차이를 구분한다.
5. 한 단계의 한 검증 대상을 정해 개발하고 관련 지식 문서와 실행 기록을 갱신한다.

## 개발 규칙 목록

| 문서 | 내용 | 계획서 근거 |
|---|---|---|
| [구조와 범위](rules/architecture.md) | 프로젝트 범위, 기술, 패키지·계층 책임 | 1~3절 |
| [도메인과 트랜잭션](rules/domain-and-transactions.md) | 수량 보존, 멱등성, SQL, 잠금·롤백 | 2·5절 |
| [API와 보안](rules/api-and-security.md) | 엔드포인트, 엄격한 입력, 인증·권한, 오류 | 2·4절, 7절 4·6단계 |
| [Redis 요청 제한](rules/rate-limit.md) | 고정 창, Lua·TTL, 처리 순서·장애 | 2절, 7절 8단계 |
| [개발과 검증](rules/development-and-testing.md) | 단계별 개발, 테스트 분류·경쟁·계약·속성·변이 | 1.3·6~9절 |
| [환경과 기록](rules/environment-and-records.md) | 로컬 설정, 버전·비밀정보, 증거·CI·부하 | 3절, 7절 1·11·12단계, 8.3·13절 |
| [커밋](rules/commit.md) | 커밋 단위·메시지·스테이징·검증 | 1.3·8.3절, 사용자 요청으로 추가한 규약 |
| [Git Flow](rules/git-flow.md) | 브랜치 역할·작업·병합·로컬 버전 관리 | 단계별 검증·로컬 범위, 사용자 요청으로 추가한 규약 |

## 도메인 문서 목록과 작성 방법

- [_template.md](domain/_template.md): 새 도메인 문서의 공통 형식. 템플릿 자체는 특정 도메인의 확정 계약이 아니다.
- [resource.md](domain/resource.md): 예약 대상의 책임·입력·수량 불변식·권한과 코드 포인트 기준 이름 길이. 저장 스키마·읽기/생성 SQL, `ResourceRules` Unit 52건, 4단계 `POST /api/resources`·`GET /api/resources/{id}` HTTP 통합 테스트(`ResourceApiIT` 8건)까지 구현·검증했으며 예약 생성/취소에 따른 수량 차감·복구는 5~7단계 후속이다.
- [reservation.md](domain/reservation.md): 예약·멱등 재현·취소·트랜잭션·경쟁의 설계 지식.
- [security.md](domain/security.md): 로컬 고정 계정 Basic 인증·권한·고정 UUID Principal·인증 앞단 8KiB 본문 상한 필터·공통 `problem+json` 오류 처리의 설계 및 4단계 구현·검증 지식 (`SecurityIT` 6건). 예약 소유권 조회·취소(6단계)와 Redis 요청 제한(8단계)은 후속이다.
- [ratelimit.md](domain/ratelimit.md): Redis 고정 창·처리 순서·장애의 설계 지식.
- 예약 생성·취소 API·쓰기 트랜잭션·경쟁·Redis 요청 제한(5~8단계)은 미구현이다. 1단계 도구 프로브의 실제 HTTP·DB·Redis 최소 검증과 업무 기능 검증을 구분한다.
- [2단계 명세](../specs/003-define-db-schema/spec.md): 대상·예약 저장 제약, 전체 저장 속성 조회, 테스트 전용 초기화와 수량 보존 검사 요구사항. 명세 품질 검토와 저장 기반 구현·실행 검증을 완료했다.
- [2단계 구현 계획](../specs/003-define-db-schema/plan.md): 데이터 모델·저장 계층 계약·검증 안내를 연결한다. 설계와 실제 저장 코드·SQL·테스트·실행 증거를 연결한다.
- [2단계 요구사항 검토 목록](../specs/003-define-db-schema/checklists/database.md): 저장 제약·조회와 테스트 격리·재현성의 요구사항 품질을 검토한다. 사용자 요청으로34개 항목의 요구사항 품질을 평가했고 CHK022 실패 처리도 문서 보완 후 충족으로 판단했다. 체크는 구현 완료 표시가 아니다.
- [2단계 작업 목록](../specs/003-define-db-schema/tasks.md): 네 사용자 이야기별 테스트·실패 탐지·구현·검증과 의존성 순서다. 작업별 실제 검증 후 체크하며 맞춤 요구사항 검토를 대신하지 않는다.
- [3단계 명세](../specs/004-validate-domain-rules/spec.md): 이름·최초 수량·생성 키와 현재 상태별 취소 판단의 순수 규칙 요구사항이다. 명세 품질 검토와 순수 규칙 구현·Unit 검증을 완료했다. 사용자 설명 인수는 2026-10-16 설명 후 명시적 완료 지시로 처리했다. 독립 재설명 시험은 미실시다. `ResourceRules`는 4단계에서 실제 요청 경로에 연동했으며 `IdempotencyKey`·`CancellationDecision`은 후속 5~6단계 연동 조건으로 유지한다.
- [3단계 명세 품질 검토](../specs/004-validate-domain-rules/checklists/requirements.md): 명세 내용·완결성·준비 상태 16개 항목의 검토 결과이며 기능 테스트 통과 기록이 아니다.
- [3단계 구현 계획](../specs/004-validate-domain-rules/plan.md): 순수 규칙의 파일·인터페이스·테스트 우선 순서와 후속 연동 조건을 설계했다. [조사](../specs/004-validate-domain-rules/research.md)·[값 모델](../specs/004-validate-domain-rules/data-model.md)·[호출 계약](../specs/004-validate-domain-rules/contracts/rules-contract.md)·[검증 안내](../specs/004-validate-domain-rules/quickstart.md)를 포함하며 순수 규칙 구현·Unit116건 검증을 완료했다.
- [3단계 맞춤 요구사항 검토 목록](../specs/004-validate-domain-rules/checklists/domain-rules.md): 규칙·경계값과 인수·증거 기준의 요구사항 품질34개 항목을 생성했다. 구현 전 검토자용·표준 깊이이며 사용자 요청으로34개 항목을 근거 대조해 전부 충족 판정했다. 구현·실행 완료 표시는 아니다. 내장 명세 품질16개 체크와 별개다.
- [3단계 작업 목록](../specs/004-validate-domain-rules/tasks.md): 준비·공통 선행·세 사용자 이야기·전체 인수로 나눈33개 작업이다. US1 10개·US2 6개·US3 6개·공통11개이며 설명 후 사용자 완료 지시로 T029까지33개 작업을 수행했다. 테스트 파일 작성3개만 병행 후보이고 Gradle 실행·공통 기록·커밋은 순차 처리한다.
- [4단계 명세](../specs/005-auth-resource-api/spec.md): 로컬 고정 계정(`ADMIN`·`USER_A`·`USER_B`) 인증·권한 확인, 8KiB 본문 상한과 엄격한 입력 검증, 공통 `application/problem+json` 오류 응답, 예약 대상 생성·단건 조회 API의 요구사항을 정의하고 구현·실행 검증(`SecurityIT` 6건, `ResourceApiIT` 8건, `./gradlew verify`)을 완료했다.
- [4단계 명세 품질 검토](../specs/005-auth-resource-api/checklists/requirements.md): 명세 내용·완결성·준비 상태 16개 항목의 검토 결과이며 구현·테스트 통과 기록이 아니다.
- [4단계 구현 계획](../specs/005-auth-resource-api/plan.md): 로컬 Basic 인증·고정 UUID Principal, 인증 앞단 8KiB 본문 상한 필터, Jackson 3 엄격 설정과 공통 `problem+json` 오류 처리, UTC `Clock` 기반 대상 생성·조회 API 및 TDD 통합 검증 순서를 설계하고 구현·실행 검증을 완료했다. [조사](../specs/005-auth-resource-api/research.md)·[데이터 모델](../specs/005-auth-resource-api/data-model.md)·[인터페이스 및 API 계약](../specs/005-auth-resource-api/contracts/auth-resource-contract.md)·[검증 안내](../specs/005-auth-resource-api/quickstart.md)를 포함한다.
- [4단계 맞춤 요구사항 검토 목록](../specs/005-auth-resource-api/checklists/auth-resource-api.md): 인증·권한·8KiB 본문 상한·엄격 입력 검증·예약 대상 API·`problem+json` 및 4단계/5~8단계 경계의 요구사항 품질 34개 항목을 생성했다. 구현 전 검토자용·표준 깊이이며 사용자 지시로 U1·U2 보완 후 34개 항목을 근거 대조해 전부 충족 판정했다. 내장 명세 품질 16개 체크와 별개이며 구현·실행 완료 표시가 아니다.
- [4단계 작업 목록](../specs/005-auth-resource-api/tasks.md): 준비(Phase 1)·공통 선행(Phase 2)·세 사용자 이야기(US1 4개·US2 5개·US3 7개)·전체 검증 및 정리(Phase 6)로 나눈 27개 작업을 전부 수행·검증 완료(`27/27`)했다.
- 첫 버전의 업무 도메인은 예약 대상(`resource`)과 예약(`reservation`)이다. `security`·`ratelimit`은 이를 지원하는 기능이며, 필요할 때 같은 형식으로 책임과 계약을 문서화한다. `common`을 독립 업무 도메인으로 가정하지 않는다.
- 파일 이름은 `<domain-name>.md`를 사용한다. 근거·경계·기대 결과를 먼저 적고, 코드·SQL·테스트·명령은 실제 존재 여부를 확인해 연결한다.
- 템플릿의 모든 절을 유지한다. 해당 없는 항목은 이유를 적고, 미확정 사항은 질문과 영향 범위를 적는다. 다른 도메인과 공유하는 규칙은 관련 문서로 연결한다.

## 문서와 구현 상태

2026-10-13 1단계 수행 후 애플리케이션은 backend/, 기본 패키지는 com.limitedbooking.backend이며 Java25·Boot4.1.1·Gradle9.7.1을 유지했다. MVC·Security·JDBC·Redis·Flyway·Actuator 및 검증 의존성을 추가했다. [실행 안내](../README.md), [버전](../docs/versions.md), [실행 증거](../docs/test-evidence.md)에 실제 상태를 연결한다.

Gradle test/verify/verifyContract/verifyProperty/deepTest/mutationSmoke의 도구 최소 실행을 확인했다. 1~3단계에 이어 2026-10-17 4단계 로컬 Basic 인증·권한(`SecurityConfig`, `LocalIdentityResolver`), 인증 앞단 8KiB 본문 상한 필터(`RequestBodyLimitFilter`), 엄격 JSON 역직렬화 및 공통 `problem+json` 오류 처리(`ApiExceptionHandler`), UTC `Clock`(`ClockConfig`), 예약 대상 생성·단건 조회(`ResourceRepository.insert`, `ResourceService`, `ResourceController`)와 통합 테스트(`SecurityIT` 6건, `ResourceApiIT` 8건)를 구현하고 `./gradlew verify`(Unit 116건 + 일반 IT 92건, 실패·오류·skip 0건)를 통과했다. 예약 생성·멱등성·취소·동시성·Redis 요청 제한(5~8단계)은 아직 미구현이다.

`knowledge/`는 지속적인 도메인 지식·규칙을 보관한다. 현재 [OpenAPI 초안](../docs/openapi.yaml), [결정 기록](../docs/decisions.md), [문서 검토 증거](../docs/test-evidence.md)를 작성했다. 이름 공백 범위는 Java strip으로 확정해 계약 문서 인수를 마쳤고, 본인 설명은 질의·보완 뒤 사용자 확인으로 T025를 처리해 0단계 문서·학습 인수를 마쳤다. [버전 기록](../docs/versions.md)은 작성·실행 대조했고, docs/performance.md는 후속 부하 단계에서 작성할 예정이다. 문서 작성과 실제 기능·HTTP·DB·Redis 검증을 구분한다.

현재 `./gradlew verify` 기준 일반 Unit은 116건(업무 113 + 프로브 3), 일반 IT는 92건(1단계 6 + 2단계 72 + 4단계 `SecurityIT` 6·`ResourceApiIT` 8)이며 실패·오류·skip 0이다. `ResourceRules`는 4단계 실제 요청 경로에 연동 완료되었으며 `IdempotencyKey`·`CancellationDecision`의 실제 요청 경로 연동은 5~6단계에서 인수한다.
