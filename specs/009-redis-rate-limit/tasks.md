# Tasks: Redis 원자적 예약 요청 제한

**Input**: `specs/009-redis-rate-limit/`의 승인된 spec.md·plan.md·research.md·data-model.md·contracts/rate-limit-contract.md·quickstart.md.
**Prerequisites**: AGENTS.md, knowledge/README.md·rules 전체·ratelimit/security/reservation/resource 도메인, 기준 계획서 2~5절·7절 8단계, .specify/memory/constitution.md.
**Organization**: 준비 → 공통 기반 → US1(P1) → US2(P1) → US3(P1) → US4(P2) → 전체 인수. 체크는 실제 구현·실행 근거에 따라 갱신한다. 2026-10-22 US4 인수 후 최종 게이트 전 상태는 아래와 같다.

## 형식·경로·범위

`- [ ] T번호 [US번호] 설명·파일 경로`를 사용한다. 공통 작업에는 이야기 라벨을 붙이지 않는다. 아래 `main/`은 `backend/src/main/java/com/limitedbooking/backend/`, `test/`는 `backend/src/test/java/com/limitedbooking/backend/`의 정확한 경로 별칭이다. 리소스·문서·Gradle 경로는 루트 기준으로 적는다. 신규 파일은 예정이며 기존 코드 재사용이 우선이다.

이 목록은 생성 후 `speckit-implement`로 실행 중이다. US1(100d392)은 오케스트레이터 독립검토를 통과했다. T001~T034의 구현·지정 인수를 완료했고 US3(2adadb2)까지 오케스트레이터 독립검토를 통과했다. US2(84b3ef7)도 독립검토를 통과했다. T004~T006의 실제 HTTP 관측은 `RateLimitHttpTestSupport`로 구현해 US3에서 재사용할 기반을 마련했다. `RedisFailureIT`와 별도 기존연결/첫연결 중단 컨텍스트의 실제 장애12건·분류/종료지원 Unit을 구현·인수했다. 실제HTTP 공통 기반은 세 컨텍스트가 재사용한다. US4 기존 HTTP fixture 전환·110회·종료 reset0 연결도 지정 인수했다. 최종 T035/T036/T038은 후속이다.

후속 오케스트레이터가 PR/최종 리뷰/merge를 담당한다. 구현 담당은 push·PR·병합을 수행하지 않는다. Qodana T037은 사용자2026-10-22 “나중에 할거야” 지시로 미완료 후속이며 이번 완료·PR·병합 게이트에서 제외한다. 7단계 재구현, 업무 SQL/스키마·잠금 변경, 9단계 전체 계약 검증·속성·변이·부하·배포는 범위 밖이다.

기능 작업은 테스트 작성 → 컴파일 가능한 최소 경계 → 의도한 assertion RED → 최소 구현 → GREEN → 정리 순서다. 컴파일 실패·의존성 접속 실패를 기능 RED로 인정하지 않는다. 환경 기반은 최소 실행으로 검증한다. 각 RED/GREEN의 명령·종료코드·발견수·실패/오류/skip·조건은 docs/test-evidence.md에 그때 기록한다. 관련 테스트가 통과하기 전 해당 작업을 체크하지 않는다.

## Phase 1: Setup — 현재 기반과 실행 의존성 확인

- [X] T001 AGENTS.md·knowledge/README.md·knowledge/rules/ 전체·관련 4도메인·기준 계획서 2~5절/8단계·009 설계 전체를 실제 코드와 대조하고 `git rev-parse --show-toplevel`·HEAD·브랜치·기존 변경을 확인해 범위/재사용 목록을 docs/test-evidence.md에 기록한다. feature/redis-rate-limit을 유지하고 사용자 변경을 보존한다.
- [X] T002 backend/gradle/wrapper/gradle-wrapper.properties·backend/build.gradle·test/support/ContainerTestSupport.java를 대조해 Java25/Wrapper9.7.1/Boot4.1.1·실제 BOM·PG18.6/Redis8.10.2 고정 digest·Testcontainers2.0.5와 Docker 가용성을 확인하고 docs/versions.md에 실행 확인값과 미검증을 구분한다. 버전 하향·신규 의존성 추가부터 하지 않는다.
- [X] T003 qodana.yaml의 JVM2026.2·Java25·recommended·any:0·라이선스 게이트를 확인하고 정식 실행 경로/CLI·이미지·인증 확보 상태를 docs/test-evidence.md에 기록한다. 현재 인증 미확보·미실행이며 오케스트레이터의 확보 결과를 기다리는 필수 완료 의존성이다. 토큰을 출력하거나 게이트를 완화하지 않는다.

