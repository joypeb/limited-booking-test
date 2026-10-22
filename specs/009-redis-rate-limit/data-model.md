# 데이터·판정 모델

이 문서는 설계이며 아직 신규 구현·실행 증거는 없다. 기준은 [명세](spec.md)와 [계약](contracts/rate-limit-contract.md)이다.

## 사용자별 쓰기 창

| 값 | 타입/소유자 | 규칙 |
| --- | --- | --- |
| userId | 인증 Principal UUID | ADMIN/USER_A/USER_B 모두 동일 정책; 입력 헤더/body에서 받지 않음 |
| key | Redis 문자열 | `reservation:write-limit:` + UUID 표준 문자열. `{userId}`는 문서의 치환 자리이며 UUID 주위 중괄호를 추가하지 않음 |
| count | Redis 정수 문자열 | 대상 진입마다 INCR, 초과 요청 포함; DB 실패로 감소하지 않음 |
| lifetime | Redis PTTL 밀리초 | count=1일 때만 PEXPIRE60000; 후속 허용/거부로 연장하지 않음 |

없음→첫 요청 count1+TTL→후속 count증가→TTL만료로 없음. 앱 Clock으로 창을 계산하지 않는다. count와 TTL은 동일 Lua 실행 결과에서 함께 얻는다. 성공 예약 키와 Redis 창은 관계·외래키·동기 삭제가 없다.

## 설정·판정

`RateLimitProperties`는 `reservation.rate-limit.limit=20`, `window=60000ms`를 기본으로 하며 둘 다 양수·window 밀리초 표현 가능 여부를 시작 시 검증한다. Redis 명령 timeout은 `spring.data.redis.timeout=200ms`, 연결 timeout도200ms로 명시하여 첫 접속 장애 시험의 대기를 제한한다. 이는 기존 명령 timeout 정책을 바꾸지 않는다. 모든 프로필/IT에 실제 적용값을 검사한다. 비활성화 설정은 제공하지 않는다.

`RateLimitDecision(boolean allowed, long retryAfterSeconds)`는 `count<=limit`이면 `(true,0)`, 초과면 `(false,max(1,ceil(pttl/1000)))`이다. TTL0도 최소1초다. 1/999/1000/1001/60000ms 경계를 순수 Unit으로 검증한다. 올림은 정수 나눗셈/나머지로 계산해 부동소수점과 덧셈 overflow를 피한다.

Lua 반환은 `[count,pttl]` 두 정수다. null·원소수/타입 오류·count<1·pttl<0는 정상판정으로 만들지 않고 내부 불변식 오류로 탐지한다. -1(영구 키)·-2(없는 키)를 임의 TTL로 대체하지 않는다. 잘못된 Redis 값/스크립트 오류는 의존성 실패와 구현 결함을 research의 분류대로 구분한다.

## 보호할 업무 상태

resources/reservations V1, 사용자·생성키 유일성, UTC 시각, 상태 전이 CONFIRMED→CANCELLED, resource→reservation 잠금 순서와 수량 보존식을 그대로 사용한다. 마이그레이션 없음. 제한 거부는 전체 행/상태/시각/키/수량 snapshot이 동일해야 한다. 여러 대상 수량은 기존 DatabaseAssertions의 단일 SELECT snapshot으로 검증한다.

## 테스트 관측 모델

요청별 고유 식별자에 limiter 시작/완료·결정, Controller/Service 진입, TX 시작/SQL 실행 사건을 연결한다. 토큰·비밀번호·실제 개인정보는 기록하지 않는다. limiter 호출 시 트랜잭션 비활성, 허용완료 뒤에만 업무 TX 시작, 거부 시 Controller/Service/TX 진입0을 검증한다. 실제 Bean spy/테스트 전용 transaction manager decorator 등으로 관측하며 제품 장애후크를 추가하지 않는다.

병행 시험은 각 Future 결과/오류, 상한, executor·HTTP·서버 in-flight 종료와 최초 실패를 보존한다. 종료/전송 실패 상태가 있으면 다음 reset·준비·제출을 막는다. Redis 키 초기화도 같은 보호 대상이다.
