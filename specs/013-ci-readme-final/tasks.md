# Tasks: CI·재현 가능한 실행 안내·최종 검증 기록

**입력**: [명세](spec.md), [계획](plan.md), [조사](research.md), [데이터 모델](data-model.md), [검증 계약](contracts/verification-contract.md), [실행 안내](quickstart.md)
**상태**: 2026-10-26 T001~T030 완료. 리뷰 지적은 검사기 보완(2f451c8·c4585ed·0b5749e)·요약 스크립트(b88df61)·문서(d992d3b·0d176ac)로 반영. 실행 중 실제 결함 1건(`load/run.sh` git_dirty 항상 true)을 발견·수정(3ddf007)해 기준점을 c4585ed→3ddf007로 갱신(백엔드 동일). T031 전체 로컬 리뷰(Critical/High/Medium 0, Low 2 반영)·T032 Converge(Converged, 추가 작업 0) 완료. T033 Draft PR #17·verify run 40269778342 success, T035 OCR delegate 리뷰(유효 지적 0, 리뷰 5647455906), T036 코멘트 0건으로 추가 수정 없음. T034 deep-tests 외부 실행은 main 반영(사용자 승인) 후 대기. Merge는 사용자 승인 대기. 실행 근거는 docs/test-evidence.md 12단계 절.

## 형식과 공통 실행 계약

`- [ ] T번호 [P 선택] [US번호] (담당) 설명·경로 — 선행: …`

- 담당: **orch**(실행 검증·기록·Git/PR), **scaf**(scaffolder), **ta**(test-author: 이번 단계는 `scripts/check-*.sh`, 실제 결함 시 `backend/src/test`), **impl**(implementer: 이번 단계는 `.github/**`·`README.md`·`docs/traceability.md`·`docs/demo.md`, 실제 결함 시 `backend/src/main`), **rev**(reviewer, 수정 금지), **adv**(advisor).
- 단계: RED(검사가 의도한 항목으로 실패) → GREEN(최소 구성으로 같은 검사 통과) → REFACTOR(동작 불변 정리·재실행) → VERIFY(orch 독립 실행·결함 사본 탐지력·기록) → REVIEW(rev).
- 실행 불가(파일 부재로 스크립트 종료 1)도 RED로 인정하되, 반드시 `FAIL <ID>` 출력으로 의도한 항목임을 확인한다. 셸 문법 오류는 RED가 아니다.
- 제품 코드·SQL·API·의존성·이미지 무변경. 실제 결함이 드러나면 orch가 기록 → ta 재현 테스트 RED → impl 최소 수정 GREEN.
- 같은 파일을 두 에이전트가 동시에 수정하지 않는다. `[P]`는 서로 다른 파일·선행 완료 시에만 병행한다. 같은 문제 3회 반복 시 adv.
- 명령·종료 코드·수·결과는 orch가 `docs/test-evidence.md` 12단계 절에 순차 기록한다.

## Phase 1: 준비

- [X] T001 (orch) 루트·브랜치 `feature/013-ci-readme-final`·HEAD·작업 트리 확인, 환경 기록(`java -version`, `./gradlew --version`, `docker info` 버전·CPU·메모리, `k6 version`, 포트 5432·6379·8080 비어 있음). — 선행: 없음

## Phase 2: 공통 골격

- [X] T002 [P] (scaf) `docs/traceability.md`·`docs/demo.md` 골격만 작성: traceability는 제목, 사용법 1문단 자리, 표 머리(`ID | 규칙(계획서 절) | 대상 | 테스트 | 명령 | 증거`), `## 검증하지 않은 범위` 표 머리(`범위 | 이유 | 관련 단계`). demo는 제목, `## 5분 시연 대본`(네 주제 하위 절 제목만), `## 작은 변경 연습 안내`, `## 수행 기록(사용자 작성)`. 내용·행 금지. — 선행: T001

## Phase 3: US1 — verify CI (P1)

