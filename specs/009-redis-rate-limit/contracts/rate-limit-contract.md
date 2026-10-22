# 요청 제한 인터페이스·HTTP 계약

설계 계약을 구현·실행 인수했다. 현재37개 작업 완료·Qodana T037 사용자후속이며 실제 수와 범위는 [실행 근거](../../../docs/test-evidence.md)를 따른다. 고정 [OpenAPI3.0.3](../../../docs/openapi.yaml)와 [명세 FR001~015](../spec.md)를 유지한다. 신규 공개 API는 없다.

## Java/Redis 경계

`RedisRateLimiter.check(UUID userId): RateLimitDecision`는 인증 UUID 하나를 받으며 업무 트랜잭션을 열지 않는다. `RateLimitDecision(boolean allowed,long retryAfterSeconds)`의 허용 결과는 true/0, 거부 결과는 false/양의 정수다. 연결·명령 timeout의 명확한 의존성 실패는 별도 실패 경로로 보내며 허용 판정을 만들지 않는다.

Lua KEYS[1]은 사용자 제한 키, ARGV[1]은 양수 창 밀리초. 한 실행의 순서는 INCR→첫1일 때 PEXPIRE→PTTL이며 두 정수 count/pttl을 반환한다. 이후 요청은 TTL을 연장하지 않는다. TTL 음수·정수 형식 위반은 내부 결함이다. 앱에서 DB 실패 보상을 위해 DECR하거나 키를 지우지 않는다.

## HTTP 선택과 처리 순서

| 요청 | 제한 동작 |
| --- | --- |
| 인증된 POST /api/reservations | 입력·품절·키충돌·재요청을 포함해1회 |
| 인증된 POST /api/reservations/{id}/cancel | 잘못된 UUID·본문·타인/없는 예약·중복취소 포함1회 |
| 위 MVC 경로의 기존 허용 인코딩 표현 | 동일1회; `%63ancel`, `%72eservations` 회귀 |
| 미인증/잘못된 비밀번호·8KiB 이하 | 401+WWW-Authenticate, Redis호출0 |
| 실제 본문8193byte 이상(일반/길이없음/chunked) | 인증 앞413, Redis호출0 |
| GET 및 POST /api/resources | 제한Redis호출0; 기존 인증/권한/입력/DB 계약 |

8192byte는 크기 필터를 통과한 뒤 인증→제한→입력검증을 따른다. 유효 인증의 기본한도 초과 요청은 취소 본문/UUID 등400이나 생성415보다429가 앞선다. 제한 적용은 normal REQUEST당 한 번이며 이중서블릿등록·ERROR dispatch로 카운트가 늘지 않아야 한다. 경로 매칭은 MVC의 parsed path 규칙과 동일하게 한다. StrictHttpFirewall이 원래 거부하는 경로를 새로 허용하지 않는다.

## 응답 계약

| 조건 | HTTP/code·헤더 | 업무 처리 |
| --- | --- | --- |
| count1~20 | 기존 성공/실패 응답 계약 | 후속검증·서비스로 진행 |
| count21이상 | 429 RATE_LIMITED, Retry-After=max(1,ceil(pttl/1000)) | Controller/Service/TX0 |
| 확인된 Redis 연결/명령timeout/WRONGTYPE/정형OOM 의존성 실행 실패 | 503 DEPENDENCY_UNAVAILABLE | Controller/Service/TX0 |
| 분류되지 않은 내부결함·스크립트/직렬화 결함 | 500 탐지 | 성공/429/503으로 은폐금지 |

429/503은 공통 ApiExceptionHandler.writeProblem으로 application/problem+json 및 type/title/status/detail/instance/code를 제공한다. 민감정보·SQL·stack을 노출하지 않는다.429 이외에 Retry-After를 새 계약으로 추가하지 않는다. 성공201 Location·Idempotent-Replayed·최초 CONFIRMED 재현, GET현재 CANCELLED, 중복취소 동일 cancelledAt, 소유자404는 기존대로다.

Redis 장애 중 GET resource200·GET 소유예약200·ADMIN POST resource201을 실제 정상PG로 확인한다. 허용 뒤 DB503이면 Redis횟수는 유지하고 DB행/수량은 전체롤백한다. 복구 후 같은 키는 새 집계 뒤201이 가능하다. timeout 뒤 Redis수행여부는 불확실할 수 있고 카운트정확복구·분산원자커밋을 약속하지 않는다.

## 인수와 종료 계약

기본 정책 시험은20/60000ms·200ms timeout. 만료 전용만3/1000ms를 사용하고 값/이유를 기록한다. 기본 동시40건은 정확20 허용·20거부이며 준비와 기존 소유예약은 직접 커밋해 카운터를 오염시키지 않는다. limiter 완료 시점의 PTTL과 나중 PTTL의 경과를 구분한다.

관련 기존 HTTP 원래 기대값 유지, 경쟁10000/60000ms·각회차키초기화·C01~C11 각10회·기존종료fixture 보존. Future 오류/timeout·서버/클라이언트/executor 종료를 모두 확인한 뒤 단일SELECT snapshot을 검사한다. 종료 실패·전송결과 불확실이면 다음 Redis/DB reset·준비·제출0이며 최초 오류와 다른 모든 결과를 보존한다.

필수 완료 증거는 지정IT/Unit·일반verify·`./gradlew build`·기존 `./gradlew verifyContractDependencies` 실제 결과, 발견수/실패/skip·환경/설정·장애조건·정리·미검증의 docs 기록이다. 이 계약 문서 자체는 실행증거가 아니다.

최종 Gradle 게이트는 `./gradlew build verify verifyContractDependencies`로 합쳐 공유 test를 한 번 실행할 수 있다. 실제 명령·종료 코드와 빌드/의존성 검사 결과를 docs/test-evidence.md에 기록한다. 어느 필수 게이트든 미실행·실패이면 전체 완료로 처리하지 않는다. 사용자2026-10-22 “나중에 할거야” 지시로 Qodana T037은 미완료 후속이며 이번 완료·PR·병합 게이트에서 제외한다. 미실행을 통과로 기록하지 않는다.
