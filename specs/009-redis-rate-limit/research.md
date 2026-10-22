# Phase 0 조사: Redis 요청 제한

2026-10-22. 기준 문서·실제 코드·로컬 source jar 및 공식 자료 조사다. 실제 신규 Redis 장애 실험이나 기능 시험은 수행하지 않았다. 읽기 전용 조사 에이전트의 Redis API·장애 분류 결과를 통합했다. 업무 정책 NEEDS CLARIFICATION은 없다. Qodana 실행은 사용자2026-10-22 지시로 후속 범위이며 이번 완료·PR·병합 게이트에서 제외한다.

## 1. 원자적 판단과 직렬화

**Decision**: 기존 StringRedisTemplate와 재사용 DefaultRedisScript<List>를 사용한다. 키는 문자열, window는 밀리초 문자열 인수다. Lua는 INCR→count==1일 때만 PEXPIRE→PTTL 후 `[count,pttl]`을 반환한다. limit 비교와 대기시간 계산은 Java의 순수 판정으로 둔다.

**Rationale**: 설치 Data Redis4.1.1의 DefaultScriptExecutor/ScriptUtils source를 확인했다. StringRedisTemplate가 키·인수를 문자열로 직렬화하고 List 안 Redis 정수는 Long으로 보존한다. 응답을 단순 캐스팅해 잘못된 상태를 허용하지 않고 size2/exactLong/count>=1/pttl>=0을 검사한다. PTTL0은 허용한다. TTL 음수나 깨진 응답은 내부 결함으로 탐지한다.

EVALSHA 후 NOSCRIPT만 EVAL fallback한다. Redis에서 스크립트의 INCR·PEXPIRE·PTTL이 원자 실행되는 것이며 매 요청 네트워크 명령이 반드시 하나라는 보장은 아니다. timeout 뒤 앱이 Lua를 자체 재시도하지 않는다.

**Alternatives considered**: 별도 INCR/EXPIRE는 중간 장애에 영구 키를 만들 수 있어 제외. MULTI/분산 락/Redis 수량 저장은 불필요하며 범위 밖. 스크립트 매 요청 객체 생성은 캐싱 이점이 없다.

