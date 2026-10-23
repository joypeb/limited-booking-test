# Tasks: 고정 OpenAPI와 실제 업무 API 계약 검증

**입력**: [명세](spec.md), [계획](plan.md), [조사](research.md), [검증 모델](data-model.md), [검사 계약](contracts/validation-contract.md), [실행 안내](quickstart.md).
**상태**: 2026-10-23 공통 선행·US1~US4 T001~T031 구현·지정 검증을 완료했다. T032 전체 게이트(`build verifyContract verifyContractDependencies` 종료0, Unit294·IT312·Contract96, 실패/오류/skip0)와 T033 기존 회귀 XML 대조를 완료했다. T034~T035 문서 동기화와 T036 자가검토를 완료했다(36/36). Qodana는 사용자 지시로 건너뛴 미실행 후속이며 게이트에서 제외한다. 메인 검토·push·PR은 남아 있다. 실행 근거는 docs/test-evidence.md를 따른다. 승인된 spec/plan은 수정하지 않는다.
**절차**: 최신 사용자 Spec Kit 지시를 적용한다. 작업 생성 당시 구현·위임·브랜치 생성·커밋·외부 작업은 하지 않았다. 이후 메인 지시로 T001~T031까지 구현·검증하며 목적 단위 로컬 커밋은 허용한다. T032는 메인이 실행했고 T033~T036은 메인 지시로 교체 담당이 순차 수행한다(제품·SQL·Gradle·Wrapper 무변경, 전체 테스트 재실행 없음). push·PR은 범위 밖이다.

## 형식과 공통 실행 계약

`- [ ] T번호 [P 선택] [US번호] 설명과 정확한 경로`를 사용한다. 경로는 저장소 루트 기준이다. 편의를 위해 아래 Java 작업은 실제 전체 경로를 적는다.

- 기능별로 테스트 작성 → 컴파일 가능한 최소 골격에서 의도한 assertion RED → 최소 구현 → 동일 시험 GREEN → 관련 회귀 순서다. 컴파일/컨테이너 기동 오류를 기능 RED로 세지 않는다. 각 RED/GREEN의 명령·종료 코드·단계/key/location을 `docs/test-evidence.md`에 단일 담당자가 순차 기록한다.
- 승인 정책에 반하는 기존 코드/명세 문제가 나오면 완화하지 않고 메인에 이유·영향을 보고한다. spec/plan 수정은 별도 판단 전 진행하지 않는다.
- Java25·Boot4.1.1·Wrapper9.7.1·기존 SQL/DTO/기본20회/60000ms·Redis200ms·DB 설정을 유지한다. 새 migration·업무 API·런타임 계약 라이브러리를 추가하지 않는다.
- `[P]`는 선행 완료 뒤 독립 파일 **작성만** 병행 가능한 후보다. 병렬 수정 시 각각 별도 worktree/branch가 필수다. 공유 명세·Gradle·실행·통합·증거·문서·커밋은 단일 책임자가 순차 처리한다. 현재 실제 위임은 금지다.
- Qodana는 최신 사용자 “추후 직접/이번 스킵” 지시에 따른 미실행 후속이며 이번 완료·PR·병합 게이트에서 제외한다. Property/PIT 확장·부하·외부 CI·배포도 이번 범위 밖이다.

## Phase 1: 준비 — 승인 근거와 현재 기반

- [X] T001 `AGENTS.md`, `knowledge/README.md`, `knowledge/rules/*.md` 전체와 `knowledge/domain/resource.md`, `reservation.md`, `security.md`, `ratelimit.md`, `한정 수량 예약 백엔드 로컬 개발 계획.md` 2~5절·7절9단계 및 승인된 `specs/010-openapi-contract/` 산출물을 다시 대조하고 저장소 루트·HEAD·브랜치·사용자 diff·Wrapper9.7.1을 확인해 후속 실행 기준을 `docs/test-evidence.md`에 기록한다.
- [X] T002 `backend/build.gradle`, `backend/src/test/java/com/limitedbooking/backend/support/ToolchainContractValidator.java`, `backend/src/test/java/com/limitedbooking/backend/common/ToolchainContractIT.java`, 기존 ContainerTestSupport/HttpClientTestSupport/TestData/DatabaseAssertions/RateLimitTestSupport/ConcurrencyTestSupport를 조사해 business.openapi.yaml 복사·contract 태그·발견/결과 게이트·프로브 재사용 경계와 기존 장애/경쟁 회귀 목록을 `docs/test-evidence.md`에 고정한다. 기본 Gradle 수정은 하지 않는다.

