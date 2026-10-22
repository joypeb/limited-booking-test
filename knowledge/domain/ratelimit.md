# ratelimit 도메인 지식

## 1. 기준과 현재 상태

- 구현 패키지: `com.limitedbooking.backend.ratelimit`. 최초 작성:2026-10-13, US1 구현·검증:2026-10-22.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절, [API 규칙](../rules/api-and-security.md), [트랜잭션 규칙](../rules/domain-and-transactions.md), [요청 제한 규칙](../rules/rate-limit.md).
- 설계는 기존 확정 규칙이다. US1 판정/설정/RedisRateLimiter·Lua와 순수 Unit13/실제Redis IT5를 구현·검증했다. HTTP 필터·실제 장애·기존 회귀·최종 게이트는 미완료다. US1 독립 중간검토를 위해 멈춘 상태이며 전체 완료가 아니다.

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
| ratelimit-001 | 20회·21번째·TTL·만료·동시40 | 허용20·이후거부·TTL 비연장·새 창1회 | RedisRateLimitIT5 실제 검증; HTTP429는 후속 |
| ratelimit-002 | 재요청·입력 오류·사용자 격리 | 포함·사용자별 독립 | 후속 HTTP 미실행 |
| ratelimit-003 | Redis 중단·DB 실패 | 쓰기 503/무변경·DB 실패 카운트 유지, 조회/대상 생성은 정상 DB에서 성공 | 후속 장애 IT 미실행 |

실제 검토·실행 기록은 [증거 문서](../../docs/test-evidence.md)에서 구분한다. US1 외 후속 HTTP/장애 테스트는 아직 존재하거나 실행된 것으로 간주하지 않는다. [009 작업 상태](../../specs/009-redis-rate-limit/tasks.md)를 따른다.

## 8. 구현 연결과 변경 영향

| 책임 | 실제 파일·예정 위치 | 상태 |
| --- | --- | --- |
| 앱 골격 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java) | 존재, 해당 기능 없음 |
| 순수 판정·설정 | [RateLimitDecision](../../backend/src/main/java/com/limitedbooking/backend/ratelimit/RateLimitDecision.java)·[RateLimitProperties](../../backend/src/main/java/com/limitedbooking/backend/ratelimit/RateLimitProperties.java) | Unit13·시작 설정 검증 완료 |
| 원자 집계 | [RedisRateLimiter](../../backend/src/main/java/com/limitedbooking/backend/ratelimit/RedisRateLimiter.java)·[Lua](../../backend/src/main/resources/redis/rate-limit.lua) | 실제 Redis IT5 검증 완료 |
| 실제 인수·키 지원 | [RedisRateLimitIT](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RedisRateLimitIT.java)·[RateLimitTestSupport](../../backend/src/test/java/com/limitedbooking/backend/support/RateLimitTestSupport.java) | 소유 키만 reset/count/PTTL, 기존 ConcurrencyTestSupport 재사용 |
| 필터·HTTP·장애 | 승인된009 후속 파일 예정 | 미구현 |
| SQL·마이그레이션 | 추가하지 않음 | Redis에 업무 상태를 두지 않으므로 불필요; HTTP/DB 보호 검증은 후속 |
| 고정 계약·결정 기록 | [OpenAPI 초안](../../docs/openapi.yaml)·[결정 기록](../../docs/decisions.md) | 작성·문서 인수됨, 이름 strip 정책 확정·T025 사용자 확인 완료; 독립 재설명/기능 실행 미검증 |

계약·업무 변경은 계획서·관련 도메인·공통 규칙·OpenAPI·테스트를 함께 검토한다. 서비스 경계·키·수량·권한·실패 무변경은 관련 도메인에 함께 영향을 준다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| HTTP 필터·장애 미구현 | 아직 API에 제한이 적용되지 않음 | US1 중간검토 후 US2 실제 HTTP RED |
| 기존 HTTP/110회·최종 기능/빌드/의존성 게이트 미완료 | 전체 완료 선언 불가 | 후속 지시 후 지정 인수 |
| Qodana T037 미실행 | 사용자2026-10-22 지시로 이번 완료·PR·병합 게이트 제외 | 미완료 후속으로 유지; 통과로 기록하지 않음 |

기존 규칙 이외의 새 정책을 추가하지 않았다.