**독립 인수**: `scripts/check-workflows.sh` PASS(W1~W6), 결함 사본 W1·W2·W4·W6 각각 FAIL, Draft PR의 실제 verify run 결과 기록(T031).

- [X] T003 [US1] RED (ta) `scripts/check-workflows.sh` 작성: contracts C1 공통 규칙 W1~W6(W7은 T009). 디렉터리 인자, actionlint 고정 digest 이미지(`docker run --rm -v "<abs dir>:/repo/.github/workflows:ro" -w /repo <image>`), 항목별 `FAIL <ID>` 출력. — 선행: T001
- [X] T004 [US1] RED 확인 (orch) 워크플로 부재 상태에서 실행 → 종료 1·`FAIL`(부재/W6) 출력 기록. — 선행: T003
- [X] T005 [US1] GREEN (impl) `.github/workflows/verify.yml` 작성(plan D1, 고정 SHA·corretto 25·setup-gradle·`./gradlew --no-daemon verifyContract`·`if: always()` artifact·권한·timeout·concurrency·TZ/LANG). 같은 검사 PASS 확인. — 선행: T004
- [X] T006 [US1] REFACTOR (impl) 단계 이름·주석(태그 표기)·순서 정리 후 검사 재실행 PASS. — 선행: T005
- [X] T007 [US1] VERIFY (orch) 검사 PASS 확인, `/private/tmp` 사본에 W1·W2·W4·W6 결함을 하나씩 넣어 해당 ID FAIL·종료 1 확인(커밋 금지). 로컬 `cd backend && ./gradlew --no-daemon verifyContract` 1회 실행으로 CI 명령의 로컬 동등 결과 기록. 커밋 `ci: …`와 `test(ci): …`(스크립트) 분리. — 선행: T006
- [X] T008 [US1] REVIEW (rev) US1 diff를 spec US1·FR-001·002·004·005·C1 기준으로 리뷰(실패 은닉, 권한, SHA 고정, artifact 경로, 검사 스크립트의 거짓 PASS 가능성). — 선행: T007

## Phase 4: US2 — deep-tests 수동 워크플로 (P2)

**독립 인수**: 검사 W7 PASS, 결함 사본 W7 FAIL, 로컬 동등 실행의 요약(seed·attempts≥100·PIT 수) 확인. 외부 run은 main 반영 후(T034).

- [X] T009 [US2] RED (ta) `scripts/check-workflows.sh`에 W7 추가. 실행 → `FAIL W7`(파일 부재) 확인. — 선행: T008
- [X] T010 [US2] GREEN (impl) `.github/workflows/deep-tests.yml`(plan D2)과 `.github/scripts/property-summary.sh`(XML `<system-out>`에서 seed/attempts/generated-attempts/scenario-digest 줄 추출, XML 0개면 종료 1, 실행 권한) 작성. 검사 PASS. — 선행: T009
- [X] T011 [US2] REFACTOR (impl) 정리 후 검사 재실행 PASS. — 선행: T010
- [X] T012 [US2] VERIFY (orch) W7 결함 사본(push 트리거 추가) FAIL 확인. 로컬 `cd backend && ./gradlew --no-daemon propertyTest mutationSmoke` 실행 후 `property-summary.sh` 출력(seed·attempts·PIT generated/killed) 기록, XML 없는 디렉터리 인자에서 종료 1 확인. 커밋. — 선행: T011
- [X] T013 [US2] REVIEW (rev) US2 diff를 FR-003·004·C1 W7 기준으로 리뷰(수동 전용, seed 전달, 요약 추출의 빈 결과 통과 위험, artifact 경로). — 선행: T012

## Phase 5: US3 — README Quick Start와 새 환경 재현 (P1)

**독립 인수**: `scripts/check-readme.sh` PASS(R1~R6), 새 환경 재현이 contracts C4와 일치.