**완료 조건**: 현재 코드/도구/변경 소유권이 확인되고 외부 실행 의존성이 기록됨. T003의 확인 자체는 Qodana 통과가 아니다.

## Phase 2: Foundational — 격리된 실제 의존성과 관측 기반

- [X] T004 test/support/ContainerTestSupport.java를 재사용해 신규 제한 IT가 실제 PG/Redis 동적 접속값·랜덤 인증·UTF-8/UTC를 받도록 최소 지원을 구성하고 기동/정리 최소 실행을 기록한다. 기존 postgresOnly 경로와 고정 이미지/digest를 보존하며 개발 DB/Redis에 연결하지 않는다.
- [X] T005 test/support/RateLimitTestSupport.java에 ADMIN/A/B 등 시험이 소유한 UUID의 키만 초기화·count/PTTL 관측하는 지원을 작성한다. 기존 test/support/ConcurrencyTestSupport.java·HttpClientTestSupport.java·DatabaseAssertions.java의 유한 실행·종료·단일 SELECT를 재사용하고 초기화는 모든 작업 종료 확인 뒤에만 허용한다. FLUSHALL·공개 제품 장애후크를 만들지 않는다.
- [X] T006 test/ratelimit/RateLimitApiIT.java·공통 test/ratelimit/RateLimitHttpTestSupport.java의 실제 RANDOM_PORT/Basic HTTP 기반에 직접 커밋한 준비 데이터·요청별 ID·실제 limiter/Controller/Service/TX 관측·전체 업무 행 snapshot 지원을 준비한다. DB 풀10/획득3000ms·READ COMMITTED·SET LOCAL 2s/5s 및 Redis 명령/연결200ms 실제값 확인점을 둔다. 신규 기능 assertion은 각 이야기에서 RED 확인한다. US3 RedisFailureIT는 이 공통 기반을 재사용하며 실제 장애 fixture는 T023 이후 작성한다.

**완료 조건**: 독립 테스트 자원과 관측 기반이 실행 가능함. 신규 기능을 미리 구현하지 않는다. 기존 HTTP fixture 전체 전환은 US4에서 한다.

## Phase 3: US1 — 사용자별 고정 창 (P1, MVP 기반)

**목표**: 원자적 카운트·최초 만료·PTTL과 정확한 허용/대기시간을 제공한다.
**독립 인수**: 실제 Redis에서 기본20/60000ms의 20허용/21거부·A/B/ADMIN 격리·TTL 비연장·동시40건의 정확20허용을 검증한다. HTTP 혼합 인수는 US2 연결 뒤 별도로 확인한다.

