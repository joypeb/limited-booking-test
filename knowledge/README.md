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
- [resource.md](domain/resource.md): 예약 대상의 책임·입력·수량 불변식·권한과 코드 포인트 기준 이름 길이. 저장 스키마·읽기/생성 SQL, `ResourceRules` Unit 52건, 4단계 `POST /api/resources`·`GET /api/resources/{id}` HTTP 통합 테스트(`ResourceApiIT` 8건)까지 구현·검증했으며 예약 생성/취소·수량 보존·실제 경쟁과 Redis 독립성을8단계까지 검증했다.
- [reservation.md](domain/reservation.md): 예약 생성·멱등 재현·트랜잭션·롤백·취소·경쟁의 구현·검증과 10단계 속성(독립 모델)·고의 변경 탐지, 11단계 DB 장애·응답 유실 복구·부하 정합성 연결.
- [security.md](domain/security.md): 로컬 고정 계정 Basic 인증·권한·고정 UUID Principal·인증 앞단 8KiB 본문 상한 필터·공통 `problem+json` 오류 처리의 설계 및 4단계 구현·검증 지식 (`SecurityIT` 6건). 예약 소유권 조회·취소(6단계)와 Redis 요청 제한(8단계)까지 구현·실행 검증했다. SecurityIT는 취소 입력 검증을 포함해 현재7건이다.
- [ratelimit.md](domain/ratelimit.md): Redis 고정 창·처리 순서·장애의 기준과 US1 limiter/Lua·Unit13/실제RedisIT5 구현·검증 지식. US1~US4와 최종 전체 인수를 완료했다. 실제HTTP37건·장애12건·기존HTTP/110회·종료 reset0을 확인했다.
- [frontend-console.md](domain/frontend-console.md): 2026-10-26 사용자 지시로 추가한 localhost 브라우저 콘솔(`frontend/`). 같은 출처 전달 계층(경로 허용 목록·Host/Origin 검사·원바이트·`WWW-Authenticate` 이름 변경), 메모리 전용 자격 증명, 백엔드 응답만 표시, 최근 식별자 기록과 Unit·Proxy·Component·실제 백엔드 E2E 검증.
- 예약 생성 API·쓰기 트랜잭션·성공 키·장애 롤백은5단계에서 구현했다. 조회·취소는6단계, 실제 경쟁·Redis 제한은7~8단계에서 구현·검증했다. 1단계 도구 프로브의 실제 HTTP·DB·Redis 최소 검증과 업무 기능 검증을 구분한다.
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


## 8단계 US1~US3 중간 인수 (2026-10-22)

[009명세](../specs/009-redis-rate-limit/spec.md)·[계획](../specs/009-redis-rate-limit/plan.md)·[작업](../specs/009-redis-rate-limit/tasks.md)·[검증 안내](../specs/009-redis-rate-limit/quickstart.md): T001~T028 구현·지정 인수를 완료했다. US1·US2 독립검토 후 US3의 좁은503분류·실제지연/중단/WRONGTYPE/OOM12건·DB롤백/카운트유지·기본TTL독립·종료/전송불확실차단을확인했다. 관련Unit37건·실제장애9+US2회귀37건과정리보강영향시험의명령별수는 [증거](../docs/test-evidence.md)를따른다. US4 기존HTTP/110회·최종build/verify/의존성은미완료이며 US3 오케스트레이터 중간검토를기다린다. Qodana T037은사용자2026-10-22지시로미완료후속이며이번완료·PR·병합게이트제외다. 이전확인일의후속표시는당시기록이다.


## 8단계 현재 인수 상태 (2026-10-22)

