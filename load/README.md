# localhost 예약 부하 검증

11단계 US3의 실행 도구다. 작성·문법 검사와 실제 부하 통과는 별개이며, 실행 기록은 `docs/performance-runs/`에 남긴다. k6 v2.3.0의 실행 호환성과 threshold·summary 출력은 실제 실행에서 확인한다. 사용 API 근거는 [k6 옵션](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/), [threshold](https://grafana.com/docs/k6/latest/using-k6/thresholds/), [예상 HTTP 상태](https://grafana.com/docs/k6/latest/javascript-api/k6-http/set-response-callback/) 문서다.

## 준비

저장소 루트에서 실행한다. Docker Compose, JDK 25, k6, Git이 필요하다. 셸에 다음 환경 변수를 **export**한다. 비밀번호 값은 파일·명령 이력·결과물에 기록하지 않는다.

- `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `ADMIN_PASSWORD`, `USER_A_PASSWORD`, `USER_B_PASSWORD`
- `LOAD_RATE_LIMIT=100000`, `LOAD_RATE_WINDOW=60000ms`

Compose의 `.env`는 호스트 JVM과 k6에 자동 주입되지 않는다. 앱 실행 터미널과 부하 실행 터미널에 같은 환경을 제공한다. 실제 계정명은 대소문자를 구분하는 `ADMIN`·`USER_A`·`USER_B`다.

```sh
docker compose up -d --wait
./load/prepare-db.sh
```

`prepare-db.sh`는 같은 PostgreSQL의 `reservation_load` DB가 없을 때만 생성한다. 기존 named volume에는 Compose 초기화 스크립트가 다시 적용되지 않으므로 이 준비가 필요하다. DB가 만들어진 다음, 별도 터미널에서 load 프로필로 앱을 기동한다. Flyway가 스키마를 적용한다.

```sh
cd backend
LOAD_RATE_LIMIT=100000 ./gradlew bootRun --args='--spring.profiles.active=load'
```

기존 8080 앱을 먼저 종료해 load 앱이 바인딩에 성공했는지 확인한다. 대상은 `http://127.0.0.1:8080`이며 다른 localhost 포트는 `BASE_URL`로 지정할 수 있다. 실행기는 `LOAD_RATE_LIMIT`을 기록하지만 서버의 실제 적용값을 조회하지는 못하므로 같은 값으로 앱을 기동해야 한다. 다른 프로필의 앱을 대상으로 실행하지 않는다.

## 시나리오

실행마다 `reset.sh`가 **reservation_load에서만** 예약→대상을 한 트랜잭션으로 삭제하고, USER_A·USER_B의 제한 키만 제거한다. 실행 중 다른 클라이언트가 같은 앱·DB·Redis 키를 사용하면 결과를 오염시킨다. 시나리오는 하나씩 실행하고 label을 겹치지 않게 지정한다. 아래 명령의 제한값은 실행 중인 앱과 일치해야 한다.

```sh
LOAD_RATE_LIMIT=100000 ./load/run.sh low 1
LOAD_RATE_LIMIT=100000 ./load/run.sh high 1
LOAD_RATE_LIMIT=100000 ./load/run.sh replay 1
# 각 시나리오를 label 2, 3으로도 실행해 총 3회 측정한다.
```

| 시나리오 | 요청 | 판정 |
|---|---|---|
| low | 10VU, 대상 10개×1000, 준비 30초 + 측정 2분, 생성→취소 | 측정 p95<500ms·예상 외 오류율<1%, 종료 시 확정 0·수량 복구 |
| high | 50VU, 한 대상×1000, 서로 다른 키 2000회 | 201=1000, SOLD_OUT 409=1000, 예상 외=0, DB 확정1000·잔여0 |
| replay | 10VU×50회, VU별 키5개×10회 | HTTP 201=500, id 불일치0·Replay 헤더 정확, DB 고유 예약50 |
| rate-limit | 1VU×25회, 대상 수량100 | 최초20회 201·다음5회 RATE_LIMITED 429, Retry-After 정수≥1, DB 예약20 |

low의 warmup·measure 경계에서는 이미 시작한 반복을 완료할 수 있도록 종료 유예를 둔다. HTTP 지연은 k6의 시나리오 태그로 분리하며 준비 요청은 측정 p95에 포함하지 않는다. 예상 품절은 `SOLD_OUT`인 409만 인정한다. 429·503·500·timeout은 high에서 모두 실패다.

제한 시험은 앱을 종료한 뒤 원래 제한값으로 **재기동**한다.

```sh
# 앱 터미널: 기존 bootRun 종료 후
cd backend
LOAD_RATE_LIMIT=20 ./gradlew bootRun --args='--spring.profiles.active=load'
# 부하 터미널: 저장소 루트에서
LOAD_RATE_LIMIT=20 ./load/run.sh rate-limit 1
# label 2, 3으로도 실행한다.
```

제한 시험의 25회는 최초 쓰기부터 60초 고정 창 안에 끝나야 한다. 이후 low/high/replay 실행 전 앱을 다시 `LOAD_RATE_LIMIT=100000`으로 기동한다.

고의 기대 불일치로 종료 코드 판정기를 확인한다. high용 앱이 실행 중이어야 한다.

```sh
LOAD_RATE_LIMIT=100000 EXPECT_CREATED=999 ./load/run.sh high selfcheck
# k6 threshold 실패로 비0 종료 기대. 실패 후에도 DB 검사는 실행한다.
```

`EXPECT_SOLD_OUT`도 high의 예상 품절 건수를 바꿀 수 있다. 실제 데이터 수량·요청 수는 바꾸지 않는다. `RUN_ID`는 기본 UTC 시각+프로세스 번호이며 직접 지정하면 영문·숫자·밑줄·하이픈 1~32자다. 생성 키는 64자 이내다.

## 결과와 종료

결과 위치는 UTC 날짜 기준 `docs/performance-runs/<YYYYMMDD>-<scenario>-<run-label>/`이다. 기존 디렉터리가 있으면 덮어쓰지 않고 실패한다.

- `summary.json`: k6 `--summary-export` 원시 요약. p50/p95/p99·처리량·상태별 Counter를 확인한다.
- `k6.txt`: k6 표준 출력·오류와 threshold 결과.
- `check.txt`: 불변식 위반 행·중복 키·대상별 집계·전체 예약/고유 키 수·시나리오 최종 DB 판정.
- `meta.txt`: UTC 시각·commit·dirty 여부·시나리오·label·RUN_ID·기록한 제한값·기대값·k6/JDK·이미지·각 종료 코드.
- `reset.txt`: 해당 실행의 load DB·제한 키 초기화 출력.

k6 또는 DB 검사(혹은 출력 저장)가 실패하면 `run.sh`도 비0 종료한다. SQL은 단일 repeatable-read 트랜잭션에서 수량 보존·범위·사용자/키 유일성과 시나리오별 최종 건수를 검사한다. 초기화 실패 시 k6는 실행하지 않으며 meta에 `not_run`으로 남긴다.

앱을 종료한 뒤 다음 명령으로 Compose를 정지한다. 기본 종료에서 `-v`를 사용하거나 named volume을 삭제하지 않는다.

```sh
docker compose down
```

측정은 localhost의 Basic 인증·BCrypt를 포함한 전체 HTTP 비용이다. 순수 서비스 성능이나 운영 규모의 성능 증거로 일반화하지 않는다. 잠정 지연 기준 미달은 숨기지 않고 기록하며, 개선은 측정한 병목 하나씩 비교한다.
