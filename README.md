# 한정 수량 예약 백엔드

Java25·Spring Boot4.1.1·Gradle9.7.1의 localhost CLI/API 개발 프로젝트다. 기준은 [로컬 개발 계획](<한정 수량 예약 백엔드 로컬 개발 계획.md>)이다. 1단계 도구 최소 실행, 2단계 저장 기반, 3단계 순수 규칙의 Unit 실행, 4단계 로컬 Basic 인증·권한·8KiB 본문 상한·예약 대상 생성/조회 API 및 통합 테스트(`SecurityIT`·`ResourceApiIT`)를 검증했다. 5단계 예약 생성·성공 키 재현·전체 롤백을 구현했다. 6단계 소유자 조회·취소·수량 복구·롤백을 구현하고 검증했다. 7단계 실제 경쟁 11종110회와 종료 실패 격리를 검증했다. Redis 요청 제한은8단계 후속이다.

## 로컬 환경

`.env.example`을 참고해 루트 `.env`에 `POSTGRES_USER`, `POSTGRES_PASSWORD`, `ADMIN_PASSWORD`, `USER_A_PASSWORD`, `USER_B_PASSWORD`를 설정한다. 값은 공유·커밋하지 않는다. Compose 보간이 호스트 JVM에도 자동 전달되지는 않는다. IDE에서는 같은 환경 변수를 별도로 설정한다.

프로젝트 루트에서:

```sh
docker compose up -d --wait --wait-timeout 120
set -a
source .env
set +a
cd backend
./gradlew bootRun --args='--spring.profiles.active=local' -Dfile.encoding=UTF-8 -Duser.timezone=UTC
```

앱은 127.0.0.1:8080, PostgreSQL은 127.0.0.1:5432/reservation_local, Redis는 127.0.0.1:6379다. 앱 준비 상한60초, HTTP 연결3초·전체10초로 health를 확인한다. health만 노출하고 상세값은 숨긴다. `ADMIN`·`USER_A`·`USER_B`는 고정 UUID Principal과 환경 변수 주입 비밀번호(BCrypt 보관)를 사용한다. UTF-8/UTC를 사용한다.

```sh
curl --connect-timeout 3 --max-time 10 --fail http://127.0.0.1:8080/actuator/health
```

앱은 Ctrl-C로 정상 종료하고 프로젝트 루트에서 `docker compose down`으로 서비스만 종료한다. PostgreSQL named volume은 보존한다. 볼륨 삭제 옵션은 기본 절차에 넣지 않는다. Redis 재시작 후 키 보존은 요구하지 않는다. 테스트는 별도 동적 컨테이너를 쓰며 개발 DB를 재사용하지 않는다.

## 검증 상태

실제 검증 기록은 [실행 증거](docs/test-evidence.md), 도구 조합은 [버전](docs/versions.md)에서 확인한다. 네 분류 태스크와 현재 실행 결과/XML 게이트를 구성했다. 실행0건·실패·skip·누락 결과는 비성공이다. Test 결과 캐시 및 UP-TO-DATE 재사용은 끄고 실제 실행을 확인한다. 일반 build/check는 순수 Unit만 실행하며 전체 심화 검증을 뜻하지 않는다.

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

## 테스트 실행

backend/에서 실행한다. Unit은 Java25만 필요하다. IT는 실행 가능한 Docker가 추가로 필요하며 개발 Compose 기동은 IT의 선행 조건이 아니다. Testcontainers는 별도 동적 포트·격리 DB를 생성하고 성공/실패 후 이번 자원을 정리한다.