## Phase 2: 공통 선행 — 고정 명세와 교환 경계

**차단 조건**: T001~T006 완료 전 사용자 이야기 구현을 시작하지 않는다.

- [X] T003 `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidatorTest.java`에 고정 명세 파싱/참조/버전/5 operation·명시 응답 검사와 정상 교환의 요청·응답 전달 테스트를 작성한다. 제공 void assertValid(method,path,Map<String,String>,String,int,Map<String,String>,String) 골격에서 의도한 미검증 assertion RED를 확인한다. 프로브만 통과한 것을 신규 업무 탐지력으로 세지 않는다.
- [X] T004 T003 뒤 `docs/openapi.yaml` 단일 원본을 계획서와 적용표에 맞춰 확정하고 `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidator.java`의 parser/ref 로딩·명시 operation/status 선택·원문 SimpleRequest/Response 전달을 최소 구현해 동일 시험 GREEN을 확인한다. 자동 생성/별도 YAML 사본·입력 정규화·default에 의한 미명시 상태 허용을 금지한다. schema 표현 한계는 raw/의미 검사로 분리한다.
- [X] T005 `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidator.java`에 HttpExchange/ExpectedRequestViolations/ValidationFailure/ScenarioExpectation의 테스트 전용 경계를 정하고 `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidatorTest.java`에 body null/빈문자열·실제 raw path·헤더 대소문자·중복 singleton·원문 보존의 RED→최소 구현→GREEN을 추가한다. HttpExchange는 실제 원문, ExpectedRequestViolations는 불변 정확한 library key+location 및 raw rule+location multiset, ValidationFailure는 단계/key/location, ScenarioExpectation은 사전 기대값/독립 관측이며 결과에서 정답을 만들지 않는다. 지정 public 인터페이스는 바꾸지 않는다.
- [X] T006 `backend/src/test/java/com/limitedbooking/backend/support/ApiContractHttpSupport.java`의 원문 capture·중복 singleton의 Map 변환 전 거부·실제 Authorization·동적 소유 DB/Redis·사전 커밋·소유키 초기화·최종 단일 DBsnapshot·유한 종료를 위한 최소 지원을 작성한다. `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`의 최소 실제 HTTP 시험에서 원문 누락/종료 보호 결함 RED→최소 보완 GREEN을 확인한다. connect3초/request9초/raw socket10초/client await10초/container 종료10초를 유지하고 서버 in-flight도 유한 확인한다. 최초 StopState·원인/suppressed 오류를 보존하고 미종료/전송불확실 뒤 다음 reset·준비·제출0을 검증한다. 기존 package-private 지원 공개화·개발 DB·flushAll·클래스 병렬 실행을 금지한다.

## Phase 3: US1 — 문서대로 정상 API 사용 (P1, MVP)

**목표**: 정상 다섯 작업의 실제 요청·응답과 스키마 밖 의미를 독립 계약으로 검증한다.
**독립 인수**: 검사기 Unit 및 ApiContractIT 정상 사례를 지정 실행하여 5작업·정규화·수량0·필드/헤더/UTC가 모두 적합하고 정상 요청 위반0이다.

