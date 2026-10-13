# 검증 안내: 1단계 최소 실행

## 상태와 선행 조건

2026-10-13 구현·실행 확인한 1단계 재현 안내다. Compose/local 설정·분류/조합 태스크와 smoke/property/contract/PIT 최소 사례가 존재한다. 현재 실행 근거는 [test-evidence](../../docs/test-evidence.md), 정확한 조합은 [versions](../../docs/versions.md)에서 확인한다. 업무 기능의 실행 근거는 아니다.

먼저 [실행 계약](contracts/validation-contract.md), [시험 데이터](data-model.md), [도구 선택 근거](research.md)를 읽는다. 0단계 계약은 루트 docs/openapi.yaml이다. 실행 환경은 Java25·Docker/Compose와 image 취득 권한, CPU에 맞는 컨테이너 실행이 필요하다. k6는 버전 확인까지만 한다. 설치 전 실제 존재 여부를 확인해 부족한 것만 준비한다.

## 1. 기존 저장소와 도구 점검

현재 수행 가능한 조회 명령이며 이번 작업에서 실행 완료한 명령 목록은 아니다. 프로젝트 루트에서 수행한다.

```sh
git rev-parse --show-toplevel
git branch --show-current
git status --short
uname -m
java -version
docker --version
docker compose version
k6 version
```

미설치 명령은 실패 이유를 기록하고 준비한다. Docker daemon의 실제 메모리 설정과 CPU, JDK 배포판/패치도 기록한다. backend에서 `./gradlew --version`으로 실제 Wrapper/JVM을 확인하고 취득 실패와 실행 실패를 구분한다. 도구의 현재 버전 조회는 후보 조사·전체 호환 확인과 다르다.

## 2. 로컬 기동

실제 compose.yaml·.env.example·local 설정으로 프로젝트 루트에서 수행한다. `.env.example`을 개인 `.env`로 복사해 변수값을 채우되 비밀번호를 공유하지 않는다. Compose 환경과 별도로 호스트 JVM에도 POSTGRES_USER, POSTGRES_PASSWORD, TOOLCHAIN_SMOKE_PASSWORD를 주입한다. 환경 전체 덤프나 보간된 Compose 설정 전체를 실행 증거에 붙이지 않는다.

```sh
docker compose config --quiet
docker compose up -d --wait --wait-timeout 120
docker compose ps
```

Compose 준비는120초, 시험 컨테이너별 시작은120초, 앱 HTTP 준비는 시작 후60초로 제한한다. 초과는 비성공/미검증으로 기록하고 시작한 시험 자원을 정리한다. host port는 loopback5432/6379, PostgreSQL DB는 reservation_local이다. exact image tag·digest와 실제 서버 버전을 versions에 기록한다.

backend에서 실행한다.

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

별도 터미널에서 앱 readiness를 확인한다.

```sh
curl --fail --silent --show-error --connect-timeout 3 --max-time 10 http://127.0.0.1:8080/actuator/health
```

예상은200과 건강 상태다. 앱 바인딩·JDBC·Redis 설정이 맞는지 확인한다. Boot 자동 보안의 임시 환경 계정은 기동 확인용이고 업무 UUID·소유권 인증은 미구현이다. Actuator health 상세·다른 업무 경로를 노출하지 않는다. 기동 종료는 실행 터미널의 정상 종료로 확인한다.

## 3. PostgreSQL 보존 확인

[시험 데이터](data-model.md)의 보존 표식 UUID·정수를 개발용 시험 schema에 저장하고 commit한다. 전체 migration·SQL 구현은 이 안내에 복사하지 않는다. 실행 시 시험 표식 준비·조회 명령을 README와 증거에 구체적으로 남긴다. 앱을 종료한 뒤 프로젝트 루트에서 수행한다.

```sh
docker compose down
docker compose up -d --wait --wait-timeout 120
```

재생성된 PostgreSQL에서 동일 UUID·정수를 조회하고 named volume 연결을 확인한다. PostgreSQL18 마운트 대상은 `/var/lib/postgresql`이다. 검증 후 본인이 생성한 표식만 정리한다. 기본 종료에 volume 삭제 옵션을 넣지 않는다. Redis 재시작 후 키 보존은 인수하지 않는다.

## 4. 기본·연동 검증

구현된 분류별 태스크를 backend에서 수행한다. JDK·Wrapper 확인은 의존성 해석보다 먼저 마쳐야 한다. 최초 증거는 새 실행을 강제하고 각 보고서 생성 시각·실행수를 기록한다.

```sh
./gradlew test --rerun-tasks
./gradlew test --tests '*ToolchainSmokeTest' --rerun-tasks
./gradlew verify --rerun-tasks
./gradlew test integrationTest --tests '*ToolchainSmokeIT' --rerun-tasks
```

각 명령을 별도로 실행해 종료 코드를 기록한다. 지정 IT 명령의 filter는 integrationTest에 적용되고 Unit은 그대로 실행된다. 기본은 Spring 없는 Unit, 일반 IT는 실제 동적 PostgreSQL/Redis 및 앱 기동이다. Flyway 시험 migration 적용 메타데이터와 PG 값 일치, Redis 값 일치가 각각 실제 assertion으로 검사되어야 한다. local 프로필·고정 개발 포트로 IT를 실행하지 않는다.

