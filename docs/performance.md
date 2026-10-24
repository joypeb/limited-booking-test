# 로컬 성능·부하 기록

이 문서는 localhost 단일 기기의 측정 기록이다. 운영 규모·처리 한계·합격률의 근거로 일반화하지 않는다. 모든 수치는 Basic 인증·BCrypt 검증·보안 필터·Redis 요청 제한·PostgreSQL 트랜잭션을 포함한 **전체 HTTP 경로**이며 순수 예약 서비스 비용과 구분한다.

## 2026-10-24 11단계 (specs/012-load-security-recovery)

### 실행 조건

| 항목 | 값 |
|---|---|
| 기기 | Apple M3 Pro, arm64, 11 CPU, 메모리 18GiB, macOS(Darwin 27.0.0) |
| Docker | Docker Desktop 29.8.2, VM 할당 11 CPU·약 7.7GiB |
| JDK | Amazon Corretto 25.0.4 LTS(25.0.4+7) |
| 앱 실행 | `cd backend && ./gradlew bootJar` 후 `java -jar build/libs/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=load`(bootRun과 같은 설정, Gradle 프로세스 오버헤드 제외) |
| 앱 설정 | `application.yaml` + `application-load.yaml`: 127.0.0.1:8080, Hikari 최대 10·연결 획득 3초, pgjdbc socketTimeout 7초, Redis 200ms, 트랜잭션 lock 2초·statement 5초 |
| 요청 제한 | low·high·replay: `LOAD_RATE_LIMIT=100000`/60초(부하용 변경값). rate-limit: 원래 값 20/60초로 앱 재기동 |
| DB·Redis | Compose `postgres:18.6-bookworm@sha256:afc7e2d4…a647c`의 별도 DB `reservation_load`, `redis:8.10.2-trixie@sha256:2c2dff79…6bfd6` |
| 부하 도구 | k6 v2.3.0(darwin/arm64), `--summary-mode=full --summary-export` |
| 계정 | ADMIN(대상 준비), USER_A·USER_B(쓰기). BCrypt 기본 강도(`new BCryptPasswordEncoder()`) |
| 대상 커밋 | 앱 jar: high·replay·low run1~3은 `594ddb0` 작업 트리 + `application-load.yaml`(이후 `a9a93dc`로 같은 내용 커밋)로 빌드, low v2/v3·rate-limit v2/v3는 T026 최종 게이트가 다시 빌드한 jar(제품 소스는 `594ddb0` 이후 무변경). 스크립트: high·replay `90cb0fd`, rate-limit v3 `5f33e4a`, low v3 `00a784a` |
| 작업 트리 | 모든 meta `git_dirty=true`: 미추적 결과 디렉터리와 문서·테스트 파일(앱 실행물·스크립트와 무관) |
| 초기화 | 매 실행 `load/reset.sh`(reservation_load의 reservations→resources DELETE, USER_A·USER_B 제한 키 DEL) |
| 원시 결과 | `docs/performance-runs/20261024-<시나리오>-<label>/`(summary.json·k6.txt·check.txt·meta.txt·reset.txt) |

### 결과 (판정 실행)

| 실행 | 요청 수 | 처리량(req/s) | p50 | p95 | p99 | max | 상태 코드별 | 예상 외 | k6 종료 | DB 검사 |
|---|---:|---:|---:|---:|---:|---:|---|---:|---:|---|
| low v3-run1 (측정 2분) | 11,644 | 97.0 | 96.1 | 141.3 | 221.2 | 547.0 | 201 7,324 · 200 7,324 | 0 | 0 | PASS(확정0·수량 전부 복구) |
| low v3-run2 | 12,046 | 100.4 | 94.4 | 133.4 | 186.7 | 514.4 | 201 7,543 · 200 7,543 | 0 | 0 | PASS |
| low v3-run3 | 13,028 | 108.6 | 89.3 | 112.6 | 140.7 | 259.3 | 201 8,094 · 200 8,094 | 0 | 0 | PASS |
| high run1 | 2,001 | 89.0 | 533.2 | 835.0 | 1,047.6 | 1,207.8 | 201 1,000 · 409 SOLD_OUT 1,000 | 0 | 0 | PASS(확정1000·잔여0) |
| high run2 | 2,001 | 106.2 | 464.4 | 578.0 | 710.0 | 874.7 | 201 1,000 · 409 SOLD_OUT 1,000 | 0 | 0 | PASS |
| high run3 | 2,001 | 101.1 | 493.0 | 605.2 | 664.7 | 759.6 | 201 1,000 · 409 SOLD_OUT 1,000 | 0 | 0 | PASS |
| replay run1 | 501 | 90.7 | 97.6 | 146.6 | 351.4 | 382.5 | 201 500(최초 50·재현 450) | 0 | 0 | PASS(고유 예약 50) |
| replay run2 | 501 | 98.4 | 94.6 | 123.7 | 144.4 | 208.9 | 201 500 | 0 | 0 | PASS |
| replay run3 | 501 | 85.2 | 100.2 | 200.6 | 305.9 | 349.5 | 201 500 | 0 | 0 | PASS |
| rate-limit v3-run1~3 | 25 | - | - | - | - | - | fill 201 20 → limited 429 RATE_LIMITED 5(Retry-After 정수≥1) | 0 | 0·0 | PASS(예약 20, 429 전후 업무 행 `cmp` 동일, 소요 2~3초) |

