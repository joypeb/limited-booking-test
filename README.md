# 한정 수량 예약 백엔드

Java25·Spring Boot4.1.1·Gradle9.7.1의 localhost CLI/API 개발 프로젝트다. 기준은 [로컬 개발 계획](<한정 수량 예약 백엔드 로컬 개발 계획.md>)이다. 현재 1단계 환경 검증을 진행 중이며 업무 API는 미구현이다.

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

실제 검증 기록은 [실행 증거](docs/test-evidence.md), 도구 조합은 [버전](docs/versions.md)에서 확인한다. 네 분류 태스크는 구성했으나 최소 사례·0건/skip 게이트 및 심화 태스크는 진행 중이다. 일반 build는 전체 심화 검증을 뜻하지 않는다.

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