| 명령 | 최소 검증 범위·현재 JUnit 수 |
|---|---|
| `./gradlew test` | Spring 없는 Unit156 (기존143+경쟁지원13) |
| `./gradlew verify` | Unit156+일반IT257 (기존138+DB지원4+경쟁110+fixture5) |
| `./gradlew test --tests '*ToolchainSmokeTest'` | 지정Unit3 |
| `./gradlew test integrationTest --tests '*ToolchainSmokeIT'` | Unit156+지정IT3 (이번 단계 재실행 없음) |
| `./gradlew verifyContract` | 구성상 Unit156+일반IT257+Contract3; 이번 단계 재실행 없음 |
| `./gradlew verifyProperty` | 구성상 Unit156+일반IT257+Property3; 이번 단계 재실행 없음 |
| `./gradlew deepTest` | 네 그룹 구성상 총281; 이번 단계 재실행 없음 |
| `./gradlew mutationSmoke` | PIT 별도: 생성1·탐지1·NO_COVERAGE0 |

`contractTest`는 `*IT`+contract 태그만, `propertyTest`는 `*Test`+property 태그만 실행한다. 이름/태그 필터는 실행 대상을 정하며 공유 test source의 컴파일 범위를 줄이지 않는다. 지정 사례 옵션은 해당 Test 태스크 바로 뒤에 둔다. 최초 실행 근거는 --rerun-tasks로 남겼다.

```sh
QT_SEED=20261013 ./gradlew propertyTest
```

정상 Property는 별도 경계0/100과 생성 예산1000 중 실제 assertion101회다. 실제 assertion 최소100회를 검사하며 JUnit3건과 내부 시도 수를 구분한다. 고의 n>50 결함의 100→51 축소와 동일 seed 재실패·원복 통과는 [증거](docs/test-evidence.md)에 기록했다. 결함은 현재 원복 상태다.

XML은 `backend/build/test-results/<task>/`, HTML은 `backend/build/reports/tests/<task>/`, PIT XML/HTML은 `backend/build/reports/pitest/`에 있다. 계약 fixture는 시험 전용이며 업무 OpenAPI 원본은 docs/openapi.yaml이다. 해당 문서 parse/ref 성공을 업무 HTTP 준수로 해석하지 않는다.

Container 시작120초·자원별 종료10초, HTTP 연결3초·요청10초를 사용한다. 시험 migration 실패는 적용 metadata 확인·실패 컨테이너 정리 후 새 격리 DB에서 정상 migration/insert/select를 다시 검증한다. 실패 DB repair/clean이나 개발 DB 초기화는 하지 않는다. 프로세스 중단으로 cleanup이 실행되지 않았다면 로그의 실행 UUID/컨테이너 ID를 대조해 이번 시험 자원만 정리한다. 정리 불명은 인수 보류다.

도구 프로브는 후속 순수 업무 규칙으로 대체할 예정이다. 업무 스키마와 두 읽기 Repository SQL, 4단계 예약 대상 생성(`ResourceRepository.insert`)·조회·로컬 인증·8KiB 상한·공통 오류 계약을 검증했다. 예약 생성 쓰기 SQL·멱등 응답·전체 롤백을5단계에서 검증했다. 취소 쓰기·소유권·롤백은6단계에서 검증했다. 실제 경쟁 반복은7단계에서 검증했다. 요청 제한·부하·외부 CI는 미검증이다. Gradle10 및 configuration cache 호환성도 검증하지 않았다.

최종 리뷰에서 끝나지 않는 실제 HTTP 응답의 종료 회귀 IT를 추가했다. 앱 IT 클라이언트는 shutdownNow 후 최대10초 awaitTermination을 사용하며 초과는 실패다. 2026-10-13 최초 조합은 일반IT4/총13이며, 이후 종료 회귀로14건, PR 적대적 리뷰의 본문 정체 회귀로15건이 됐다.

앱 IT와 계약 fixture의 문자열 응답 요청은 공통 지원 코드의 sendAsync→Future.get(10초)로 본문 완료까지 기다린다. timeout/중단은 요청 Future를 취소하고 finally에서 클라이언트 등 소유 자원을 정리한다. HttpRequest.timeout만으로는 응답 헤더 이후 본문 정체를 제한하지 못함을 실제 회귀로 확인했다.

## 2단계 저장 기반

