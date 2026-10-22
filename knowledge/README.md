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
- [reservation.md](domain/reservation.md): 예약 생성·멱등 재현·트랜잭션·롤백의 구현·검증과 취소·경쟁의 설계 지식.
- [security.md](domain/security.md): 로컬 고정 계정 Basic 인증·권한·고정 UUID Principal·인증 앞단 8KiB 본문 상한 필터·공통 `problem+json` 오류 처리의 설계 및 4단계 구현·검증 지식 (`SecurityIT` 6건). 예약 소유권 조회·취소(6단계)와 Redis 요청 제한(8단계)은 후속이다.
- [ratelimit.md](domain/ratelimit.md): Redis 고정 창·처리 순서·장애의 기준과 US1 limiter/Lua·Unit13/실제RedisIT5 구현·검증 지식. HTTP 연결·실제 장애·전체 인수는 미완료다.
- 예약 생성 API·쓰기 트랜잭션·성공 키·장애 롤백은5단계에서 구현했다. 조회·취소는6단계 구현·검증 완료, 실제 경쟁·Redis 제한(7~8단계)은 후속이다. 1단계 도구 프로브의 실제 HTTP·DB·Redis 최소 검증과 업무 기능 검증을 구분한다.
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

- [5단계 명세](../specs/006-reservation-create/spec.md): 예약 생성·성공한 키 재현·실패 복구·잘못된 요청 거부를 네 사용자 이야기와 12개 요구사항으로 정의했다. 명세 품질 검토 후 생성·멱등성·롤백·입력 HTTP/DB 구현·실행 검증을 완료했다.
- [5단계 명세 품질 검토](../specs/006-reservation-create/checklists/requirements.md): 내용·완결성·준비 상태 16개 항목의 문서 검토 결과다. 기능 테스트 통과나 실제 경쟁·장애 검증 완료를 뜻하지 않는다.
- [5단계 구현 계획](../specs/006-reservation-create/plan.md): 트랜잭션 밖 Service·별도 Writer, 성공 키 재현·정밀도·유일 위반 분류·롤백 검증 순서를 설계했다. [조사](../specs/006-reservation-create/research.md)·[데이터 모델](../specs/006-reservation-create/data-model.md)·[생성 계약](../specs/006-reservation-create/contracts/reservation-create-contract.md)·[검증 안내](../specs/006-reservation-create/quickstart.md)를 포함한다. 코드·신규 Unit21/IT28과 실행 인수를 완료했다.
- [5단계 맞춤 요구사항 검토 목록](../specs/006-reservation-create/checklists/reservation-create.md): 생성·성공 키 재현·원자적 실패 복구·오류·단계 경계를 검토하는 34개 항목이다. 구현 전 검토자용·표준 깊이로 생성했고, 사용자 지시로 항목별 근거를 대조해34/34 품질 충족 판정했다. 구현·실행 완료가 아니며 내장 명세 품질16개 체크와 별개다.
- [5단계 작업 목록](../specs/006-reservation-create/tasks.md): 준비·공통 선행·네 사용자 이야기·전체 인수의 43개 작업이다. US1 10개·US2 7개·US3 11개·US4 6개·공통 9개다. 작업별 실제 실행 증거로 완료 상태를 관리하며 전체 인수 결과는 tasks.md와 docs/test-evidence.md를 따른다. 서로 다른 테스트 파일 작성 4개만 병행 후보이고 공유 구현·Gradle·기록·커밋은 순차 처리한다. 작업 생성 당시에는 맞춤 요구사항 검토 상태를 유지했고, 후속 사용자 지시로 별도34/34 품질 평가를 완료했다.
  2026-10-19 분석의 C1·G1·G2 보완으로 T019 테스트·RED→T020 구현·GREEN 순서, 신규 세 IT의 풀10/연결 획득3000ms 설정·실제 적용값 확인, 입력 오류·품절 후 같은 키 재시도 성공을 관련 계획·계약·검증 안내에 반영했다. 당시에는 문서 보완이었고 이후5단계 구현에서 해당 순서·설정·실패 키 복구를 실행 검증했다.

## 문서와 구현 상태

2026-10-13 1단계 수행 후 애플리케이션은 backend/, 기본 패키지는 com.limitedbooking.backend이며 Java25·Boot4.1.1·Gradle9.7.1을 유지했다. MVC·Security·JDBC·Redis·Flyway·Actuator 및 검증 의존성을 추가했다. [실행 안내](../README.md), [버전](../docs/versions.md), [실행 증거](../docs/test-evidence.md)에 실제 상태를 연결한다.