- [X] T007 [P] [US1] `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidatorTest.java`에 UUID/타입/필수·추가필드/상태·시각·필수 헤더·미디어 타입·default 거부의 정상/단일 결함 fixture를 작성한다. T006 뒤 T008과 독립 파일 작성만 병행할 수 있으며 실행은 순차다. ResourceView는 “id UUID, name 정규화 문자열(코드포인트1~100), capacity/available 정수0~1000”, “available≤capacity”; CreatedReservationView는 “네 필드 필수·추가 필드 거부·cancelledAt 없음”, “status=CONFIRMED”; ReservationView는 “다섯 필드 필수”, “CONFIRMED이면 cancelledAt 명시 null, CANCELLED이면 UTC 값”을 그대로 기대한다.
- [X] T008 [P] [US1] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 `resourceSuccessMatchesContract`를 포함한 5작업 정상 HTTP 시험을 작성한다. ADMIN 대상 생성201/USER 조회200·예약201/소유자조회200/취소200, capacity0/1000, strip 전100초과·strip후100/😀/NBSP 허용을 포함한다. 실제 원문 전달·헤더 case-insensitive·JSON 필드순서 동등성을 확인하고 T007과 독립 파일 작성만 병행한다.
- [X] T009 [US1] T007~T008 작성 뒤 `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidatorTest.java`와 `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`를 순차 지정 실행하여 누락된 의미 검사의 의도한 RED를 `docs/test-evidence.md`에 기록한다. DB 기동/컴파일 실패 대신 관련 assertion 실패를 확인한다.
- [X] T010 [US1] `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidator.java`에 스키마와 별도 의미 검사를 최소 구현한다. 시간은 “offset=UTC”, “Z 표현”, “나노초 값은 1000으로 나누어 떨어져야 한다”; 소수부6자리 강제는 금지한다. Location은 body id와 단건경로 일치, Replay는 소문자 false/true, 401 Basic WWW-Authenticate, 429 양의 정수 Retry-After를 필수로 검사한다. 추가 전송헤더는 허용하고 중복 singleton은 거부한다. 성공 application/json·오류 application/problem+json은 charset 파라미터를 구별하며 허용한다. T009 동일 시험 GREEN을 확인한다.
- [X] T011 [US1] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java` 정상 부분과 검사기 Unit을 지정 실행해 실제 다섯 교환·DB 수량식·필드/헤더/UTC·요청 위반0·정규화 오거부0을 확인하고 `docs/test-evidence.md`에 MVP 결과와 미검증 오류/변이 범위를 기록한다.

## Phase 4: US2 — 실패 결과와 오류 응답 신뢰 (P1)

**목표**: 적용표 전체 오류·정확한 무효 요청 위반·오류 응답 전체를 검증한다.
**독립 인수**: 각 적용표 셀 최소1회, 두409 code 및 DB5/Redis2 작업503, 429 기본정책·판정PTTL, 413 전역 우선순위를 실제 HTTP로 관측한다.

- [X] T012 [US2] `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidatorTest.java`에 무효 요청의 예상 library key+location/raw rule+location multiset 누락·추가·중복·무관 위반, 정상 요청 위반, 무효 요청+잘못된 오류응답 조합을 작성해 의도한 RED를 확인한다. 실제 고정 라이브러리 보고서의 key/context/중첩 위치를 관측해 fixture를 고정하고 메시지 contains/포괄 validation.request.* 허용은 금지한다.
- [X] T013 [US2] `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidator.java`에 불변 expected-invalid 파생 인스턴스와 요청/응답 보고서 분리·raw strict JSON·전 단계 오류 합산을 최소 구현해 T012 GREEN을 확인한다. 요청 실패에도 response-schema/semantic 전체를 실행한다. 정상/인증/소유권/품절/충돌/장애는 유효입력·요청위반0이다. parser/runtime 예외·알 수 없는 key/context를 빈 보고서로 바꾸지 않고 실패시키며 ThreadLocal/공유 mutable 설정/응답 오류 허용 목록을 금지한다. assertion에는 Authorization/비밀번호를 출력하지 않는다.
- [X] T014 [US2] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 대상 name 공백/101·capacity -1/1001/문자열/소수(1.0)/누락/null·미정의/중복키·malformed JSON과 예약 resourceId 누락/잘못된UUID·키 누락/잘못된문자/길이·quantity/userId·중복키/잘못된JSON, 두GET/cancel 잘못된UUID·cancel 공백/JSON본문400을 작성한다. 각 사례의 정확한 schema/raw 예상 위반과 400 INVALID_REQUEST·유효한 전체 오류응답을 선언한다. raw 검사 누락의 RED→최소 검사기 보완 GREEN을 순차 확인하며 제품 규칙을 기대값으로 호출하지 않는다.
- [X] T015 [US2] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 각5작업 미인증/틀린비밀번호401·대상생성 USER403·없는대상/예약404·타인 USER_B/ADMIN 조회/취소404·품절409 SOLD_OUT·동일키다른대상409 IDEMPOTENCY_CONFLICT를 실제 인증/필터로 추가한다. 유효 입력의 요청위반0, Problem “type/title/status/detail/instance/code 필수”, HTTP/status/code·실제 instance path 일치·유효 URI type(about:blank 포함)·시험비밀/SQL/스택 비노출을 확인한다. 예시 문구를 바이트 고정하지 않는다. 검사 누락 RED→최소 검사기 보완 GREEN을 확인한다.
- [X] T016 [US2] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 두 생성 text/plain415와 다섯작업8193 UTF-8바이트413을 Content-Length/길이미지정 chunked 각각으로 추가한다. 생성8192 유효JSON 성공·8193거부, GET/cancel8192 후속검증 도달(cancel400), 멀티바이트·미인증/틀린인증+초과413·제한소진+초과413을 확인한다. raw framing과 payload 원문을 구분하고 조기종료 시 전송예정/실제수신 관측 차이를 기록한다. 각 무효 요청의 정확한 schema/raw 위반과 응답 전체를 검사하며 보완 누락 RED→최소 지원/검사기 GREEN을 확인한다.
- [X] T017 [US2] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 예약 생성/취소 각각 기본20/60000ms 소진429 RATE_LIMITED·DB무변경을 추가한다. test-only 독립 판정PTTL로 “max(1,ceil(ms/1000))” Retry-After와 비교하고 응답 뒤 PTTL 재조회로 정답을 만들지 않는다. `backend/src/test/java/com/limitedbooking/backend/support/ApiContractHttpSupport.java`에 필요한 최소 관측만 재사용/보완하며 검사 누락 RED→GREEN을 확인한다.
- [X] T018 [US2] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractDependencyIT.java`에 contract 태그·별도 소유 컨테이너/컨텍스트의 실제 DB503 fixture를 작성한다. 정상 준비 뒤 소유 DB 중단·기존 풀 연결 정리로 실제 JDBC 실패를 만들고 Redis 정상·limiter 통과 상태에서 다섯작업503 DEPENDENCY_UNAVAILABLE 전체 계약을 검사한다. mock 서비스 예외로 대체하지 않는다. 컴파일 가능한 시험/지원의 의도한 관측·검사 누락 RED 후 최소 보완 GREEN을 확인한다. 중단 DB에서 snapshot 조회를 강제하지 않고 별도 정상연결/기존 timeout 회귀로 부분반영 없음을 확인한다.
- [X] T019 [US2] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractDependencyIT.java`에 별도 소유 Redis 중단·정상DB fixture를 추가해 정상인증/입력의 생성·취소503과 거부 전후 business snapshot 동일을 확인한다. 같은 fixture에서 대상 생성·두GET은 정상이어야 한다. 라이프사이클/검사 누락 RED→최소 보완 GREEN을 확인하고 기존 RedisFailureIT 지연/중단/첫·기존연결/WRONGTYPE/OOM·DB실패카운터유지 회귀를 보존한다. timeout 뒤 queued Lua count0을 강제하거나 DB실패 카운터를 보상하지 않는다.
- [X] T020 [US2] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`와 `ApiContractDependencyIT.java`를 순차 지정 실행하고 아래 적용표 각 셀을 실제 사례ID/메서드/raw path/status/code/정확한 위반/필수헤더/종료 결과로 `docs/test-evidence.md`에 채운다. 원인 분리된 유효입력 선행거부와 의도적 복합무효 요청을 구별하고 예정 셀 누락·500/default 통과·응답검사 생략0을 확인한다.