[업무 V1](backend/src/main/resources/db/migration/V1__create_reservation_schema.sql)은 resources/reservations와 수량·필수값·PK·사용자/키 유일성·FK·상태/취소시각·길이·대상 인덱스를 생성한다. [대상 조회·생성](backend/src/main/java/com/limitedbooking/backend/resource/ResourceRepository.java)과 [예약 조회](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationRepository.java)는 저장값을 다룬다. 2단계 자체는 생성·취소 트랜잭션이나 예약 HTTP API를 제공하지 않는다. 생성 HTTP/트랜잭션은 아래5단계에서 추가했다.

```sh
./gradlew test integrationTest --tests '*SchemaIT' --tests '*ResourceRepositoryIT' --tests '*ReservationRepositoryIT' --tests '*DatabaseSupportIT'
```

4단계까지 구성은 Unit116+일반IT92(1단계 6 + 2단계 72 + 4단계 14)다. 네 클래스의 fresh/reapplied 두 모드에서 같은 사례를 검증하며, 후자는 validate·migrate0건·표식/이력/구조 보존을 확인한다. [TestData](backend/src/test/java/com/limitedbooking/backend/support/TestData.java)는 전달한 테스트 DataSource에서 독립 커밋과 예약→대상 초기화 한 트랜잭션을 제공한다. [수량 검사](backend/src/test/java/com/limitedbooking/backend/support/DatabaseAssertions.java)는 SELECT1회로 보존식을 관측하고 불일치·대상 부재를 실패시킨다. 고의 SQL 실패의 마이그레이션 전체 롤백·새 DB 복구와 초기화 롤백·장애 제거 후 재요청도 검증한다.

업무 V1이 추가되어 이후 local 앱 기동은 해당 DB에 V1을 적용한다. 이번 단계는 Testcontainers 소유 DB에서만 실행했으며 기존 reservation_local의 실제 적용/전환은 검증하지 않았다. 적용된 V1은 편집하지 않는다. 상세 재현은 [2단계 안내](specs/003-define-db-schema/quickstart.md), 실행 결과는 [증거](docs/test-evidence.md)를 따른다. 3단계 순수 입력 규칙과 4단계 로컬 인증·예약 대상 API는 아래와 같이 구현했다.

## 3단계 순수 규칙

[ResourceRules](backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java)는 수량0~1000과 strip 후 코드 포인트1~100 이름을 검사한다. [IdempotencyKey](backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java)는 parse와 직접 생성 모두에서 키를 검증하고 원문을 보존한다. [CancellationDecision](backend/src/main/java/com/limitedbooking/backend/reservation/CancellationDecision.java)은 현재 상태에 따른 판단만 반환한다.

```sh
./gradlew test --tests 'com.limitedbooking.backend.resource.ResourceRulesTest'
./gradlew test --tests 'com.limitedbooking.backend.reservation.IdempotencyKeyTest'
./gradlew test --tests 'com.limitedbooking.backend.reservation.CancellationDecisionTest'
./gradlew test
```

2026-10-15 전체 Unit116건(이름/수량52·키56·판단5·프로브3), 실패·오류·skip0을 확인했다. [규칙 계약](specs/004-validate-domain-rules/contracts/rules-contract.md)과 [RED/GREEN 증거](docs/test-evidence.md)를 함께 읽는다. 사용자 설명 인수(T029)는 2026-10-16 설명 후 명시적 완료 지시로 처리했다. 독립 재설명 시험은 미실시다.

## 4단계 로컬 인증과 예약 대상 API