Gradle test/verify/verifyContract/verifyProperty/deepTest/mutationSmoke의 도구 최소 실행을 확인했다. 1~3단계에 이어 2026-10-17 4단계 로컬 Basic 인증·권한(`SecurityConfig`, `LocalIdentityResolver`), 인증 앞단 8KiB 본문 상한 필터(`RequestBodyLimitFilter`), 엄격 JSON 역직렬화 및 공통 `problem+json` 오류 처리(`ApiExceptionHandler`), UTC `Clock`(`ClockConfig`), 예약 대상 생성·단건 조회(`ResourceRepository.insert`, `ResourceService`, `ResourceController`)와 통합 테스트(`SecurityIT` 6건, `ResourceApiIT` 8건)를 구현하고 `./gradlew verify`(Unit 116건 + 일반 IT 92건, 실패·오류·skip 0건)를 통과했다. 5단계 예약 생성·멱등성·원자적 실패 복구를 추가 구현했다. 예약 조회·취소는6단계 검증 완료, 실제 동시성 반복·Redis 제한(7~8단계)은 후속이다.

`knowledge/`는 지속적인 도메인 지식·규칙을 보관한다. 현재 [OpenAPI 초안](../docs/openapi.yaml), [결정 기록](../docs/decisions.md), [문서 검토 증거](../docs/test-evidence.md)를 작성했다. 이름 공백 범위는 Java strip으로 확정해 계약 문서 인수를 마쳤고, 본인 설명은 질의·보완 뒤 사용자 확인으로 T025를 처리해 0단계 문서·학습 인수를 마쳤다. [버전 기록](../docs/versions.md)은 작성·실행 대조했고, docs/performance.md는 후속 부하 단계에서 작성할 예정이다. 문서 작성과 실제 기능·HTTP·DB·Redis 검증을 구분한다.

4단계 `./gradlew verify` 기준 일반 Unit은 116건(업무 113 + 프로브 3), 일반 IT는 92건(1단계 6 + 2단계 72 + 4단계 `SecurityIT` 6·`ResourceApiIT` 8)이며 실패·오류·skip 0이다. `ResourceRules`는 4단계 실제 요청 경로에 연동 완료되었으며 `IdempotencyKey`는5단계 생성 경로에 연동했고 `CancellationDecision`은6단계 취소의 이미 취소됨 분기에 연동·검증했다.


2026-10-19 5단계: 생성 Controller/Service/Writer·Repository 조건UPDATE/INSERT RETURNING·구조화 유일 위반 분류기를 구현했다. 전체 Unit137과 생성3IT28의 지정 인수를 통과했다. 최종 일반verify의 현재 수/결과는 [실행 증거](../docs/test-evidence.md)를 따른다. 실제 경쟁/취소/Redis/심화는 후속이다.

최종 일반verify: Unit137·일반IT120, 실패·오류·skip0. 상세 명령/환경/한계는 docs/test-evidence.md에 기록했다.


## 6단계 소유자 조회·취소 (2026-10-20)

- [명세](../specs/007-reservation-cancel/spec.md)·[계획](../specs/007-reservation-cancel/plan.md)·[작업](../specs/007-reservation-cancel/tasks.md): 소유자 조회·원자적 취소·중복 취소·복구 장애 롤백·취소 후 생성 재요청을 구현·검증했다.
- [조사](../specs/007-reservation-cancel/research.md)·[값 모델](../specs/007-reservation-cancel/data-model.md)·[계약](../specs/007-reservation-cancel/contracts/reservation-cancel-contract.md)·[검증 안내](../specs/007-reservation-cancel/quickstart.md)·[명세 품질16개](../specs/007-reservation-cancel/checklists/requirements.md)를 연결한다. 체크리스트는 구현 완료 표시와 구분한다.
- 신규 OwnershipIT5·CancelIT13, 최종 일반 verify Unit137·일반IT138 실패/오류/skip0. local 프로필 curl11단계와 최종 DB 보존식을 확인했다. 상세 실행·고의 변경 탐지는 [증거](../docs/test-evidence.md), 구현 연결은 [예약](domain/reservation.md)·[보안](domain/security.md)·[대상](domain/resource.md)을 따른다. 실제 경쟁 반복·Redis·심화·부하는 후속이다.

## 7단계 경쟁 검증 명세 (2026-10-20)