## Phase 5: US3 — 최초 생성 결과와 현재 상태 구분 (P1)

**목표**: 재전송·중복취소가 최초값과 현재값을 혼동하거나 수량을 추가 변경하지 않음을 확인한다.
**독립 인수**: 실제 연결 시퀀스와 최종 단일 DBsnapshot으로 최초 생성값/Location 동일·추가예약0·복구1회다.

- [X] T021 [US3] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 생성201(false)→같은키201(true)→GET CONFIRMED→취소200→중복취소200→같은생성201(true)→GET CANCELLED 시퀀스를 작성한다. 최초 id/resourceId/CONFIRMED/createdAt·Location을 사전 baseline으로 보존하고 필드값을 비교한다. 생성 cancelledAt 없음·현재5필드/명시null 또는 UTC·중복취소 동일시각을 검사하고 시퀀스 검사 누락의 의도한 RED를 확인한다.
- [X] T022 [US3] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`의 시나리오 의미 비교를 최소 구현해 T021 GREEN을 확인한다. 최초 true/재요청 false처럼 개별 유효 boolean도 기대 시점이 다르면 거부한다. 재현 JSON 공백/순서의 바이트동일성은 요구하지 않는다. 모든 요청 종료 뒤 `DatabaseAssertions` 단일 snapshot으로 예약행1·추가차감0·복구1·available+CONFIRMED수=capacity·불변 user/key/resourceId/createdAt을 확인한다.
- [X] T023 [US3] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java` 시퀀스 지정 실행 결과·원문/최초값 비교·최종 DB상태·소유키/종료와 정상/오류 기존 사례의 영향 회귀를 `docs/test-evidence.md`에 기록한다. SC-004와 FR-008을 실행 증거로 연결한다.