- 지연 단위 ms.
- low: 단일 10VU·2분30초 시나리오(최대 VU 10 확인)에서 반복 **시작 시각**이 시나리오 시작(setup 이후) 30초 이후인 반복을 `phase:measure`로 태그했다. 요청 수·지연은 measure 태그만, 처리량은 measure 요청 수/120초, 상태 코드별 건수는 Counter가 준비+측정 전체를 센 값이다(전체 `http_reqs` 14,658/15,096/16,198에는 setup 대상 생성 10건 포함). 경계에 걸친 반복은 시작 시각으로 분류한다. 스크립트 `00a784a`.
- high·replay: 요청 수·지연·처리량은 setup 대상 생성 **1건을 포함한** 전체 지표(`http_reqs`·`http_req_duration`)다. 상태 코드별 건수는 업무 요청만 센 Counter다.
- rate-limit: fill(20회)과 limited(5회)를 같은 60초 창에서 연속 실행하고, 그 사이·이후의 resources·reservations 전체 행 JSON을 비교했다.
- 모든 판정 실행의 `http_req_failed` 비율 0, measure `checks` 비율 1. replay의 id 불일치 0, 최초 `Idempotent-Replayed: false`·재요청 `true`.

### 판정 제외 실행(참고·무효)

| 실행 | 사유 | 결과 |
|---|---|---|
| low v2-run1~3 | 2차 스크립트(`5f33e4a`): phase 판정 시계가 setup 이전부터 세어 warmup이 setup 소요(약 1초)만큼 짧았다(전체 리뷰 Medium). `00a784a`에서 수정 후 v3로 재측정 | 참고: measure p95 141.3/111.8/119.4ms, 예상 외 0, DB PASS |
| low run1~3 | 초기 스크립트: warmup(30초+종료 유예)과 measure(10VU)가 겹쳐 측정 초반 최대 20VU가 경쟁할 수 있었다(로컬 리뷰 Medium). `5f33e4a`에서 수정 후 v2로 재측정 | 참고: measure p95 135.4/113.0/108.9ms, 예상 외 0, DB PASS |
| rate-limit run1~3 | 초기 스크립트: 429의 DB 무변경을 최종 집계(예약 20·잔여 80)로만 판정(로컬 리뷰 Medium). v3로 재측정 | 참고: 201 20·429 5, DB PASS |
| rate-limit v2-run1~3 | **측정 하네스 오류(무효)**: 직전 100000 제한 앱(PID 23567)이 종료되지 않아 제한 20 앱이 포트 충돌로 기동 실패했고, 실행은 100000 제한 앱을 대상으로 했다. 종료가 늦어진 원인은 미확인이다. 이후 포트 해제를 확인한 뒤 기동하는 절차로 v3를 실행했다 | limited 5회가 모두 201, 429 전후 업무 행 불일치로 k6 종료99·검사 종료3. 새 검사가 오설정을 탐지했다. 제품 결함 아님 |
| high selfcheck(`EXPECT_CREATED=999`) | 판정기 검증용 고의 기대 불일치 | k6 종료 **99**, DB 검사 PASS |

별도로 보존식을 깬 고의 UPDATE에서 `check-invariants.sql`이 종료 3(불변식 FAIL)을 냈다.

### 판정

| 시나리오 | 기준 | 판정 |
|---|---|---|
| 낮은 경쟁 | 잠정 p95<500ms·예상 외 오류율<1% | v3 3회 모두 **통과**(p95 112.6~141.3ms, 오류 0%). 최초 기준선이며 잠정 기준이다 |
| 높은 경쟁 | 201=1000·SOLD_OUT 409=1000·기타0·확정1000/잔여0 | 3회 모두 **통과** |
| 재요청 | HTTP 성공 500, 고유 예약 50, id 불일치0 | 3회 모두 **통과** |
| 요청 제한 | 21번째부터 429·Retry-After≥1·429의 DB 변경0 | v3 3회 모두 **통과** |

정합성·권한 위반 0건. 판정 미달이 없어 병목 변경·재측정(T022)은 수행하지 않았다.

### 관찰과 한계

- 처리량이 low(10VU)·high(50VU) 모두 약 100 req/s에서 비슷하고, VU를 5배 늘리자 p50이 약 90ms→약 500ms로 늘었다. 요청마다 수행하는 BCrypt 검증의 CPU 비용이 지배적이라는 가설과 일치하지만 **프로파일링으로 확인하지 않았다(미측정 가설)**. DB 풀 대기·Redis 지연과의 분리 측정도 하지 않았다.
- high run1의 p95가 다른 두 회보다 높다(835 vs 578·605ms). JIT 예열·첫 경쟁 부하의 영향으로 추정하며 원인은 미확인이다. low v3-run1·run2의 p95(141.3·133.4ms)도 run3보다 높으며 원인은 미확인이다.
- 단일 기기에서 k6·앱·Docker VM이 CPU를 공유한다. 네트워크 지연·다중 인스턴스·운영 DB 설정을 반영하지 않는다.
- 측정은 각 시나리오 3회뿐이며 통계적 신뢰 구간을 계산하지 않았다.