태그로 모든 사례를 제외하는 임시 설정, 발견 이름 불일치, 고의 skip, 값 불일치를 각각 한 대상씩 넣어 비성공 종료를 확인한다. 원복 후 해당 범위만 재검증한다. 예외 로그만으로 실패 검증이 된 것은 아니며 NO-SOURCE·UP-TO-DATE·이전 XML을 새 성공으로 기록하지 않는다. 발견 설정 결함은 기능 TDD의 동작 실패와 구분한다.

## 5. 심화 도구의 최소 확인

심화 라이브러리와 계약 fixture·validator·리소스가 모두 준비되고 전체 test 소스가 컴파일되는지 먼저 확인한다. Property 실행 태그에서 Contract를 제외해도 미완성 계약 클래스의 컴파일은 제외되지 않는다. HTTP 연결3초/요청10초, 시험 자원 종료10초와 초과 판정은 [실행 계약](contracts/validation-contract.md)의 대기 상한을 따른다.

```sh
./gradlew compileTestJava
```

```sh
./gradlew mutationSmoke --rerun-tasks
./gradlew verifyProperty --rerun-tasks
./gradlew verifyContract --rerun-tasks
```

- Mutation: PIT XML/HTML에서 생성≥1·탐지≥1·NO_COVERAGE0을 확인한다. 보고서 누락·도구 예외·생성0개를 실패로 판정한다. 생존 변이는 설명한다.
- Property: 0/100과 생성입력 최소100회 성공을 확인한다. n>50 저장값 결함을 일시 적용해 비성공 종료와 최초 반례·축소 반례51·seed를 기록한다. 최초 반례>51이어야 실제 축소 차이를 입증한다. 같은 seed를 QT_SEED로 전달해 같은 실패를 재현하고 원복 후 통과를 확인한다. 초기 반례 기록 방법도 도구 출력/지원 API로 확인한다.
- Contract: 실제 localhost 동적 HTTP fixture의 정상 응답은 오류0, 필수id 누락 응답은 검증 오류가 있는지 assertion으로 판정한다. 정상·음성 검사 두 사례가 실행되어야 한다. 별도 고의 잘못된 정상 응답은 비성공 종료를 확인하고 원복한다. 루트 기존 OpenAPI 읽기·참조 검사도 실제 사례로 수행한다.

seed 재현 시 동일 환경·도구 버전을 유지하고 QT_SEED 값을 명시적으로 test JVM에 전달한다. 아래 명령은 seed를 기록된 값으로 환경에 설정한 뒤 수행한다. 고의 결함이 있는 실행은 비성공, 원복 후 실행은 성공이어야 한다.

```sh
QT_SEED=20261013 ./gradlew propertyTest --tests '*ToolchainPropertyTest' --rerun-tasks
```

고의 결함 주입·원복은 각각 diff로 검토하고 실행 증거와 연결한다. 부적합한 후보는 원인을 분리해 조사에 기재한 대안을 평가하며 테스트 제외·버전 임의 하향으로 통과시키지 않는다.

## 6. 최종 조합과 기록

```sh
./gradlew deepTest --rerun-tasks
```

Unit·일반 IT·Contract·Property 각 그룹의 실행수≥1·실패0·skip0을 확인한다. PIT은 별도 판정이다. 마지막 PIT 실행 뒤 변경이 없으면 무거운 동일 검증을 재실행할 필요가 없다. 새 변경·미원복이 있다면 영향 범위를 재검증한다.

보고서 위치는 [실행 계약](contracts/validation-contract.md)을 따른다. 명령·종료 코드·테스트 수·Property 시도 수/seed/축소·변이 수·환경·고의 결함 원복·미검증을 docs/test-evidence.md에 남긴다. 버전·image·실제 dependencyInsight 결과는 docs/versions.md, 선택 이유는 docs/decisions.md, 재현 절차는 README에 기록한다. 로컬 설정에서 의존성 장애·범위 누락·보존 실패가 남아 있으면 원계획1단계를 완료 표시하지 않는다. 환경용 예제의 후속 실제 규칙 대체도 기록한다.

필요한 조합이 모두 확인된 후 Git Flow에 맞게 목적별 로컬 커밋을 남긴다. 외부 push·CI·공개·배포는 이번 범위가 아니다.

## 중단 후 재개 안내

[복구 계약](contracts/validation-contract.md#중단실패-후-정리와-재개)을 적용한다. 격리 시험 migration 실패는 실행 담당자가 오류·metadata·컨테이너 식별값을 남기고 지원 코드/담당자가 이번 시험 자원만 정리한 후 원인 수정·새 DB로 재검증한다. 개발 표식 중단은 새 연결에서 같은 UUID·기대값과 commit 여부를 확인하고, 준비가 확인된 표식으로 전체 보존 시험을 다시 수행한다. 조회 불가·값 불일치·잔존 자원 정리 미확인은 성공으로 기록하지 않는다. schema/개발 DB 초기화·named volume 삭제·무조건 재실행은 복구 절차가 아니다.

실제 현재 수: Unit3, 일반IT4, Contract3, Property3(내부101회), deepTest13. PIT생성1·탐지1·생존0. QuickTheories는 중복을 제거하므로 생성 예산1000과 실제101회를 구분하며 실제 최소100회 조건을 검사한다. 프로브 후속 대체와 미검증 범위는 README/계획의 실제 구현 결과를 참고한다.
