# Tasks: 명령 순서 속성 검증과 변이 탐지력 확인

**입력**: [명세](spec.md), [계획](plan.md), [조사](research.md), [데이터 모델](data-model.md), [검증 계약](contracts/verification-contract.md), [실행 안내](quickstart.md)
**상태**: 2026-10-23 T001~T029 구현·검증 완료(T019·T026은 생존 변이/미탐지 0으로 해당 없음). T030 이후 리뷰·Converge·PR 진행. 실행 근거는 docs/test-evidence.md 10단계 절.

## 형식과 공통 실행 계약

`- [ ] T번호 [P 선택] [US번호] (담당) 설명·경로 — 선행: …`. 경로는 저장소 루트 기준이며 Java 경로의 공통 접두 `backend/src/test/java/com/limitedbooking/backend/`는 `T/`, `backend/src/main/java/com/limitedbooking/backend/`는 `M/`로 줄인다.

- 담당: **orch**(orchestrator: 실행 검증·문서·Git), **scaf**(scaffolder), **ta**(test-author, `src/test`만), **impl**(implementer, `src/main`과 이 문서가 명시한 `backend/build.gradle`만), **rev**(reviewer, 수정 금지), **adv**(advisor).
- 단계: RED(의도한 assertion 실패 확인) → GREEN(최소 구현 후 같은 시험 통과) → REFACTOR(동작 불변 정리·재실행) → VERIFY(orch 독립 실행·기록) → REVIEW(rev).
- 컴파일 오류·컨테이너 기동 실패는 RED로 세지 않는다. 실패를 skip·기대값 변경·반복 실행으로 숨기지 않는다. 같은 문제 3회 반복 시 adv 분석.
- 제품 코드·SQL·API·Flyway·Wrapper·의존성 버전은 변경하지 않는다. 예외: US4·US2의 고의 변경(커밋 금지, 즉시 원복), 검증 중 실제 결함 발견 시 orch 판단 후 별도 작업.
- 같은 파일을 두 에이전트가 동시에 수정하지 않는다. `[P]`는 서로 다른 파일·선행 완료 시에만 병행한다.
- RED/GREEN/고의 변경의 명령·종료 코드·실패 테스트·seed는 orch가 `docs/test-evidence.md`에 순차 기록한다.

## Phase 1: 준비

- [X] T001 (orch) 저장소 루트·브랜치 `feature/011-property-mutation`·HEAD(c71afaa)·작업 트리를 확인하고 `cd backend && ./gradlew test propertyTest mutationSmoke` 기준선(현재 수·PIT 프로브 결과)을 실행해 `docs/test-evidence.md`에 10단계 기준선으로 기록한다. — 선행: 없음
- [X] T002 (impl) `backend/build.gradle`의 `propertyTest`에 `include '**/*IT.class'`를 추가한다(`include '**/*Test.class'`·`includeTags 'property'` 유지, 다른 태스크 무변경). 검증: `./gradlew propertyTest`가 기존 `ToolchainPropertyTest`만 발견해 통과하고 `./gradlew integrationTest --tests '*ToolchainSmokeIT'`가 property 태그를 계속 제외함을 확인. — 선행: T001

## Phase 2: 공통 골격 (모든 US 차단)

- [X] T003 (scaf) 컴파일 가능한 골격만 작성한다. 로직 금지, 모든 메서드는 `null`/`0`/빈 컬렉션 반환.
  - `T/support/ReservationCommand.java`: `sealed interface` + `record Create(User user, Target target, Key key)`, `record Get(User user, int ref)`, `record Cancel(User user, int ref)`; 중첩 enum `User{A,B}`, `Target{R1,R2,MISSING}`, `Key{K1,K2,K3}`; `toString()`은 `Create(A,R1,K1)` 형식.
  - `T/support/CommandOutcome.java`: `record CommandOutcome(Kind kind, Integer logicalId, ReservationCommand.Target target, ModelStatus status, Boolean firstCancel)`, `enum Kind{CREATED,REPLAYED,SOLD_OUT,IDEMPOTENCY_CONFLICT,RESOURCE_NOT_FOUND,RESERVATION_NOT_FOUND,VIEWED,CANCELLED}`, `enum ModelStatus{CONFIRMED,CANCELLED}`(제품 `ReservationStatus` 미참조).
  - `T/support/ReferenceReservationModel.java`: `ReferenceReservationModel(int capacityR1, int capacityR2)`, `CommandOutcome apply(ReservationCommand)`, `int available(Target)`, `int confirmed(Target)`, `int reservationCount()`, `ModelStatus status(int logicalId)`, `Target target(int)`, `User owner(int)`.
  - `T/support/ReservationCommandRunner.java`: 생성자 `(ReservationService, ResourceService, DataSource)`, `void prepare(int capR1, int capR2)`, `CommandOutcome run(ReservationCommand)`, `void assertFinalStateMatches(ReferenceReservationModel)`.
  - 검증: `./gradlew compileTestJava` 종료0. `org.springframework`·`ReservationStatus`·SQL을 모델/명령/결과에서 import하지 않음. — 선행: T002