- [X] T007 [US1] test/ratelimit/RateLimitDecisionTest.java에 count 경계20/21, 허용 true/0·거부 false/양수, PTTL0/1/999/1000/1001/60000→1/1/1/1/2/60초 및 long overflow 방지, 깨진 Lua응답(null/길이/타입/count<1/PTTL<0·-1/-2) 거부의 Unit을 작성하고 최소 컴파일 경계로 의도한 RED를 기록한다.
- [X] T008 [US1] main/ratelimit/RateLimitDecision.java·main/ratelimit/RedisRateLimiter.java에 T007의 순수 판정/응답 검증을 최소 구현한다. `[count,pttl]` 두 정수·count>=1·pttl>=0, `count<=limit`이면 `(true,0)` 아니면 `(false,max(1,ceil(pttl/1000)))`를 지키며 내부 오류를 정상판정으로 바꾸지 않고 Unit GREEN을 확인한다.
- [X] T009 [US1] test/ratelimit/RateLimitPropertiesTest.java에 `reservation.rate-limit.limit=20`, `window=60000ms`, “둘 다 양수·window 밀리초 표현 가능 여부를 시작 시 검증”하는 경계 테스트를 작성해 RED를 확인한다. 비활성화 설정은 제공하지 않는다.
- [X] T010 [US1] main/ratelimit/RateLimitProperties.java·backend/src/main/resources/application.yaml·backend/src/main/resources/application-local.yaml에 T009의 검증과 기본값·spring.data.redis.timeout=200ms·connect-timeout=200ms를 연결하고 Unit/최소 컨텍스트 실행으로 GREEN과 적용값을 확인한다.
- [X] T011 [US1] test/ratelimit/RedisRateLimitIT.java에 실제 Redis 기본20/60000·21번째 이후 집계·UUID별 격리·TTL 존재/비연장·최초 동시40건 정확20허용/20거부/count40을 작성하고 현재 미동작 assertion RED를 확인한다. 기본 창 내30초 상한·모든 Future 결과/예외·종료를 검사한다.
- [X] T012 [US1] backend/src/main/resources/redis/rate-limit.lua·main/ratelimit/RedisRateLimiter.java에 `check(UUID userId): RateLimitDecision`을 구현한다. StringRedisTemplate·재사용 DefaultRedisScript로 INCR→count==1일 때만 PEXPIRE→PTTL을 원자 실행하고 `[count,pttl]`을 반환한다. 키는 `reservation:write-limit:`+UUID 표준문자열(중괄호 없음), ARGV는 양수 창ms다. 앱 시계 창·DB TX·timeout 자체 재시도·DECR 보상 없이 T011 GREEN을 확인한다.
- [X] T013 [US1] test/ratelimit/RedisRateLimitIT.java에 만료 전용3/1000ms 컨텍스트를 분리하고 20ms polling/최대5초로 만료 후 count1 재허용을 검증한다. 후속 허용/거부의 최초 만료시점 비연장과 판정 PTTL/나중 PTTL의 경과를 구분하며 docs/test-evidence.md에 변경값·오차·실제결과를 기록한다.

**완료 조건**: Unit 및 `./gradlew integrationTest --tests '*RedisRateLimitIT'` 발견>=1·실패/오류/skip0, TTL 영구키/유실 없음. T013은 T012 이후의 만료 회귀 확장이다.

## Phase 4: US2 — 실제 HTTP 순서·재전송·인코딩 (P1)

**목표**: 인증 UUID의 생성/취소만 검증 전에 정확히 한 번 집계한다.
**독립 인수**: 직접 준비한 데이터로 혼합21회·인증/크기 경계·잘못된 입력/인코딩·재전송을 실제 HTTP로 검사하고 count·응답·후속 진입·DB 상태를 대조한다.

- [X] T014 [US2] test/ratelimit/RateLimitApiIT.java에 기본20/60000ms의 생성·취소 혼합21회, 사용자B/ADMIN 격리·X-User-Id 무시, 단일요청 count+1·429 Retry-After/ProblemDetail/무변경을 작성하고 필터 부재에 의한 RED를 확인한다. 준비용 소유예약은 HTTP 쓰기로 카운터를 소비하지 않고 직접 커밋한다.
- [X] T015 [US2] test/ratelimit/RateLimitApiIT.java에 인증된 잘못된 JSON/UUID/키·미정의/중복 필드·생성415·취소본문400·타인/없는예약404·품절/충돌409를 각각 count+1로 확인하고 소진 뒤 입력/소유권보다429가 앞서는 RED 사례를 추가한다. 미인증/틀린비번401+WWW-Authenticate·Redis호출0 및 GET/resource POST 미집계를 포함한다.
- [X] T016 [US2] test/ratelimit/RateLimitApiIT.java에 일반/길이없음/chunked 8192/8193 실제바이트 경계와 인증유무별413 우선·Redis호출0, 기존 `%63ancel`/`%72eservations` 취소 경로의 빈/비빈본문·잘못된UUID·정확1회 집계 및 ERROR dispatch/이중등록 방지 RED를 추가한다. StrictHttpFirewall 정책은 완화하지 않는다.
- [X] T017 [US2] main/ratelimit/RateLimitFilter.java·main/security/SecurityConfig.java에 RequestBodyLimitFilter→Basic→Authorization→RateLimitFilter→취소검증→DispatcherServlet을 명시한다. MVC와 같은 PathPatternRequestMatcher로 UUID검증 전 경로를 선택하고 LocalIdentityResolver의 UUID만 쓴다. 필요시 main/security/CancelRequestValidationFilter.java로 기존 익명 검증을 분리하며 Controller 본문 방어를 유지한다. 필터 자동서블릿등록을 비활성화하거나 체인 소유 인스턴스로 한정해 T014~T016 GREEN을 확인한다.
- [X] T018 [US2] test/ratelimit/RateLimitApiIT.java에 성공키 재전송·중복취소·취소후생성재전송을 추가해 매번 집계하되 한도 내201/Location/Replay/최초CONFIRMED·동일시각, GET현재CANCELLED·중복취소 cancelledAt 보존을 확인한다. 소진 뒤 같은 재요청429·Controller/Service/TX0·전체 snapshot 동일을 검증한다.
- [X] T019 [US2] test/ratelimit/RateLimitApiIT.java에 기본정책 HTTP 동시40건/수량40/서로다른생성키를 추가해 201정확20·429정확20·count40·TTL존재와 최종 available20/CONFIRMED20을 확인한다. 30초 안 결과/예외를 모으고 서버/클라이언트/executor 종료 확인 뒤 test/support/DatabaseAssertions.java의 단일 SELECT를 사용한다.
- [X] T020 [US2] test/ratelimit/RateLimitApiIT.java를 지정 실행해 입력/인증/413/인코딩/재요청/기본동시의 응답·카운터·DB·진입 관측을 docs/test-evidence.md에 기록한다. main/ratelimit/RateLimitFilter.java의429는 ApiExceptionHandler.writeProblem 재사용, 표준6필드·민감정보 비노출·Retry-After 판정PTTL 근거까지 대조한다.