- [X] T014 [P] [US3] RED (ta) `scripts/check-readme.sh` 작성(contracts C2 R1~R6, 기대 수치는 인자 `--counts 308,322,102,4` 기본값). 현재 README로 실행 → R1 등 FAIL 기록. — 선행: T001 (T003과 다른 파일)
- [X] T015 [US3] GREEN (impl) README 재구성(plan D4): Quick Start 8단계·대기/healthcheck/health/URL 차이 표·검증 명령 표(최신 수치)·CI 절·부하 링크·문서 지도·단계별 기록 축약. 모든 명령은 저장소에 실제 존재해야 함. `scripts/check-readme.sh` PASS. — 선행: T014, T010
- [X] T016 [US3] REFACTOR (impl) 중복·과거 수치 정리, 링크 확인 후 검사 재실행. — 선행: T015
- [X] T017 [US3] VERIFY (orch) 새 환경 재현(plan D5): 현재 커밋을 새 clone, `COMPOSE_PROJECT_NAME=lbfresh`, 깨끗한 셸에서 README 1~8단계와 빌드 산출물 실행 경로까지 수행, contracts C4 대조. 실패 단계는 원인 분류(문서→impl T015 재할당, 제품 결함→실제 결함 절차) 후 재실행. 임시 프로젝트만 `down -v`, 기존 볼륨 보존 확인. 커밋 `docs(readme): …`·`test(docs): …`. — 선행: T016, T012
- [X] T018 [US3] REVIEW (rev) README·검사 스크립트를 FR-007~010·C2 기준으로 리뷰(없는 명령, 볼륨 삭제, 비밀정보 노출, 수치 불일치). — 선행: T017

## Phase 6: US4 — 최종 기준점 검증 (P1)

**독립 인수**: contracts C5 전부 충족, 기준점 SHA 기록.

- [X] T019 [US4] (orch) 기준점 B 고정: T018까지 반영·리뷰 지적 처리된 깨끗한 HEAD의 SHA 기록. 이후 허용 경로 목록 확정. — 선행: T018
- [X] T020 [US4] VERIFY (orch) B에서 `cd backend && ./gradlew build deepTest mutationSmoke verifyContractDependencies --rerun-tasks` → 그룹별 수·PIT·종료 코드 기록. 실패 시 원인 분류(실제 결함→ta/impl, 환경→adv). — 선행: T019
- [X] T021 [US4] VERIFY (orch) 원래 제한 IT 지정 실행(`SecurityIT`·`RateLimitApiIT`·`RedisRateLimitIT`·`RedisFailureIT`)과 20/60000ms 적용 근거 기록. — 선행: T020
- [X] T022 [US4] VERIFY (orch) load 환경(load/README) 준비 후 k6 low·high·replay(100000/60초)·rate-limit(20/60초) 각 1회, meta commit=B·dirty=false, `check.txt` PASS 확인. 결과 디렉터리 비밀정보 부재 검사. — 선행: T021
- [X] T023 [US4] VERIFY (orch) 고의 결함 잔존 검사(plan D6): porcelain 비어 있음, `git diff --exit-code B -- backend load compose.yaml .github`, 고의 표식 검색 0건. 결과 커밋 `docs(evidence): …`(결과 디렉터리 포함). — 선행: T022
- [X] T024 [US4] REVIEW (rev) 최종 실행 기록을 FR-011·012·C5 기준으로 대조(수치·SHA·dirty·무효 실행 구분). — 선행: T023

## Phase 7: US5 — 추적표·결함 기록·시연 자료 (P2)

**독립 인수**: `scripts/check-traceability.sh` PASS(T1~T5), 결함 기록 각 사례 근거 확인, 시연 자료 4주제.

