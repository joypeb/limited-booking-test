# 환경과 도구 버전

2026-10-13 실행, 2026-10-14 최종 기록 정리. 작업 브랜치는 `chore/setup-test-toolchain`이다. **후보 → 설치/취득됨 → 버전 확인됨 → 해당 최소 사례 실행 확인됨**을 구분하며 아래 표는 현재 확인 상태다. 업무 API·SQL·경쟁·부하 호환성은 미검증이다.

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
| 계약 검증기 | swagger-request-validator-core2.46.1 | 실제 HTTP3건 및 UUID 훼손 탐지·원복, 업무 OpenAPI parse/ref 확인 |
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
