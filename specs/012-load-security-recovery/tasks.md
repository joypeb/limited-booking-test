# Tasks: 로컬 부하·보안·의존성 장애 복구 검증

**입력**: [명세](spec.md), [계획](plan.md), [조사](research.md), [데이터 모델](data-model.md), [검증 계약](contracts/verification-contract.md), [실행 안내](quickstart.md)
**상태**: 2026-10-24 T001~T026 완료(T022는 미달 없음으로 해당 없음, T021 측정은 Codex 샌드박스 네트워크 제한 가능성으로 orch가 직접 실행). T027~T031 리뷰·Converge·PR 진행. 실행 근거는 docs/test-evidence.md 11단계 절.

## 형식과 공통 실행 계약

`- [ ] T번호 [P 선택] [US번호] (담당) 설명·경로 — 선행: …`. Java 경로의 공통 접두 `backend/src/test/java/com/limitedbooking/backend/`는 `T/`, `backend/src/main/java/com/limitedbooking/backend/`는 `M/`, `backend/src/main/resources/`는 `R/`로 줄인다.

- 담당: **orch**(실행 검증·문서·Git), **scaf**(scaffolder), **ta**(test-author: `src/test`와 이번 작업에 한해 `load/`·`docs/performance-runs/`), **impl**(implementer: `src/main`만), **rev**(reviewer, 수정 금지), **adv**(advisor).
- 단계: RED(의도한 assertion 실패 확인) → GREEN(최소 구현 또는 고의 변경 원복 후 같은 시험 통과) → REFACTOR(동작 불변 정리·재실행) → VERIFY(orch 독립 실행·기록) → REVIEW(rev).
- 이미 올바른 동작을 검증하는 새 테스트는 첫 실행 GREEN일 수 있다. 이 경우 contracts C1·C2의 고의 변경으로 RED를 확인하고 원복한다(커밋 금지, 원복 후 `git diff --exit-code -- backend/src/main`).
- 컴파일 오류·컨테이너 기동 실패는 RED로 세지 않는다. 실패를 skip·기대값 변경·반복 실행으로 숨기지 않는다. 같은 문제 3회 반복 시 adv 분석.
- API·SQL·Flyway·Wrapper·의존성 버전은 변경하지 않는다. 제품 변경은 T018(load 설정)과 RED로 확인된 실제 결함의 최소 수정(T013)만.
- 같은 파일을 두 에이전트가 동시에 수정하지 않는다. `[P]`는 서로 다른 파일·선행 완료 시에만 병행한다.
- 명령·종료 코드·실패 테스트·상한·실행 수는 orch가 `docs/test-evidence.md` 11단계 절에 순차 기록한다.

## Phase 1: 준비

- [X] T001 (orch) 루트·브랜치 `feature/012-load-security-recovery`·HEAD(ea2e058)·작업 트리 확인, 기준선 `cd backend && ./gradlew test integrationTest --tests '*SecurityIT' --tests '*ReservationRollbackIT' --tests '*ReservationCancelIT'` 실행·기록. — 선행: 없음
- [X] T002 (orch) 환경 확인·기록: `k6 version`(v2.3.0 기대), Docker 버전·CPU·메모리, `docker compose config -q`. k6 threshold·summary-export 실제 동작 확인은 T020 self-check에서 수행한다. — 선행: 없음

## Phase 2: 공통 골격

- [X] T003 (scaf) 컴파일 가능한 골격만 작성(로직 금지, 메서드는 `UnsupportedOperationException`):
  - `T/support/LogCapture.java`: `final class LogCapture implements AutoCloseable` — `static LogCapture attachToRoot()`, `List<String> renderedEvents()`(메시지·인자·throwable 문자열 포함), `int size()`, `close()`.
  - `T/support/PostgresFaults.java`: 생성자 `(PostgreSQLContainer postgres)`, `void blockAndTerminateConnections()`, `void restoreConnections()`, `void pause()`, `void unpause()`, `AutoCloseable lockResourceRow(UUID resourceId)`.
  - `T/reservation/DependencyFailureIT.java`: `@SpringBootTest(webEnvironment=RANDOM_PORT)`, 소유 `ContainerTestSupport`, `@DynamicPropertySource`(풀 10·연결 획득 3000·제한 limit 10000·window 60000ms), 기존 IT의 cleanup listener 패턴, `@BeforeEach` DB 초기화. 테스트 메서드 없음.
  - 검증: `./gradlew compileTestJava` 종료0. — 선행: T001

## Phase 3: US1 — 실제 인증 경로 보안 (P1)

**독립 인수**: `./gradlew integrationTest --tests '*SecurityIT'` 통과, S4·S11 각각 고의 변경 RED → 원복 GREEN.