## Phase 6: US4 — 계약 이탈 탐지력 (P2)

**목표**: 응답 사본 훼손과 실제 DTO 타입 변경의 별도 탐지 증거를 확보한다.
**독립 인수**: 정상 대조군100%통과·각 단일훼손100%거부·실제 DTO compile성공→HTTP schema RED→정확원복→동일 GREEN이다.

- [X] T024 [US4] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 정상 실제 교환을 먼저 통과시키고 매번 사본 한 요소만 훼손하는 시험을 작성한다. 필수id 제거/UUID/숫자→문자열, malformed·비UTC·마이크로초초과 시각, 미정의status/추가필드/생성cancelledAt/현재cancelledAt누락·상태관계 위반을 독립 사례로 둔다. 검사 구멍의 의도한 RED를 확인하고 `backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidator.java`를 최소 보완해 각 AssertionError의 해당 단계/key/location 거부와 정상대조 GREEN을 확인한다.
- [X] T025 [US4] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 각각 필수 Location/Replay/WWW-Authenticate/Retry-After 제거, 미디어타입/Location다른id/Replay오류/401비Basic/Retry-After0·소수·문자열·중복singleton, HTTP/status/code불일치/instance다른path/미명시500·default를 독립 훼손한다. 정확한 훼손원인 RED→최소 검사기 보완→정상대조 및 거부 GREEN을 확인한다.
- [X] T026 [US4] `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`에 최초true/재요청false, 양의정수지만 독립 판정PTTL과 다른 Retry-After, 재요청 최초생성값 변경의 사본 훼손을 추가한다. `ScenarioExpectation`의 시퀀스 의미 누락 RED→최소 비교 GREEN을 확인한다. T024~T026의 종류별 실제 탐지 결과를 `docs/test-evidence.md`에 기록하며 응답사본 실험을 실제 DTO 변이로 세지 않는다.
- [X] T027 [US4] 실제 DTO 실험 전에 `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`의 `resourceSuccessMatchesContract`를 지정 실행해 정상 baseline GREEN을 확보한다. `backend/src/main/java/com/limitedbooking/backend/resource/ResourceView.java`의 정확한 내용·해시·기존diff를 임시경로에 보관하고 해당 파일의 외부 수정 부재·단독실험·finally 원복 절차를 `docs/test-evidence.md`에 기록한다.
- [X] T028 [US4] `backend/src/main/java/com/limitedbooking/backend/resource/ResourceView.java` available record component를 int→String으로 임시변이하고 기존 (UUID,String,int,int) 호출은 String.valueOf(available)로 위임하는 보조생성자로 보존한다. controller/SQL/schema/시험기대값을 바꾸지 않고 backend에서 `./gradlew compileJava compileTestJava` 종료0을 확인한다. 컴파일 실패는 탐지로 인정하지 않으며 최소 보완·확인 결과를 `docs/test-evidence.md`에 기록한다.
- [X] T029 [US4] T028 컴파일 성공 뒤 `backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java`의 `./gradlew contractTest --tests '*ApiContractIT.resourceSuccessMatchesContract'`를 실행한다. 실제 앱201/200 available JSON 문자열과 schema integer 위반의 해당 key/location·종료비0을 `docs/test-evidence.md`에 기록한다. 일반 assertion/기동/컴파일 실패는 HTTP schema RED가 아니다.
- [X] T030 [US4] `backend/src/main/java/com/limitedbooking/backend/resource/ResourceView.java`를 보관한 정확한 내용으로 임시변경만 원복하고 기존diff/해시 일치를 확인한다. checkout/reset으로 사용자변경을 덮어쓰지 않는다. T029와 **동일 명령** GREEN·최종 DTO 임시diff0을 `docs/test-evidence.md`에 기록한다. 실패/중단에도 원복하고 미원복이면 명시·최종게이트 차단한다.
- [X] T031 [US4] `backend/build.gradle`의 기존 contract 발견·verifyContract→verify+contractTest·listener/XML·0건/skip거부·verifyContractDependencies 연결을 신규 업무시험에 대해 확인한다. 기본 변경 없이 업무발견>0·프로브와 업무 수 구분을 검증하고 실제 결함이 있을 때만 RED 회귀→최소 Gradle 수정→GREEN을 수행해 `docs/test-evidence.md`에 남긴다. 기존 게이트의 고의실패를 수정 없이 반복하지 않는다.