**완료 조건**: `./gradlew integrationTest --tests '*RateLimitApiIT'`의 발견>=1·실패/오류/skip0. 정상 응답 보존 및 앞단거부 Redis접근0을 실제 요청으로 입증한다.

## Phase 5: US3 — 실제 장애·DB 보호 (P1)

**목표**: Redis 장애에 쓰기503, 독립 API 유지, Redis 이후 DB TX와 실패 카운트 유지.
**독립 인수**: 시험 소유 Redis의 명령 지연·연결 단절·실제 WRONGTYPE 및 정상 PG에서의 DB 실패를 재현하고 쓰기/재요청503·무변경·독립200/200/201·복구를 확인한다.

- [X] T021 [US3] test/ratelimit/RateLimitFilterTest.java에 research.md의 실제 예외 원인 분류표를 고정해 연결/명령timeout/검증된 WRONGTYPE·정형OOM은503, Lua문법/없는명령/직렬화/깨진응답/일반 RuntimeException은500 탐지, 인터럽트 보존·전파를 RED로 검증한다. wrapper 타입만의 광범위 DataAccessException/RedisSystemException catch를 허용하지 않는다.
- [X] T022 [US3] main/ratelimit/RateLimitFilter.java·main/ratelimit/RedisRateLimiter.java에서 Redis호출 영역의 확인된 의존성 실패만 공통503으로 연결하고 T021 GREEN을 확인한다. 후속 filterChain 내부결함까지503으로 감싸지 않고 새500 업무code·허용대체·무제한재시도를 추가하지 않는다.
- [X] T023 [US3] test/ratelimit/RedisFailureIT.java에 관리연결 CLIENT PAUSE 1000 WRITE의 OK 후 실제 HTTP 생성/취소/성공키재요청을 보내 명령200ms timeout·503·Controller/Service/TX0·전체업무 snapshot 동일을 확인한다. timeout 뒤 Lua수행/count불확실성을 허용하되 DB무변경은 반드시 검사한다.
- [X] T024 [US3] test/ratelimit/RedisFailureIT.java에 기존연결 후 소유Redis중단·첫접속 전 중단을 별도 컨텍스트로 구성하고 생성/취소/기존키503 및 실제 리스트타입 제한키 WRONGTYPE503을 검증한다. 소유Redis의원래maxmemory/policy를보존하고maxmemory1/noeviction OOM500 RED→알려진OOM503 GREEN·DB무변경·독립API·복구를검증하며finally에서두설정을원복한다. 재기동 동적포트는 새ApplicationContext로 반영하며 mock예외만으로 장애인수를 대체하지 않는다.
- [X] T025 [US3] test/ratelimit/RedisFailureIT.java에서 Redis중단/지연 중 정상PG의 GET resource200·GET 소유예약200·ADMIN POST resource201과 Redis호출0을 확인한다.429/503의 problem+json 표준필드/code·민감정보비노출·재요청무변경도 대조한다.
- [X] T026 [US3] test/ratelimit/RedisFailureIT.java에 제한허용 후 테스트전용 실제 SQL/잠금 실패를 주입해 DB503·전체롤백·Redis count유지, 장애해제 후 같은생성키 count+1/201을 검증한다. 요청별 사건으로 limiter시점 TX비활성·허용완료 뒤 TX시작·거부때업무진입0을 확인하고 성공키보존과 TTL독립을 검증한다. 기존 Writer/SQL 계약을 변경하지 않는다.
- [X] T027 [US3] test/ratelimit/RedisFailureIT.java의 finally에서 독립관리 CLIENT UNPAUSE(관리명령3초)·최대5초PING·서버in-flight/HTTP/executor 각각10초 종료와 HTTP요청9초 상한을 보장한다. 최초오류·정리오류를 모두 보존하고 종료/전송실패 후 다음 DB/Redis reset·준비·제출을 차단하는 assertion을 작성해 탐지한다.
- [X] T028 [US3] test/ratelimit/RedisFailureIT.java 지정 실행과 관련 Unit을 인수하고 docs/test-evidence.md에 실제 예외/경과·200ms적용값·장애조건·복구/종료결과·카운트불확실성·미검증을 기록한다. 실패 시 최소재현을 찾고 mock/skip/반복실행으로 통과시키지 않는다.

