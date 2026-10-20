---
description: "6단계 소유자의 예약 조회·취소 작업 목록"
---

# Tasks: 소유자의 예약 조회·취소

**Input**: `specs/007-reservation-cancel/` (spec, plan, research, data-model, contracts, quickstart)

**Tests**: 계획서 7절 6단계와 AGENTS.md의 TDD 순서(기대값 → RED 확인 → 최소 구현 → GREEN)에 따라 테스트 작업을 포함한다. 컴파일 실패만으로 RED를 기록하지 않는다.

**경로 기준**: `backend/src/main/java/com/limitedbooking/backend/` = `MAIN/`, `backend/src/test/java/com/limitedbooking/backend/` = `TEST/`

## Phase 1: Setup

- [X] T001 `git rev-parse --show-toplevel`을 확인한다. 순서: ① `docs/reservation-cancel-spec`에서 specs/007-reservation-cancel/과 .specify/feature.json만 경로 지정해 커밋한다(`git add .`/`-A` 금지). ② 사용자 확인 후 `develop`에 `--no-ff`로 병합한다. ③ `develop`에서 `feature/reservation-cancel`를 분기한다. 각 단계에서 안내와 실제 수행 기록을 구분한다(knowledge/rules/git-flow.md, commit.md).
- [X] T002 knowledge/README.md, knowledge/rules/*, knowledge/domain/reservation.md·resource.md·security.md를 읽고 계획서 5.3절과 실제 코드의 차이를 확인한다. 4단계 MAIN/security/SecurityConfig.java의 취소 경로 본문 필터(413·400)에 기존 IT 증거가 있는지 docs/test-evidence.md와 테스트 코드에서 확인하고 결과를 기록한다.

## Phase 2: Foundational

- [X] T003 [P] 신규 응답 record `ReservationView(UUID id, UUID resourceId, ReservationStatus status, Instant createdAt, Instant cancelledAt)`와 `from(StoredReservation)`을 MAIN/reservation/ReservationView.java에 만든다. "cancelledAt은 항상 직렬화하며 확정 상태에서는 null"을 지킨다(전역 Jackson 설정이 null을 생략하는지 확인).
- [X] T004 [P] `ReservationService.ReservationNotFoundException`을 추가하고 MAIN/common/ApiExceptionHandler.java에서 404 `NOT_FOUND` problem+json으로 매핑한다. 대상 404와 같은 형식을 쓰며 식별자 존재 여부를 detail에 노출하지 않는다.
- [X] T005 MAIN/reservation/ReservationRepository.java의 행 매핑을 private RowMapper로 공통화하고 `findByIdAndUser(UUID id, UUID userId): Optional<StoredReservation>`(`WHERE id = :id AND user_id = :userId`)를 추가한다.

## Phase 3: User Story 1 - 내 예약의 현재 상태 확인하기 (P1) 🎯 MVP

**Goal**: 소유자만 단건 조회하고, 타인·관리자·없는 예약은 동일한 404를 받는다.
**Independent Test**: USER_A 예약을 준비하고 A/B/ADMIN/미인증/없는 UUID/잘못된 UUID로 GET해 비교한다.

- [X] T006 [US1] TEST/reservation/ReservationOwnershipIT.java를 만든다(기존 ReservationCreateIT의 Cleanup·TestData·HTTP 지원과 풀 설정 `maximum-pool-size=10`·`connection-timeout=3000` 재사용). GET 사례를 작성한다: 본인 200(cancelledAt null 필드 존재), USER_B·ADMIN 404 `NOT_FOUND`, 없는 UUID 404, 미인증 401+WWW-Authenticate, 잘못된 UUID 400 `INVALID_REQUEST`, 민감 정보 비노출.
- [X] T007 [US1] T006을 실행해 기능 부재로 인한 HTTP 상태 불일치(RED)를 확인하고 출력 요약을 기록한다.
- [X] T008 [US1] MAIN/reservation/ReservationService.java에 `get(UUID userId, UUID reservationId): ReservationView`를 추가한다(없으면 ReservationNotFoundException, 트랜잭션 없음).
- [X] T009 [US1] MAIN/reservation/ReservationController.java에 `GET /{id}`(produces JSON, `@PathVariable UUID`, 인증 UUID는 LocalIdentityResolver)를 추가한다. 잘못된 UUID가 기존 핸들러에서 400으로 매핑되는지 확인한다.
- [X] T010 [US1] `./gradlew integrationTest --tests '*ReservationOwnershipIT'`로 GREEN을 확인한다.

## Phase 4: User Story 2 - 내 예약 취소하고 수량 돌려주기 (P1)

**Goal**: 확정 예약의 취소와 수량 1 복구를 한 트랜잭션으로 커밋한다.
**Independent Test**: capacity=1 생성 → 취소 → 상태·cancelledAt·available·보존식을 대조한다.

- [X] T011 [US2] TEST/reservation/ReservationCancelIT.java를 만든다. capacity=1 생성 → USER_A 취소 → 200/CANCELLED/cancelledAt≠null, DB available=1·CONFIRMED 0·보존식, 이후 GET이 같은 status·cancelledAt인지, 비어 있지 않은 본문의 취소는 400이고 상태가 바뀌지 않는지 검사한다.
- [X] T012 [US2] TEST/reservation/ReservationOwnershipIT.java에 cancel 사례를 추가한다: USER_B·ADMIN·없는 UUID는 404이고 상태·available 불변, 미인증 401, 잘못된 UUID 400. 8,193바이트 본문 취소는 자격 증명이 없어도 413 `PAYLOAD_TOO_LARGE`이고 상태·available은 불변이다.
- [X] T013 [US2] T011·T012를 실행해 RED(HTTP 상태 불일치)를 확인한다.
- [X] T014 [P] [US2] MAIN/resource/ResourceRepository.java에 `lockForUpdate(UUID)`(`SELECT id FROM resources WHERE id=:id FOR UPDATE`)와 `incrementIfBelowCapacity(UUID): int`(`UPDATE resources SET available = available + 1 WHERE id=:id AND available < capacity`)를 추가한다.
- [X] T015 [P] [US2] MAIN/reservation/ReservationRepository.java에 `markCancelled(UUID id, UUID userId, Instant cancelledAt): int`(`SET status='CANCELLED', cancelled_at=:now WHERE id=:id AND user_id=:userId AND status='CONFIRMED'`)를 추가한다.
- [X] T016 [US2] MAIN/reservation/ReservationWriter.java에 `@Transactional(isolation = READ_COMMITTED) cancel(UUID userId, UUID reservationId): ReservationView`를 구현한다. 순서는 SET LOCAL lock_timeout 2s·statement_timeout 5s → findByIdAndUser(없으면 404) → `CancellationDecision.from` → ALREADY_CANCELLED면 그대로 반환 → resource lockForUpdate → markCancelled → 1건이면 incrementIfBelowCapacity(0건이면 IllegalStateException) → 재조회 반환. 잠금 순서는 resource → reservation이며 cancelled_at은 `Instant.now(clock).truncatedTo(MICROS)`이다.
- [X] T017 [US2] MAIN/reservation/ReservationService.java에 `cancel(userId, reservationId)`를 추가한다. 활성 상위 트랜잭션은 거부(create와 동일)하고 Writer에 위임한다.
- [X] T018 [US2] MAIN/reservation/ReservationController.java에 `POST /{id}/cancel`(본문 없음, 200 ReservationView)을 추가한다.
- [X] T019 [US2] T011·T012와 기존 SecurityIT를 실행해 GREEN을 확인한다.

## Phase 5: User Story 3 - 취소를 다시 보내도 안전하기 (P1)

**Goal**: 중복 취소는 변경 없이 200을 반환하고, 복구 실패·장애는 전체를 롤백한다.
**Independent Test**: 두 번째·세 번째 취소의 응답·DB를 첫 결과와 비교하고, 복구 실패·timeout 주입 후 DB를 확인한다.

- [X] T020 [US3] TEST/reservation/ReservationCancelIT.java에 중복 취소 사례를 추가한다: 두 번째·세 번째 취소가 모두 200/CANCELLED, cancelledAt이 첫 값과 동일, available=1 유지(SC-001). 이미 통과하면 RED 미성립 사실과 대체 근거(T021)를 기록한다.
- [X] T021 [US3] 고의 변경으로 탐지력을 확인한다: MAIN/reservation/ReservationWriter.java의 ALREADY_CANCELLED 조기 반환 제거, MAIN/reservation/ReservationRepository.java markCancelled의 `status='CONFIRMED'` 조건 제거를 각각 따로 적용하고, T020이 실패(cancelledAt 변경 또는 available=2)하는지 확인한 뒤 원복한다. 실패한 테스트를 기록한다.
- [X] T022 [US3] TEST/reservation/ReservationCancelIT.java에 `@MockitoSpyBean ResourceRepository`로 `incrementIfBelowCapacity`가 예외를 던지게 하는 사례를 추가한다. 500, 안전한 본문, CONFIRMED·cancelled_at null·available=0, 주입 해제 후 정상 취소 200을 확인하며 finally에서 reset한다.
- [X] T023 [US3] 같은 파일에 `incrementIfBelowCapacity`가 0을 반환하는 사례를 추가한다. 500 내부 오류(409/503 아님)와 전체 롤백을 확인한다.
- [X] T024 [US3] 같은 파일에 `incrementIfBelowCapacity`가 `QueryTimeoutException`을 던지는 사례를 추가한다. 503 `DEPENDENCY_UNAVAILABLE`, 안전한 본문, CONFIRMED·cancelled_at null·available=0을 확인한다. 실제 lock_timeout 경쟁 재현은 7단계 범위로 기록한다.
- [X] T025 [US3] 고의 변경으로 탐지력을 확인한다: MAIN/reservation/ReservationWriter.java에서 복구 0건 검사를 임시로 제거하면 T023이 실패하는지 확인하고 원복한다. 결과를 기록한다.

## Phase 6: User Story 4 - 취소 이후의 생성 요청 구분하기 (P2)

**Goal**: 원 키 재요청은 최초 결과를 재현하고, 복구된 수량은 다른 키로 예약할 수 있다.
**Independent Test**: 실제 취소 기능으로 만든 취소 예약에 원 키·새 키로 생성을 요청한다.

- [X] T026 [US4] TEST/reservation/ReservationCancelIT.java에 다음을 추가한다. 생성 → 취소 → 같은 키 POST 201/`Idempotent-Replayed: true`/CONFIRMED/최초 id·createdAt·Location → GET이 CANCELLED, available·예약 수 불변.
- [X] T027 [US4] 같은 파일에 다음을 추가한다. capacity=1에서 USER_A 예약 → USER_B 키 K2 409 SOLD_OUT → USER_A 취소 → USER_B K2 201/false, available=0, 보존식.

## Phase 7: Polish & 마감

- [X] T028 `cd backend && ./gradlew test integrationTest --tests '*ReservationOwnershipIT' --tests '*ReservationCancelIT'`와 `./gradlew verify`를 실행한다. 종료 코드·Unit/IT 수·실패/skip 0을 확인한다.
- [X] T029 quickstart.md의 CLI 1회 전체 실행(local 프로필, curl)을 수행하고 명령·응답 요약을 기록한다.
- [X] T030 [P] docs/test-evidence.md에 실행 증거를 기록하고 knowledge/domain/reservation.md(구현됨·검증 근거, CancellationDecision 연동)를 갱신한다. 계획서 7절 6단계 체크박스는 실제 검증된 항목만 표시하고, 현재 상태 문단을 갱신한다.
- [X] T031 [P] docs/openapi.yaml과 실제 응답(필드·코드)을 대조하고, 차이가 있으면 계약 변경 없이 구현을 고친다.
- [X] T032 검증 대상별 로컬 커밋을 만든다(commit.md). push·병합은 사용자 지시가 있을 때만 한다.

## Dependencies & Execution Order

- Setup(T001~T002) → Foundational(T003~T005) → US1 → US2 → US3 → US4 → Polish.
- US1은 Foundational만 필요하다. US2는 US1의 조회·IT 기반을, US3·US4는 US2의 취소 구현을 필요로 한다.
- 같은 IT 파일을 수정하는 작업은 순차로 진행한다.

## Parallel Opportunities

- T003·T004 (서로 다른 파일)
- T014·T015 (ResourceRepository / ReservationRepository)
- T030·T031 (문서 / 계약 대조)

## Implementation Strategy

1. MVP: Phase 1~3(소유자 조회)까지 진행하고 GREEN을 확인한 뒤 중간 커밋한다.
2. US2 → US3 → US4 순서로 각 단계마다 RED → GREEN을 기록한다.
3. 동시 취소·생성/취소 경쟁은 7단계 범위로 남기고, 완료로 표시하지 않는다.

### 실행 중 보완 결정

- T021: 두 방어를 각각 제거한 최초 일반 중복 취소 시험은 통과했다. 보강 후 조기 반환 제거는 `alreadyCancelledDoesNotWaitForResourceLock`에서 기대200/실제503, status 조건 제거는 `staleConfirmedReadCannotRepeatStatusUpdateOrRecovery`에서 기대200/실제500으로 각각 실패했다. 모두 원복하고 재검증한다. 두 방어의 개별 탐지를 위해 T021 기대 실패 사례를 확장했으며 도메인 계약은 유지한다.
- T020·T022~T027은 US2 구현의 동작을 추가 확인하므로 최초 GREEN이면 이를 명시하고 고의 변경 탐지로 보완한다.

### 최종 실행 상태 (2026-10-20)

32/32 완료. 구현 로컬 커밋 `5b4019c`; 지정 Unit137/IT16, 최종 verify Unit137/일반IT136, 실패/오류/skip0. local CLI11단계 및 DB 최종 보존식을 확인했다. 전체 변경의 독립 코드 리뷰에서 중요/경미 기능 결함 없음. 세부 명령·고의 변경·원복·계획 조정은 docs/test-evidence.md와 docs/decisions.md에 기록했다. 구현 브랜치는 유지하며 외부 push·구현 병합은 하지 않는다. `.specify/extensions.yml`이 없어 사전/사후 확장 훅은 없다.


### PR #6 적대적 리뷰 후속 (2026-10-20)

사용자의 PR 생성·리뷰·문제 없을 때 코멘트/병합 지시에 따라 GitHub PR6을 생성했다. 인코딩 취소 경로의 비어 있지 않은 본문이200·데이터 변경으로 이어지는 계약 결함을 실제 HTTP로 재현하고 Controller의 Handler 단위 검사로 수정했다. 두 회귀를 추가해 신규IT18(Ownership5+Cancel13), 일반IT138로 확장했다. 기존32개 작업의 완료 상태와 과거 검증 수는 당시 기록이며 최신 검증·리뷰·병합 진행은 docs/test-evidence.md의 PR6 후속을 따른다.