## Phase 3: US1 — 임의 명령 순서와 독립 모델 일치 (P1, MVP)

**독립 인수**: `./gradlew propertyTest` 기본 seed 통과, `attempts>=100`, 같은 seed 2회 `scenario-digest` 동일.

- [X] T004 [US1] RED (ta) `T/support/ReferenceReservationModelTest.java`(Spring 없는 Unit)에 data-model.md 전이 규칙을 손으로 계산한 기대값으로 작성한다: capacity0 품절·실패 키 비보존 후 다른 대상 성공·같은 키 다른 대상 충돌·재요청 무변화·취소 후 같은 키 재요청 REPLAYED·중복 취소 first=false 무복구·타인 Get/Cancel NOT_FOUND·범위 밖/실패 생성 ref NOT_FOUND·MISSING 대상(키 없을 때 RESOURCE_NOT_FOUND, 키 있을 때 CONFLICT)·보존식. 모델 구현 클래스를 기대값 계산에 호출하지 않는다. `./gradlew test --tests '*ReferenceReservationModelTest'`로 assertion RED 확인. — 선행: T003
- [X] T005 [US1] GREEN (ta) `T/support/ReferenceReservationModel.java`를 Map/Set만으로 구현해 T004 GREEN. 제품 클래스·SQL 미사용. — 선행: T004 RED 확인(orch)
- [X] T006 [US1] RED (ta) `T/reservation/ReservationPropertyIT.java`(`@Tag("property")`, `@SpringBootTest(webEnvironment=NONE)`, `ContainerTestSupport.postgresOnly()`, `IdempotencyIT`의 Cleanup listener 패턴, `@Transactional` 금지)를 작성한다. QuickTheories `withFixedSeed(QT_SEED 또는 20261023)·withExamples(150)·withShrinkCycles(명시)`, 입력 = capacity 2개(0~5) + 명령 `lists().of(...).ofSizeBetween(1,30)`; 사용자 {A,B}, 대상 {R1,R2,MISSING(낮은 비중)}, 키 {K1,K2,K3}, ref 0~9. 매 실행(축소 포함) `TestData.reset()`→`runner.prepare`→명령별 `assertThat(runner.run(c)).isEqualTo(model.apply(c))`(불일치 index·명령 포함 메시지)→`runner.assertFinalStateMatches(model)`. 시작/종료에 contracts C2 출력과 `scenario-digest=<생성 시나리오 toString 연결의 SHA-256>`, `attempts>=100` assert. 골격 runner 상태에서 `./gradlew propertyTest` 실행해 assertion RED 확인. — 선행: T005
- [X] T007 [US1] GREEN (ta) `T/support/ReservationCommandRunner.java` 구현: `ResourceService.create`로 R1/R2 생성, MISSING은 고정 무작위 UUID; 생성 명령 순번→실제 예약 id·createdAt·cancelledAt 대응표; 예외 매핑(`ReservationWriter.SoldOutException`→SOLD_OUT, `ReservationService.IdempotencyConflictException`→IDEMPOTENCY_CONFLICT, `ResourceService.ResourceNotFoundException`→RESOURCE_NOT_FOUND, `ReservationService.ReservationNotFoundException`→RESERVATION_NOT_FOUND, 그 외 예외는 그대로 전파); data-model.md “실제 값 관계”(REPLAYED id·createdAt 최초값 동일·replayed=true, CREATED replayed=false, VIEWED cancelledAt 관계, 반복 취소 시각 동일) assert; 최종 단일 SELECT 집계로 available·CONFIRMED·보존식·행 수·(user,key) 중복 0·논리 예약별 status/resource/user 비교. T006 GREEN 확인. — 선행: T006 RED 확인(orch)
- [X] T008 [US1] REFACTOR (ta) 중복 제거·이름 정리, 모델/명령/결과에 제품·Spring import 없음 확인 후 T004·T006 재실행 GREEN. — 선행: T007
- [X] T009 [US1] VERIFY (orch) `./gradlew test --tests '*ReferenceReservationModelTest'`, `./gradlew propertyTest` 2회(같은 digest·attempts), `QT_SEED=<새 seed> ./gradlew propertyTest` 1회(탐색으로 구분)를 실행·기록. import grep으로 모델 독립성 확인. — 선행: T008
- [X] T010 [US1] REVIEW (rev) US1 diff를 spec US1·FR-001~008·FR-010과 data-model 기준으로 리뷰(모델 독립성·예외 매핑의 과도한 흡수·초기화 누락·@Transactional·결과 게이트). 지적 시 orch가 원인별 재할당. — 선행: T009