**완료 조건**: `./gradlew integrationTest --tests '*RedisFailureIT'` 및 분류Unit 발견>=1·실패/오류/skip0. 실제 장애/DB 보호와 독립 API를 각각 확인하고 정리 실패는 인수 실패다.

## Phase 6: US4 — 기존 HTTP·경쟁 회귀 (P2)

**목표**: Redis를 연결해도 원래 검증하던 예약 계약·110회 경쟁·종료 보호를 유지한다.
**독립 인수**: 기본제한 전용시험과 분리해 영향받는 기존 HTTP와 경쟁10000/60000ms·C01~C11 각10회 및 지원fixture를 실행한다. 이전 Unit150/IT257은 과거 기준이며 이번 수로 복사하지 않는다.

- [X] T029 [US4] test/reservation/ReservationConcurrencyIT.java와 기존 종료보호fixture에 Redis reset spy/관측을 추가해 종료실패·전송불확실 뒤 부모setup의 첫 보호검사보다 reset이 앞서면 실패하도록 RED를 작성한다. fixture 정확 위치는 기존 파일 내 탐색해 재사용하며 새로운 경쟁 실행기를 만들지 않는다.
- [X] T030 [US4] test/reservation/ReservationApiTestSupport.java·test/support/RateLimitTestSupport.java에서 beforeDatabaseReset을 첫 동작으로 유지한 뒤 소유키/DB초기화를 수행한다. 미주입 Redis·mock DataSource 지원fixture도 실제 reset횟수로 검사하며 T029 GREEN을 확인한다. 중단된작업 뒤 초기화는0이어야 한다.
- [X] T031 [US4] test/reservation/ReservationCreateIT.java·IdempotencyIT.java·ReservationRollbackIT.java·ReservationOwnershipIT.java·ReservationCancelIT.java 및 test/security/SecurityIT.java의 실제 요청을 확인해 영향받는 postgresOnly fixture만 PG+Redis로 전환하고 사례별 소유키격리/적용값 검사를 연결한다. test/resource/ResourceApiIT.java와 순수DB 시험은 쓰기가 없으면 postgresOnly를 유지한다. 원래 HTTP/DB assertion을429/503 기대값으로 바꾸지 않는다.
- [X] T032 [US4] test/reservation/ReservationConcurrencyIT.java에 경쟁전용10000/60000ms·명령/연결200ms 실제값을 명시하고 각110회 전 ADMIN/A/B 소유키만 초기화한다. 기존 C07·UNIQUE 관측, HTTP인증/필터/Controller 경로·DB준비 커밋·resource→reservation 잠금 기대를 보존한다.
- [X] T033 [US4] test/reservation/ReservationConcurrencyIT.java·test/support/ConcurrencyTestSupportTest.java의 기존 종료/전송/오류보존 fixture를 실행해 최초StopState와 모든결과·고유ID·다음reset/준비/제출0을 확인한다. 기존100요청120초·나머지30초·gate5초·HTTP9초·종료10초와 클래스순차 실행을 유지한다.
- [X] T034 [US4] backend/build.gradle의 기존 integrationTest에서 영향받는 HTTP IT·ReservationConcurrencyIT를 지정 실행해 C01~C11 각10회(110회)와 지원fixture수를 분리 집계한다. 예상밖429/503·미수집결과·미종료·수량불일치는 실패로 처리하며 모든종료 뒤 단일 SELECT snapshot과 경로기록을 docs/concurrency-runs.json·docs/test-evidence.md에 반영한다.

