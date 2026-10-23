# 환경과 도구 버전

2026-10-13 실행, 2026-10-14 최종 기록 정리. 작업 브랜치는 `chore/setup-test-toolchain`이다. **후보 → 설치/취득됨 → 버전 확인됨 → 해당 최소 사례 실행 확인됨**을 구분하며 아래 표는 당시 확인 상태다. 업무 API·SQL·경쟁·부하 호환성은 미검증이다.

| 항목 | 실제 확인 | 상태·최소 실행 범위 |
|---|---|---|
| CPU/OS | arm64, macOS 27.0.1 | 호스트 확인 |
| JDK | Amazon Corretto25.0.4+7-LTS | Java·Gradle·테스트 실행 확인; preview 제외 |
| Gradle Wrapper | 9.7.1 | 실제 --version 및 여덟 실행 조합 확인 |
| Boot | 4.1.1 | starter 해석·local 기동·동적 컨테이너 앱 HTTP 확인 |
| Docker | CLI/daemon29.2.1, aarch64 | Desktop 기동·데몬·이미지 내부 실행·실제 연동 확인 |
| Docker 메모리 | 8,216,915,968 bytes (약7.65GiB) | docker info 실제값 |
| Compose | 착수 CLI v5.1.0 → Desktop 기동 후 v5.6.0 | 최종 version 출력·up/down·PostgreSQL 보존 확인 |
| k6 | 2.3.0, go1.27.1, darwin/arm64 | 누락 도구만 brew install 후 버전 확인; 부하 미실시 |
| PostgreSQL | postgres:18.6-bookworm, arm64 | 내부18.6 출력·local 표식 보존·격리 IT 확인 |
| Redis | redis:8.10.2-trixie, arm64 | 내부8.10.2 출력·local health·격리 set/get 확인 |
| Jupiter | 6.0.3 | Boot BOM dependencyInsight·네 테스트 그룹 실행 확인 |
| Testcontainers | 2.0.5 | Boot BOM dependencyInsight·동적 PostgreSQL/Redis 실행·정리 확인 |
| Flyway/JDBC | 12.4.0 / PostgreSQL42.7.13 | BOM dependencyInsight·시험 migration metadata/insert/select·실패 후 새 DB 재개 확인 |
| QuickTheories | 0.26 | Java25/Jupiter6.0.3에서 실제101회·경계값·seed 축소재현·원복 확인 |
| 계약 검증기 | openapi-request-validator-core3.0.0 / networknt2.0.1 / swagger-parser-v32.1.41 | 실제 HTTP 계약 프로브6건·업무 OpenAPI parse/ref 확인. Swagger2 변환기 제외 |
| PIT | Gradle plugin1.19.0 / engine1.22.1 / Jupiter지원1.2.3 | 순수 프로브 변이1개 생성·탐지, NO_COVERAGE0 |

Boot BOM 관리 버전 override 없음. 심화 도구는 research.md의 첫 후보 조합으로 최소 실행을 통과했으며 Java/JUnit을 임의 하향하지 않았다. Gradle10 및 configuration cache 호환성은 미검증이다.

Compose와 Testcontainers는 다음 취득 digest를 함께 고정한다. `docker image inspect` 및 `docker run --rm <image> <version command>`는 각각 종료0이다.

- PostgreSQL18.6: `postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c`
- Redis8.10.2: `redis@sha256:2c2dff791878316e3b083188be0d578423b24813cdda5de0e80ea4f7b3e6bfd6`

실행 조합은 Unit3, 일반IT6, Contract3, Property3(내부assertion101회), 지정IT3이다. verifyContract는 Property를, verifyProperty는 Contract를 제외하며 deepTest는 총15건이다. 최초 여덟 조합의 실행 근거는 당시 일반IT4/총13이다. 회귀 보완 후 현재 deepTest15건을 종료0·실패skip0으로 확인했고, PR 전 별도 PIT는 생성1·탐지1·생존0·NO_COVERAGE0이다. 현재 결과/XML 게이트와 고의 실패·원복까지 확인했다.

Property 생성 예산1000과 실제assertion101회를 구분한다. 고의 n>50 결함은 최초100에서51까지 축소되고 seed20261013으로 재현됐다. 계약 검증기 core는 JDK HTTP fixture의 실제 요청·응답을 검사하며 Spring adapter는 사용하지 않는다. 고정 업무 OpenAPI 읽기 성공은 업무 HTTP 준수 증거가 아니다.

