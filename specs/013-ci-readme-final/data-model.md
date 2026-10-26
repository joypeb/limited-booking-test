# Data Model: 12단계

업무 데이터(대상·예약)와 스키마는 변경하지 않는다. 아래는 검증·기록 산출물의 구조다.

## 워크플로 (verify / deep-tests)

| 필드 | verify | deep-tests |
|---|---|---|
| 트리거 | pull_request·push (develop, main) | workflow_dispatch(input `qt_seed`) |
| runner | ubuntu-24.04 | ubuntu-24.04 |
| 권한 | contents: read | contents: read |
| 동시 실행 | ref별 cancel-in-progress true | ref별 cancel-in-progress false |
| 시간 상한 | 45분 | 60분 |
| 명령 | `./gradlew --no-daemon verifyContract` | `./gradlew --no-daemon propertyTest mutationSmoke` |
| artifact(always) | verify-test-reports | deep-property-reports, deep-pit-reports |

## CI 실행 기록

`run id · workflow · event · head SHA · conclusion · 소요 시간 · JDK 실제 버전 · 그룹별 tests/failures/skipped · artifact 이름` — `docs/test-evidence.md` 12단계 절.

## 최종 기준 커밋 B

`SHA · 날짜 · 이후 허용 경로(README.md, docs/, specs/, knowledge/, 계획서, AGENTS.md, scripts/check-*.sh) · 실행 목록(명령·종료 코드·수·결과 위치)`.

## 추적 행 (`docs/traceability.md`)

| 열 | 형식 | 규칙 |
|---|---|---|
| ID | `R01`… | contracts C3의 필수 ID 전부 포함 |
| 규칙 | 한 줄 + 계획서 절 | 계획서 2~5절 근거 |
| 대상 | `backend/src/main/...` 경로(백틱) | 실제 존재 |
| 테스트 | `Class#method`(백틱) | 메서드가 해당 클래스 파일에 존재 |
| 명령 | `./gradlew <task> [--tests ...]` | task 존재 |
| 증거 | 문서 링크(절) | 링크 파일 존재 |

미검증 행: `범위 · 이유 · 관련 단계`.

## 결함 기록 (test-evidence 12단계 절)

`단계 · 구분(실제/고의) · 재현 · 원인 · 탐지 테스트 · 수정(커밋·파일) · 한계`.

## 새 환경 재현 기록

`clone 경로·커밋 · Compose 프로젝트 · 단계(1~8)별 명령·종료 코드·관찰 · API 단계별 상태/수량 기대 vs 실제 · 정리`.