## Phase 7: 전체 인수와 문서 동기화

- [X] T032 T001~T031 완료·고의변이 원복 뒤 `backend/build.gradle` 기준 backend에서 `./gradlew build verifyContract verifyContractDependencies`를 단일 담당자가 순차 실행한다. 신규 Unit/업무Contract·기존프로브 수를 구분하고 각 Test 태스크 현재 listener/XML 일치·실행>0·실패/오류/skip0을 확인한다. 과거193/312나 이전XML을 현재 증거로 복사하지 않는다. 미해결 의존성·json-patch/jackson-coreutils 재유입·테스트Jackson2/런타임Jackson3 경계를 기존 게이트로 확인한다.
- [X] T033 T032의 실제 `backend/build/test-results/`와 실행로그에서 기존 `backend/src/test/java/com/limitedbooking/backend/reservation/ReservationConcurrencyIT.java` C01~C11 각10회(110회)·종료지원fixture, ReservationRollbackIT/ReservationCancelIT, RedisFailureIT 계열·RateLimitApiIT·권한/입력 HTTP 회귀의 발견/종료/실패0을 확인해 `docs/test-evidence.md`와 필요한 `docs/concurrency-runs.json` 실행기록을 실제 결과로 갱신한다. 새 경쟁시험 복제·임의제한하향·실패반복/skip은 금지하고 실패 시 원인/최소수정 후 영향검증한다.
- [X] T034 `docs/test-evidence.md`에 모든 실제 명령·종료코드·태스크/클래스별 수·환경·최소재현·정확위반·적용표·단일훼손별 탐지·DTO compile/HTTP RED/원복GREEN·DB/Redis503·PTTL·최종수량/종료·미검증을 단일작성자로 정리한다. `docs/versions.md`, `docs/decisions.md`에 실제 해석버전/선택을 반영하고 `docs/performance.md`는 비용을 측정했을 때만 실제조건/한계와 기록한다. 미측정은 미측정으로 남긴다.
- [X] T035 `README.md`, `knowledge/README.md`, `knowledge/rules/api-and-security.md`, `knowledge/rules/development-and-testing.md`, `knowledge/domain/resource.md`, `reservation.md`, `security.md`, `ratelimit.md`, `한정 수량 예약 백엔드 로컬 개발 계획.md` 7절9단계를 순차 동기화한다. 구현/실행/미검증을 구분해 실제 파일·증거만 연결하고 실제 인수 뒤에만 완료표시한다. `docs/openapi.yaml`의 과거 미실행 설명도 사실에 맞추며 정책 변경이 필요하면 먼저 메인보고한다.
- [X] T036 `specs/010-openapi-contract/tasks.md` 추적표·적용표와 최종diff/링크/실행증거를 대조해 FR-001~012·US1~4·SC-001~005 누락0, 임시DTO변경0·실패/skip0을 자가검토한다. 변경이유·실제결과·미완료/미검증·핵심코드/테스트·다음한작업을 보고하고 대기한다. Qodana는 미실행후속/게이트제외로 명시하며 커밋·push·PR은 이번 tasks 생성 담당의 범위 밖이며 메인 게이트 후 기존 사용자 승인 범위에서 진행한다.

## Phase 8: PR #12 독립 리뷰 보완 (2026-10-23 메인 지시)

제품 변경은 T037만 허용한다. OpenAPI·계획서 정책은 바꾸지 않는다.