2026-10-22 8단계 구현·실행 인수 완료: `./gradlew build verify verifyContractDependencies` 종료0, 일반 Unit193·IT312, 실패/오류/skip0. IT에는 기본 Redis5·HTTP제한37·실제장애12·경쟁110회와 종료지원fixture5가 포함된다. T001~T036/T038 37개 완료, Qodana T037만 사용자 “나중에 할거야” 지시에 따른 미실행 후속이며 이번 완료·PR·병합 게이트에서 제외한다. 이 요약은 구현·실행 인수 시점의 기록이다. 이후 PR·독립 리뷰·병합 결과는 해당 PR과 최종 보고에서 확인한다. [실행 근거](../docs/test-evidence.md)·[009 작업](../specs/009-redis-rate-limit/tasks.md)을 따른다. 위 날짜별 과거 확인 상태는 당시 기록이다. 이번8단계는 최신 사용자 지시의 Spec Kit를 적용했다.


## 9단계 계약 검증 공통 선행

[010 명세](../specs/010-openapi-contract/spec.md)·[계획](../specs/010-openapi-contract/plan.md)·[작업](../specs/010-openapi-contract/tasks.md)을 기준으로 공통 선행 T001~T006만 진행했다. [ApiContractValidator](../backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidator.java)의 고정 명세/명시 operation·status/원문 경계와 [Unit](../backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidatorTest.java), [HTTP 지원](../backend/src/test/java/com/limitedbooking/backend/support/ApiContractHttpSupport.java)·[최소 실제 HTTP와 종료 시험](../backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java)을 추가했다. 예상위반·시나리오 값 모델은 불변 경계만 정의했으며 후속 판정 구현과 US1~US4는 미완료다. 실제 명령·RED/GREEN·수·한계는 [실행 증거](../docs/test-evidence.md)를 따른다. 새 업무 도메인·제품/API/SQL 변경은 없다.


### 9단계 US1 정상 계약 인수

T007~T011에서 검사기 Unit87건과 실제 업무HTTP6건·기존프로브6건을 지정 실행해 통과했다. `ApiContractValidator`는 스키마와 별도 semantic 단계로 UTC Z/마이크로초 값·이름 정규화/수량 관계·현재상태별 cancelledAt·필수응답헤더를 검사한다. 실제5operation·capacity0/1000·이름 경계/유니코드·원문/헤더case·DBsnapshot·기존종료보호를 확인했다. 선행 절의 US1 미완료는 당시 상태이며 현재 US2~US4/정확한무효요청·전체오류/변이 인수는 미완료다. [실행 증거](../docs/test-evidence.md)와 [작업](../specs/010-openapi-contract/tasks.md)을 따른다.


### 9단계 US2 일부 인수

T012~T017에서 불변의정확요청위반multiset·원문JSON/UTF8검사와항상실행하는오류응답검사를추가했다. 알수없는library key/context는예상목록과무관하게실패한다. 실제입력/권한/업무오류·5작업413·두생성415·기본429/판정PTTL까지검증했고관련Unit110(검사기97+기존13), Contract92(업무86+프로브6)가통과했다. GET8192본문의OAS body.unexpected/서버200은표현한계로정확히분리한다. publisher전달바이트는wire전송완료가아니며서버앱read관측과구별한다. T018이후DB/Redis503fixture·시퀀스/변이/전체인수는미완료다. [작업](../specs/010-openapi-contract/tasks.md)·[실행증거](../docs/test-evidence.md)를따른다.

### 9단계 US2 의존성 계약 (T018~T020)

[의존성 계약 시험](../backend/src/test/java/com/limitedbooking/backend/common/ApiContractDependencyIT.java)은 DB 중단과 Redis 중단을 별도 소유 컨테이너·컨텍스트로 격리한다. 실제 DB 중단의5API503·정상Redis, Redis 중단의예약생성/취소503·업무전체행무변경·대상생성/두조회정상을 고정 OpenAPI와 대조한다. 중단 DB의 사후snapshot은 조회하지 않고 기존 실제timeout 롤백회귀와 근거를 구분한다. 전체 오류 적용표·지정검증수·정리는 [실행 증거](../docs/test-evidence.md)의 T018~T020 절을 따른다. 제품/SQL/정책변경없음. T021 이후·전체9단계 인수는 미완료이며 Qodana는 사용자지정스킵/후속이다.