- [X] T025 [P] [US5] RED (ta) `scripts/check-traceability.sh` 작성(contracts C3 T1~T5). 골격 상태(T002)에서 실행 → T1·T5 등 FAIL 기록. — 선행: T002, T014(R3 task 판정 재사용 시)
- [X] T026 [US5] GREEN (impl) `docs/traceability.md` 내용(R01~R21 행, 실제 클래스·`Class#method`·명령·증거 링크, 미검증 범위 표)과 `docs/demo.md` 내용(네 주제 대본: 보일 명령·테스트·관찰 포인트·설명 문장, 작은 변경 연습 안내, 수행 기록 칸은 비움). 검사 PASS. — 선행: T025, T023
- [X] T027 [US5] REFACTOR (impl) 표 정리·링크 확인 후 검사 재실행 PASS. — 선행: T026
- [X] T028 [US5] VERIFY (orch) 추적표 무작위 5행을 직접 대조, 시연 대본의 명령을 실제 실행 가능한지 지정 테스트로 1회 확인(예: `--tests` 필터 존재). AI 결함 후보 5건을 커밋·test-evidence로 확인하고 확인된 것만 `docs/test-evidence.md` 12단계 절에 재현→원인→탐지→수정→한계로 기록(고의 변이 결과와 구분). 커밋. — 선행: T027
- [X] T029 [US5] REVIEW (rev) traceability·demo·결함 기록을 FR-013~015·C3 기준으로 리뷰(없는 참조, 근거 없는 결함, 사람 항목 체크 여부). — 선행: T028

## Phase 8: 기록 동기화·전체 검증

- [X] T030 (orch) 기록 동기화(plan D8): decisions·versions·performance·test-evidence·계획서(6.2 수치, 12단계·13절 체크박스는 실제 검증 항목만, 사람 항목·외부 미실행 사유, 진행 기록 행)·knowledge(README 목록, environment-and-records 문서 책임 표·CI 규칙, development-and-testing의 CI 적용)·AGENTS.md CI 현황. 세 검사 스크립트 재실행 PASS, 링크 확인. 커밋. — 선행: T029
- [X] T031 (rev) 전체 로컬 리뷰: develop 대비 전체 diff를 spec·contracts·헌법 기준으로 리뷰(Critical/High/Medium/Low). 지적은 orch가 분류해 재할당·재검증. — 선행: T030
- [X] T032 (orch) Spec Kit Converge. Converged가 아니면 추가 작업 분배·검증 후 재실행. — 선행: T031

## Phase 9: GitHub PR

- [X] T033 (orch) push·Draft PR(develop 대상) 생성. verify run 자동 실행 결과(run id·결론·JDK·그룹별 수·artifact)를 확인·기록. 실패 시 원인 분류·재할당. — 선행: T032
- [ ] T034 (orch) deep-tests 외부 run: main 반영 전에는 수동 트리거 불가 → 미실행(대기)으로 기록하고 사용자에게 보고. 반영 후 사용자 지시 시 `gh workflow run deep-tests.yml` 1회 실행·기록. — 선행: T033
- [X] T035 (rev) PR의 정확한 diff를 Codex의 Open Code Review **ocr delegate** 방식으로 리뷰하고, 유효 항목만 GitHub PR Review API/`gh`로 파일·라인 코멘트 게시. Actions 사용 금지, 코드 수정 금지. — 선행: T033
- [X] T036 (orch) PR 코멘트 조회·분류 → 해당 에이전트 재할당 → 수정 후 테스트·필요 Spec Kit 검증·재리뷰. Critical/High·실패 테스트·누락 요구사항 0까지 반복. Merge는 사용자 승인 대기. — 선행: T035

## 의존성과 병행

- US1 → US2(같은 검사 스크립트) → US3 GREEN(README가 워크플로를 설명) → US4(기준점) → US5 GREEN(추적표가 최종 증거 참조) → Phase 8.
- 병행 후보: T002(scaf)·T003(ta)·T014(ta는 T003 뒤 순차 — 같은 담당자), T025는 T014 뒤.
- 사람 수행 대기(작업 아님): 5분 시연, AI 없는 작은 변경, AI 없는 주요 선택 설명(13절).

## 요구사항 추적

| 요구 | 작업 |
|---|---|
| FR-001·002·004·005·006, SC-001·003 | T003~T008, T033 |
| FR-003, SC-002 | T009~T013, T034 |
| FR-007~010, SC-004·007 | T014~T018 |
| FR-011·012, SC-005·008 | T019~T024 |
| FR-013~015, SC-006 | T025~T029 |
| FR-016·017 | T030~T032 |