- [X] T037 [P2-1] `backend/src/main/java/com/limitedbooking/backend/resource/ResourceController.java` 경로 UUID를 예약 Controller와 같은 정규식 binder로 엄격히 변환한다. `ResourceApiIT`·`ApiContractIT.invalidInputs`의 `1-1-1-1-1`·공백 사례 RED(200/404)→GREEN(400)을 확인한다.
- [X] T038 [P2-2] `ApiContractIT`의 400·401·403·404·409·413·415·429 사례 전후에 resources·reservations 전체 행·컬럼 단일 SQL snapshot이 같음을 확인한다.
- [ ] T039 [P3] `ApiContractDependencyIT`의 `httpClosed`를 실제 값으로 단언하고, cleanup listener가 컨텍스트 종료 뒤 실행되는 실제 순서를 검증·문서화한다.
- [X] T040 [P3] `ApiContractValidator` semantic에 Problem title·detail 비어 있지 않음 검사를 추가한다(Unit RED→GREEN).
- [ ] T041 [P3] `quickstart.md`·`research.md`의 계획/예정 표시를 현재 상태 또는 당시 기록으로 명시한다.
- [ ] T042 `./gradlew build verifyContract verifyContractDependencies` 1회 실행, XML 수 대조, 증거 기록, 목적별 커밋·push.

## 전체 계약 적용표와 실행 추적

아래 모든 셀은 최소1회 **실제 HTTP**가 필요하다. T020/T034에서 셀별 사례ID와 관측을 기록한다. 401은 미인증/틀린비밀번호, 예약404는 없음/타인USER_B/ADMIN을 구분한다. 전역413은 GET도 포함한다.

| 작업 | 성공 | 오류와 담당 작업 |
| --- | --- | --- |
| POST /api/resources | 201·Location T008~011 | 400 T014;401/403 T015;413 T016;415 T016;503 DB T018 |
| GET /api/resources/{id} | 200 T008~011 | 400 T014;401/404 T015;413 T016;503 DB T018 |
| POST /api/reservations | 201·Location·Replay T008~011/T021~023 | 400 T014;401/404/409 SOLD_OUT/409 IDEMPOTENCY_CONFLICT T015;413/415 T016;429 T017;503 DB T018;503 Redis T019 |
| GET /api/reservations/{id} | 200 T008~011/T021~023 | 400 T014;401/404 T015;413 T016;503 DB T018 |
| POST /api/reservations/{id}/cancel | 200 T008~011/T021~023 | 400 T014;401/404 T015;413 T016;429 T017;503 DB T018;503 Redis T019 |

400 INVALID_REQUEST /401 UNAUTHENTICATED /403 FORBIDDEN /404 NOT_FOUND /409 위 두 code /413 PAYLOAD_TOO_LARGE /415 UNSUPPORTED_MEDIA_TYPE /429 RATE_LIMITED /503 DEPENDENCY_UNAVAILABLE. 모든 오류는 응답 전체 검증이며 모든 미명시상태·500은 default와 무관하게 거부한다.

## FR·US·SC 추적표

| 요구사항 | 구현/검증 작업 | 사용자 이야기·인수 |
| --- | --- | --- |
| FR-001 고정독립명세 | T001~004,T020,T035 | US1~4;SC-001/005 |
| FR-002 실제원문전달 | T003~006,T008~011 | US1;SC-001/002 |
| FR-003 정확무효위반·응답항상검증 | T005,T012~016,T020 | US2;SC-002 |
| FR-004 전체적용표·409/503구분 | T008~011,T014~020 | US1/2;SC-001 |
| FR-005 UUID/타입/UTC/필드/관계·정규화 | T007~016,T021~026 | US1/2/3/4;SC-001/002/003/004 |
| FR-006 미디어·필수헤더·의미 | T005,T007~011,T015~017,T021~026 | US1/2/3/4;SC-001/003/004 |
| FR-007 Problem6필드·민감정보·default | T010,T012~020,T025 | US2/4;SC-001/002/003 |
| FR-008 재전송·현재상태·수량 | T021~023,T026 | US3;SC-004 |
| FR-009 독립사본훼손 | T024~026 | US4;SC-003 |
| FR-010 실제DTO컴파일/HTTP RED/정확원복GREEN | T027~030 | US4;SC-003/005 |
| FR-011 격리의존성/인증/정책/종료 | T001~002,T006,T008,T014~023,T032~033 | US1~4;SC-001/005 |
| FR-012 발견/수/버전/증거 | T009~011,T020,T023,T026~036 | US4·전체;SC-005 |