## Phase 4: US2 — seed·축소 재현과 회귀 고정 (P1)

**독립 인수**: 고의 변경 D 상태 실패 seed·축소 ≤5건, 같은 seed 재실행 동일 축소, 회귀 IT RED→원복 GREEN.

- [X] T011 [US2] (impl) 고의 변경 D 적용: `M/reservation/ReservationService.java` `replay`의 `if (!stored.resourceId().equals(resourceId)) throw ...` 제거(커밋 금지). — 선행: T010
- [X] T012 [US2] RED-관측 (orch) `./gradlew propertyTest` 실행 → 실패 종료·seed·축소 명령 순서·불일치 기록, 같은 seed `QT_SEED=<seed>`로 재실행해 동일 축소 확인. 축소 > 5건이면 adv 분석. — 선행: T011
- [X] T013 [US2] RED (ta) `T/reservation/ReservationPropertyRegressionIT.java`(태그 없음 → integrationTest)에 T012 축소 순서·capacity를 그대로 고정하고 같은 runner/model로 검증. D 상태에서 `./gradlew integrationTest --tests '*ReservationPropertyRegressionIT'` assertion RED 확인. 출처(고의 변경 D, seed)를 클래스 주석에 기록. — 선행: T012
- [X] T014 [US2] GREEN (impl) 고의 변경 D 원복, `git diff --exit-code -- backend/src/main` 종료0. — 선행: T013 RED 확인(orch)
- [X] T015 [US2] VERIFY (orch) 회귀 IT GREEN, `./gradlew propertyTest` 기본 seed GREEN 재확인·기록(고의 결함으로 구분). — 선행: T014
- [X] T016 [US2] REVIEW (rev) 회귀 IT가 축소 결과와 일치하는지·원복 완전성 확인. — 선행: T015

## Phase 5: US3 — 순수 규칙 변이 탐지 (P2)

**독립 인수**: `./gradlew mutationSmoke` 종료0, 대상 3클래스만, ratio ≥ 0.80, 무효0, 생존 전부 분류.

- [X] T017 [US3] (impl) `backend/build.gradle` `pitest.targetClasses`=`['com.limitedbooking.backend.resource.ResourceRules','com.limitedbooking.backend.reservation.IdempotencyKey','com.limitedbooking.backend.reservation.CancellationDecision']`, `targetTests`=대응 `ResourceRulesTest`·`IdempotencyKeyTest`·`CancellationDecisionTest` FQCN. `mutationSmoke` 게이트에 contracts C3(대상 밖 mutatedClass 0, killed/generated ≥ 0.80, ratio 출력) 추가, 기존 무효 상태 거부 유지. 게이트 자가검증: 임계값 임시 1.01로 실패 종료 확인 후 원복(커밋 금지). — 선행: T016 (같은 작업 트리에서 고의 변경 D 상태와 PIT 실행이 섞이지 않도록 US2 뒤 순차)
- [X] T018 [US3] RED-관측 (orch) `./gradlew mutationSmoke` 실행, `build/reports/pitest/mutations.xml`의 생존·무효 변이를 위치·mutator·분류(검증문 부족/미검증 입력/동등/도구 한계) 초안으로 기록. 무효(NO_COVERAGE 등)가 있으면 원인 분류. — 선행: T017
- [X] T019 [US3] GREEN (ta) “검증문 부족·미검증 입력” 생존 변이를 죽이는 Unit을 `T/resource/ResourceRulesTest.java`·`T/reservation/IdempotencyKeyTest.java`·`T/reservation/CancellationDecisionTest.java`에 추가(규칙 기대값은 계획서 2절). 제품 코드 무변경. — 선행: T018
- [X] T020 [US3] VERIFY (orch) `./gradlew test mutationSmoke` 재실행, ratio ≥ 0.80·설명되지 않은 생존 0 확인, 동등/도구 한계 사유 기록. 무효 상태를 허용으로 바꾸지 않는다. 해결 불가 시 adv. — 선행: T019
- [X] T021 [US3] REVIEW (rev) build.gradle 게이트·추가 Unit·분류 근거 리뷰. — 선행: T020

