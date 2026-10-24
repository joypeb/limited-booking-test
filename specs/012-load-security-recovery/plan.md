# Implementation Plan: 로컬 부하·보안·의존성 장애 복구 검증

**Branch**: `feature/012-load-security-recovery` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-load-security-recovery/spec.md`

## Summary

계획서 11단계를 구현한다. (1) `SecurityIT`에 예약 경로의 `X-User-Id` 위조 무시와 로그 비노출 검사를 추가하고, 이미 충족된 보안 항목은 기존 테스트로 연결한다. (2) 새 `DependencyFailureIT`가 테스트 소유 PostgreSQL에서 연결 강제 종료·신규 연결 거부, 서버 응답 정지(컨테이너 pause), 확정 예약 취소의 잠금 시간 초과, 커밋 후 응답 유실을 재현해 503·응답 시간 상한·부분 저장 없음·같은 키 복구를 검증한다. 응답 정지에서 무한 대기가 재현되면 드라이버 소켓 읽기 상한(7초) 설정을 최소 수정으로 추가한다. (3) `application-load.yaml`과 `load/`의 k6 시나리오(낮은 경쟁·높은 경쟁·재요청·제한)·DB 준비/초기화/불변식 검사 스크립트를 만들고, 3회씩 실행해 `docs/performance.md`에 기록한다.

## Technical Context

**Language/Version**: Java 25(preview 제외), Gradle Wrapper 9.8.0, Spring Boot 4.1.1

**Primary Dependencies**: 기존 고정 의존성만 사용(Testcontainers 2.0.5·JUnit 6.0.3·Logback은 Boot BOM). 새 Gradle 의존성 없음. 부하 도구 k6 v2.3.0(로컬 설치 `/opt/homebrew/bin/k6`, 실행 확인 후 versions.md 기록). DB 검사는 `docker compose exec postgres psql`.

**Storage**: IT는 테스트 소유 PostgreSQL 18.6·Redis 8.10.2 컨테이너(`ContainerTestSupport`, 고정 digest). 부하는 Compose의 같은 이미지에 별도 DB `reservation_load`.

**Testing**: 일반 IT `*IT`(`integrationTest`, 태그 없음) → `verify`/`deepTest`에 포함. 부하는 Gradle 밖 k6 + SQL 검사(종료 코드 판정).

**Target Platform**: localhost macOS arm64(Apple M3 Pro, 11 CPU, 18GiB) + Docker Desktop 29.8.2(할당 메모리 약 7.7GiB, 11 CPU)

**Project Type**: 단일 Spring Boot 백엔드(`backend/`) + 루트 `load/` 스크립트

**Performance Goals**: 낮은 경쟁 잠정 p95 < 500ms·예상치 못한 오류율 < 1%(달성 여부는 측정 후 판정). 높은 경쟁은 정확성 기준(201=1000, SOLD_OUT 409=1000).

**Constraints**: 장애는 테스트 소유 컨테이너에만, 후크는 `src/test`에만. 클래스 병렬 실행 금지. 상한: 잠금 4초·연결 거부 6초·응답 정지 12초. 계약·업무 규칙 무변경. 부하 제한값은 필수 환경 변수. `.env`·비밀번호 미커밋.

**Scale/Scope**: 계정 3개(ADMIN·USER_A·USER_B), 낮은 경쟁 10VU·대상 10개×수량 1000, 높은 경쟁 50VU·2000회·수량 1000, 재요청 10VU×키 5개×10회, 제한 1사용자 25회.

## Constitution Check

*GATE: Phase 0 전 확인, Phase 1 뒤 재확인.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 서비스 경계·API 호환 | 통과 | API·SQL·오류 계약 무변경. 제품 변경 후보는 load 설정 신규와(재현 시) 소켓 읽기 상한 설정·실제 결함의 최소 수정뿐 |
| II. 도메인 근거 | 통과 | knowledge 규칙 전체·reservation/resource/security/ratelimit 문서·계획서 2~5·6·7절 11단계 확인, Explorer 갭 분석 |
| III. 검증 방식·증거 | 통과 | 새 IT는 RED(실제 결함 또는 고의 변경) → GREEN, k6는 고의 기대 불일치로 비0 종료 확인, 실행 수·종료 코드 기록 |
| IV. 문서 동기화 | 통과(작업 포함) | test-evidence·performance·versions·decisions·README·계획서 5.3(설정 변경 시)/11단계·13절·knowledge 갱신을 Tasks에 포함 |
| V. 미확정 정책 | 통과 | 업무 정책 질문 없음. 설계 모호점 9건 spec Clarifications 기록. 소켓 상한은 재현 시에만 적용하고 결정 기록 |

재확인(Phase 1 뒤): 위반 없음. Complexity Tracking 해당 없음.

## Project Structure

### Documentation (this feature)

```text
specs/012-load-security-recovery/
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
backend/src/main/resources/
├── application.yaml                 # (조건부) spring.datasource.hikari.data-source-properties.socketTimeout: 7
└── application-load.yaml            # 신규: reservation_load·풀10/3초·Redis·제한값 필수 환경 변수·127.0.0.1
backend/src/main/java/.../common/ApiExceptionHandler.java   # (조건부) 장애 중 500이 재현될 때만 최소 분류 수정
backend/src/test/java/com/limitedbooking/backend/
├── security/SecurityIT.java         # 확장: 예약 경로 X-User-Id 위조, 로그 비노출
├── support/LogCapture.java          # 신규: Logback ListAppender 캡처(AutoCloseable)
├── support/PostgresFaults.java      # 신규: 관리 연결로 연결 차단/종료·복구, pause/unpause
└── reservation/DependencyFailureIT.java  # 신규: 연결 중단·응답 정지·취소 잠금·응답 유실
load/
├── README.md                        # 실행 절차(준비·시나리오·검사·결과 위치)
├── lib.js                           # 공통: 인증 헤더·대상 준비·상태 분류
├── reservation-load.js              # SCENARIO=low|high|replay
├── rate-limit.js                    # 20/60초 제한 시나리오
├── prepare-db.sh                    # reservation_load 없을 때 생성
├── reset.sh                         # load DB DELETE(reservations→resources) + 제한 키 삭제
├── check-invariants.sql             # 대상별 보존식·범위·키 유일성 위반 집계
└── run.sh                           # reset → k6 → check → docs/performance-runs/<run-id>/ 저장
docs/performance.md                  # 신규 보고서
docs/performance-runs/               # 실행별 k6 summary JSON·DB 검사 결과
```

**Structure Decision**: 계획서 3.3절의 `load/reservation-load.js`·`load/rate-limit.js`·`application-load.yaml`·`docs/performance.md` 위치를 따른다. 장애 도구는 기존 `support/` 관례(ContainerTestSupport·RateLimitTestSupport)에 맞춘다. 실행 결과는 9단계 `docs/concurrency-runs.json` 관례처럼 `docs/` 아래에 남긴다.

## 설계 요점

1. **보안 확장(US1)**: `SecurityIT`에 (a) USER_A가 `X-User-Id: USER_B/ADMIN`으로 생성 → DB `user_id = USER_A`, USER_A GET 200, USER_B GET 404, USER_B cancel 404, 같은 헤더로 USER_A GET/cancel 200 (b) 잘못된 비밀번호·미인증·403·413·400·DB 무관 오류 요청을 실행하는 동안 `LogCapture`로 루트 로거 이벤트를 모아 비밀번호 원문·Base64 Authorization 값이 메시지·인자·스택에 없음을 단언. 응답 비노출은 기존 `assertProblem`/`ReservationCreateIT.assertSafe`를 재사용.
2. **연결 중단(US2-1,2)**: `PostgresFaults.blockAndTerminate(db)` = 컨테이너의 `postgres` DB 관리 연결에서 `ALTER DATABASE <db> ALLOW_CONNECTIONS false` → `pg_terminate_backend`(대상 DB의 다른 pid). 관리 연결은 앱 풀과 별개로 장애 중에도 유지. 생성·취소 각각 503·6초 이내·DB 스냅샷 동일. `restore()` 후 같은 키 생성 201·Replayed false, 실패했던 취소 200·수량 +1.
3. **응답 정지(US2-3)**: Testcontainers `DockerClient.pauseContainerCmd`로 PostgreSQL을 정지(try/finally unpause). HTTP 클라이언트 timeout 20초로 생성 요청 → 503·12초 이내 기대. 현재 소켓 상한이 없으면 RED(시간 초과). GREEN 최소 수정: `application.yaml`에 `spring.datasource.hikari.data-source-properties.socketTimeout: 7`(pgjdbc 초 단위; statement 5초보다 길어 정상 서버 timeout을 앞지르지 않음). unpause 후 DB 무변경·같은 키 201.
4. **취소 잠금(US2-4)**: 별도 연결로 대상 행 `FOR UPDATE` 보유 → 확정 예약 취소 503·4초 이내·상태 CONFIRMED·수량 동일 → 해제 후 같은 취소 200·+1. 생성 잠금은 기존 `ReservationRollbackIT.lockTimeoutIs503AndSameKeyRecovers` 연결.
5. **응답 유실(US2-5)**: `java.net.Socket`으로 HTTP/1.1 POST(Authorization·Idempotency-Key·Content-Length·`Connection: close`) 전송 → DB를 100ms 간격 최대 5초 폴링해 (USER_A, key) 행 확인 → 응답 미수신 상태로 close. 같은 키 재시도 201·같은 id/createdAt·Replayed true·행1·available −1.
6. **제한 카운터 간섭 방지**: `DependencyFailureIT`는 제한을 10,000/60초로 명시(테스트 설정, 기록). Redis는 정상 컨테이너.
7. **결함 발견 시**: 500 등 계약 밖 응답이 나오면 원인(예외 종류·SQLSTATE)을 재현 기록하고 implementer가 `ApiExceptionHandler`의 분류를 최소로 확장(08/57 연결 계열만, 미분류는 500 유지).
8. **load 설정(US3)**: `application-load.yaml` = 127.0.0.1:8080, `jdbc:postgresql://127.0.0.1:5432/reservation_load`, 자격 증명 환경 변수, 풀 10/3초, Redis 127.0.0.1:6379·200ms, `reservation.rate-limit.limit: ${LOAD_RATE_LIMIT}`·`window: ${LOAD_RATE_WINDOW:60000ms}`(limit 기본값 없음), 보안 비밀번호 환경 변수, health만 노출.
9. **k6 시나리오**: 공통 `lib.js`가 ADMIN으로 대상 생성(setup). 상태 분류 Counter(`created_201`, `sold_out_409`, `unexpected`)와 Rate(`unexpected_rate`).
   - low: `warmup`(constant-vus 10, 30s) + `measure`(constant-vus 10, 2m, startTime 30s). 반복마다 새 키 생성→취소, 대상 10개 순환. threshold `http_req_duration{scenario:measure}` p(95)<500, `unexpected_rate{scenario:measure}` rate<0.01.
   - high: shared-iterations 50VU·2000회, 대상 1개·수량 1000. `expectedStatuses(201,409)`, 409 본문 code 검사. threshold `created_201 count==${EXPECT_CREATED:-1000}`, `sold_out_409 count==1000`, `unexpected count==0`.
   - replay: per-vu-iterations 10VU×50회, VU별 키 5개를 순환해 키당 10회 전송(총 500요청·고유 키 50, 대상 수량 충분). VU 안에서 최초 id 대비 불일치 Counter. threshold `id_mismatch count==0`, `unexpected count==0`. DB 검사로 고유 예약 = 50.
   - rate-limit: 앱을 `LOAD_RATE_LIMIT=20`으로 재기동, USER_A 제한 키 삭제 후 1VU 25회 생성(서로 다른 키). 1~20 비429, 21~25 429·`Retry-After` 정수≥1. DB 검사 예약 수 = 201 수.