[SecurityConfig](backend/src/main/java/com/limitedbooking/backend/security/SecurityConfig.java)·[LocalIdentityResolver](backend/src/main/java/com/limitedbooking/backend/security/LocalIdentityResolver.java)는 고정 UUID를 가진 `ADMIN`·`USER_A`·`USER_B` 로컬 무상태 Basic 인증(BCrypt)과 `Principal` 단일 신뢰를 제공한다. [RequestBodyLimitFilter](backend/src/main/java/com/limitedbooking/backend/common/RequestBodyLimitFilter.java)는 인증 앞단에서 실제 스트림 읽기 `8,192바이트(8KiB)` 상한(`413 PAYLOAD_TOO_LARGE`)과 재사용 가능한 요청 래퍼를 제공하고, [ApiExceptionHandler](backend/src/main/java/com/limitedbooking/backend/common/ApiExceptionHandler.java)는 Jackson 3 엄격 JSON 역직렬화 및 전 구간 공통 `application/problem+json` 응답을 처리한다. [ResourceController](backend/src/main/java/com/limitedbooking/backend/resource/ResourceController.java)·[ResourceService](backend/src/main/java/com/limitedbooking/backend/resource/ResourceService.java)·[ClockConfig](backend/src/main/java/com/limitedbooking/backend/common/ClockConfig.java)는 UTC `Clock`과 `ResourceRules`를 사용해 예약 대상 생성(`POST /api/resources` `201`)과 단건 조회(`GET /api/resources/{id}` `200`/`404`)를 제공한다.

```sh
./gradlew integrationTest --tests 'com.limitedbooking.backend.security.SecurityIT' --tests 'com.limitedbooking.backend.resource.ResourceApiIT'
./gradlew verify
```

2026-10-17 `SecurityIT` 6건·`ResourceApiIT` 8건 및 `./gradlew verify`(Unit 116 + 일반 IT 92, 실패·오류·skip 0) 통과를 확인했다. 상세 계약은 [4단계 계약](specs/005-auth-resource-api/contracts/auth-resource-contract.md)과 [실행 증거](docs/test-evidence.md)를 따른다. 5단계 구현은 아래와 같다.


## 5단계 예약 생성·성공 키·롤백

[ReservationController](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationController.java)는 인증 UUID와 `resourceId`만 받아 `POST /api/reservations`를 제공한다. `Idempotency-Key`는 ASCII 영문·숫자·밑줄·하이픈1~64자다. 최초/재현 모두201·Location과 id/resourceId/status/createdAt 네 필드를 반환하며 `Idempotent-Replayed`만 false/true로 구분한다. 같은 사용자·키의 다른 대상은409, 실패한 키는 보존하지 않는다.

[Service](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationService.java)는 트랜잭션 밖 선조회·복구를, 별도 [Writer](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationWriter.java)는 READ COMMITTED의 조건부 차감·INSERT RETURNING을 담당한다. 저장 UTC 마이크로초 값을 사용하며 취소된 준비 행도 최초 CONFIRMED로 재현한다. timeout은 SET LOCAL lock2s/statement5s, 풀10/획득3s다. 정확한 사용자·키 유일 위반만 전체 롤백 뒤 재조회한다.

```sh
curl --connect-timeout 3 --max-time 10 --user "USER_A:$USER_A_PASSWORD" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: booking-1' \
  -d '{"resourceId":"<생성한 대상 UUID>"}' http://127.0.0.1:8080/api/reservations
```

같은 명령을 다시 보내면 최초 성공을 재현한다. Location의 예약 GET과 취소 API는 아래6단계에서 구현했다. 위 curl은 로컬 사용 안내이고 이번 실행은 격리 Testcontainers HTTP로 검증했다.

```sh
./gradlew test integrationTest --tests '*ReservationCreateIT' --tests '*IdempotencyIT' --tests '*ReservationRollbackIT'
./gradlew verify
```

정확한 실행 결과와 미검증 범위는 [5단계 증거](docs/test-evidence.md) 및 [검증 안내](specs/006-reservation-create/quickstart.md)를 따른다. 실제 경쟁 반복(7단계), Redis 제한(8단계), 업무 자동 계약·속성·변이·부하는 후속이다.


## 6단계 소유자 조회·취소

[ReservationController](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationController.java)의 `GET /api/reservations/{id}`와 `POST /api/reservations/{id}/cancel`은 소유자의 현재 상태를 반환한다. 타인 예약은 관리자에게도404다. 확정 상태의 `cancelledAt`은 null을 포함하고, 취소되면 저장된 UTC 취소 시각을 반환한다. 취소 본문은 비어 있어야 한다.