**완료 조건**: 영향받는 기존 응답/DB 기대·110회·지원fixture 통과, Redis 초기화로 종료보호를 우회한 사례0. US4만 따로 인수할 때도 US1~US3 구현 통합본이 선행한다.

## Phase 7: Polish — 전체 필수 게이트와 기록

- [ ] T035 README.md·knowledge/README.md·knowledge/domain/ratelimit.md·security.md·reservation.md·resource.md·관련 knowledge/rules/rate-limit.md·api-and-security.md를 실제구현/검증 상태에 동기화하고 기준 계획서의8단계 체크를 실행근거가 있는 항목만 갱신한다. docs/openapi.yaml의 기존429/503/Retry-After·성공/소유권 계약을 대조하며 불필요한 계약변경을 하지 않는다.
- [ ] T036 specs/009-redis-rate-limit/quickstart.md와 실제구현을 대조해 backend/에서 `./gradlew build verify verifyContractDependencies`를 순차 단일명령으로 실행하고 build산출물·일반Unit/IT 발견수·실패/오류/skip0·기존의존성gate 결과/종료코드를 docs/test-evidence.md에 기록한다. 공유test는 한 번만 실행하고0건/skip을 통과로 보지 않는다. 실패나새변경이 없으면 전체실행을 반복하지 않는다.
- [ ] T037 qodana.yaml의 정식확보 실행경로로 Qodana JVM2026.2를 실제실행해 recommended·any:0·라이선스 조건을 확인하고 명령/종료코드/분석결과를 docs/test-evidence.md·docs/qodana-review.md에 기록한다. 사용자2026-10-22 지시로 실행은 후속 범위다. 현재 인증미확보·미실행이며 이 작업만 미완료로 유지하고 이번 완료·PR·병합 게이트에서는 제외한다. 존재하지않는 Gradle태스크를 안내하지 않는다.
- [ ] T038 docs/versions.md·docs/decisions.md·docs/test-evidence.md에 실제버전·RED/GREEN·각지정/최종명령·제한값·장애/정리조건·110회·미검증을 정리하고 specs/009-redis-rate-limit/tasks.md·spec.md·plan.md의 추적/완료상태와 링크를 대조한다. 기능/경쟁/장애/빌드/verify/의존성 필수게이트가 모두통과한 경우에만 전체완료를 보고하고 핵심Lua/필터/테스트와 다음한작업을 제시한다. 목록 생성 에이전트는 커밋·push·PR·외부CI를 수행하지 않는다. 후속 오케스트레이터는 사용자 승인 범위에서 목적별 커밋과 검증 완료 후 단일 develop PR을 진행하고, 지정 프로필의 독립 최종 SHA 리뷰·필수 게이트·보호 조건을 모두 충족한 뒤 merge한다. Qodana T037은 사용자지정 미완료 후속으로 별도 보고하며 이번 완료·PR·병합을 막지 않는다. 나머지 필수검증 완료 전 PR을 생성하지 않는다.

## 의존성·실행 순서

`T001 → T002 → T003 → T004 → T005 → T006 → US1(T007~T013) → US2(T014~T020) → US3(T021~T028) → US4(T029~T034) → T035 → T036 → T037 → T038`.
각 구간 내 번호순으로 실행한다. T003의 환경확인 뒤 기능작업은 진행 가능하지만 Qodana T037은 사용자지정 후속이므로 T038의 이번 완료 조건에서 제외한다. T011의 실제 Redis RED는 T012 전에, T014~T016의 HTTP RED는 T017 전에, T021 분류RED는 T022 전에, T029 보호RED는 T030 전에 둔다. 이미 구현한 경로의 추가 인수는 별도 재구현 없이 실행하며 새 결함 발견시 최소재현→RED→수정→GREEN을 기록한다.

US2는 US1 limiter에, US3는 US1/US2 필터경계에, US4는 통합된 제한동작과 종료관측에 의존한다. “독립 인수”는 격리fixture/명령으로 그 이야기의 결과를 판정한다는 뜻이며 기반구현까지 서로 독립이라는 뜻은 아니다. 기본정책 전용 HTTP와 높은한도 경쟁은 설정·컨텍스트·증거를 분리한다.

