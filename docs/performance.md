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
| 대상 커밋 | 앱 jar: `594ddb0` 작업 트리 + `application-load.yaml`(이후 `a9a93dc`로 같은 내용 커밋). 스크립트: `90cb0fd`(low run1 시작 시점 meta는 `594ddb0`·미커밋 스크립트) |
| 작업 트리 | 모든 meta `git_dirty=true`: 미추적 결과 디렉터리와 테스트 파일 1개(`DependencyFailureIT`, 앱 실행물과 무관) |
| 초기화 | 매 실행 `load/reset.sh`(reservation_load의 reservations→resources DELETE, USER_A·USER_B 제한 키 DEL) |
| 원시 결과 | `docs/performance-runs/20261024-<시나리오>-<label>/`(summary.json·k6.txt·check.txt·meta.txt·reset.txt) |

### 결과

| 실행 | 요청 수 | 처리량(req/s) | p50 | p95 | p99 | max | 상태 코드별 | 예상 외 | k6 종료 | DB 검사 |
|---|---:|---:|---:|---:|---:|---:|---|---:|---:|---|
| low run1 (측정 2분) | 12,002 | 100.0 | 94.2 | 135.4 | 193.4 | 583.4 | 201 7,542 · 200 7,542 | 0 | 0 | PASS(확정0·수량 전부 복구) |
| low run2 | 12,932 | 107.8 | 89.9 | 113.0 | 140.7 | 375.4 | 201 8,138 · 200 8,138 | 0 | 0 | PASS |
| low run3 | 13,038 | 108.7 | 89.8 | 108.9 | 134.3 | 332.1 | 201 8,126 · 200 8,126 | 0 | 0 | PASS |
| high run1 | 2,000 | 89.0 | 533.2 | 835.0 | 1,047.6 | 1,207.8 | 201 1,000 · 409 SOLD_OUT 1,000 | 0 | 0 | PASS(확정1000·잔여0) |
| high run2 | 2,000 | 106.2 | 464.4 | 578.0 | 710.0 | 874.7 | 201 1,000 · 409 SOLD_OUT 1,000 | 0 | 0 | PASS |
| high run3 | 2,000 | 101.1 | 493.0 | 605.2 | 664.7 | 759.6 | 201 1,000 · 409 SOLD_OUT 1,000 | 0 | 0 | PASS |
| replay run1 | 500 | 90.7 | 97.6 | 146.6 | 351.4 | 382.5 | 201 500(최초 50·재현 450) | 0 | 0 | PASS(고유 예약 50) |
| replay run2 | 500 | 98.4 | 94.6 | 123.7 | 144.4 | 208.9 | 201 500 | 0 | 0 | PASS |
| replay run3 | 500 | 85.2 | 100.2 | 200.6 | 305.9 | 349.5 | 201 500 | 0 | 0 | PASS |
| rate-limit run1~3 | 25 | - | - | - | - | - | 201 20 · 429 RATE_LIMITED 5(Retry-After 정수≥1) | 0 | 0 | PASS(예약 20) |
| high selfcheck(`EXPECT_CREATED=999`) | 2,000 | 99.4 | 493.1 | 616.0 | 669.7 | 788.1 | 201 1,000 · 409 1,000 | 0 | **99** | PASS |

- 지연 단위 ms. low의 요청 수·지연은 `scenario:measure` 태그 구간만(준비 30초 제외)이며 처리량은 측정 요청 수/120초다. low의 상태 코드별 건수는 Counter가 준비+측정 전체(약 2분 30초)를 센 값이다(전체 `http_reqs` 15,094/16,286/16,262에서 setup 1건 제외). 나머지 시나리오는 setup의 대상 생성 1건을 제외한 전체 HTTP 요청이다.
- 모든 실행의 `http_req_failed` 비율 0. replay의 id 불일치 0, 최초 응답 `Idempotent-Replayed: false`·재요청 `true` 검사 통과.
- selfcheck는 threshold 판정기가 종료 코드에 반영됨을 보이는 고의 기대 불일치 실행이다(측정 결과 아님). 별도로 보존식을 깬 고의 UPDATE에서 `check-invariants.sql`이 종료 3(불변식 FAIL)을 냈다.

### 판정

| 시나리오 | 기준 | 판정 |
|---|---|---|
| 낮은 경쟁 | 잠정 p95<500ms·예상 외 오류율<1% | 3회 모두 **통과**(p95 108.9~135.4ms, 오류 0%). 최초 기준선 측정이며 잠정 기준이다 |
| 높은 경쟁 | 201=1000·SOLD_OUT 409=1000·기타0·확정1000/잔여0 | 3회 모두 **통과** |
| 재요청 | HTTP 성공 500, 고유 예약 50, id 불일치0 | 3회 모두 **통과** |
| 요청 제한 | 21번째부터 429·Retry-After≥1·429의 DB 변경0 | 3회 모두 **통과** |

정합성·권한 위반 0건. 판정 미달이 없어 병목 변경·재측정(T022)은 수행하지 않았다.

### 관찰과 한계

- 처리량이 low(10VU)·high(50VU) 모두 약 100 req/s에서 비슷하고, VU를 5배 늘리자 p50이 약 90ms→약 500ms로 늘었다. 요청마다 수행하는 BCrypt 검증의 CPU 비용이 지배적이라는 가설과 일치하지만 **프로파일링으로 확인하지 않았다(미측정 가설)**. DB 풀 대기·Redis 지연과의 분리 측정도 하지 않았다.
- high run1의 p95가 다른 두 회보다 높다(835 vs 578·605ms). JIT 예열·첫 경쟁 부하의 영향으로 추정하며 원인은 미확인이다. low run1 측정 중 다른 에이전트의 `compileTestJava`(수 초)가 실행된 시간이 겹쳤을 수 있다.
- 단일 기기에서 k6·앱·Docker VM이 CPU를 공유한다. 네트워크 지연·다중 인스턴스·운영 DB 설정을 반영하지 않는다.
- 측정은 각 시나리오 3회뿐이며 통계적 신뢰 구간을 계산하지 않았다.