## Phase 6: US4 — SQL·소유자·키 비교 고의 변경 탐지 (P2)

**독립 인수**: A~E 각각 실패 테스트 ≥1(B의 속성 미탐지는 사유 기록), 원복 diff 0.

- [X] T022 [US4] (impl→orch) 변경 A: `M/resource/ResourceRepository.java` `decrementIfAvailable`의 `AND available > 0` 제거 → orch `./gradlew test integrationTest propertyTest` 실행·실패 테스트 기록 → impl 원복 → `git diff --exit-code -- backend/src/main`. — 선행: T016, T021
- [X] T023 [US4] (impl→orch) 변경 B: `M/reservation/ReservationRepository.java` `markCancelled`의 `AND status = 'CONFIRMED'` 제거 → 같은 절차. — 선행: T022
- [X] T024 [US4] (impl→orch) 변경 C: `ReservationRepository.findByIdAndUser`의 `AND user_id = :userId` 제거 → 같은 절차. — 선행: T023
- [X] T025 [US4] (impl→orch) 변경 D: T011과 같은 변경으로 `./gradlew test integrationTest` 실행·실패 테스트 기록(속성 결과는 T012 재사용) → 원복 확인. — 선행: T024
- [X] T025a [US4] (impl→orch) 변경 E(T018 분석으로 추가): `M/reservation/CancellationDecision.java` `from`의 두 case 반환값 교체 → `./gradlew test integrationTest propertyTest` 실행·실패 기록 → 원복 확인. PIT가 enum 매핑 교체 변이를 만들지 못하는 도구 한계의 보완. — 선행: T025
- [X] T026 [US4] (ta, 조건부) 탐지 0인 변경이 있으면 해당 규칙 테스트를 보완하고 그 변경 재적용 상태 RED → 원복 GREEN 확인. 없으면 “해당 없음” 기록. — 선행: T025a
- [X] T027 [US4] REVIEW (rev) 탐지 표·원복 증거 검토. — 선행: T026

## Phase 7: 마무리·통합 검증

- [X] T028 (orch) 최종 게이트: `./gradlew build deepTest mutationSmoke verifyContractDependencies` 종료0, 그룹별 수·실패0·skip0·attempts·PIT ratio 기록, `git diff --exit-code -- backend/src/main` 0. — 선행: T027
- [X] T029 (orch) 문서 동기화: `docs/test-evidence.md`(10단계 절), `docs/versions.md`(Property/PIT 현재 대상·수치), `docs/decisions.md`(R2·R4·R5 결정), `README.md`(propertyTest·mutationSmoke 범위), 계획서 상단 상태·6.2 표·10단계 체크박스(실행 검증 항목만), `knowledge/rules/development-and-testing.md`, `knowledge/domain/reservation.md` 검증 사례, 필요 시 `knowledge/README.md`. 링크·형식 검사. — 선행: T028
- [ ] T030 (rev) 로컬 전체 diff 독립 리뷰(역할 범위 위반 파일 포함 점검). — 선행: T029
- [ ] T031 (orch) Spec Kit Converge 수행, 미수렴 항목 재분배. — 선행: T030
- [ ] T032 (orch) 목적별 커밋·push·Draft PR(base `develop`). — 선행: T031
- [ ] T033 (rev) Codex Open Code Review로 PR 정확한 diff 리뷰, 유효 항목을 GitHub PR Review API로 파일·라인 코멘트 게시. — 선행: T032
- [ ] T034 (orch) PR 코멘트 분류·재할당·재검증·재리뷰. Critical/High·실패 테스트·누락 요구사항 0까지 반복. Merge는 사용자 승인 후. — 선행: T033

## Dependencies

T001→T002→T003→US1(T004~T010)→US2(T011~T016)→US3(T017~T021)→US4(T022~T027)→T028~T034. 모두 순차다. 병렬 후보: T019의 세 Unit 파일 보완은 한 담당(ta)이 처리하므로 병렬화하지 않는다. 같은 작업 트리에서 고의 변경과 다른 Gradle 실행을 겹치지 않는다.

## Implementation Strategy

MVP = US1(독립 모델 + 속성 IT). 이후 US2로 재현성, US3로 순수 규칙 탐지력, US4로 SQL·권한 탐지력을 추가한다. 커밋 단위: ① Spec Kit 문서 ② propertyTest 발견 규칙 ③ 독립 모델·속성 IT ④ 축소 회귀 IT ⑤ PIT 대상·게이트·Unit 보완 ⑥ 실행 근거·문서.
