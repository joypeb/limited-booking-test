# 한정 수량 예약 백엔드

Java25·Spring Boot4.1.1·Gradle9.7.1의 localhost CLI/API 개발 프로젝트다. 기준은 [로컬 개발 계획](<한정 수량 예약 백엔드 로컬 개발 계획.md>)이다. 1단계 도구 최소 실행을 검증했다. 업무 API는 미구현이다.

## 로컬 환경

`.env.example`을 참고해 루트 `.env`에 POSTGRES_USER, POSTGRES_PASSWORD, TOOLCHAIN_SMOKE_PASSWORD를 설정한다. 값은 공유·커밋하지 않는다. Compose 보간이 호스트 JVM에도 자동 전달되지는 않는다. IDE에서는 같은 환경 변수를 별도로 설정한다.

프로젝트 루트에서:

```sh
docker compose up -d --wait --wait-timeout 120
set -a
source .env
set +a
cd backend
./gradlew bootRun --args='--spring.profiles.active=local' -Dfile.encoding=UTF-8 -Duser.timezone=UTC
```

앱은 127.0.0.1:8080, PostgreSQL은 127.0.0.1:5432/reservation_local, Redis는 127.0.0.1:6379다. 앱 준비 상한60초, HTTP 연결3초·전체10초로 health를 확인한다. health만 노출하고 상세값은 숨긴다. toolchain 계정은 기동 확인용 Boot 기본 계정이며 업무 UUID 인증 구현이 아니다. UTF-8/UTC를 사용한다.

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

backend/에서 실행한다. Java25와 실행 가능한 Docker가 필요하며 개발 Compose 기동은 IT의 선행 조건이 아니다. Testcontainers는 별도 동적 포트·격리 DB를 생성하고 성공/실패 후 이번 자원을 정리한다.

| 명령 | 최소 검증 범위·현재 JUnit 수 |
|---|---|
| `./gradlew test` | Spring 없는 Unit3 |
| `./gradlew verify` | Unit3+일반IT5 |
| `./gradlew test --tests '*ToolchainSmokeTest'` | 지정Unit3 |
| `./gradlew test integrationTest --tests '*ToolchainSmokeIT'` | Unit3+지정IT3 |
| `./gradlew verifyContract` | Unit3+일반IT5+Contract3; Property 제외 |
| `./gradlew verifyProperty` | Unit3+일반IT5+Property3; Contract 제외 |
| `./gradlew deepTest` | 네 그룹 총14 |
| `./gradlew mutationSmoke` | PIT 별도: 생성1·탐지1·NO_COVERAGE0 |

`contractTest`는 `*IT`+contract 태그만, `propertyTest`는 `*Test`+property 태그만 실행한다. 이름/태그 필터는 실행 대상을 정하며 공유 test source의 컴파일 범위를 줄이지 않는다. 지정 사례 옵션은 해당 Test 태스크 바로 뒤에 둔다. 최초 실행 근거는 --rerun-tasks로 남겼다.

```sh
QT_SEED=20261013 ./gradlew propertyTest
```

정상 Property는 별도 경계0/100과 생성 예산1000 중 실제 assertion101회다. 실제 assertion 최소100회를 검사하며 JUnit3건과 내부 시도 수를 구분한다. 고의 n>50 결함의 100→51 축소와 동일 seed 재실패·원복 통과는 [증거](docs/test-evidence.md)에 기록했다. 결함은 현재 원복 상태다.

XML은 `backend/build/test-results/<task>/`, HTML은 `backend/build/reports/tests/<task>/`, PIT XML/HTML은 `backend/build/reports/pitest/`에 있다. 계약 fixture는 시험 전용이며 업무 OpenAPI 원본은 docs/openapi.yaml이다. 해당 문서 parse/ref 성공을 업무 HTTP 준수로 해석하지 않는다.

Container 시작120초·자원별 종료10초, HTTP 연결3초·요청10초를 사용한다. 시험 migration 실패는 적용 metadata 확인·실패 컨테이너 정리 후 새 격리 DB에서 정상 migration/insert/select를 다시 검증한다. 실패 DB repair/clean이나 개발 DB 초기화는 하지 않는다. 프로세스 중단으로 cleanup이 실행되지 않았다면 로그의 실행 UUID/컨테이너 ID를 대조해 이번 시험 자원만 정리한다. 정리 불명은 인수 보류다.

도구 프로브는 후속 순수 업무 규칙으로 대체할 예정이다. 업무 SQL·권한·경쟁·멱등성·요청 제한·부하·외부 CI는 미검증이다. Gradle10 및 configuration cache 호환성도 검증하지 않았다.

최종 리뷰에서 끝나지 않는 실제 HTTP 응답의 종료 회귀 IT를 추가했다. 앱 IT 클라이언트는 shutdownNow 후 최대10초 awaitTermination을 사용하며 초과는 실패다. 2026-10-13 최초 조합 일반IT4/총13과2026-10-14 보완 후 일반IT5/총14를 구분한다.