1단계 필수 최소 검증 인수는 충족했다. 구체적 명령·종료 코드·실패 재현·원복·미검증은 [실행 증거](test-evidence.md), 선택 이유는 [결정 기록](decisions.md)을 참조한다.

### 2026-10-14 2단계 환경 재확인

Gradle9.7.1·Corretto25.0.4+7-LTS·MacOS27.0.1 aarch64·Docker29.8.2를 실제 명령 exit0으로 확인했다. 기존 Boot/BOM/고정 이미지 digest를 유지한다.

### 2단계 실제 저장 기반 검증 (2026-10-15)

기존 Boot4.1.1·Java25·Gradle9.7.1·Flyway/pgJDBC BOM 및 PostgreSQL18.6 고정 digest로 V1·조회·초기화를 실행했다. pgJDBC는 기존 runtimeOnly에 더해 SQLSTATE/제약 이름을 확인할 testImplementation에도 노출했다. 새 버전 변경은 없다. Unit3+일반IT78 검증 결과는 docs/test-evidence.md에 기록한다.

### 3단계 순수 규칙 실행 (2026-10-15)

feature/validate-domain-rules에서 `java --version`·`./gradlew --version` exit0으로 Corretto25.0.4+7-LTS·Gradle9.7.1·Mac OS X27.0.1 aarch64를 재확인했다. 기존 의존성·BOM·이미지·빌드 태스크 변경 없음. 전체 Unit116건(업무113+프로브3), 실패/오류/skip0을 listener/XML로 확인했다. Docker·DB·Redis를 기동하지 않았고 IT/Contract/Property/PIT·부하는 이번 단계에 재실행하지 않았다. 기존 Gradle10 비호환 deprecated 기능 경고는 남아 있으며 이번 규칙 범위에서는 수정하지 않았다.


### 2026-10-19 예약 생성 컴파일 범위

PostgreSQL 드라이버를 동일 Boot BOM의 implementation으로 이동하고 중복 testImplementation을 제거했다. compileClasspath 해석 결과는 후속 실행 기록으로 확인한다. 새 버전 지정 없음.

compileClasspath 실행: 종료0, org.postgresql:postgresql →42.7.13, 동일 BOM 유지.


### 2026-10-19 5단계 실행 환경

Corretto25.0.4.7.1·Gradle9.7.1·Docker daemon29.8.2에서 동일 PostgreSQL18.6 고정digest 및 Boot4.1.1/BOM을 유지했다. PostgreSQL42.7.13은 compileClasspath 해석 종료0으로 확인했다. 신규 IT 각각 실제 Hikari maximumPoolSize10/connectionTimeout3000ms를 확인하며 실제 Writer에서 read committed/lock_timeout2s/statement_timeout5s를 검사했다. 새 버전 추가·하향 없이 MockitoSpyBean/실제 프록시/SQLSTATE 메타데이터를 실행 확인했다. Gradle10 호환/심화·부하는 이번에 검증하지 않았다.

5단계 리뷰 수정 후 최종 일반verify는 Unit137/일반IT120, 실패·오류·skip0이다. 비표준UUID HTTP 회귀5건을 추가했으며 의존성 버전 변경은 없다.


### 6단계 조회·취소 실행 (2026-10-20)

Corretto25.0.4+7-LTS·Gradle9.7.1·Boot4.1.1·Docker daemon29.8.2 및 기존 PostgreSQL18.6 고정 digest를 유지했다. 새 의존성·스키마 변경 없음. verify Unit137·일반IT136, local 프로필 curl11단계와 최종 DB 보존식 통과. 소유권/취소/복구 롤백까지 실제 HTTP·DB 검증하며 경쟁 반복·Redis 제한·심화·부하/외부CI는 후속이다. 세부는 [실행 증거](test-evidence.md)를 따른다.


### PR6 적대적 리뷰 후 재검증 (2026-10-20)

Codex CLI0.163.0의 읽기 전용 리뷰 실행을 확인했다. 버전·의존성 변경 없이 인코딩 취소 경로의 본문 계약 회귀2건을 추가해 최종 verify Unit137·일반IT138 실패/오류/skip0을 확인했다. 앞선6단계 최초 인수137/136과 구분한다. 외부CI 성공을 의미하지 않는다.

