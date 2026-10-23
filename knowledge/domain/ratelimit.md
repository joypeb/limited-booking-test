# ratelimit 도메인 지식

## 1. 기준과 현재 상태

- 구현 패키지: `com.limitedbooking.backend.ratelimit`. 최초 작성:2026-10-13, US1 구현·검증:2026-10-22.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절, [API 규칙](../rules/api-and-security.md), [트랜잭션 규칙](../rules/domain-and-transactions.md), [요청 제한 규칙](../rules/rate-limit.md).
- 설계는 기존 확정 규칙이다. US1 판정/설정/RedisRateLimiter·Lua와 Unit13/실제Redis IT5를 구현·검증했고 오케스트레이터 독립검토를 통과했다. 이어 US2 필터·실제HTTP37건 인수를 완료했다. US3 좁은503분류·실제장애12건·DB롤백/TTL독립·종료보호도 인수했다. US3 독립검토와 US4 기존HTTP/110회·최종빌드/verify/의존성 인수까지 완료했다.


**현재 검증 상태(2026-10-22):** 2026-10-22 8단계 구현·실행 인수 완료: `./gradlew build verify verifyContractDependencies` 종료0, 일반 Unit193·IT312, 실패/오류/skip0. IT에는 기본 Redis5·HTTP제한37·실제장애12·경쟁110회와 종료지원fixture5가 포함된다. T001~T036/T038 37개 완료, Qodana T037만 사용자 “나중에 할거야” 지시에 따른 미실행 후속이며 이번 완료·PR·병합 게이트에서 제외한다. 이 요약은 구현·실행 인수 시점의 기록이다. 이후 PR·독립 리뷰·병합 결과는 해당 PR과 최종 보고에서 확인한다. [실행 근거](../../docs/test-evidence.md)·[009 작업](../../specs/009-redis-rate-limit/tasks.md)을 따른다. 아래 날짜별 이력은 해당 시점 기록이다.

## 2. 목적·책임·경계

인증된 예약 생성·취소의 진입 횟수를 제한한다. PostgreSQL 업무 수량 원본을 대체하지 않는다. 조회·대상 생성/조회는 Redis에 의존하지 않는다. Redis 수량 저장·분산 락·사용자별 새 정책은 제외한다.

## 3. 용어와 데이터

| 용어 | 타입·의미 | 제약·원본 |
| --- | --- | --- |
| reservation:write-limit:{userId} | Redis 키 | 인증 UUID별, 생성·취소 합산 |
| 카운터 | 정수 | 최초 요청부터 20회/60초 고정 창 |
| PTTL | 남은 밀리초 | Redis 원본, 요청마다 TTL 연장 없음 |
| Retry-After | 정수 초 | max(1,ceil(PTTL/1000)) |

업무 소유자/상태/시각/생성 키는 별도 저장하지 않는다. Redis 제한 키 TTL은 성공 예약 키 보존 정책과 무관하다.

## 4. 불변식과 상태 전이

| 규칙 | 기대값 | 근거 |
| --- | --- | --- |
| ratelimit-001 | INCR·최초 PEXPIRE·PTTL을 Lua로 원자 처리 | 요청 제한 규칙 |
| ratelimit-002 | 재요청·잘못된 입력도 인증 뒤 진입하면 포함 | 계획서 2 |
| ratelimit-003 | 429·Redis 503은 DB 무변경, DB 실패로 카운터 복구 안 함 | 계획서 2 |

| 상태 | 요청 | 다음 상태·효과 |
| --- | --- | --- |
| 키 없음 | 최초 쓰기 | count=1·TTL=60초, 통과 |
| 유효 창 | 2~20번째 | 카운트 증가·TTL 비연장, 통과 |
| 유효 창 | 21번째 이후 | 카운트 증가·TTL 비연장, 429 |
| 창 만료 | 다음 요청 | 새 창 시작 |

독립 업무 예약 상태·멱등 행은 없다.

## 5. 유스케이스와 API 계약