- [X] T004 [US1] RED 준비 (ta) `T/support/LogCapture.java` 구현과 `T/security/SecurityIT.java`에 contracts C1 S4 `reservationPathsIgnoreSpoofedUserIdHeader`, S11 `rejectedRequestsDoNotLogPasswordsOrAuthorizationValues`를 추가한다. 기존 테스트 무변경. S11은 캡처 이벤트 수 > 0을 함께 단언. — 선행: T003
- [X] T005 [US1] 첫 실행 (orch) `./gradlew integrationTest --tests '*SecurityIT'` 실행·기록(GREEN이면 T006, 실패면 원인 분류: 테스트 결함→ta, 실제 결함→기록 후 impl). — 선행: T004
- [X] T006 [US1] RED (impl→orch) 고의 변경 S4: `M/security/LocalIdentityResolver.java`가 `X-User-Id` 헤더를 우선 사용하도록 일시 변경 → orch가 S4 실패 확인 → impl 원복. 고의 변경 S11: Authorization 헤더 값을 INFO 로그로 출력하는 일시 변경(필터 또는 resolver) → orch가 S11 실패 확인 → impl 원복. 각 원복 후 `git diff --exit-code -- backend/src/main`. — 선행: T005
- [X] T007 [US1] GREEN·REFACTOR (ta→orch) 원복 상태에서 중복 정리(도우미 재사용) 후 SecurityIT 재실행 GREEN. — 선행: T006
- [X] T008 [US1] VERIFY (orch) SecurityIT 단독 + `ReservationOwnershipIT`·`ReservationCreateIT` 실행, C1 S1~S11 항목→테스트 연결표를 test-evidence에 기록. 커밋 `test(security): ...`. — 선행: T007
- [X] T009 [US1] REVIEW (rev) US1 diff를 spec US1·FR-001~004·C1 기준으로 리뷰(실제 Authorization 경로, DB 무변경 단언, 빈 캡처 통과 위험). — 선행: T008

## Phase 4: US2 — 의존성 장애·응답 유실 복구 (P1)

**독립 인수**: `./gradlew integrationTest --tests '*DependencyFailureIT'` 통과, F1~F6 상한·스냅샷·복구 단언, RED 근거(실제 결함 또는 고의 변경) 기록.

- [X] T010 [US2] RED (ta) `T/support/PostgresFaults.java` 구현(관리 연결은 컨테이너의 `postgres` DB, `ALLOW_CONNECTIONS`·`pg_terminate_backend`, `DockerClientFactory`의 pause/unpause, `FOR UPDATE` 보유 연결)과 `DependencyFailureIT`에 F1·F2·F3 `connectionLossRejectsWritesWithin6sAndSameKeyRecovers`를 작성. 모든 주입은 try/finally 복구. — 선행: T003
- [X] T011 [US2] RED (ta) F4 `pausedDatabaseRejectsCreateWithin12sWithoutPartialWrite`(HTTP timeout 20초). — 선행: T010
- [X] T012 [US2] RED (ta) F5 `cancelLockTimeoutIs503Within4sAndRecovers`, F6 `responseLostAfterCommitIsRecoveredBySameKey`(raw Socket, 커밋 폴링 100ms·최대 5초). — 선행: T011
- [X] T013 [US2] RED 관측·GREEN (orch→impl) orch가 `./gradlew integrationTest --tests '*DependencyFailureIT'` 실행해 사례별 결과(상태·경과·예외)를 기록. 실제 결함만 impl이 최소 수정: F4 시간 초과 시 `R/application.yaml`에 `spring.datasource.hikari.data-source-properties.socketTimeout: 7`; 연결 계열 예외의 500은 `M/common/ApiExceptionHandler.java` 분류 최소 확장(08·57 연결 계열만, 미분류 500 유지). 수정 후 같은 명령 GREEN. — 선행: T012
- [X] T014 [US2] RED(탐지력) (impl→orch) 첫 실행 GREEN 사례만 contracts C2 고의 변경 적용 → orch 실패 확인 → 원복: F5 취소의 `SET LOCAL lock_timeout` 제거, F6 재현 응답 replayed=false, F1~F3 연결 실패 503 매핑 제거. 원복 후 diff 확인. — 선행: T013
- [X] T015 [US2] REFACTOR (ta) 도우미 정리·상한 상수 명명 후 DependencyFailureIT 재실행 GREEN. — 선행: T014
- [X] T016 [US2] VERIFY (orch) DependencyFailureIT + 영향 범위(`ReservationRollbackIT`·`ReservationCancelIT`·`RedisFailureIT`·`IdempotencyIT`, 설정 변경 시 `./gradlew verify verifyContract`) 실행·기록. 커밋(제품 수정과 테스트를 목적별로 분리). — 선행: T015
- [X] T017 [US2] REVIEW (rev) US2 diff를 spec US2·FR-005~011·C2 기준으로 리뷰(복구 누락·컨테이너 상태 누수·폴링 상한·500 숨김·소켓 상한 근거). — 선행: T016

## Phase 5: US3 — 로컬 부하 측정 (P2)