SC-001은 T020의 전체적용표, SC-002는 T011~016/T020의 정상/무효/잘못된오류응답, SC-003은 T024~030의 모든종류100%·대조군, SC-004는 T021~023, SC-005는 T030~036을 최종 대조한다. US별 인수 시나리오 번호도 각각 US1(1~3) T007~011, US2(1~6) T012~020, US3(1~2) T021~023, US4(1~3) T024~031/T032~036에 대응한다.

## 의존성과 실행 순서

```text
T001 → T002 → T003 → T004 → T005 → T006
  → (T007 작성 ∥ T008 작성) → T009 → T010 → T011 [US1 MVP]
  → T012 → T013 → T014 → T015 → T016 → T017 → T018 → T019 → T020 [US2]
  → T021 → T022 → T023 [US3]
  → T024 → T025 → T026 → T027 → T028 → T029 → T030 → T031 [US4]
  → T032 → T033 → T034 → T035 → T036
```

US2/3는 공유 검사기/HTTP 파일 안정화를 위해 US1 뒤 순차 진행한다. US3의 정상 시퀀스는 오류 fixture와 별도로 지정 실행할 수 있다. US4는 US1~3의 정상 baseline과 시나리오 기대값을 필요로 한다. 각 이야기의 독립 인수는 해당 지정시험으로 가능하며 전체 종료 조건을 대체하지 않는다.

## 병행 예와 역할별 분배안 — 실제 위임 아님

| 역할 | 소유 작업/파일 | 통합 조건 |
| --- | --- | --- |
| 통합·계약 담당 | T001~006, docs/openapi.yaml·backend/build.gradle·실행/증거·T027~036 | 공유파일/실행/기록은 단독 순차, 실제DTO실험 중 모든 다른수정 중단 |
| 검사기 담당 | T007 작성, ApiContractValidatorTest.java; 이후 T009~010/T012~013 검사기 작업 | T006 경계 확정 후 별도worktree/branch에서 T008 작성과만 병행 가능. 공유실행은 통합담당에게 반환 |
| 업무 HTTP 담당 | T008 작성, ApiContractIT.java; 이후 T014~017/T021~026 | T007과 다른파일 작성만 병행. 이후 공유파일/지원보완은 순차 |
| 의존성 fixture 담당 | T018~019 ApiContractDependencyIT.java | T017 후 순차 배정. 정상/장애컨텍스트 격리, 실제 실행은 통합담당 단독 |

US1 병행 예: T006 완료 후 T007과 T008의 파일 작성만 별도worktree/branch에서 병행하고 모두 회수한 뒤 T009 실행. US2 병행 예: 없음(검사기·HTTP지원·공유 IT 및 파괴적fixture의 순차 의존성). US3 병행 예: 없음(동일 IT의 연결 시퀀스). US4 병행 예: 없음(공유baseline/IT·실제DTO 단독 변이/정확원복). 독립파일이라는 이유만으로 선행 미완료 작업에 [P]를 붙이지 않는다.

## 구현 전략과 이번 생성 자가검토

준비/공통선행 → US1 MVP 독립 검증 → US2 오류전체 → US3 재현 → US4 탐지력 → 전체게이트/문서 순으로 한 검증대상씩 진행한다. MVP 완료는 9단계 전체 완료가 아니다. 실패 시 최소 재현/원인/수정/관련재검증을 따르고 새변경·실패가 없으면 무거운 게이트를 반복하지 않는다.

생성 기준: 총36작업(공통11, US1 5, US2 9, US3 3, US4 8), 전부 미완료. 형식·연속ID·이야기라벨·경로·의존성·FR12/US4/SC5·적용표 전체·사본훼손과 별도DTO실험을 대조한다. 신규 파일 링크는 구현 전이므로 기존 근거문서 링크와 구별해 코드경로로만 표기한다. 공식 check-prerequisites.sh --json/setup-tasks.sh --json은 생성 시 정상종료했고 .specify/extensions.yml은 없어 전후훅이 없다. 이번에는 Gradle/Docker/HTTP/변이/외부CI를 실행하지 않았다. 다음 한 작업은 메인 검토이며 그 전 구현·위임하지 않는다.