- [7단계 명세](../specs/008-reservation-concurrency/spec.md): 한정 수량 100건 경쟁, 동일 키·다른 대상 경쟁, 중복 취소·생성/취소 경쟁과 강제 경로·각 10회 반복·결과 수집·최종 수량 보존 검증 요구사항을 정의했다. 명세 작성 단계이며 구현·경쟁 실행은 미완료다.
- [7단계 명세 품질 검토](../specs/008-reservation-concurrency/checklists/requirements.md): 문서 품질 16개 항목을 검토했다. 실행 증거가 아니며 다음 작업은 구현 계획 작성이다.

- [7단계 구현 계획](../specs/008-reservation-concurrency/plan.md): 11종 경쟁·강제 경로·실패 사례 각10회와 실제 HTTP/DB 관측·종료·정리 설계다. [조사](../specs/008-reservation-concurrency/research.md)·[데이터/검증 모델](../specs/008-reservation-concurrency/data-model.md)·[경쟁 계약](../specs/008-reservation-concurrency/contracts/concurrency-contract.md)·[검증 안내](../specs/008-reservation-concurrency/quickstart.md)를 작성했다. 설계 완료이며 신규 테스트 구현·실행은 후속이다. Wrapper 현재 파일9.8.0(사용자 변경)과 기존 실행9.7.1을 구분한다.

- [7단계 작업 목록](../specs/008-reservation-concurrency/tasks.md): 공통 기반과 네 사용자 이야기·전체 인수의38개 작업을 생성했다. US1 4개·US2 7개·US3 7개·US4 6개·공통14개이며 지원 테스트 파일 작성2개만 병행 후보다. 모든 체크는 미완료이고11종110회 실행은 후속이다.


## 7단계 경쟁 검증 (2026-10-20)

- [명세](../specs/008-reservation-concurrency/spec.md)·[계획](../specs/008-reservation-concurrency/plan.md)·[작업](../specs/008-reservation-concurrency/tasks.md)·[실행 안내](../specs/008-reservation-concurrency/quickstart.md): C01~C11 각10회 구현·지정 실행 검증을 완료했다. 과거 절의 경쟁 후속 표시는 당시 상태다.
- [경쟁 IT](../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationConcurrencyIT.java)·[유한 실행 지원](../backend/src/test/java/com/limitedbooking/backend/support/ConcurrencyTestSupport.java)·[지원 Unit](../backend/src/test/java/com/limitedbooking/backend/support/ConcurrencyTestSupportTest.java)·[다중 snapshot](../backend/src/test/java/com/limitedbooking/backend/support/DatabaseAssertions.java)·[DB 지원 IT](../backend/src/test/java/com/limitedbooking/backend/support/DatabaseSupportIT.java)를 연결한다.
- 지정 인수 Unit147·경쟁110회+부모setup차단fixture1 통과. [실행 증거](../docs/test-evidence.md)·[회차 기록](../docs/concurrency-runs.json)은 전체 결과·DBsnapshot·경로/정리·관측 한계를 보관한다. 후속은 Redis·심화·부하이며 업무 계약은 유지한다.


### 2026-10-21 최종 리뷰 보완 확인

최종 일반 verify Unit150·IT257(경쟁110회+지원fixture5 포함), 실패/오류/skip0. 종료/전송실패 차단·모든 오류/결과 보존·호출별 고유ID와 C07 경로기록을 보완했다. 제품·API·스키마는 유지한다. [최종 실행 증거](../docs/test-evidence.md)·[110회 기록](../docs/concurrency-runs.json)을 따른다. 이전 날짜의 실행 수는 당시 기록이다. Redis·심화·부하·외부CI는 후속이다.


## 8단계 US1 중간검토 인계 (2026-10-22)
[009명세](../specs/009-redis-rate-limit/spec.md)·[계획](../specs/009-redis-rate-limit/plan.md)·[작업](../specs/009-redis-rate-limit/tasks.md)·[검증 안내](../specs/009-redis-rate-limit/quickstart.md): T001~T003/T007~T013을 실제 수행했다. 일반Unit169·RedisIT5 통과이며 상세는 [증거](../docs/test-evidence.md)다. T004~T006 전체HTTP기반 인수·T014 이후는 미완료다. 필터 미연결로 HTTP에는 아직 제한이 적용되지 않는다. 최신지시로 US1 중간검토 대기, Qodana는 필수 미완료다. 이전 확인일의 “Redis 후속”은 당시 기록이다.