## 7단계 실행 폴더 확인 (2026-10-20)

limited-booking-concurrency-plan/backend: `java --version` Corretto25.0.4+7-LTS, `./gradlew --version` Gradle9.7.1, macOS27.0.1/aarch64. `docker version` client/server29.8.2, Desktop4.95.0. 모두 종료0. 추적Wrapper 유지. 원래 checkout의 Wrapper9.8.0 및 build.gradle 사용자 변경은 옮기거나 수정하지 않았다. 발견 계약은 Unit *Test, 일반IT *IT, 클래스병렬없음·0건/실패/skip거부다.


7단계 실행 확인: 실제 PostgreSQL `18.6 (Debian 18.6-1.pgdg12+2)`, UTF-8/UTC, READ COMMITTED·lock2s·statement5s·풀10/획득3000ms. 고정 이미지/digest는 ContainerTestSupport 재사용(새 의존성 해석/버전 변경 없음). 지정 Unit147/IT111 및 일반verify Unit147/IT253이 종료0이었다. ByteBuddy Unsafe/Gradle10 폐기 경고는 남아 있으며 향후 버전 호환성은 검증하지 않았다.


2026-10-21 최종 리뷰 보완 재실행: 동일 Java25.0.4/Gradle9.7.1/Boot4.1.1·PostgreSQL18.6 고정digest·DB/풀 설정을 유지했다. 지정 Unit149/IT115 및 최종verify Unit149/IT257 종료0. 지원Unit12·경쟁110회·fixture5를 구분한다. 신규 의존성/버전 변경은 없다.

최종 checked예외 보완 뒤 `./gradlew verify`를 다시 실행했다: Unit150(기존137+지원13)/일반IT257, 종료0·실패/오류/skip0. 실행 환경과 버전은 동일하다.

## 2026-10-20 Qodana 보안 예외

테스트 구성 testCompileClasspath/testRuntimeClasspath에만 Jackson2 BOM2.21.8(core/databind 및 관련 모듈2.21.8, annotations2.21)과 Rhino1.7.15.1을 적용했다. Gradle 실제 해석 및 Contract 프로브3건·bootJar로 최소 호환성을 확인했다. runtime Jackson3.1.5는 유지한다. Boot BOM 기본값의 보안 예외이며 근거/잔여 의존성은 [Qodana 검토](qodana-review.md), 실행 한계는 [증거](test-evidence.md)를 따른다. 실행 Gradle9.8.0은 작업 전 사용자 Wrapper 변경에 의한 것으로 이번 변경에서 Wrapper를 갱신한 것이 아니다.


## 잔여 보안 의존성 제거 후 현재 구성 (2026-10-20)

Atlassian 공식3.0.0으로 전환하고 swagger-parser-v2-converter만 제외했다. Jackson2 BOM2.21.8은 유지한다. json-patch1.13·jackson-coreutils2.0 및 Rhino가 실제 테스트/실행 그래프에서 사라졌으므로 이전 Rhino 강제 버전 지정은 제거했다. 이전 보안 예외 절은 당시 기록이며 이 절이 현재 상태다. 취약 모듈 재유입 검사는 verifyContractDependencies로 testCompileClasspath/testRuntimeClasspath/runtimeClasspath를 확인하고 contractTest의 선행 조건으로 실행한다.


## 2026-10-21 테스트 PostgreSQL 연결 모드

도구·라이브러리·이미지 버전은 유지했다. ContainerTestSupport가 소유하는 ssl=off Testcontainers PostgreSQL URL에만 `sslmode=disable`을 명시한다. 실제 서버 설정·비TLS 연결과 앱 기동은 DatabaseSupportIT/BackendApplicationIT에서 확인하며 [결정](decisions.md)·[실행 및 관측 한계](test-evidence.md)를 따른다. 앱 기본/local 설정이나 외부 DB의 TLS 정책 변경은 아니다.


### 2026-10-22 8단계 실행 환경 확인
Corretto25.0.4+7-LTS / Wrapper9.7.1 / DockerEngine29.8.2 실제 명령 종료0. Boot4.1.1·Testcontainers2.0.5와 ContainerTestSupport PG18.6/Redis8.10.2 고정digest 유지. 신규 Redis 제한 호환성 검증은 진행 중. Qodana JVM2026.2 설정 유지·실행 미확보.