근거: [Spring Data Redis scripting](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html), [Redis INCR](https://redis.io/docs/latest/commands/incr/), [PEXPIRE](https://redis.io/docs/latest/commands/pexpire/), [PTTL](https://redis.io/docs/latest/commands/pttl/). 로컬 설치 source: spring-data-redis4.1.1의 DefaultScriptExecutor·ScriptUtils.

## 2. 인증·경로·필터 순서

**Decision**: Security 체인에 `RequestBodyLimitFilter→BasicAuthenticationFilter→AuthorizationFilter→RateLimitFilter→취소 입력 검증→DispatcherServlet`을 명시한다. 제한 필터는 OncePerRequestFilter로 정상 REQUEST를 한 번 처리한다. Bean 자동 서블릿 등록은 비활성 FilterRegistrationBean으로 막거나 체인 생성 시 인스턴스만 만들어 이중 등록을 방지한다. 두 사용자 정의 필터를 동일 AuthorizationFilter 뒤에 각각 추가하는 순서 의존은 피한다.

POST 생성 정확 경로와 POST `/api/reservations/{id}/cancel`에 PathPatternRequestMatcher를 사용한다. 현재 MVC 기본 PathPatternParser와 같은 파서·대소문자·후행 slash 정책을 사용한다. raw URI startsWith/endsWith·직접 URLDecoder를 제한 기준으로 사용하지 않는다. UUID 형식 검사 전에 `{id}` 경로를 매칭해 잘못된 UUID도 집계한다. 현재 Controller의 인코딩 경로 취소 본문 방어는 유지한다.

**Rationale**: 설치 Security7.1.1 source의 matcher는 RequestPath를 파싱해 PathContainer에 패턴을 적용한다. Framework7.0.9의 세그먼트 기반 디코딩과 일치해야 `%63ancel`, `%72eservations`가 우회하지 않는다. 실제 서버 경로 회귀로 최종 입증한다. Basic/인가 통과 후 LocalIdentityResolver를 이용해 고정 Principal UUID만 얻는다.

**Alternatives considered**: Controller interceptor만의 제한은 앞선 취소 필터400을 집계하지 못한다. Basic 직후에만 배치하면 미인증 요청을 별도로 처리해야 한다. 인가 뒤·검증 앞의 명시 순서가 현재 체인과 맞는다. 광범위한 `/api/**` 제한은 독립 조회/대상 생성을 오염시킨다.

근거: [Security architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html), [PathPatternRequestMatcher API](https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/web/servlet/util/matcher/PathPatternRequestMatcher.html). 설치7.1.1 source에서 withDefaults().matcher(HttpMethod,pattern), RequestPath 처리 API를 확인했다. 문서 예제 버전과 설치 API를 구분했다.

## 3. timeout·엄격 실패 분류

**Decision**: 기본 application에도 명령 timeout200ms를 명시하고 모든 IT에서 적용값을 검사한다. 첫 연결 대기도 유한하게 하도록 connect-timeout200ms를 별도로 명시한다. 필터는 Redis 호출 영역에서 확인된 연결 단절·접속 실패·명령 timeout만503으로 변환하고 공통 writeProblem을 사용한다. 한도 초과429에는 Retry-After를 붙인다.

**Rationale**: 설치 Lettuce7.5.2.RELEASE·SDR4.1.1의 LettuceExceptionConverter는 명령 timeout을 QueryTimeoutException, transport 연결 오류를 RedisConnectionFailureException으로 변환한다. 연결 획득 래퍼가 RuntimeException을 넓게 감쌀 수 있으므로 알려진 transport/timeout 원인까지 확인하고 인증·설정·프로그램 결함을 혼동하지 않는다. DataAccessException/RedisSystemException/RuntimeException 전체 catch→503을 금지한다.

명령 실행 오류(예: 실제 Redis WRONGTYPE)는 검증된 RedisCommandExecutionException 원인이 있는 의존성 실행 실패로 구별해503을 반환할 수 있으나, Lua 문법/없는 명령/직렬화/잘못된 응답 등 구현 결함은500으로 탐지한다. 원인·오류별 분류표를 Unit으로 고정하고 실제 WRONGTYPE IT로 오류 경로를 확인한다. RedisSystemException이라는 wrapper만으로 판단하지 않는다. 인터럽트는 보존·전파하며 의존성 실패로 일괄 숨기지 않는다. 500의 새 업무 code는 만들지 않는다.

**Alternatives considered**: 모든 Redis 예외를503으로 감추거나 장애를 허용으로 대체하는 방식 제외. command timeout만으로 TCP 접속 대기 상한을 주장하지 않는다. HTTP 전체200ms 보장도 하지 않는다.

근거: 설치 source의 LettuceExceptionConverter·LettuceConnectionFactory·LettuceConnectionProvider, [LettuceClientConfiguration API](https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/connection/lettuce/LettuceClientConfiguration.html). 실제 발생 원인은 구현 단계에 기록한다.

## 4. 실제 장애 주입과 안전한 종료

**Decision**: 전용 RedisFailureIT 소유 컨테이너에서 관리 연결로 `CLIENT PAUSE 1000 WRITE`의 OK를 확인한 뒤200ms 제한의 실제 앱 HTTP를 호출한다. finally에 별도 관리 연결의 CLIENT UNPAUSE·PING·유한 종료를 확인한다. EVAL/EVALSHA 모두 WRITE pause 대상이다. 모든 연결을 pause하는 ALL 대신 관측 가능한 WRITE를 선택한다.

접속 실패는 기존 연결을 연 뒤 해당 테스트 소유 Redis를 중단한 경우와 최초 접속 전에 중단한 경우를 별도 컨텍스트/fixture로 검증한다. stop/restart로 포트가 바뀌면 기존 ApplicationContext를 재사용하지 않는다. 새 컨텍스트가 새 동적 포트를 받도록 구성한다. 복구 검증이 필요한 경우 같은 소유 Redis 프로세스를 Docker pause/unpause로 일시 중단하는 별도 사례를 사용할 수 있으나 관리 API/복구를 finally에서 보장한다.

**Rationale**: 실제 서버 지연/연결단절을 mock 예외보다 직접 입증한다. 명령 timeout 뒤 queued Lua가 실행될 수 있으므로 카운터0을 요구하지 않는다. DB 무변경·Controller 진입0은 반드시 요구한다. 지연/장애 중 정상 PG의 두 GET·ADMIN resourcePOST도 확인한다.

HTTP 요청9초, pause1000ms, 관리 명령3초, 복구PING 최대5초, 서버 in-flight/HTTP/executor 정리 각각10초를 검증 상한으로 둔다. 기존100요청 경쟁120초·나머지30초와 연결3초/요청9초 상한은 유지한다. 고정200ms 이하 HTTP assertion이나 무한 polling은 금지한다. 조건·실제 경과·실제 예외와 모든 정리결과를 남긴다. 정리 실패 시 다음 초기화·요청을 차단한다.

**Alternatives considered**: 무한 DEBUG SLEEP·공개 장애 API·제품 테스트플래그 제외. 실제 timeout 발생 전 서버를 재개해 지연을 숨기지 않는다.

근거: [CLIENT PAUSE](https://redis.io/docs/latest/commands/client-pause/), [CLIENT UNPAUSE](https://redis.io/docs/latest/commands/client-unpause/). 공식 문서와 API 조사이며 이 단계에서 장애 실행은 미실시다.

## 5. 기존 시험 전환과 증거

**Decision**: 예약 쓰기가 있는 ReservationCreateIT/IdempotencyIT/ReservationRollbackIT/ReservationOwnershipIT/ReservationCancelIT/ReservationConcurrencyIT·SecurityIT의 실제 요청을 확인해 PG+Redis로 전환한다. ResourceApiIT는 대상 API만 호출하는 경우 postgresOnly를 유지할 수 있고 독립성 회귀가 된다. 순수 Repository/Schema/DB 시험에는 Redis를 강제하지 않는다.

기존 HTTP 사례별로 사용한 키만 DEL한다. ReservationApiTestSupport.setup의 beforeDatabaseReset 보호검사를 가장 먼저 유지하고 그 뒤 DB/Redis 초기화와 준비를 한다. fixture의 mock DataSource/미주입 Redis 정리도 기존 지원fixture와 충돌하지 않게 실제 reset 횟수로 검사한다. concurrency는10000/60000ms를 명시·검사하고 110회 각 시작 전 ADMIN/A/B 테스트 소유 키만 제거한다. FLUSHALL은 금지한다. 원래 상태/응답/경로 assertion을429 기대값으로 바꾸지 않는다.

**Rationale**: 기존 postgresOnly가 작성 당시 올바른 지원이었다는 점과 제한 연결 이후 쓰기에 실제 Redis가 필요한 점을 구분한다. StopState·모든 결과 보존·C07/UNIQUE 사건·전체 서버종료의 회귀를 유지한다. 새 제한 동시 시험은40건/수량40·서로 다른 키로 정확20개201/20개429 및 count40·TTL존재·단일SQL수량보존을 확인한다.

**Alternatives considered**: Redis mock/영구제한해제/기본 제한 시험의 높은 한도/내부Service로 HTTP대체 제외. 전체 테스트 공통 Redis 강제도 순수DB 경계를 해치므로 제외.

## 6. 정적검사와 미해결 실행 환경

**Decision**: 사용자2026-10-22 “나중에 할거야” 지시로 Qodana는 후속 범위다. T037을 미완료 후속으로 유지하되 이번 완료·PR·병합 게이트에서 제외한다. 기존 qodana.yaml의 jetbrains/qodana-jvm:2026.2, Java25, recommended·any0/라이선스 조건을 유지한다. 설정이나 품질 기준은 바꾸지 않으며 미실행을 통과로 기록하지 않는다.

**Rationale**: 최초 설계의 정적검사 요구 이후 사용자가 실행을 나중으로 명시해 범위를 조정했다. 현재 CLI·이미지·QODANA_TOKEN 미확보·미실행이며 이번 기능·경쟁·장애·빌드·verify·의존성·독립리뷰 게이트는 유지한다. 사용자 토큰을 문서/로그에 쓰지 않는다.

**Alternatives considered**: 게이트 완화/린터 임의교체/미실행 성공처리 제외. 실행 명령은 확보된 정식 경로가 확인된 뒤 기록하며 존재하지 않는 Gradle qodana 태스크를 안내하지 않는다.