10. **판정·저장**: `run.sh <scenario>`가 reset → `k6 run --summary-export` → `psql -f check-invariants.sql`(위반 행 0이 아니면 비0) → `docs/performance-runs/<date>-<scenario>-<n>/`에 summary.json·check.txt·meta.txt(commit·제한값·이미지). k6 비0 또는 검사 비0이면 스크립트 비0.
11. **고의 기대 불일치**: `EXPECT_CREATED=999 ./load/run.sh high`가 비0 종료하는지 1회 확인(SC-007).

## 역할 분담

| 역할 | 범위 |
|---|---|
| scaffolder | `support/LogCapture`·`support/PostgresFaults` 컴파일 가능한 골격(메서드 시그니처·`UnsupportedOperationException`), `DependencyFailureIT` 클래스 골격(컨테이너·속성 등록만). 업무 로직 금지 |
| test-author | 지원 클래스 구현·`SecurityIT` 확장·`DependencyFailureIT` 테스트(RED)·`load/` 스크립트 전부(이번 작업에 한해 `load/` 쓰기 허용)·부하 실행. `src/main` 금지 |
| implementer | `application-load.yaml`, (조건부) `application.yaml` 소켓 상한, (조건부) `ApiExceptionHandler` 최소 수정, 고의 변경 적용/원복. `src/test`·`load/` 금지 |
| reviewer | 로컬 diff 독립 리뷰·PR OCR delegate 리뷰·PR 라인 코멘트. 수정 금지 |
| advisor | 같은 문제 3회 반복·설계 판단 시 |
| orchestrator | Spec Kit 문서·실행 검증·docs/knowledge/README/계획서 기록·Git/PR |

## Complexity Tracking

해당 없음.
