# Data Model: 독립 모델과 명령

모두 테스트 전용 타입이다(`backend/src/test/.../support/`). 제품 클래스(`ReservationService`, `ReservationStatus` 포함)와 SQL을 참조하지 않는다. 사용자·대상은 테스트 전용 enum 또는 상수로 표현한다.

## 식별자

| 이름 | 값 | 비고 |
|---|---|---|
| `User` | `A`, `B` | 실행기가 `TestData.USER_A_ID/USER_B_ID`로 대응 |
| `Target` | `R1`, `R2`, `MISSING` | `MISSING`은 생성하지 않은 무작위 UUID(대상 없음 경로) |
| `Key` | `K1`, `K2`, `K3` | 유효 생성 키 문자열 |
| 논리 참조 `ref` | 0~9 정수 | “지금까지 실행된 생성 명령 중 ref번째” |

## 명령 `ReservationCommand` (sealed)

- `Create(User user, Target target, Key key)`
- `Get(User user, int ref)`
- `Cancel(User user, int ref)`

`toString()`은 `Create(A,R1,K1)` 형태로 축소 결과를 읽을 수 있게 한다.

## 시나리오

`Scenario(int capacityR1, int capacityR2, List<ReservationCommand> commands)` — capacity 0~5, 명령 1~30건.

## 결과 `CommandOutcome`

| 종류 | 필드 |
|---|---|
| `CREATED` | 논리 예약 id, target |
| `REPLAYED` | 논리 예약 id, target |
| `SOLD_OUT` | — |
| `IDEMPOTENCY_CONFLICT` | — |
| `RESOURCE_NOT_FOUND` | — |
| `RESERVATION_NOT_FOUND` | — |
| `VIEWED` | 논리 예약 id, status(CONFIRMED/CANCELLED) |
| `CANCELLED` | 논리 예약 id, firstCancel(boolean) |

모델은 시각을 모른다. 실행기는 실제 값으로 아래 관계를 별도 검사한다.

- `REPLAYED`의 실제 id·createdAt = 같은 논리 예약 `CREATED` 때의 실제 값, 실제 status = CONFIRMED, replayed = true. `CREATED`의 replayed = false.
- `VIEWED`의 createdAt = 최초 값. status=CONFIRMED이면 cancelledAt null, CANCELLED이면 최초 취소 시각.
- `CANCELLED` 반복의 cancelledAt = 최초 취소 시각.

## 모델 상태 `ReferenceReservationModel`

- `Map<Target, Integer> capacity, available` (R1, R2)
- `Map<(User,Key), Integer> keyIndex` → 논리 예약 id
- `List<Logical> reservations`: `Logical(User owner, Target target, boolean cancelled)`
- `List<Integer /*nullable*/> createRefs`: 생성 명령 순번 → 성공(CREATED/REPLAYED) 시 논리 id, 실패 시 null

## 전이 규칙 (계획서 2절·5절)

1. `Create(u,t,k)`: (u,k)가 있으면 기존 예약의 target이 t면 `REPLAYED`, 아니면 `IDEMPOTENCY_CONFLICT`(상태 변화 없음). 없으면 t=`MISSING`이면 `RESOURCE_NOT_FOUND`, available[t]>0이면 차감·새 예약·키 기록 후 `CREATED`, 아니면 `SOLD_OUT`. 실패 결과는 키를 기록하지 않는다. 결과와 관계없이 `createRefs`에 항목을 추가한다.
2. `Get(u,ref)`: ref가 범위 밖이거나 null이거나 owner≠u이면 `RESERVATION_NOT_FOUND`, 아니면 `VIEWED`.
3. `Cancel(u,ref)`: 같은 해석으로 없으면 `RESERVATION_NOT_FOUND`. 확정이면 cancelled=true, available[target]+1, `CANCELLED(first=true)`. 이미 취소면 `CANCELLED(first=false)`, 변화 없음.

## 최종 비교

- 대상별 available, CONFIRMED 수, `available + CONFIRMED = capacity`
- 전체 예약 행 수 = 논리 예약 수, (user,key) 중복 그룹 0
- 논리 예약별 실제 DB status = 모델 status, resource/user 일치
