# Implementation Plan: CI·재현 가능한 실행 안내·최종 검증 기록

**Branch**: `feature/013-ci-readme-final` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-ci-readme-final/spec.md`

## Summary

계획서 12단계를 수행한다. (1) `.github/workflows/verify.yml`(PR·push에서 `./gradlew verifyContract`)과 `deep-tests.yml`(수동 `./gradlew propertyTest mutationSmoke`)을 작성하고, 저장소 내 정적 검사 스크립트로 문법·금지 설정·필수 구성을 검증한다(검사 스크립트가 먼저 RED). (2) README를 Quick Start 중심으로 재구성하고 README 명령 대조 스크립트로 검사한 뒤, 새로 복제한 저장소·별도 Compose 프로젝트에서 처음부터 재현한다. (3) 코드 기준점을 고정하고 `deepTest`·`mutationSmoke`·원래 제한의 보안/Redis IT·k6 네 시나리오를 실행·기록하며 고의 결함 잔존을 검사한다. (4) 추적표·미검증 목록·AI 결함 기록·시연 대본/작은 변경 안내를 작성하고 참조 대조 스크립트로 검사한다. (5) decisions·versions·performance·test-evidence·계획서·knowledge를 최종 동기화한다. 제품 코드·SQL·API·의존성은 바꾸지 않는다.

## Technical Context

**Language/Version**: Java 25(preview 제외), Gradle Wrapper 9.8.0, Spring Boot 4.1.1. CI JDK는 로컬과 같은 배포판 Amazon Corretto 25(`actions/setup-java` `distribution: corretto`).

**Primary Dependencies**: 새 Gradle 의존성 없음. CI action(2026-10-26 `gh api`로 최신 release 확인, 커밋 SHA 고정): `actions/checkout` v7.0.1 `3d3c42e5aac5ba805825da76410c181273ba90b1`, `actions/setup-java` v6.1.0 `b843ebf9b913146b38da2eaa7aeddf76074ae1ad`, `gradle/actions/setup-gradle` v6.4.0 `3f5f9adaf7d9fecd50b5935e54106014257a94e6`, `actions/upload-artifact` v7.0.2 `cf430e030ddbb5b0abf93d22962f4752f3646cd9`. 정적 검사: `rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667`(amd64·arm64 index, Docker로 실행).

**Storage**: 변경 없음. 테스트는 `ContainerTestSupport`의 고정 digest(PostgreSQL 18.6·Redis 8.10.2, amd64·arm64 포함 OCI index 확인). 새 환경 재현은 별도 Compose 프로젝트(새 named volume).

**Testing**: CI는 기존 Gradle 태스크만 호출. 문서·CI 검증은 `scripts/` 검사 스크립트(종료 코드 판정)와 실제 실행. 부하는 기존 `load/run.sh`.

**Target Platform**: 로컬 macOS arm64 + Docker Desktop 29.8.2, k6 v2.3.0. CI는 GitHub 호스팅 `ubuntu-24.04`(x86_64, Docker 내장).

**Project Type**: 단일 Spring Boot 백엔드(`backend/`) + 루트 `load/`·`scripts/`·`.github/workflows/`

**Performance Goals**: 해당 없음(성능 판정은 11단계 기준 유지, 12단계 k6는 최종 코드 회귀 확인 1회씩).

**Constraints**: `continue-on-error`·실패 무시 금지, `latest` 계열 runner/이미지 금지, action SHA 고정, 최소 권한(`contents: read`), CI secret 추가 금지. 계약·SQL·업무 규칙·의존성 무변경. 기존 로컬 볼륨 삭제 금지. 외부 실행은 사용자 승인 범위(verify의 PR·push, deep-tests 수동 1회 — 기본 브랜치 반영 후).

**Scale/Scope**: 워크플로 2개, 검사 스크립트 3개 + 요약 추출 스크립트 1개, README 재구성, 신규 문서 2개(`docs/traceability.md`, `docs/demo.md`), 기록 문서 갱신.

## Constitution Check

*GATE: Phase 0 전 확인, Phase 1 뒤 재확인.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 서비스 경계·API 호환 | 통과 | 제품 코드·API·SQL 무변경. 실제 결함 발견 시에만 TDD 최소 수정 |
| II. 도메인 근거 | 통과 | knowledge 규칙 전체·4개 도메인 문서·계획서 3·6·7(12단계)·8.3·13절, Explorer 갭 분석·Advisor 의견 확인 |
| III. 검증 방식·증거 | 통과 | 검사 스크립트 RED → 구성 GREEN → 결함 사본으로 검사 탐지력 확인. 환경 설정은 최소 실행(새 환경 재현·로컬 동등 실행·외부 run). 실행 수·종료 코드 기록 |
| IV. 문서 동기화 | 통과(작업 포함) | README·decisions·versions·performance·test-evidence·계획서(6.2·12단계·13절)·knowledge 목록/규칙 표 갱신을 Tasks에 포함 |
| V. 미확정 정책 | 통과 | CI 실행 범위·사람 전용 항목은 사용자 답변으로 확정. 외부 실패 전파·deep-tests 외부 run은 미검증/대기로 명시 |
| 기술·운영 제약 | 통과 | Gradle 유지, Maven 경로(target/pit-reports·failsafe)는 Gradle 경로로 대응하고 차이 기록. `.env` 미커밋, 기본 종료에 볼륨 삭제 없음 |

Phase 1 재확인: 설계 산출물이 계약·규칙을 바꾸지 않으며 위 판정 유지.

## 설계

### D1. verify.yml (US1)

- 트리거: `pull_request`(branches: develop, main), `push`(branches: develop, main). schedule·workflow_dispatch 없음.
- `permissions: contents: read`. `concurrency: group: verify-${{ github.ref }}`, `cancel-in-progress: true`.
- job `verify`: `runs-on: ubuntu-24.04`, `timeout-minutes: 45`. env `TZ: UTC`, `LANG: C.UTF-8`.
- 단계: checkout → setup-java(corretto, '25') → setup-gradle(캐시) → `working-directory: backend`에서 `./gradlew --no-daemon verifyContract` → `if: always()` upload-artifact(`verify-test-reports`: `backend/build/test-results/**`, `backend/build/reports/tests/**`, `if-no-files-found: warn`, retention 14일).
- `./gradlew verifyContract`는 `verify`(Unit+일반 IT)와 `contractTest`(앞에 `verifyContractDependencies`)를 포함한다(build.gradle 243~246행). 실패 게이트는 Gradle 결과 검사(실행 ≥1·실패0·skip0)를 그대로 쓴다.

### D2. deep-tests.yml (US2)

- 트리거: `workflow_dispatch`만. 입력 `qt_seed`(string, 선택, 기본 빈 값 → 테스트 기본 seed).
- `permissions: contents: read`. `concurrency: group: deep-tests-${{ github.ref }}`, `cancel-in-progress: false`(장시간 실행 보호).
- job: `ubuntu-24.04`, `timeout-minutes: 60`, 같은 setup. 실행 단계는 `QT_SEED`를 입력이 있을 때만 env로 전달하고 `./gradlew --no-daemon propertyTest mutationSmoke`.
- `if: always()` 단계: `.github/scripts/property-summary.sh backend/build/test-results/propertyTest > property-summary.txt`(XML `<system-out>`에서 `seed=`·`attempts=`·`generated-attempts=`·`scenario-digest=` 줄 추출, XML 없으면 비0), 요약을 `$GITHUB_STEP_SUMMARY`에 추가.
- `if: always()` upload: `deep-property-reports`(propertyTest XML/HTML + property-summary.txt), `deep-pit-reports`(`backend/build/reports/pitest/**`).
- 계획서의 `target/pit-reports`·failsafe reports(Maven)는 Gradle의 `build/reports/pitest`·`build/test-results/propertyTest`로 대응한다(decisions에 기록).
- 한계: 단독 실행은 Unit/IT/Contract 회귀를 포함하지 않는다(verify 담당). 기본 브랜치 반영 전에는 수동 실행 불가.

### D3. 검사 스크립트 (테스트 역할, ta 작성)

- `scripts/check-workflows.sh [workflow-dir]`(기본 `.github/workflows`): contracts C1 W1~W7. actionlint는 고정 digest 이미지로 디렉터리를 읽기 전용 마운트해 실행. 디렉터리·파일 부재 시 비0.
- `scripts/check-readme.sh [readme]`(기본 `README.md`): contracts C2 R1~R5. 필수 절·명령 대조(`./gradlew <task>`의 task가 build.gradle·Gradle 기본 태스크에 존재, `./load/*.sh`·`scripts/*.sh` 경로 존재, `docker compose` 하위 명령이 down -v 없이 쓰임).
- `scripts/check-traceability.sh [file]`(기본 `docs/traceability.md`): contracts C3 T1~T4. 표의 클래스 경로·테스트 메서드가 `backend/src`에 존재, 명령의 Gradle task 존재, 증거 링크 파일 존재, 필수 규칙 ID 전부 존재.
- 모든 스크립트: `set -euo pipefail`, 실패 항목을 `FAIL <ID>: <이유>`로 출력, 하나라도 실패면 종료 1, 통과 시 `PASS <n> checks`.

### D4. README (US3)

절 구성: 개요(현재 상태 1문단) → **Quick Start**(1 필요 환경 확인, 2 환경 변수(`.env.example` 복사·export, 호스트 JVM 미주입 주의), 3 Compose 시작과 준비 대기(`--wait`, healthcheck), 4 마이그레이션(앱 기동 시 Flyway 적용, `flyway_schema_history` 확인 SQL), 5 앱 실행(bootRun / `./gradlew bootJar` 후 `java -jar`), 6 health 확인, 7 API 전체 흐름(curl 8단계와 기대 상태·수량), 8 종료) → 준비 대기·healthcheck·health·URL 차이 표 → 검증 명령 표(6.2절, 최신 수치) → CI(워크플로·트리거·artifact·외부 실행 상태) → 부하(load/README 링크) → 문서 지도 → 단계별 기록(기존 단계 절은 축약해 증거 링크로 대체). 만들지 않은 명령 금지.

### D5. 새 환경 재현 (US3 VERIFY, orch)

`/private/tmp/lb-fresh-<UTC>`에 `git clone`(원격 아닌 로컬 저장소 경로, 검증 대상 커밋 checkout), `.env.example` 복사 후 새 임의 값 기입, `env -i`에 가까운 깨끗한 셸(`HOME`·`PATH`만)에서 README 순서대로 실행. `COMPOSE_PROJECT_NAME=lbfresh`로 기존 `limited-booking` 볼륨과 분리. 단계별 명령·종료 코드·응답을 `docs/test-evidence.md`에 기록. 종료 후 이 임시 프로젝트만 `docker compose -p lbfresh down -v`로 정리(기존 볼륨 보존 확인).

### D6. 코드 기준점과 최종 실행 (US4, orch)

- 기준점 B: US1~US3의 워크플로·스크립트·(필요 시) 제품 수정이 커밋된 뒤, 새 환경 재현까지 통과한 커밋. 이후 커밋은 `README.md`·`docs/`·`specs/`·`knowledge/`·계획서·AGENTS.md만 변경 가능(`git diff --name-only B..HEAD`로 확인).
- B의 깨끗한 작업 트리에서: `cd backend && ./gradlew build deepTest mutationSmoke verifyContractDependencies --rerun-tasks`(실행 수·PIT 기록) → 원래 제한 20/60초 IT 지정 실행 `./gradlew integrationTest --tests '*SecurityIT' --tests '*RateLimitApiIT' --tests '*RedisRateLimitIT' --tests '*RedisFailureIT'`(각 IT의 제한 설정값 확인 근거 기록) → k6 low·high·replay(100000/60초)·rate-limit(20/60초) 각 1회(`load/run.sh`, meta의 commit=B·dirty=false 확인).
- 고의 결함 잔존: `git status --porcelain` 비어 있음, `git diff --exit-code B -- backend load compose.yaml .github`, `backend/src/main`에서 고의 표식(`고의`, `INTENTIONAL`, `DELIBERATE`, `FIXME-RED`) 검색 0건, 위 전체 테스트 통과.

### D7. 추적·설명 자료 (US5)

- `docs/traceability.md`: 규칙 ID(R01~)·계획서 절 → 대상 클래스(경로) → 테스트(클래스#메서드) → 명령 → 증거(test-evidence 절/커밋). 필수 규칙 ID는 contracts C3. 끝에 “검증하지 않은 범위와 이유” 표.
- `docs/demo.md`: 5분 시연 대본(네 주제별 1분 내외: 보일 명령·테스트·관찰 포인트·설명 문장) + 작은 변경 연습 안내(키 최대 길이 64→다른 값 변경 시 수정 지점: 계획서 2절·OpenAPI pattern·`IdempotencyKey`·관련 Unit/IT·문서, 수행 순서, 완료 판정). 수행 기록 칸은 비워 둔다(사용자 수행 대기).
- AI 결함 기록: `docs/test-evidence.md` 12단계 절에 Explorer 후보 5건을 커밋·증거로 각각 확인한 것만 재현 → 원인 → 탐지 테스트 → 수정 → 한계로 기록. 고의 변이 검증 결과(10·11단계)와 구분.

### D8. 기록 동기화 (orch)

`docs/decisions.md`(CI 설계·JDK 배포판·트리거·deep 명령·Maven 경로 대응·dispatch 제약·k6 1회 근거), `docs/versions.md`(action/actionlint 버전·runner, CI 실제 JDK), `docs/performance.md`(최종 기준점 k6 결과), `docs/test-evidence.md`(12단계 절), 계획서(6.2 수치, 12단계·13절 체크박스와 미완료 사유, 진행 기록 행), `knowledge/README.md`·`knowledge/rules/environment-and-records.md`(문서 책임 표에 traceability·demo·scripts·workflows), AGENTS.md의 CI 현황 문장.

## Project Structure

### Documentation (this feature)

```text
specs/013-ci-readme-final/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/verification-contract.md
├── checklists/requirements.md, checklists/verification.md
└── tasks.md
```

### Source Code (repository root)

```text
.github/
├── workflows/verify.yml          # 신규 (impl)
├── workflows/deep-tests.yml      # 신규 (impl)
└── scripts/property-summary.sh   # 신규 (impl)
scripts/
├── check-workflows.sh            # 신규 (ta)
├── check-readme.sh               # 신규 (ta)
└── check-traceability.sh         # 신규 (ta)
README.md                          # 재구성 (impl)
docs/traceability.md, docs/demo.md # 신규 (scaf 골격 → impl 내용)
docs/*.md, 계획서, knowledge/      # 기록 갱신 (orch)
backend/                           # 변경 없음(실제 결함 시에만 TDD 최소 수정)
```

**Structure Decision**: 검사 스크립트는 저장소 루트 `scripts/`(로컬 검증 도구), CI 전용 보조 스크립트는 `.github/scripts/`에 둔다. 애플리케이션 구조는 바꾸지 않는다.

## 역할과 파일 범위

| 역할 | 수정 가능 범위(이번 단계) |
|---|---|
| orch | `specs/013-*`, `docs/decisions.md`·`versions.md`·`performance.md`·`test-evidence.md`·`performance-runs/`, 계획서, `knowledge/`, AGENTS.md, `.specify/`, Git/PR |
| scaf | `docs/traceability.md`·`docs/demo.md` 골격(절·표 머리만) |
| ta | `scripts/check-*.sh`, (실제 결함 시) `backend/src/test` |
| impl | `.github/**`, `README.md`, `docs/traceability.md`·`docs/demo.md` 내용, (실제 결함 시) `backend/src/main` |
| rev·adv·explorer | 수정 금지 |

## Complexity Tracking

| 항목 | 필요 이유 | 더 단순한 대안을 택하지 않은 이유 |
|---|---|---|
| 검사 스크립트 3개 | CI·문서 작업의 RED/GREEN과 “만들지 않은 명령 0건”을 기계적으로 판정 | 눈으로 확인은 재현·탐지력 증명이 불가 |
| `.github/scripts/property-summary.sh` | 요약 추출을 로컬에서 실제 XML로 검증 | 워크플로 인라인 스크립트는 기본 브랜치 반영 전 실행 검증 불가 |