US1 실제 인수: 일반Unit169/RedisIT5 통과. runtimeClasspath 해석은 Framework7.0.9·Security7.1.1·DataRedis4.1.1·Lettuce7.5.2.RELEASE이며 RedisIT 런타임 출력 DataRedis4.1.1·Lettuce7.5.2.RELEASE/5728917·서버Redis8.10.2 확인. 명령/연결 timeout200ms 적용. Testcontainers2.0.5 BOM 조합으로 고정 PG/Redis 컨테이너 기동·종료했으나 이 인수에서 PG 업무 연결은 미검증이다. HTTP/장애/최종검증은 후속.

US2 지정HTTP 인수37건(2026-10-22): US1과 같은 JDK25.0.4/Wrapper9.7.1/Boot4.1.1·고정PG18.6/Redis8.10.2·BOM 유지, 신규의존성 없음. 실제동적PG연결/Flyway·SQL READ COMMITTED/2s/5s·풀10/3000ms·UTF-8/UTC와 Redis연결/명령200ms를 검사했다. 전체빌드/verify/의존성게이트는 후속이다. Qodana는 사용자지정미완료후속(문서4895488)이다.

US3(2026-10-22): 기존Corretto25.0.4/Wrapper9.7.1/Boot4.1.1·PG18.6/Redis8.10.2·SDR4.1.1/Lettuce7.5.2.RELEASE를유지하고신규의존성없음. 실제PG/Redis장애·새동적포트별도컨텍스트·기본Redis명령/연결200ms·DB2s/5s를인수했다. 관리전용접속/명령3초·복구PING5초·종료future각10초는시험정리상한이며앱timeout정책변경이아니다. 관련Unit37/전체지정IT49및영향재검증의명령별수는docs/test-evidence.md를따른다. 일반전체Unit/verify/build·US4기존110회는미실행이다.

US3 OOM보완최종: 같은버전에서RedisSystemException→RedisCommandExecutionException의정형OOM을실제확인했다. 소유Redis설정원복후최종관련Unit37/지정IT49(장애12+US2회귀37) 통과,실패/오류/skip0. 일반전체verify/build·US4기존110회는여전히미실행이다.


### 2026-10-22 8단계 최종 실행 환경

2026-10-22 8단계 구현·실행 인수 완료: `./gradlew build verify verifyContractDependencies` 종료0, 일반 Unit193·IT312, 실패/오류/skip0. IT에는 기본 Redis5·HTTP제한37·실제장애12·경쟁110회와 종료지원fixture5가 포함된다. T001~T036/T038 37개 완료, Qodana T037만 사용자 “나중에 할거야” 지시에 따른 미실행 후속이며 이번 완료·PR·병합 게이트에서 제외한다. 오케스트레이터 최종 독립검토·PR·병합은 아직 수행하지 않았다. Corretto25.0.4+7-LTS·Wrapper9.7.1·Boot4.1.1·Framework7.0.9/Security7.1.1/SDR4.1.1/Lettuce7.5.2.RELEASE·Testcontainers2.0.5·Docker29.8.2·고정PG18.6/Redis8.10.2 digest를 유지했다. 신규의존성/버전변경없음. 풀10/획득3000ms·READ COMMITTED/lock2s/statement5s·Redis기본20/60000ms/명령접속200ms 실제값을검사했다. 위 과거 중간인수의 미실행 표시는 당시기록이다.

### 2026-10-23 9단계 T018~T020 지정 검증 환경

`java -version`은 Corretto25.0.4.7.1/build25.0.4+7-LTS, Wrapper 파일과 실행은9.7.1·Boot plugin4.1.1을 유지했다. ContainerTestSupport의 고정 PG18.6-bookworm/Redis8.10.2-trixie digest·동적 포트·Testcontainers2.0.5 및 기존 계약 라이브러리를 그대로 사용한다. 새로운 의존성·버전 변경은 없다. 두 중단 fixture에서 실제 Hikari10/획득3000ms·Redis 기본20/60000ms·명령/연결200ms를 검사했다. UTF-8/UTC와 JUnit 클래스 병렬실행 false를 유지한다. 지정 실행 수·종료·기존timeout 회귀는 [실행 증거](test-evidence.md)의 T018~T020 절에 기록한다. 전체9단계/전체verifyContract 인수·Qodana 통과를 의미하지 않는다.