## 병렬 기회와 이야기별 실행 예

사용자 제약상 병렬 쓰기에는 별도 worktree/브랜치와 파일소유권 분리가 필요하다. 같은 worktree의 동시편집은 허용하지 않는다. 공유제품·fixture·문서·Gradle/Docker검증은 순차 실행한다. 이번에는 파일/실행의존성이 커서 별도분리 이득이 작으므로 **[P] 작업0개**로 설계했다. 새로운 브랜치/worktree 생성이나 하위에이전트 실행을 이 목록이 승인하지 않는다.

| 이야기 | 실행 예·병렬 판단 |
| --- | --- |
| US1 | T007 RED→T008 GREEN→T009 RED→T010 GREEN→T011 RED→T012 GREEN→T013. 동일 limiter/설정/실제Redis를 공유하므로 순차 |
| US2 | T014~T016 HTTP RED→T017 체인연결→T018~T020 인수. 같은 RateLimitApiIT·보안체인을 공유하므로 순차 |
| US3 | T021 RED→T022 GREEN→T023~T028 실제장애/복구. 소유컨테이너 중단·pause·컨텍스트·정리의존으로 순차 |
| US4 | T029 RED→T030 GREEN→T031~T034 전환/110회. 부모fixture·종료보호·키초기화·Gradle실행을 공유하므로 순차 |

## 구현 전략과 완료 판정

MVP는 Phase1~3의 US1 limiter 기반이다. 실제 사용자 HTTP MVP는 US2 연결까지 필요하다. 이어 US3의 실제장애·데이터보호, US4의 기존정합성회귀를 독립 인수하고 최종 게이트로 진행한다. MVP 통과를8단계 전체완료나 배포승인으로 보지 않는다.

전체 완료는 지정Unit/Redis/HTTP/실제장애/기존회귀·110회·종료fixture, 최종 build+verify+verifyContractDependencies 및 문서동기화를 모두 실제확인한 때다. Qodana T037은 사용자2026-10-22 지시로 미완료 후속이며 이번 완료·PR·병합 게이트에서는 제외한다. 나머지 필수검증의 미실행·실패·정리실패는 전체미완료이며 알려진 blocker/미검증을 그대로 보고한다.

## 요구사항 추적

| 요구사항 | 구현·검증 작업 | 인수 근거 |
| --- | --- | --- |
| FR-001 | T010~T012,T014,T017 | 인증UUID·20/60초·혼합·사용자격리 |
| FR-002 | T011~T014,T019 | 20/21·초과집계·TTL비연장·동시40 |
| FR-003 | T011~T013 | Lua원자성·최초TTL·Redis창 |
| FR-004 | T007~T008,T014,T020 | 올림/최소1·판정PTTL·HTTP헤더 |
| FR-005 | T015~T017 | 8KiB→인증→제한→입력→서비스 |
| FR-006 | T015,T018,T025 | 입력/재전송집계·독립API |
| FR-007 | T021~T024,T028 | 실제연결/timeout/WRONGTYPE·내부500분리 |
| FR-008 | T014,T018,T023~T027 | 거부시진입0·TX0·전체snapshot동일 |
| FR-009 | T018,T026 | Redis뒤TX·DB실패카운트유지·성공키보존 |
| FR-010 | T018,T020,T025,T031 | 오류필드/헤더·성공/소유권/취소보존 |
| FR-011 | T007~T013,T019 | 기본경계·실제동시·TTL·만료 |
| FR-012 | T014~T028 | 실제Basic/필터·크기/인코딩·실제장애/DB |
| FR-013 | T029~T034 | 실제Redis전환·10000/60초·110회·종료보호 |
| FR-014 | T005,T019,T027,T029~T034 | 모든결과/종료뒤단일SELECT·다음초기화차단 |
| FR-015 | T002~T003,T020,T028,T034~T038 | 실제버전/명령/수·최종게이트·기록 |

SC-001→T011/T014/T019, SC-002→T011/T013/T014, SC-003→T007/T015~T020, SC-004→T018/T023~T025, SC-005→T018/T026/T031, SC-006→T029~T034/T036. 기본정책20/60000ms·200ms, 만료전용3/1000ms, 경쟁전용10000/60000ms 이외의 정책을 추가하지 않는다.