**독립 인수**: L1~L4 각 3회 결과·DB 검사 저장, L5 self-check 비0, docs/performance.md 작성.

- [X] T018 [US3] GREEN (impl) `R/application-load.yaml` 작성(plan 설계 8). VERIFY(orch): `LOAD_RATE_LIMIT` 미설정 기동 실패, 설정 시 `/actuator/health` UP·Flyway 적용(`reservation_load`)·`127.0.0.1` 바인딩 확인. — 선행: T002
- [X] T019 [US3] (ta) `load/` 스크립트 작성: `README.md`, `lib.js`, `reservation-load.js`(low·high·replay), `rate-limit.js`, `prepare-db.sh`, `reset.sh`, `check-invariants.sql`, `run.sh`(결과를 `docs/performance-runs/<run-id>/`에 저장, k6 또는 검사 비0이면 비0). 비밀번호는 환경 변수만. — 선행: T018
- [X] T020 [US3] RED(판정기) (orch) `EXPECT_CREATED=999 ./load/run.sh high selfcheck` 비0 확인, load DB에서 보존식을 깨는 고의 UPDATE 후 `check-invariants.sql` 단계 비0 확인 → reset. — 선행: T019
- [X] T021 [US3] 측정 (ta, orch 확인) 앱 `LOAD_RATE_LIMIT=100000`으로 L1 low×3, L2 high×3, L3 replay×3, 앱 재기동 `LOAD_RATE_LIMIT=20`으로 L4 rate-limit×3. 매회 reset. 결과·종료 코드 저장. — 선행: T020
- [X] T022 (해당 없음: 판정 미달 없음) [US3] 조건부 (orch·adv·impl) L1 미달 시 병목 후보 1개를 측정(예: 풀 대기·BCrypt CPU·DB 지연)하고 하나만 변경해 같은 조건 재측정. 정합성 실패(L2~L4)는 결함으로 분석. 미달이 없으면 “해당 없음” 기록. — 선행: T021
- [X] T023 [US3] VERIFY·기록 (orch) `docs/performance.md` 작성(FR-017 항목 전체·판정·한계). 커밋 `feat(load)`·`test(load)`·`docs(performance)` 목적별. — 선행: T022
- [X] T024 [US3] REVIEW (rev) load 설정·스크립트·보고서를 spec US3·FR-012~018·C3 기준으로 리뷰(초기화 대상 한정, threshold 판정, 예상 외 상태 분리, 비밀정보). — 선행: T023

## Phase 6: 문서 동기화·최종 검증·PR

- [X] T025 (orch) 문서 동기화: `docs/test-evidence.md` 11단계 절, `docs/versions.md`(k6·소켓 상한), `docs/decisions.md`(장애 재현 방식·소켓 상한·load 설정), README(DependencyFailureIT·load 절차), 계획서(상단 현재 상태·3.1 k6·5.3 소켓 상한(적용 시)·6.2 수·11단계 체크박스·13절 해당 항목), `knowledge/rules/environment-and-records.md`·`development-and-testing.md`, `knowledge/domain/security.md`·`reservation.md`, `knowledge/README.md` 요약. — 선행: T017·T024
- [X] T026 (orch) 최종 게이트 `cd backend && ./gradlew build deepTest mutationSmoke verifyContractDependencies` 종료0·그룹별 수·실패0·skip0, `git diff --exit-code -- backend/src/main`(고의 변경 잔존 없음) 기록. — 선행: T025
- [ ] T027 (rev) 전체 로컬 독립 리뷰(develop 대비 diff). 지적은 orch가 원인별 재할당 후 재검증. — 선행: T026
- [ ] T028 (orch) Spec Kit Converge. Converged가 아니면 추가 작업 분배·검증 후 재실행. — 선행: T027
- [ ] T029 (orch) push·Draft PR(develop 대상) 생성. — 선행: T028
- [ ] T030 (rev) Codex의 Alibaba Open Code Review를 `ocr delegate` 방식으로 PR 정확 diff에 실행, 유효 항목만 GitHub PR Review API로 파일·라인 코멘트 게시. — 선행: T029
- [ ] T031 (orch) PR 코멘트 조회·분류·재할당, 수정 후 테스트·필요 Spec Kit 검증·재리뷰. Critical/High·실패 테스트·누락 요구사항 0까지 반복. Merge는 사용자 승인 대기. — 선행: T030

## 의존성과 병행

- US1(T004~T009)과 US2(T010~T017)는 서로 다른 테스트 파일이지만 같은 Docker 자원을 쓰므로 실행(VERIFY)은 순차로 한다. 작성은 병행 가능(ta 1명이 순차 작성 권장).
- US3의 T018(impl)은 US1·US2와 병행 가능(파일 분리). T013과 T018은 모두 impl이 `R/`를 수정하므로 같은 세션에서 순차로 처리한다.
- 고의 변경(T006·T014)은 한 번에 하나만 적용하며 그동안 다른 `src/main` 작업을 하지 않는다.
