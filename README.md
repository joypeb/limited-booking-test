# 한정 수량 예약 백엔드

한정 수량 예약의 정합성(예약·취소·재시도·경쟁·의존성 장애)을 localhost에서 재현 가능하게 검증하는 Java 25·Spring Boot 4.1.1·Gradle 9.8.0 백엔드다. 0~11단계(도구·저장·규칙·인증·예약 생성/취소·경쟁·Redis 제한·계약·Property/PIT·부하/보안/장애 복구)를 완료했다. 12단계(CI·README·최종 검증)도 완료했고 사용자 시연·작은 변경·설명만 대기 중이다. 2026-10-26 사용자 지시로 localhost 전용 [브라우저 콘솔](#브라우저-콘솔frontend)을 추가했다. 설계 기준은 [로컬 개발 계획](<한정 수량 예약 백엔드 로컬 개발 계획.md>)이다.

## Quick Start

저장소 루트에서 실행한다. 아래 명령은 `127.0.0.1`에서만 동작하며 외부 공개용이 아니다.

### 1. 필요 환경

| 항목 | 확인 명령 |
|---|---|
| JDK 25 | `java -version` |
| Docker와 Compose | `docker compose version` |
| curl | `curl --version` |

API 흐름은 curl과 셸 기본 도구(awk·sed)만 사용한다. k6는 [부하 시험](#부하-시험)에서만 필요하다.

### 2. 환경 변수

```sh
cp .env.example .env
# .env에 POSTGRES_USER, POSTGRES_PASSWORD, ADMIN_PASSWORD, USER_A_PASSWORD, USER_B_PASSWORD 값을 채운다(커밋 금지).
set -a
source .env
set +a
```

Compose의 `.env` 보간은 호스트 JVM에 자동 전달되지 않는다. 앱을 실행할 셸에서 위처럼 export한다. IDE에서 실행하면 같은 변수를 별도로 설정한다.

### 3. Compose 시작과 준비 대기

```sh
docker compose up -d --wait --wait-timeout 120
docker compose ps
```

`--wait`는 서비스 healthcheck가 통과할 때까지 기다린다. `docker compose ps`에서 `postgres`와 `redis`가 healthy여야 한다.

### 4. 마이그레이션

별도 명령은 없다. 다음 단계에서 앱이 기동할 때 Flyway가 스키마를 적용한다. 앱 기동 뒤 적용 이력을 확인한다.

```sh
docker compose exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d reservation_local -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank"'
```

`success`가 모두 `t`여야 한다.

### 5. 애플리케이션 실행

방법 a. Gradle로 실행한다.

```sh
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

방법 b. jar를 만들어 실행한다.

```sh
cd backend
./gradlew bootJar
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

앱은 별도 터미널에서 실행하고 두 터미널 모두 2단계의 환경 변수를 export해야 한다.

### 6. health 확인

```sh
curl --connect-timeout 3 --max-time 10 --fail http://127.0.0.1:8080/actuator/health
```

`status`가 `UP`이어야 한다(실제 응답 예: `{"groups":["liveness","readiness"],"status":"UP"}`). health만 노출하며 상세값은 숨긴다.

### 7. API 전체 흐름

환경 변수를 export한 터미널에서 실행한다. 계정은 `ADMIN`·`USER_A`·`USER_B`(대소문자 구분)이며 `-u "<계정>:$<계정>_PASSWORD"`로 Basic 인증한다. 응답의 Location 헤더에서 id를 꺼내므로 jq가 필요 없다. 생성·취소는 사용자당 20회/60초로 제한되며 아래 흐름은 5회를 쓴다.

```sh
BASE=http://127.0.0.1:8080
api() { curl --connect-timeout 3 --max-time 10 -sS -w '\n' "$@"; }
loc() { awk 'tolower($1)=="location:"{print $2}' | tr -d '\r' | sed 's#.*/##'; }
K1="readme-$(date +%s)"

# 1. ADMIN이 capacity 2인 대상을 만든다.
R=$(api -i -u "ADMIN:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"name":"readme-demo","capacity":2}' "$BASE/api/resources"); printf '%s\n' "$R"
RES_ID=$(printf '%s\n' "$R" | loc)

# 2. USER_A가 예약한다(최초).
R=$(api -i -u "USER_A:$USER_A_PASSWORD" -H 'Content-Type: application/json' -H "Idempotency-Key: $K1" \
  -d "{\"resourceId\":\"$RES_ID\"}" "$BASE/api/reservations"); printf '%s\n' "$R"
RSV_ID=$(printf '%s\n' "$R" | loc)

# 3. 같은 요청을 다시 보낸다(재현).
api -i -u "USER_A:$USER_A_PASSWORD" -H 'Content-Type: application/json' -H "Idempotency-Key: $K1" \
  -d "{\"resourceId\":\"$RES_ID\"}" "$BASE/api/reservations"

# 4. 소유자가 조회한다.
api -i -u "USER_A:$USER_A_PASSWORD" "$BASE/api/reservations/$RSV_ID"

# 5. 다른 사용자가 같은 id를 조회한다.
api -i -u "USER_B:$USER_B_PASSWORD" "$BASE/api/reservations/$RSV_ID"

# 6. 소유자가 취소한다(본문 없음).
api -i -u "USER_A:$USER_A_PASSWORD" -X POST "$BASE/api/reservations/$RSV_ID/cancel"

# 7. 같은 취소를 다시 보낸다.
api -i -u "USER_A:$USER_A_PASSWORD" -X POST "$BASE/api/reservations/$RSV_ID/cancel"

# 8. 단계 2를 다시 보낸다(취소 후에도 최초 응답 재현).
api -i -u "USER_A:$USER_A_PASSWORD" -H 'Content-Type: application/json' -H "Idempotency-Key: $K1" \
  -d "{\"resourceId\":\"$RES_ID\"}" "$BASE/api/reservations"

# 9. 대상의 잔여 수량을 확인한다.
api -i -u "USER_A:$USER_A_PASSWORD" "$BASE/api/resources/$RES_ID"
```

| 단계 | 기대 |
|---|---|
| 1 | 201, `Location: /api/resources/<id>`, `available` 2 |
| 2 | 201, `Idempotent-Replayed: false`, `status` `CONFIRMED` |
| 3 | 201, 단계 2와 같은 id·필드, `Idempotent-Replayed: true` |
| 4 | 200, `CONFIRMED`, `cancelledAt` null |
| 5 | 404, `code` `NOT_FOUND`(존재 여부 비노출) |
| 6 | 200, `CANCELLED`, `cancelledAt` 값 |
| 7 | 200, 단계 6과 같은 `cancelledAt` |
| 8 | 201, 단계 2와 같은 id, `CONFIRMED`(최초 응답 재현), `Idempotent-Replayed: true` |
| 9 | 200, `available` 2(취소로 1개 복구, 중복 취소는 복구하지 않음) |

### 8. 종료

앱은 Ctrl-C로 종료하고 프로젝트 루트에서 서비스만 내린다.

```sh
docker compose down
```

PostgreSQL named volume은 보존한다. 볼륨 삭제 옵션은 기본 절차에 넣지 않는다. Redis 재시작 뒤 키 보존은 요구하지 않는다. 테스트는 별도 동적 컨테이너를 쓰며 개발 DB를 재사용하지 않는다.

## 준비 대기·healthcheck·health·접속 URL

| 구분 | 대상 | 의미 |
|---|---|---|
| Compose `--wait` | `docker compose up -d --wait` | 서비스의 healthcheck가 통과할 때까지 명령이 대기한다. 앱은 포함하지 않는다. |
| 서비스 healthcheck | postgres `pg_isready`, redis `redis-cli ping` | 컨테이너 안에서 2초 간격으로 실행되며 서버 수락 여부만 본다. |
| 앱 `/actuator/health` | 호스트에서 실행한 앱 | 앱 기동과 DB·Redis 연결을 반영한다. 앱 준비 상한 60초, 응답 상세는 숨긴다. |

| 접속 | 주소 |
|---|---|
| 앱 | `127.0.0.1:8080` |
| PostgreSQL(호스트) | `127.0.0.1:5432`의 `reservation_local` (부하 시험은 `reservation_load`) |
| Redis(호스트) | `127.0.0.1:6379` |
| Compose 내부 | 서비스 이름 `postgres`, `redis` (앱은 호스트에서 실행하므로 사용하지 않음) |
| 테스트 | Testcontainers 동적 포트, 앱은 `RANDOM_PORT`. 개발 Compose와 무관 |

모든 포트는 `127.0.0.1`에만 바인딩한다.

## 테스트 실행

`backend/`에서 실행한다. Unit은 JDK 25만 필요하다. IT·Contract·Property는 Docker가 필요하며 Testcontainers가 별도 동적 컨테이너를 만든다(개발 Compose 불필요). 실행 0건·실패·skip은 모두 비성공이다.

| 명령 | 범위·현재 JUnit 수 |
|---|---|
| `./gradlew test` | Spring 없는 Unit308 |
| `./gradlew verify` | Unit308 + 일반IT322 (경쟁110회·Redis 제한/장애 포함) |
| `./gradlew verifyContract` | Unit308·IT322·Contract102 (고정 OpenAPI 3.0.3 대 실제 HTTP) |
| `./gradlew verifyProperty` | Unit308·IT322·Property4 |
| `./gradlew deepTest` | Unit308·IT322·Contract102·Property4 |
| `./gradlew mutationSmoke` | PIT 23/23 탐지(생존 0), 탐지율 ≥ 0.80 게이트 |

위 수는 11단계 최종 게이트 `./gradlew build deepTest mutationSmoke verifyContractDependencies --rerun-tasks`(종료 0)의 값이며 근거는 [test-evidence](docs/test-evidence.md)다.

- 보고서: XML `backend/build/test-results/<task>/`, HTML `backend/build/reports/tests/<task>/`, PIT `backend/build/reports/pitest/`.
- Property 재현: `QT_SEED=<정수> ./gradlew propertyTest`. 실행마다 `seed=`·`attempts=`·`scenario-digest=`를 출력한다.
- 정적 검사(저장소 루트): `scripts/check-workflows.sh`(워크플로, Docker 필요), `scripts/check-readme.sh`(이 문서의 명령·경로·수치 대조).
- 계약 검증 의존성: `./gradlew verifyContractDependencies`.

## 브라우저 콘솔(frontend)

2026-10-26 사용자 지시로 추가한 localhost 전용 조작 콘솔이다(Next.js 16.4.3·Tailwind 4.3.3, [도메인 문서](knowledge/domain/frontend-console.md), [명세](specs/014-frontend-console/spec.md)). 백엔드 API 5개를 화면에서 호출하고 상태·필드·`Location`·`Idempotent-Replayed`·`Retry-After`·오류 `code`를 원문으로 보여 준다. 브라우저는 같은 출처의 콘솔 서버(`/api/backend/...`)만 호출하고 콘솔 서버가 계약 경로만 백엔드로 전달한다(백엔드 CORS 없음). 비밀번호는 탭 메모리에만 있어 새로고침하면 다시 입력한다. 목록 API가 없으므로 이 브라우저에서 다룬 식별자를 최근 기록으로 보여 준다.

Node.js 20.9 이상(검증: v26.11.1)이 필요하다. 위 Quick Start로 백엔드를 띄운 뒤 다른 터미널에서 실행한다.

```sh
cd frontend
npm ci
npm run dev
```

`http://127.0.0.1:3000`을 연다. 백엔드 주소는 서버 환경 변수 `BACKEND_BASE_URL`(기본 `http://127.0.0.1:8080`, 루프백만 허용), 콘솔 포트를 바꾸면 `CONSOLE_PORT`도 같은 값으로 둔다.

| 명령(`frontend/`) | 범위 |
|---|---|
| `npm test` | Unit·Proxy(실제 HTTP 가짜 업스트림)·Component, 0건·skip·실패 거부 게이트 |
| `npm run lint` / `npm run typecheck` / `npm run build` | ESLint / `next typegen` + `tsc` / 프로덕션 빌드 |
| `npm run test:e2e` | Playwright, 실제 백엔드 대상(아래 격리 절차) |

E2E는 로컬 개발 DB를 쓰지 않는다. `npm run test:e2e`는 실행기(`frontend/scripts/run-e2e.sh`)로, Compose PostgreSQL에 `reservation_e2e`를 준비하고 백엔드 jar를 포트 18080·`reservation_e2e`·Redis DB 1로 직접 띄운 뒤 Playwright를 실행하고 끝나면 내린다. 18080이 이미 사용 중이거나 실행기 없이 Playwright를 직접 실행하면 실패한다. 비밀번호는 셸 환경 변수(`ADMIN_PASSWORD` 등)로 준다. 절차는 [실행 안내](specs/014-frontend-console/quickstart.md) 3절이며, 요청 제한(20회/60초) 때문에 연속 실행 사이에 60초 이상 둔다. 실행 근거는 [실행 증거](docs/test-evidence.md) 14단계 절이다.

## CI

| 워크플로 | 트리거 | 실행 | 산출물 |
|---|---|---|---|
| `.github/workflows/verify.yml` | `pull_request`·`push` (develop, main) | `./gradlew --no-daemon verifyContract` (Java 25 Corretto) | `verify-test-reports` |
| `.github/workflows/deep-tests.yml` | `workflow_dispatch`만 (`qt_seed` 선택 입력) | `./gradlew --no-daemon propertyTest mutationSmoke` | `deep-property-reports`, `deep-pit-reports` |

`deep-tests.yml`은 기본 브랜치에 반영된 뒤 수동 실행할 수 있고, Unit/IT/Contract 회귀는 `verify.yml`이 맡는다. 재현 정보 요약은 `.github/scripts/property-summary.sh`가 만든다. 외부 실행 결과: Draft PR #17의 [verify run 40269778342](https://github.com/joypeb/limited-booking-test/actions/runs/40269778342)(pull_request, head 4b692f3, 2026-10-26)이 success다. ubuntu-24.04·Corretto 25.0.5에서 `verifyContract` 6분 18초, Unit308·IT322·Contract102, 실패·skip 0이고 artifact `verify-test-reports`를 올렸다. 같은 날 develop push [verify run 40287542511](https://github.com/joypeb/limited-booking-test/actions/runs/40287542511)과 main push [verify run 40289671284](https://github.com/joypeb/limited-booking-test/actions/runs/40289671284)도 success다. main 반영 후 수동 실행한 [deep-tests run 40289776529](https://github.com/joypeb/limited-booking-test/actions/runs/40289776529)(workflow_dispatch, main 9e6a11b, 2026-10-26)도 success다. Corretto 25.0.5에서 propertyTest 4건(실패·skip 0), `attempts=149`·seed 20261023·`scenario-digest`가 로컬 arm64 실행과 같았고 PIT는 generated=23·killed=23이며 artifact `deep-property-reports`·`deep-pit-reports`를 올렸다. 외부에서 테스트 실패가 job 실패로 전파되는지는 검증하지 않았다.

## 부하 시험

[load/README.md](load/README.md)의 k6 스크립트(`low`·`high`·`replay`·`rate-limit`)를 `reservation_load` DB와 `load` 프로필로 실행하고 threshold 종료 코드와 최종 DB 검사로 판정한다. 결과와 한계는 [성능 기록](docs/performance.md)을 따르며 로컬 수치를 운영 규모로 일반화하지 않는다.

## 문서 지도

- [로컬 개발 계획](<한정 수량 예약 백엔드 로컬 개발 계획.md>): 설계 기준(2~5절 규칙·API·DB).
- [knowledge/README.md](knowledge/README.md): 작업 규칙과 도메인 지식.
- [docs/openapi.yaml](docs/openapi.yaml): 고정 OpenAPI 3.0.3.
- [docs/decisions.md](docs/decisions.md), [docs/versions.md](docs/versions.md), [docs/test-evidence.md](docs/test-evidence.md), [docs/performance.md](docs/performance.md).
- [specs/](specs/): 단계별 명세·계획·작업·계약.

## 단계별 기록

날짜별 상세 수치는 당시 기록이며 현재 수는 [테스트 실행](#테스트-실행)을 따른다. 근거는 [실행 증거](docs/test-evidence.md)다.

- **2단계 저장 기반**: [업무 V1](backend/src/main/resources/db/migration/V1__create_reservation_schema.sql)이 resources/reservations와 수량·유일성·FK·상태 제약을 만든다. [ResourceRepository](backend/src/main/java/com/limitedbooking/backend/resource/ResourceRepository.java)·[ReservationRepository](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationRepository.java)가 저장값을 다룬다. 적용된 V1은 편집하지 않는다. [2단계 안내](specs/003-define-db-schema/quickstart.md).
- **3단계 순수 규칙**: [ResourceRules](backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java)(수량 0~1000·이름 1~100자), [IdempotencyKey](backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java), [CancellationDecision](backend/src/main/java/com/limitedbooking/backend/reservation/CancellationDecision.java). [규칙 계약](specs/004-validate-domain-rules/contracts/rules-contract.md).
- **4단계 로컬 인증·대상 API**: [SecurityConfig](backend/src/main/java/com/limitedbooking/backend/security/SecurityConfig.java)의 고정 UUID `ADMIN`·`USER_A`·`USER_B` 무상태 Basic 인증(BCrypt), 8KiB 본문 상한, 공통 오류 계약. [4단계 계약](specs/005-auth-resource-api/contracts/auth-resource-contract.md).
- **5단계 예약 생성**: [ReservationController](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationController.java)·[Service](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationService.java)·[Writer](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationWriter.java). 성공한 예약 행이 키 기록이며 같은 키·같은 대상은 최초 201을 재현하고 다른 대상은 409다. 실패한 키는 보존하지 않는다. [검증 안내](specs/006-reservation-create/quickstart.md).
- **6단계 조회·취소**: 소유자만 조회·취소하고 타인은 관리자에게도 404다. 취소와 수량 1 복구는 Writer의 단일 트랜잭션이며 중복 취소는 시각·수량을 유지한다. [6단계 안내](specs/007-reservation-cancel/quickstart.md).
- **7단계 경쟁**: [ReservationConcurrencyIT](backend/src/test/java/com/limitedbooking/backend/reservation/ReservationConcurrencyIT.java)가 실제 HTTP·PostgreSQL에서 C01~C11을 각 10회(110회) 실행한다. [110회 기록](docs/concurrency-runs.json). Qodana(2026-10-20) 지적 분류는 [검토 기록](docs/qodana-review.md)이며 Qodana 실행은 사용자 지시에 따른 미실행 후속이다.
- **8단계 Redis 제한**: 사용자별 고정 창 Lua limiter, 생성·취소 합산 20회/60초, 초과 429+`Retry-After`, Redis 장애 503·DB 무변경(실제 장애는 [RedisFailureIT](backend/src/test/java/com/limitedbooking/backend/ratelimit/RedisFailureIT.java)). 연결/명령 timeout 200ms. [작업 상태](specs/009-redis-rate-limit/tasks.md).
- **9단계 계약 검증**: 고정 [OpenAPI 3.0.3](docs/openapi.yaml)을 기대값으로 실제 요청·응답 원문을 [검사기](backend/src/test/java/com/limitedbooking/backend/support/ApiContractValidator.java)가 검사한다. 응답 사본 훼손 44종 거부와 DTO 타입 변이 탐지를 확인했다. [계약 의존성 시험](backend/src/test/java/com/limitedbooking/backend/common/ApiContractDependencyIT.java).
- **10단계 Property·PIT**: `ReservationPropertyIT`가 서비스 경로의 명령 1~30건을 독립 모델과 비교하고 고의 결함의 축소 사례를 `ReservationPropertyRegressionIT`에 고정했다. PIT는 순수 규칙 23/23 탐지다.
- **11단계 부하·보안·장애 복구**: [SecurityIT](backend/src/test/java/com/limitedbooking/backend/security/SecurityIT.java)에 `X-User-Id` 위조 무시·로그 비밀 비노출을, [DependencyFailureIT](backend/src/test/java/com/limitedbooking/backend/reservation/DependencyFailureIT.java)에 DB 연결 중단·응답 정지(pause)·취소 잠금 시간 초과·커밋 후 응답 유실의 503·같은 키 복구 시험을 추가했다(Redis 장애는 8단계 [RedisFailureIT](backend/src/test/java/com/limitedbooking/backend/ratelimit/RedisFailureIT.java)). DB 정지 시 무한 대기를 pgjdbc `socketTimeout=7`로 막았다. [검증 계약](specs/012-load-security-recovery/contracts/verification-contract.md).
- **12단계 CI·README·최종 검증**: CI 워크플로, README Quick Start(새 환경 재현), 최종 기준점 전체 재실행, 추적표, AI 결함 기록, 시연 자료를 완료했다. verify와 deep-tests의 외부 실행은 성공했고 사용자 시연·작은 변경·설명만 대기 중이다. [실행 근거](docs/test-evidence.md). [명세](specs/013-ci-readme-final/spec.md).

## PostgreSQL 보존 표식

기동한 뒤 실행 담당자는 이번 시험 UUID와 기대값(예:73)을 기록한다. 아래 `<이번 UUID>`를 실제 새 UUID로 바꿔 루트에서 실행한다. 비밀값은 psql 인자에 넣지 않는다.

```sh
docker compose exec -T postgres sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d reservation_local' <<'SQL'
SELECT current_database();
BEGIN;
CREATE SCHEMA IF NOT EXISTS toolchain_smoke;
CREATE TABLE IF NOT EXISTS toolchain_smoke.retention_probe (id uuid PRIMARY KEY, value integer NOT NULL);
INSERT INTO toolchain_smoke.retention_probe VALUES ('<이번 UUID>',73);
COMMIT;
SQL
```

current_database는 reservation_local이어야 한다. 기존 표 구조가 UUID PK·integer NOT NULL과 다르면 삽입 전에 중단한다. 새 연결로 다음 SELECT를 실행해 같은 UUID/73을 확인한 뒤 `docker compose down`, `docker compose up -d --wait --wait-timeout 120`을 순서대로 실행하고 같은 SELECT를 다시 확인한다.

```sh
docker compose exec -T postgres sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d reservation_local' <<'SQL'
SELECT id,value FROM toolchain_smoke.retention_probe WHERE id='<이번 UUID>';
SQL
```

commit 전 오류는 rollback하고 연결을 닫는다. commit 응답 유실은 새 연결로 같은 UUID를 조회한다. 행이 없으면 준비 미완료, 같은 값이면 준비 commit 확인, 값 불일치나 접속/구조 불명은 인수 보류다. 재시작 중단은 동일 volume과 commit 표식을 확인한 뒤 down→up→조회 전체를 다시 수행한다. 상세 조건은 [복구 계약](specs/002-setup-test-toolchain/contracts/validation-contract.md)을 따른다.

성공 후 실행 담당자는 소유가 확인된 이번 UUID의 행만 `DELETE FROM toolchain_smoke.retention_probe WHERE id='<이번 UUID>';`로 정리한다. 실패 표식은 원인 조사·증거 확보 전에 지우지 않는다. schema/DB 전체 초기화나 volume 제거는 하지 않는다.