### 9단계 US3 생성 재현·현재 상태 인수

T021~T023은실제7회HTTP시퀀스의첫생성baseline과각시점의Replay/상태/필드값을`ScenarioExpectation`으로비교한다. 취소후생성은최초CONFIRMED4필드·Location을재현하고현재GET은CANCELLED5필드이며중복취소시각도같다. `DatabaseAssertions.reservationSnapshot`의한SELECT로수량·예약행과불변컬럼을함께관측한다. 관련Unit114(검사기101+종료지원13)·Contract93(업무87+프로브6)을지정실행해통과했으며자세한RED/GREEN/한계는[실행증거](../docs/test-evidence.md)를따른다. US4/T024이후와최종전체인수는미완료다.


## 9단계 계약 검증 최종 인수 (2026-10-23)

[010 작업](../specs/010-openapi-contract/tasks.md) T001~T042 구현·실행 인수를 완료했다(PR #12 리뷰 보완 포함). `./gradlew build verifyContract verifyContractDependencies` 종료0, Unit298·일반IT315(경쟁110회·종료 fixture5 포함)·Contract102(업무96+프로브6), 실패/오류/skip0이다(T042). T032 당시 Unit294·IT312·Contract96은 historical 값이다. 고정 [OpenAPI](../docs/openapi.yaml)를 기대값으로 [검사기](../backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidator.java)·[계약 HTTP 시험](../backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java)·[의존성 계약 시험](../backend/src/test/java/com/limitedbooking/backend/common/ApiContractDependencyIT.java)이 적용표 전체, 응답 사본 훼손44종, 실제 DTO 타입 변이 RED→원복 GREEN을 검증했다. 위 9단계 진행 절들의 미완료 표시는 당시 기록이다. 제품 변경은 PR #12 리뷰 보완의 `ResourceController` 엄격 UUID binder 1건이며 SQL·Gradle·OpenAPI·정책 변경은 없다. Qodana는 사용자 지시로 건너뛴 미실행 후속이다. 근거는 [실행 증거](../docs/test-evidence.md)와 [110회 기록](../docs/concurrency-runs.json)을 따른다.


## 12단계 CI·README·최종 검증 (2026-10-26)

- [013 명세](../specs/013-ci-readme-final/spec.md)·[계획](../specs/013-ci-readme-final/plan.md)·[검증 계약](../specs/013-ci-readme-final/contracts/verification-contract.md)·[작업](../specs/013-ci-readme-final/tasks.md).
- CI: [verify](../.github/workflows/verify.yml)(PR·push, `verifyContract`)와 [deep-tests](../.github/workflows/deep-tests.yml)(수동, `propertyTest mutationSmoke`). 파일 작성·로컬 동등 실행과 외부 run 결과를 구분하며 외부 결과는 [실행 증거](../docs/test-evidence.md) 12단계 절을 따른다.
- 정적 검사: `scripts/check-workflows.sh`·`scripts/check-readme.sh`·`scripts/check-traceability.sh`(RED→GREEN 판정 도구, 형식만 판정).
- 새 문서: [추적표](../docs/traceability.md)(규칙 → 대상 → 테스트 → 명령 → 증거, 검증하지 않은 범위), [시연 자료](../docs/demo.md)(5분 시연·작은 변경 연습, 사용자 수행 대기).
- 외부 CI는 verify(PR·develop·main)와 deep-tests(main 반영 후 수동 1회) 모두 success다. README Quick Start를 새 clone·깨끗한 셸에서 재현했고, 최종 기준점 `3ddf007`(백엔드는 `c4585ed`)에서 심화 전체·PIT·원래 제한 IT·k6 네 시나리오를 실행했다. 실행 중 `load/run.sh`의 dirty 표기 결함을 발견해 수정했다. 규칙·계약·SQL 변경은 없다.