```sh
curl --connect-timeout 3 --max-time 10 --user "USER_A:$USER_A_PASSWORD" \
  http://127.0.0.1:8080/api/reservations/<예약-UUID>
curl --connect-timeout 3 --max-time 10 --user "USER_A:$USER_A_PASSWORD" \
  -X POST http://127.0.0.1:8080/api/reservations/<예약-UUID>/cancel
cd backend
./gradlew test integrationTest --tests '*ReservationOwnershipIT' --tests '*ReservationCancelIT'
./gradlew verify
```

취소와 수량1 복구는 [Writer](backend/src/main/java/com/limitedbooking/backend/reservation/ReservationWriter.java)의 단일 트랜잭션으로 커밋한다. 중복 취소는 시각·수량을 유지한다. 원래 생성 키의 재요청은 최초201/CONFIRMED를 재현하고 GET은 CANCELLED다. 지정IT18·일반verify(Unit137/IT138)·local CLI 11단계를 통과했다. [실행 증거](docs/test-evidence.md), [6단계 안내](specs/007-reservation-cancel/quickstart.md)를 참조한다. 이후7단계 경쟁 검증을 완료했으며 아래 기록을 참조한다.

## 7단계 경쟁 정합성 검증

[ReservationConcurrencyIT](backend/src/test/java/com/limitedbooking/backend/reservation/ReservationConcurrencyIT.java)는 실제 Basic 인증·HTTP·격리 PostgreSQL에서 C01~C11 각10회를 실행한다. 수량10/요청100, 동일키10요청, 대상간 키충돌, 중복취소, 생성·취소 일반경쟁/두 강제순서, 차감후 INSERT실패를 검사한다. 지정 인수의 최종 수와 전체 회귀 결과는 아래 실행 증거에 기록하며 [실행 증거](docs/test-evidence.md)·[회차 기록](docs/concurrency-runs.json)에서 조건과 결과를 확인한다.

```sh
cd backend
./gradlew test integrationTest --tests '*ReservationConcurrencyIT'
./gradlew verify
```

요청작업자 최대10·Hikari풀10/획득3000ms, 공통제한100건120초/그외30초, 장벽5초·HTTP9/10초·종료10초다. 표본 연결 최대는10ms 관측이며 운영 성능으로 일반화하지 않는다. 최초 종료 실패를 보존하여 다음 회차 DB초기화/준비/제출을 막는다. 제품/API/SQL 스키마 변경은 없고, 다음 작업은8단계 Redis 요청 제한이다. 업무 OpenAPI 자동대조·Property/PIT심화·부하·외부CI는 미검증이다.


### 2026-10-21 최종 리뷰 보완 확인

최종 일반 verify Unit150·IT257(경쟁110회+지원fixture5 포함), 실패/오류/skip0. 종료/전송실패 차단·모든 오류/결과 보존·호출별 고유ID와 C07 경로기록을 보완했다. 제품·API·스키마는 유지한다. [최종 실행 증거](docs/test-evidence.md)·[110회 기록](docs/concurrency-runs.json)을 따른다. 이전 날짜의 실행 수는 당시 기록이다. Redis·심화·부하·외부CI는 후속이다.

Qodana 2026-10-20 지적의 수정/추가 검토 분류와 잔여 의존성은 [Qodana 검토 기록](docs/qodana-review.md), 실제 실행 결과와 한계는 [검증 기록](docs/test-evidence.md)을 참조한다.

계약 검증 의존성 보안 확인은 backend/에서 `./gradlew verifyContractDependencies`로 실행한다. `contractTest`에도 이 검사가 선행한다. 고정 OpenAPI3.0.3 지원을 유지하며 Swagger2 변환기는 포함하지 않는다.


PR 게시 직전 최신 develop 통합 검증은 Unit156·일반IT257 중 기존 앱 기동1건 실패로 전체 통과 상태가 아니다. 경쟁115건과 별도 Contract6·보안 의존성 검사·bootJar는 통과했다. SSL 협상 연결 실패의 상세·미확정 원인은 [최신 실행 기록](docs/test-evidence.md)을 참조한다.