| 내부 경계 | 입력 | 결과 | 업무 변경 |
| --- | --- | --- | --- |
| 인증 뒤 예약 쓰기 필터 | Principal UUID, POST 생성/취소 | 20회 이내 통과 | 후속 서비스만 변경 |
| 제한 초과 | 남은 PTTL | 429 RATE_LIMITED + Retry-After | 없음 |
| Redis 오류·명령 timeout | 초기 200ms 설계 | 503 DEPENDENCY_UNAVAILABLE | 없음 |

오류 본문은 공통 ProblemDetail이다. 미인증 요청은 Redis로 보내지 않는다. GET과 대상 POST에는 이 제한을 적용하지 않는다.

## 6. 멱등성·트랜잭션·경쟁·장애

동일 요청도 카운터를 증가시키므로 제한 자체는 멱등하지 않다. 생성의 성공 응답 재현은 제한 통과 뒤에만 가능하다. Lua는 카운터·만료를 원자 처리하고 매 요청 TTL을 연장하지 않는다. 창의 기준은 앱 시계가 아닌 Redis TTL이다.

DB 트랜잭션은 제한 통과 뒤 시작한다. 제한 카운터는 DB 트랜잭션과 분리되며 DB 실패로 되돌리지 않는다. Redis 장애에 허용 처리하지 않는다. Redis와 PostgreSQL의 원자 커밋을 보장하는 설계가 아니다. 응답 유실 후 재요청도 횟수에 포함한다.

실제 Redis IT는 기본 20/60초, 테스트 한정 3건·짧은 창 또는 경쟁 10000/60초·부하 100000/60초 변경 시 값·이유를 증거에 기록한다. 원 제한 시험과 분리하고 테스트 전용 키만 초기화한다.

## 7. 검증할 사례와 실제 증거

| 규칙 | 사례 | 기대 | 검증 상태 |
| --- | --- | --- | --- |
| ratelimit-001 | 20회·21번째·TTL·만료·동시40 | 허용20·이후거부·TTL 비연장·새 창1회 | RedisRateLimitIT5·RateLimitApiIT 실제 검증 |
| ratelimit-002 | 합산·재요청·입력 오류·인코딩·사용자 격리 | 각1회·사용자별 독립·인증전/413미집계 | RateLimitApiIT37 실제 검증 |
| ratelimit-003 | 실제지연·기존연결/첫연결중단·WRONGTYPE·OOM·DB실패 | 쓰기503/무변경·DB실패카운트유지·독립API정상 | RedisFailureIT 계열12건·분류/종료Unit 인수 |

실제 검토·실행 기록은 [증거 문서](../../docs/test-evidence.md)에서 구분한다. US3 실제장애12건도존재·지정인수했으며 US4 기존회귀·최종전체게이트도완료했다. [009 작업 상태](../../specs/009-redis-rate-limit/tasks.md)를 따른다.

## 8. 구현 연결과 변경 영향

| 책임 | 실제 파일·예정 위치 | 상태 |
| --- | --- | --- |
| 앱 골격 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java) | 앱 기동 및 기능 컴포넌트 스캔 |
| 순수 판정·설정 | [RateLimitDecision](../../backend/src/main/java/com/limitedbooking/backend/ratelimit/RateLimitDecision.java)·[RateLimitProperties](../../backend/src/main/java/com/limitedbooking/backend/ratelimit/RateLimitProperties.java) | Unit13·시작 설정 검증 완료 |
| 원자 집계 | [RedisRateLimiter](../../backend/src/main/java/com/limitedbooking/backend/ratelimit/RedisRateLimiter.java)·[Lua](../../backend/src/main/resources/redis/rate-limit.lua) | 실제 Redis IT5 검증 완료 |
| 실제 인수·키 지원 | [RedisRateLimitIT](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RedisRateLimitIT.java)·[RateLimitTestSupport](../../backend/src/test/java/com/limitedbooking/backend/support/RateLimitTestSupport.java) | 소유 키만 reset/count/PTTL, 기존 ConcurrencyTestSupport 재사용 |
| 필터·HTTP | [RateLimitFilter](../../backend/src/main/java/com/limitedbooking/backend/ratelimit/RateLimitFilter.java)·[CancelRequestValidationFilter](../../backend/src/main/java/com/limitedbooking/backend/security/CancelRequestValidationFilter.java)·[RateLimitApiIT](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitApiIT.java)·[관측 기반](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitHttpTestSupport.java) | 실제HTTP37건 인수 |
| 장애·503 분류 | [RateLimitFilter](../../backend/src/main/java/com/limitedbooking/backend/ratelimit/RateLimitFilter.java)·[RedisFailureIT 계열](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RedisFailureIT.java)·[분류 Unit](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitFilterTest.java)·[종료 fixture](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitHttpLifecycleTest.java) | 실제장애12건·분류22/종료2Unit 인수 |
| SQL·마이그레이션 | 추가하지 않음 | Redis에 업무 상태를 두지 않으므로 불필요; HTTP/DB 보호 검증은 US3 지정인수 |
| 고정 계약·결정 기록 | [OpenAPI 초안](../../docs/openapi.yaml)·[결정 기록](../../docs/decisions.md) | 고정 계약 대조·실제 HTTP 인수 완료; 업무전체 자동 OpenAPI 대조는9단계 후속 |

