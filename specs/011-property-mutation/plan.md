# Implementation Plan: 명령 순서 속성 검증과 변이 탐지력 확인

**Branch**: `feature/011-property-mutation` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/011-property-mutation/spec.md`

## Summary

계획서 10단계를 구현한다. (1) 실제 PostgreSQL에서 `ReservationService`/`ResourceService`를 순차 호출하는 `ReservationPropertyIT`를 QuickTheories 명령 순서 생성·축소로 만들고, SQL·제품 코드를 호출하지 않는 테스트 전용 `ReferenceReservationModel`과 대조한다. (2) 고의 결함(재요청 대상 비교 제거)으로 실패 seed·축소 순서를 얻고 최소 사례를 일반 회귀 IT로 고정한다. (3) 기존 PIT 구성을 `ResourceRules`·`IdempotencyKey`·`CancellationDecision`과 대응 Unit으로 교체하고 탐지율 80% 게이트를 추가한다. (4) SQL·소유자·키 비교 고의 변경 4종의 탐지 테스트를 기록·원복한다. 제품 코드·SQL·API·DB 계약은 변경하지 않는다.

## Technical Context

**Language/Version**: Java 25(preview 제외), Gradle Wrapper 9.8.0, Spring Boot 4.1.1

**Primary Dependencies**: 기존 고정 도구만 사용. QuickTheories 0.26(testImplementation), PIT Gradle plugin 1.19.0·engine 1.22.1·JUnit Platform plugin 1.2.3, JUnit Jupiter 6.0.3(Boot BOM), Testcontainers 2.0.5(Boot BOM). 새 의존성·버전 변경 없음.

**Storage**: 테스트 소유 PostgreSQL 18.6 컨테이너(`ContainerTestSupport.postgresOnly()`, 고정 digest). Redis는 사용하지 않는다(서비스 경로에 Redis 의존 없음, `ResourceApiIT`가 같은 방식으로 컨텍스트를 기동함).

**Testing**: Unit `*Test`(`test`), 일반 IT `*IT`(`integrationTest`, contract/property 제외), Property(`propertyTest`, `property` 태그), PIT(`pitest` → `mutationSmoke` 게이트)

**Target Platform**: localhost macOS(arm64) + Docker

**Project Type**: 단일 Spring Boot 백엔드(`backend/`)

**Performance Goals**: 속성 실행은 기본 seed에서 실제 100회 이상, 로컬에서 수 분 이내를 목표로 하되 시간은 판정 기준이 아니다.

**Constraints**: 클래스 병렬 실행 금지(`maxParallelForks=1`), 매 시도·축소 재실행마다 DB 초기화, PIT에 Spring·컨테이너 미기동, 고의 변경은 커밋 금지·즉시 원복, 결과 게이트(실행≥1·실패0·skip0) 유지.

**Scale/Scope**: 대상 2개 × capacity 0~5, 사용자 2명(USER_A·USER_B), 키 3개, 명령 1~30건, 생성 예산 ≥ 100.

## Constitution Check

*GATE: Phase 0 전 확인, Phase 1 뒤 재확인.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 서비스 경계·API 호환 | 통과 | 제품 코드·API·SQL 무변경. 속성 IT는 기존 Service 공개 메서드만 호출 |
| II. 도메인 근거 | 통과 | knowledge 규칙 전체·reservation/resource/security 문서·계획서 2~5·6·7절 10단계 확인 |
| III. 검증 방식·증거 | 통과 | 모델 Unit RED→GREEN, 고의 결함 RED→원복 GREEN, PIT 게이트 고의 실패 확인, 결과 게이트 유지 |
| IV. 문서 동기화 | 통과(작업 포함) | test-evidence·versions·decisions·README·계획서 6.2/10단계·development-and-testing·reservation 도메인 갱신을 Tasks에 포함 |
| V. 미확정 정책 | 통과 | 업무 정책 질문 없음. 설계 모호점 4건은 spec Clarifications에 근거와 함께 기록 |

재확인(Phase 1 뒤): 위반 없음. Complexity Tracking 해당 없음.

## Project Structure

### Documentation (this feature)

```text
specs/011-property-mutation/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/verification-contract.md
├── checklists/requirements.md
├── checklists/verification.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/build.gradle                                   # propertyTest 발견 규칙, pitest 대상, mutationSmoke 게이트
backend/src/test/java/com/limitedbooking/backend/
├── support/
│   ├── ReservationCommand.java                        # 신규: sealed 명령(Create/Get/Cancel) record
│   ├── CommandOutcome.java                            # 신규: 결과 종류 + 비교 필드
│   ├── ReferenceReservationModel.java                 # 신규: Map/Set 독립 모델 (SQL·제품 클래스 미사용)
│   ├── ReferenceReservationModelTest.java             # 신규 Unit: 손으로 계산한 순서로 모델 검증
│   └── ReservationCommandRunner.java                  # 신규: 명령 → 실제 Service 호출, 논리 ID 대응표, 최종 DB 관측
├── reservation/
│   ├── ReservationPropertyIT.java                     # 신규: @Tag("property"), QuickTheories
│   └── ReservationPropertyRegressionIT.java           # 신규: 축소 최소 사례 일반 IT
├── resource/ResourceRulesTest.java                    # 필요 시 PIT 생존 변이 보완
└── reservation/{IdempotencyKeyTest,CancellationDecisionTest}.java  # 필요 시 보완
```

**Structure Decision**: 계획서 3.3절의 `support/ReferenceReservationModel.java` 위치를 따른다. 명령·결과 record도 모델과 함께 `support/`에 둔다. 실행기는 Spring Bean을 인자로 받는 일반 클래스로 두어 속성 IT와 회귀 IT가 공유한다.

## 설계 요점

1. **발견 규칙**: `propertyTest`의 include를 `**/*Test.class`, `**/*IT.class`로 확장(태그 `property`만). `integrationTest`는 이미 `property`를 제외하므로 일반 IT 수는 변하지 않는다. 회귀 IT는 태그 없음 → `integrationTest`에 포함.
2. **속성 입력**: `Gen<Scenario>` = capacity 2개(0~5) + 명령 목록(1~30). 명령은 `Generate.pick`/`integers` 조합, `describedAs`로 읽을 수 있는 출력. `withFixedSeed(QT_SEED 또는 20261023)`, `withExamples(150)`, `withShrinkCycles` 명시. 시도마다 `TestData.reset()` → 대상 2개 생성(`ResourceService.create`) → 명령 실행 → 최종 관측.
3. **결과 매핑**: 실행기가 예외를 결과 종류로 변환한다. `SoldOutException`→SOLD_OUT, `IdempotencyConflictException`→CONFLICT, `ResourceNotFoundException`→RESOURCE_NOT_FOUND, `ReservationNotFoundException`→RESERVATION_NOT_FOUND. 그 외 예외는 변환하지 않고 그대로 실패시킨다(500 탐지와 동일 원칙).
4. **비교**: 명령별 결과 종류·논리 예약 번호·대상·상태·재현 여부, 최초 생성 시각 재현 동일성, 취소 시각 존재와 중복 취소 동일성. 최종: 대상별 available, CONFIRMED 수, 보존식, (user,key) 유일성(DB 집계), 논리 예약별 상태. 모델은 시각을 모르며 “같은 논리 예약의 최초 값과 같다”만 기대한다.
5. **축소 결과 고정**: 고의 결함(ReservationService.replay의 resourceId 비교 제거) 상태에서 실패 seed와 축소 순서를 얻어 `ReservationPropertyRegressionIT`에 명령 목록 그대로 넣는다.
6. **PIT**: `targetClasses`=세 규칙 FQCN, `targetTests`=세 Unit FQCN. `mutationSmoke` 게이트에 `killed/generated ≥ 0.8`, 변이 클래스 ⊆ 대상 검사를 추가. 생존 변이는 `docs/test-evidence.md`에 분류.
7. **고의 변경 4종**: implementer가 하나씩 적용 → 지정 테스트 실행 → 실패 목록 기록 → `git diff -- backend/src/main` 빈 결과로 원복 확인. 상세 위치·기대 탐지 테스트는 [verification-contract](contracts/verification-contract.md).

## 역할 분담

| 역할 | 범위 |
|---|---|
| scaffolder | `support/ReservationCommand`·`CommandOutcome`·모델/실행기 컴파일 가능한 골격(로직 없음, `UnsupportedOperationException`) |
| test-author | 모델 Unit(RED)·모델 구현(테스트 지원 코드)·실행기·속성 IT·회귀 IT·PIT 생존 변이 Unit 보완. `src/test`만 |
| implementer | `backend/build.gradle`(발견 규칙·PIT 대상·게이트, 이번 작업에 한한 예외 범위)·고의 변경 적용/원복·실제 결함 수정이 필요할 때만 `src/main` |
| reviewer | 로컬 diff 독립 리뷰·PR OCR 리뷰. 수정 금지 |
| orchestrator | Spec Kit 문서·실행 검증·docs/knowledge/README/계획서 기록·Git/PR |

## Complexity Tracking

해당 없음.
