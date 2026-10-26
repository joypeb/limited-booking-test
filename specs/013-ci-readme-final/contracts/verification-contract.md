# Verification Contract: 12단계

각 검사 스크립트는 `set -euo pipefail`, 항목별 `FAIL <ID>: <이유>` 출력, 실패 1개 이상이면 종료 1, 모두 통과면 `PASS <n> checks`와 종료 0. 저장소 루트에서 실행한다. 입력 파일·디렉터리가 없으면 실패(종료 1)다.

## C1. `scripts/check-workflows.sh [dir]` (기본 `.github/workflows`)

| ID | 대상 | 통과 조건 |
|---|---|---|
| W1 | 전체 | `rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667`로 검사 오류 0. 이미지 실행 실패도 FAIL |
| W2 | 전체 | `continue-on-error` 문자열 0건 |
| W3 | 전체 | 실패 무시 구문 0건: `\|\| true`, `\|\| :`, `set +e`, Gradle 태스크 제외 `-x `/`--exclude-task` |
| W4 | 전체 | 모든 `uses:`가 `owner/repo[/path]@<40자 16진 SHA>` 형식. `runs-on`에 `latest` 없음 |
| W5 | 전체 | 모든 워크플로에 `permissions:`와 `contents: read`, 모든 job에 `timeout-minutes:` |
| W6 | `verify.yml` | 존재. `pull_request`·`push` 트리거와 `develop`·`main` 브랜치, `schedule` 없음. `java-version: '25'`, `gradle/actions/setup-gradle`, `./gradlew --no-daemon verifyContract`, `actions/upload-artifact` 단계에 `if: always()`와 `backend/build/test-results`·`backend/build/reports/tests` 경로 |
| W7 | `deep-tests.yml` | 존재. 트리거는 `workflow_dispatch`만(`push`·`pull_request`·`schedule` 없음), 입력 `qt_seed`. `./gradlew --no-daemon propertyTest mutationSmoke`, `.github/scripts/property-summary.sh` 호출, `if: always()` upload에 `backend/build/reports/pitest`와 `backend/build/test-results/propertyTest` 경로. `cancel-in-progress: false` |

탐지력: orch가 임시 사본에 W1(문법 오류)·W2(`continue-on-error: true`)·W4(태그 `@v7`)·W6(명령을 `verify`로 변경)·W7(`push:` 추가) 결함을 각각 넣어 해당 ID FAIL·종료 1을 확인한다. 사본은 커밋하지 않는다.

## C2. `scripts/check-readme.sh [file]` (기본 `README.md`)

| ID | 통과 조건 |
|---|---|
| R1 | `## Quick Start` 절 존재, 그 안에 하위 절 8개 순서대로: 필요 환경, 환경 변수, Compose 시작과 준비 대기, 마이그레이션, 애플리케이션 실행, health 확인, API 전체 흐름, 종료 (제목에 해당 단어 포함) |
| R2 | 준비 대기·healthcheck·health·접속 URL 차이를 설명하는 절(`healthcheck`와 `/actuator/health`와 `127.0.0.1` 모두 포함) |
| R3 | 문서의 모든 `./gradlew <task>`의 task가 `backend/build.gradle`에 정의되었거나 Gradle/Boot 기본 태스크(`test`·`build`·`bootRun`·`bootJar`·`clean`·`compileJava`·`compileTestJava`)다 |
| R4 | 문서의 모든 `./load/<f>`·`load/<f>`·`scripts/<f>`·`.github/<path>` 경로가 실제 존재 |
| R5 | 기본 절차의 `docker compose down`에 `-v`/`--volumes` 없음(“하지 않는다” 설명 문장 제외: 코드 블록만 검사) |
| R6 | 검증 명령 표의 Unit·IT·Contract·Property 수치가 인자로 받은 기대값(기본: 308·322·102·4)과 일치하는 문자열을 포함 |

## C3. `scripts/check-traceability.sh [file]` (기본 `docs/traceability.md`)

| ID | 통과 조건 |
|---|---|
| T1 | 필수 규칙 ID 행 존재: R01 이름·수량 경계, R02 요청당 1개·사용자 UUID는 인증에서, R03 상태 전이 CONFIRMED→CANCELLED, R04 수량 불변식, R05 생성 키 형식, R06 멱등 재현·충돌, R07 실패 키 미보존, R08 품절, R09 취소 복구 최대 1·중복 취소, R10 실패 롤백, R11 잠금 순서·경쟁, R12 소유자 권한·타인 404, R13 관리자 권한, R14 인증·위조 무시, R15 입력 엄격·본문 상한, R16 오류 형식, R17 요청 제한, R18 Redis 장애, R19 DB 장애·timeout, R20 계약(OpenAPI), R21 속성·변이 |
| T2 | 각 행의 대상 경로(백틱 `backend/src/main/...`)가 존재 |
| T3 | 각 행의 테스트 `Class#method`에 대해 `backend/src/test`에 `Class.java`가 있고 그 파일에 `method(`가 존재 |
| T4 | 명령의 Gradle task가 C2 R3 기준으로 존재, 증거 링크(상대 경로)의 파일이 존재 |
| T5 | `## 검증하지 않은 범위` 절과 표 행 1개 이상, 각 행에 이유 칸이 비어 있지 않음 |

## C4. 새 환경 재현 판정 (orch)

API 흐름 기대값(대상 capacity 2로 시작):

| 단계 | 요청 | 기대 |
|---|---|---|
| 1 | ADMIN `POST /api/resources` | 201, Location, available 2 |
| 2 | USER_A `POST /api/reservations` 키 K1 | 201, `Idempotent-Replayed: false`, CONFIRMED |
| 3 | 같은 요청 재전송 | 201, 같은 id, `Idempotent-Replayed: true` |
| 4 | USER_A `GET /api/reservations/{id}` | 200 CONFIRMED |
| 5 | USER_B 같은 id GET | 404 `NOT_FOUND`(존재 여부 비노출) |
| 6 | USER_A `POST …/cancel` | 200 CANCELLED, cancelledAt |
| 7 | 같은 취소 재전송 | 200, 같은 cancelledAt |
| 8 | 단계 2 재전송 | 201, 같은 id, CONFIRMED(최초 재현), Replayed true |
| 9 | `GET /api/resources/{id}` | 200, available 2 |

## C5. 최종 기준점 판정 (orch)

- `build deepTest mutationSmoke verifyContractDependencies --rerun-tasks` 종료 0, 그룹별 tests>0·failures 0·errors 0·skipped 0, PIT generated=killed·게이트 통과.
- 원래 제한 IT 4종 통과, 기본 제한 20/60000ms 적용 근거(테스트 출력·설정 단언) 기록.
- k6 4종 각 1회 종료 0·`check.txt` PASS·meta commit=B·dirty=false.
- 고의 결함 잔존 0(plan D6).