계약·업무 변경은 계획서·관련 도메인·공통 규칙·OpenAPI·테스트를 함께 검토한다. 서비스 경계·키·수량·권한·실패 무변경은 관련 도메인에 함께 영향을 준다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 실제장애·503분류 | US3 지정인수·독립검토 완료 | 최종 오케스트레이터 검토 |
| 기존 HTTP/110회·최종 기능/빌드/의존성 게이트 | 실행 인수 완료 | 최종 오케스트레이터 검토 |
| Qodana T037 미실행 | 사용자2026-10-22 지시로 이번 완료·PR·병합 게이트 제외 | 미완료 후속으로 유지; 통과로 기록하지 않음 |

기존 규칙 이외의 새 정책을 추가하지 않았다.


### US3 장애 경계와 카운트 불확실성

필터는limiter.check 호출만catch하며확인된전송/명령timeout·실제WRONGTYPE·정형OOM 원인만503으로변환한다. 직렬화·미확인명령/스크립트·원인순환·내부결함은전파하며인터럽트를복원한다. 실제timeout 뒤queuedLua의실행은불확실하며복구뒤count0과1을모두관측했다. DB전체snapshot은거부요청으로변하지않고복구후쓰기201을확인했다. DB실패로카운트는보상하지않고성공한생성키는Redis기본60초만료와무관하게재현된다. 정리/전송불확실은첫StopState와모든오류를보존해다음reset·준비·제출을차단한다. [실행근거](../../docs/test-evidence.md)를따른다.


### 2026-10-22 US4 회귀·최종 인수

기존 HTTP는 기본20/60초·실제 Redis를 사용하고 사례별 ADMIN/A/B 소유키만 초기화한다. 경쟁은 별도10000/60초로110회 모두 실제 HTTP·Redis·DB를 통과하며 최종 단일SQL 수량 snapshot과 종료를 확인했다. 부모 setup 첫 보호검사 전에는 Redis/DB를 접근하지 않으며 종료/전송불확실 fixture에서 reset0을 검증했다. 취소 입력 검증은 SecurityIT, 순수 대상 API는 postgresOnly ResourceApiIT에 둔다. 업무 SQL/스키마·성공/소유권/취소·OpenAPI 계약은 유지한다. [증거](../../docs/test-evidence.md)·[110회 기록](../../docs/concurrency-runs.json)을 따른다.


### 9단계 US2 의존성 계약 연결 (2026-10-23)

[의존성 계약 시험](../../backend/src/test/java/com/limitedbooking/backend/common/ApiContractDependencyIT.java)은 별도 소유 DB/Redis 중단의5API/두쓰기503과 정상DB에서의업무전체행무변경·독립3API정상을 고정명세와 대조한다. 중단DB의사후snapshot과 기존정상DB의timeout롤백증거를 구분한다. 제품·SQL·정책은 유지하며 [T018~T020 실행증거](../../docs/test-evidence.md)를 따른다. T021이후/전체9단계인수·Qodana는미완료다.
