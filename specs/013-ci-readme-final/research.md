# Research: 12단계 CI·README·최종 검증

확인일 2026-10-26. “확인”은 실제 명령 출력, “문서”는 공식 문서·README 근거, “추정”은 미실행 판단이다.

## R1. 외부 실행 환경과 이미지 호환

- **Decision**: GitHub 호스팅 `ubuntu-24.04`(x86_64). 고정 digest 유지.
- **Rationale**: 확인 — `docker buildx imagetools inspect`로 `postgres:18.6-bookworm@sha256:afc7e2d4…`와 `redis:8.10.2-trixie@sha256:2c2dff79…`가 `application/vnd.oci.image.index.v1+json`이며 `linux/amd64` manifest를 포함한다. Explorer의 “arm64 전용 digest” 추정은 기각.
- **Alternatives**: `ubuntu-24.04-arm` runner(로컬과 같은 아키텍처) — 공개 저장소에서 사용 가능하나 x86_64에서도 실행되는지가 재현성의 의미가 커서 기본 runner를 택함. `ubuntu-latest` — 버전 이동으로 재현성 저하, `latest` 금지 원칙.

## R2. JDK 배포판

- **Decision**: CI는 Amazon Corretto 25(`actions/setup-java` `distribution: corretto`, `java-version: '25'`).
- **Rationale**: 확인 — 로컬 `docs/versions.md` JDK는 Corretto 25.0.4+7-LTS. 같은 배포판으로 차이를 줄인다. build.gradle은 toolchain `languageVersion = 25`(13~14행)이므로 setup-java가 제공한 JDK를 Gradle이 탐지한다. 실제 CI 패치 버전은 run 로그에서 기록한다.
- **Alternatives**: Temurin — setup-java 기본 예시지만 로컬과 다른 배포판.

## R3. action 버전 고정

- **Decision**: 2026-10-26 `gh api repos/<r>/releases/latest`와 `commits/<tag>`로 확인한 커밋 SHA 고정(plan Technical Context). 주석으로 태그 표기.
- **Rationale**: 태그 이동에 의한 공급망 위험 제거, 재현성.

## R4. 실행할 Gradle 명령

- **Decision**: verify는 `./gradlew --no-daemon verifyContract`, deep은 `./gradlew --no-daemon propertyTest mutationSmoke`.
- **Rationale**: 계획서 12단계가 verify에 `verifyContract`를 명시. deep은 Property/PIT만 요구하며 verify와 중복 실행을 피한다(Advisor). `propertyTest`는 6.2절 seed 재현 명령이자 build.gradle 112~119행 태스크로 자체 결과 게이트가 있다. `--no-daemon`은 CI 일회성 실행의 일반 관행.
- **Alternatives**: `deepTest mutationSmoke` — 약 4~5분(로컬) 추가 중복.

## R5. Property seed·시도 수 산출물

- **Decision**: 테스트 코드 무변경. `.github/scripts/property-summary.sh`가 propertyTest XML의 `<system-out>`에서 `seed=`·`attempts=`·`generated-attempts=`·`scenario-digest=` 줄을 추출.
- **Rationale**: 확인 — `ReservationPropertyIT`는 시작 시 `seed=… examples=150`, `finally`에서 `attempts=<실제 수> seed=… scenario-digest=…`를 출력(73·97~98행). `ToolchainPropertyTest`는 `property generated-attempts=…`(30행). Advisor의 “실제 attempts 미출력” 지적은 사실이 아님.

## R6. workflow_dispatch 제약

- **Decision**: deep-tests 외부 run은 main 반영 후 수행, 그 전에는 로컬 동등 실행으로 검증.
- **Rationale**: 문서 — GitHub Actions `workflow_dispatch`는 워크플로 파일이 기본 브랜치에 있어야 트리거된다. 작업 브랜치·Draft PR에서는 수동 실행 불가. 임시 자동 트리거 추가는 사용자 결정(수동만)에 어긋난다.

## R7. 정적 검사

- **Decision**: `rhysd/actionlint:1.7.12` 고정 digest 이미지 + 저장소 grep 검사.
- **Rationale**: 확인 — 로컬에 actionlint 미설치, Docker 사용 가능, 이미지가 amd64·arm64 index. 새 바이너리 설치 없이 재현 가능.
- **한계**: 외부에서 실패 전파(실패 테스트 → job failure)는 증명하지 않는다(사용자 승인 범위 밖의 실패 커밋 push 필요). Gradle 결과 게이트의 고의 실패 근거(1·10단계 test-evidence)와 `continue-on-error` 부재로 보장.

## R8. 새 환경 재현

- **Decision**: 로컬 저장소에서 새 clone + `COMPOSE_PROJECT_NAME=lbfresh` + 깨끗한 셸 환경.
- **Rationale**: 확인 — 5432·6379·8080 LISTEN 없음, 기존 `limited-booking-*` 컨테이너는 Exited, 볼륨 `limited-booking_postgres_data` 존재(보존 대상). 별도 프로젝트 이름은 새 named volume을 만든다. macOS `/tmp`→`/private/tmp`는 Compose가 바인드 마운트를 쓰지 않아 영향 없음(compose.yaml은 named volume만 사용, 추정 아님: 파일 확인).

## R9. 최종 k6 횟수

- **Decision**: 시나리오당 1회(회귀 확인). 3회 판정은 11단계 결과를 유지.
- **Rationale**: 12단계 문구는 “최종 commit에서 실행하고 commit과 결과 기록”. 규칙 문서의 3회는 판정 측정 절차. 차이를 decisions에 기록.

## R10. 실제 AI 결함 후보 (Explorer, 검증 예정)

1단계 HTTP 본문 정체 무한 대기, 5단계 관대한 UUID 역직렬화, 6단계 인코딩 경로의 취소 본문 검사 우회, 9단계 대상 조회 경로의 관대한 UUID binder, 11단계 DB pause 무한 대기(socketTimeout). 각 사례는 커밋·test-evidence로 실제 결함 여부를 확인한 뒤 기록한다.
