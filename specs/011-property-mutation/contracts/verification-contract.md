# 검증 계약: 속성·변이·고의 변경

이 기능은 외부 API를 추가하지 않는다. 아래는 Gradle 실행 계약과 판정 규칙이다. 명령은 `backend/`에서 실행한다.

## C1. 실행 그룹

| 명령 | 포함 | 판정 |
|---|---|---|
| `./gradlew test` | `*Test`, contract/property 제외 (+ 신규 `ReferenceReservationModelTest`) | 결과 게이트 실행≥1·실패0·skip0 |
| `./gradlew integrationTest` | `*IT`, contract/property 제외 (+ 신규 `ReservationPropertyRegressionIT`) | 동일 |
| `./gradlew propertyTest` | `*Test`·`*IT` 중 `property` 태그 (`ToolchainPropertyTest`, `ReservationPropertyIT`) | 동일 + 출력에 `seed=`·`attempts=` |
| `./gradlew verifyProperty` | verify + propertyTest | 동일 |
| `./gradlew mutationSmoke` | pitest(세 규칙/세 Unit) + 보고서 게이트 | C3 |
| `./gradlew deepTest` | verify + contractTest + propertyTest | 동일 |

## C2. 속성 실행 출력

- 시작: `reservation-property seed=<seed> examples=<budget>`
- 종료: `reservation-property attempts=<실제 실행 수> seed=<seed> scenario-digest=<생성 시나리오 표현 연결의 SHA-256>`; `attempts >= 100` assert. 같은 seed 두 실행의 digest가 같아야 한다(SC-001).
- 실패: QuickTheories 보고(축소된 최소 값·seed)와 함께, 명령별 모델 기대/실제 결과의 첫 불일치 index를 assertion 메시지에 포함

## C3. 변이 게이트(`mutationSmoke`)

- `reports/pitest/mutations.xml`·`index.html`이 이번 실행에서 생성됨(시작 시 삭제 유지)
- generated ≥ 1, killed ≥ 1, `KILLED`/`SURVIVED` 외 상태 0
- 모든 `mutatedClass` ∈ {`com.limitedbooking.backend.resource.ResourceRules`, `com.limitedbooking.backend.reservation.IdempotencyKey`, `com.limitedbooking.backend.reservation.CancellationDecision`}
- killed / generated ≥ 0.80
- lifecycle 출력: `PIT generated=…, killed=…, survived=…, invalid=…, ratio=…`
- 게이트 자체 검증: 판정 기준을 일시적으로 위반시키는 최소 실행(예: 임계값을 1.01로 임시 변경) 1회로 실패 종료를 확인하고 원복한다(커밋 금지).

## C4. 고의 변경 절차

| ID | 변경 | 실행 | 기대 |
|---|---|---|---|
| A | `ResourceRepository.decrementIfAvailable`의 `AND available > 0` 제거 | `integrationTest`, `propertyTest` | 실패 ≥1 |
| B | `ReservationRepository.markCancelled`의 `AND status = 'CONFIRMED'` 제거 | `integrationTest` (경쟁 포함), `propertyTest` | integrationTest 실패 ≥1, 속성 미탐지는 R4 사유로 기록 |
| C | `ReservationRepository.findByIdAndUser`의 `AND user_id = :userId` 제거 | `integrationTest`, `propertyTest` | 실패 ≥1 |
| D | `ReservationService.replay`의 resourceId 비교 제거(항상 재현) | `test`, `integrationTest`, `propertyTest` | 실패 ≥1, 속성 실패 seed·축소 순서 기록 |
| E | `CancellationDecision.from`의 매핑 교체(CONFIRMED→ALREADY_CANCELLED, CANCELLED→CANCEL_NOW) | `test`, `integrationTest`, `propertyTest` | 실패 ≥1. PIT 연산자로 만들 수 없는 enum 매핑 변이의 보완(2026-10-23 T018 분석 추가) |

각 ID: 적용 → 실행(종료 코드·실패 테스트 이름 기록) → 원복 → `git diff --exit-code -- backend/src/main` 종료 0. 한 번에 하나만 적용한다. 고의 변경 상태는 커밋하지 않는다.

## C5. 회귀 사례

D의 축소 최소 순서를 `ReservationPropertyRegressionIT`에 그대로 고정한다. D 적용 상태에서 실패, 원복 상태에서 통과를 각각 실행으로 확인한다.
