# 5분 시연과 작은 변경 연습

AI의 도움 없이 사용자가 직접 설명·시연·수정할 수 있는지 확인하는 자료다. 시연과 작은 변경은 **사용자 수행 대기** 상태이며 이 문서의 어떤 항목도 수행한 것으로 표시하지 않았다. 규칙의 근거는 [추적표](traceability.md)와 [계획서](<../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절이다.

## 시작 전 준비(시연 전, 시간 외)

Gradle 실행은 컴파일과 컨테이너 기동 때문에 5분 안에 네 번 하기 어렵다. **시연 직전에 네 필터를 한 번에 실행해 두고**, 시연 중에는 그 HTML 보고서와 README curl 흐름을 보여주는 방식을 기본으로 한다.

- 저장소 루트에서 `docker compose up -d --wait --wait-timeout 120`을 실행하고 `docker compose ps`로 healthy를 확인한다(curl 흐름용). 환경 변수는 [README Quick Start](../README.md#quick-start) 2단계처럼 export한다.
- `backend/`에서 네 시연 필터를 한 번에 실행한다. 2026-10-26 로컬 측정은 1분 40초·32건 통과였고 이 중 `RedisFailureIT` 클래스만 약 70초였다. 실행 시간은 기기에 따라 다르다.

```sh
./gradlew integrationTest --tests '*ReservationConcurrencyIT.C01*' --tests '*IdempotencyIT' --tests '*ReservationOwnershipIT' --tests '*RedisFailureIT'
```

- 결과 HTML 보고서: `backend/build/reports/tests/integrationTest/index.html` (클래스별로 열어 케이스와 소요 시간을 보여준다).
- 시연 중 한 주제만 다시 실행해야 하면 아래 각 주제의 명령을 쓴다. 개별 실행 시간은 `IdempotencyIT` 2.6초, `ReservationOwnershipIT` 3.2초, `ReservationConcurrencyIT.C01*` 13.6초(테스트 본문 시간, Gradle·컨테이너 기동 제외)이고 `RedisFailureIT.stopsAnAlreadyConnectedOwnedRedis` 단독 명령은 2026-10-26 로컬에서 Gradle 포함 8초·1건이다.

| 구간 | 시간 | 방식 |
|---|---|---|
| 1. 마지막 수량의 경쟁 | 1분 15초 | 보고서(C01) + 설명 |
| 2. 같은 키 재시도 | 1분 15초 | README curl 2·3·8단계 + 보고서 |
| 3. 타인의 예약 취소 | 1분 | README curl 5단계 + 보고서 |
| 4. Redis 중단 | 1분 15초 | 보고서(`RedisFailureIT`) + 정책 설명 |
| 정리 | 15초 | 질문 대응 |

## 5분 시연 대본

### 1. 마지막 수량의 경쟁

- **보여줄 것**(기본: 보고서의 `ReservationConcurrencyIT` C01 결과, 필요 시 아래 명령 단독 실행): 수량 10개에 서로 다른 키의 100개 요청이 동시에 몰려도 정확히 10건만 성공하고 수량이 음수가 되지 않는다.

```sh
./gradlew integrationTest --tests '*ReservationConcurrencyIT.C01*'
```

- **관찰 포인트**: 201이 10건, 409 `SOLD_OUT`이 90건이고 마지막 `available`은 0이다. 각 회차 끝에 단일 SELECT로 `available + CONFIRMED 수 = capacity`와 `0 ≤ available ≤ capacity`를 확인한다(10회 반복). 마지막 1개에 같은 키 10건이 몰리면 1건 성공과 9건 재현이며 `'*ReservationConcurrencyIT.C02*'`로, 갱신 0건 뒤 승자를 새로 조회하는 경로는 `'*ReservationConcurrencyIT.C07*'`로 볼 수 있다.
- **설명**: 차감은 `UPDATE … WHERE available > 0` 한 문장이라 READ COMMITTED에서도 동시 요청이 행 잠금에서 순서대로 처리된다. 갱신이 0건이면 새 SELECT로 재요청·충돌·품절을 구분한다. 같은 사용자·키의 중복 INSERT는 유일 제약 `uq_reservations_user_key`가 막고, 이때 Writer 전체를 롤백해 차감도 되돌린다.

### 2. 같은 키 재시도

- **보여줄 것**(기본: README curl 흐름, 보고서의 `IdempotencyIT`): 같은 `Idempotency-Key`와 대상으로 다시 보내면 같은 응답이 재현되고, 다른 대상이면 409다.

```sh
./gradlew integrationTest --tests '*IdempotencyIT'
```

HTTP로 직접 보려면 README Quick Start 7단계의 2·3·8단계(최초, 재전송, 취소 후 재전송)를 실행한다.

- **관찰 포인트**: 최초는 `Idempotent-Replayed: false`, 재전송은 `true`이고 id·createdAt이 같다. 취소한 뒤 재전송해도 최초 `CONFIRMED` 응답이 나오지만 GET은 `CANCELLED`다. 다른 대상이면 409 `IDEMPOTENCY_CONFLICT`다.
- **설명**: 성공한 예약 행이 곧 키 기록이라 별도 키 테이블이나 TTL이 없다. 품절·입력 오류·503 같은 실패한 요청의 키는 남기지 않으므로 같은 키로 안전하게 재시도할 수 있다. 시각은 DB에 저장된 마이크로초 값을 그대로 돌려줘 최초와 재전송이 일치한다.

### 3. 타인의 예약 취소

- **보여줄 것**(기본: README curl 5단계, 보고서의 `ReservationOwnershipIT`): 다른 사용자(관리자 포함)는 남의 예약을 조회·취소할 수 없고 존재 여부도 알 수 없다.

```sh
./gradlew integrationTest --tests '*ReservationOwnershipIT'
```

HTTP로 직접 보려면 README Quick Start 7단계의 5단계(USER_B가 USER_A의 예약을 GET)를 실행한다.

- **관찰 포인트**: 타인의 GET·취소 모두 404 `NOT_FOUND`이고 없는 id와 응답이 구분되지 않는다. 취소 시도 뒤에도 예약은 `CONFIRMED`이고 수량은 그대로다.
- **설명**: 사용자 UUID는 요청 본문이나 `X-User-Id` 헤더가 아니라 인증 결과에서만 얻는다. 조회와 취소 SQL이 `id`와 소유자 조건을 함께 걸어, 소유자가 아니면 "행 없음"과 같은 결과가 나온다. 취소는 대상 행을 먼저 `FOR UPDATE`로 잠그고(잠금 순서 `resource → reservation`) 상태가 `CONFIRMED`일 때만 갱신해 수량을 최대 1개 복구한다.

### 4. Redis 중단

- **보여줄 것**(기본: 보고서의 `RedisFailureIT`): Redis가 응답하지 않으면 쓰기는 503으로 거부되고 DB는 바뀌지 않으며, 조회는 계속된다.

```sh
./gradlew integrationTest --tests '*RedisFailureIT'
```

시연 중 단독 실행이 필요하면 실제 `@Test`인 단일 메서드를 쓴다(2026-10-26 로컬 Gradle 포함 8초·1건). 실행 중인 Redis 컨테이너를 정지해 쓰기 503과 독립 API 정상을 확인한다.

```sh
./gradlew integrationTest --tests '*RedisFailureIT.stopsAnAlreadyConnectedOwnedRedis'
```

- **관찰 포인트**: 쓰기(생성·취소)는 503이고 예약·수량이 그대로다. 대상 생성·조회 같은 독립 API는 정상이다. 제한 정책 자체(20회/60초, 초과 429와 `Retry-After`)는 `'*RateLimitApiIT'`와 `'*RedisRateLimitIT'`가 다룬다.
- **설명**: 요청 제한은 인증 뒤, 입력 검증과 DB 트랜잭션 앞에서 Redis Lua(INCR·최초 PEXPIRE·PTTL)로 원자적으로 판정하는 고정 창이다. 요청마다 TTL을 늘리지 않으므로 창은 최초 요청에서 60초 뒤 끝난다. Redis는 요청 제한에만 쓰고 수량의 원본이 아니므로, 장애 시 안전하게 쓰기를 막아도 정합성에는 영향이 없다. Redis 명령 timeout은 200ms이고 DB 실패로 카운터를 되돌리지 않는다.

## 작은 변경 연습 안내

예시 과제: **생성 키 최대 길이를 64자에서 32자로 줄인다.** 이 문서는 안내만 하며 실제로 수행하지 않았다. 사용자가 AI 없이 직접 수행한다.

바꿔야 할 지점:

| 구분 | 위치 | 확인할 것 |
|---|---|---|
| 설계 문구 | 계획서 2절·4절의 키 형식, 3절 스키마의 `idempotency_key varchar(64)` | `{1,64}`와 길이 설명 |
| 규칙 클래스 | `backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java`의 `VALID_KEY` | 정규식 `{1,32}` |
| 계약 | `docs/openapi.yaml`의 `Idempotency-Key` 파라미터 `maxLength`와 `pattern` | 두 값 모두 변경 |
| 단위 테스트 | `IdempotencyKeyTest`의 경계값(`"A".repeat(64)`)과 거부 사례 | 32자 통과·33자 거부 |
| 통합 테스트 | `ReservationCreateIT#rejectsInvalidKeysWithoutNormalizingAndAcceptsLengthBoundaries` | 경계가 32/33이어야 함 |
| 계약 테스트 | `ApiContractIT`가 OpenAPI와 응답을 대조 | 키 길이 관련 사례 |
| 스키마 | `V1__create_reservation_schema.sql`의 `varchar(64)` | 적용된 V1은 편집하지 않는다. 컬럼은 64로 두어도 규칙이 지켜지며, 줄이려면 새 마이그레이션이 필요하고 기존 데이터를 먼저 확인한다 |
| 기타 | `load/lib.js`의 키 형식 검사, `knowledge/domain/reservation.md`·`knowledge/rules/domain-and-transactions.md`, `AGENTS.md`, README의 키 설명 | 문서와 스크립트의 64 표기 |

권장 순서:

1. 규칙을 정한다(32자, 기존 키와의 호환 여부).
2. `IdempotencyKeyTest`와 IT 경계 사례를 먼저 고쳐 의도한 대로 **실패(RED)** 하는지 확인한다.
3. `IdempotencyKey`를 고쳐 통과(GREEN)시킨다.
4. `docs/openapi.yaml`과 `ApiContractIT`를 맞춘다.
5. 계획서·knowledge·AGENTS.md·README 문구를 함께 갱신한다.

완료 판정 명령(`backend/`):

```sh
./gradlew test --tests '*IdempotencyKeyTest'
./gradlew integrationTest --tests '*ReservationCreateIT' --tests '*IdempotencyIT'
./gradlew contractTest --tests '*ApiContractIT'
./gradlew verifyContract
```

저장소 루트에서는 `scripts/check-traceability.sh`와 `scripts/check-readme.sh`로 문서 참조가 깨지지 않았는지 확인한다.

## 수행 기록(사용자 작성)

AI 없이 사용자가 수행한 뒤 작성한다. 비어 있으면 아직 수행하지 않은 것이다.

| 날짜 | 항목 | 소요 시간 | 막힌 부분 | 결과 |
|---|---|---|---|---|
| | 5분 시연 1. 마지막 수량의 경쟁 | | | |
| | 5분 시연 2. 같은 키 재시도 | | | |
| | 5분 시연 3. 타인의 예약 취소 | | | |
| | 5분 시연 4. Redis 중단 | | | |
| | 작은 변경(키 길이 64→32) | | | |
| | AI 없는 설명 | | | |
