# 프로젝트 작업 지침

## 언어와 기준 문서

- 모든 대답은 한국어로 한다. 작업 설명, 진행 보고, 검증 결과도 한국어로 작성한다.
- 설계와 구현 계획의 기준은 [한정 수량 예약 백엔드 로컬 개발 계획](<한정 수량 예약 백엔드 로컬 개발 계획.md>)이다. 작업 전 관련 절을 읽는다.
- 특히 계획서 2~5절의 규칙·API·DB 설계를 구현과 테스트의 기대값으로 사용한다. 이 파일은 요약이며, 상세 동작은 계획서를 확인한다.
- 사용자의 최신 지시를 우선한다. 설계 변경이 필요하면 이유와 영향을 설명하고 계약·관련 테스트·계획서를 함께 갱신한다. 테스트 통과를 위해 규칙이나 기대값을 임의로 바꾸지 않는다.

## 개발 전 지식 문서 확인과 유지

- 새로운 개발·수정·테스트·리뷰를 시작하기 전에 [knowledge 읽기 안내](knowledge/README.md)를 읽고, `knowledge/` 하위 Markdown 파일 목록을 확인한다.
- `knowledge/rules/`의 모든 규칙 파일과 작업 대상·연관 도메인의 `knowledge/domain/` 문서를 읽는다. 문서의 근거로 연결된 계획서 절을 확인하고, 실제 코드·SQL·테스트와 대조한 뒤 작업한다. 진행 설명에는 적용한 문서와 이번 검증 대상을 밝힌다.
- Git 작업은 [커밋 규칙](knowledge/rules/commit.md)과 [Git Flow 규칙](knowledge/rules/git-flow.md)을 따른다. 최초 커밋·브랜치 존재 여부를 확인하고, 검증 대상별 커밋과 작업 브랜치를 사용한다. 브랜치 생성·병합·태그 작성의 안내와 실제 수행 기록을 구분한다.
- 새 도메인은 [도메인 문서 템플릿](knowledge/domain/_template.md)을 복사해 `knowledge/domain/<domain-name>.md`로 작성한다. 기존 도메인 문서가 없을 때도 같은 템플릿으로 먼저 작성한다. 도메인 이름은 실제 기능·패키지와 맞추고 `knowledge/README.md`의 도메인 목록에 등록한다.
- 도메인 문서에는 책임·범위, 용어·데이터, 불변식·상태 전이, 유스케이스·권한·오류, 멱등성·경쟁·장애, 검증 사례와 근거를 적는다. 적용하지 않는 항목은 이유를, 미확정 항목은 확인할 질문을 명시한다. 템플릿의 예시를 새 도메인의 확정 규칙으로 그대로 사용하지 않는다.
- `knowledge/`는 계획서의 규칙을 작업에 적용하기 위한 문서다. 설계 기준은 계속 계획서 2~5절이며, 최신 사용자 지시를 우선한다. 문서와 계획서·구현이 충돌하면 차이와 영향을 설명하고 기준을 확인한다. 구현이 다르다는 이유로 규칙을 바꾸지 않는다.
- 동작·계약 변경 시 계획서, 관련 도메인 문서·규칙 파일, OpenAPI와 테스트를 함께 갱신한다. 파일 추가·이름 변경 시 지식 문서 목록과 링크도 갱신한다.
- 설계됨·구현됨·실행 검증됨을 구분한다. 실제로 존재하는 코드·SQL·테스트와 실행 증거만 연결하고, 아직 없는 파일·명령은 예정 또는 미구현으로 표시한다. `knowledge/`에는 지속적인 지식·규칙을, 실행 증거·버전·결정·성능 기록에는 기존 `docs/` 문서 체계를 사용한다.

## 프로젝트 목적과 범위

Java/Spring 개발 감각을 복구하고, AI가 작성한 코드의 정확성을 직접 이해·검증하기 위한 로컬 한정 수량 예약 백엔드다. 기능 수보다 예약·취소·재시도의 정합성과 재현 가능한 실행 증거를 중요하게 여긴다.

- 관리자는 예약 대상과 최초 수량을 생성하고 단건 조회한다.
- 사용자는 요청당 1개를 예약하고 자신의 예약을 단건 조회·취소한다.
- 동일 요청 재전송, 마지막 수량의 경쟁, 중복 취소, 의존성 장애에서도 수량과 예약 상태가 일치해야 한다.
- Redis로 인증된 사용자의 예약 생성·취소 요청을 제한한다.
- 결제, 만료, 대기열, 알림, 프런트엔드, Kafka, Kubernetes, 마이크로서비스, 대상 수량 변경·삭제, 회원가입, 비밀번호 재설정, 목록·페이지네이션은 첫 버전 범위 밖이다.
- 실행 범위는 localhost의 CLI/API 클라이언트다. 외부 공개·클라우드 배포·브라우저 서비스는 별도 설계와 사용자 지시가 필요하다.

## 현재 저장소와 계획의 차이

이 절은 파일을 확인한 2026-10-13 기준이다. 이후 작업에서는 실제 파일을 다시 확인한다.

- 작업 루트는 `limited-booking/`, 애플리케이션 루트는 `backend/`다.
- 기존 골격은 Java 25, Spring Boot 4.1.1, Gradle Wrapper 9.7.1을 사용한다. 기본 패키지는 `com.limitedbooking.backend`, 진입점은 `BackendApplication`이다.
- 2026-10-13 1단계에서 MVC·Security·JDBC·PostgreSQL·Redis·Flyway·Actuator와 심화 도구 기반을 추가했다. 도구 프로브 최소 실행은 확인했으나 업무 API·SQL·권한·경쟁 구현이 완료됐다고 가정하지 않는다.
- 계획서의 Maven Wrapper, `reservation-backend/`, `com.example.reservation`은 계획상의 구성이다. 존재하지 않는 `mvnw`나 Maven 프로필을 실행하지 않는다.
- 현재 Gradle·패키지·디렉터리를 자동으로 교체하지 않는다. 빌드 도구 변경이 필요한 작업에서는 차이와 이유를 제시하고 사용자 지시에 따른다. Gradle을 유지한다면 계획서의 테스트 분류·실행 계약을 명시적인 Gradle 태스크로 구현하고 문서도 맞춘다.
- 2026-10-13 재확인한 Git 최상위 경로는 `/Users/joypeb/Desktop/projects/limited-booking`이다. Git 작업 전 `git rev-parse --show-toplevel`을 다시 확인한다. 상위 저장소에서 `git add .`나 `git add -A`를 실행하지 않는다. 작업 위치가 바뀌면 독립 저장소 여부도 확인한다.

현재 구성의 기본 명령은 `backend/`에서 실행한다. 아래는 명령 안내이며 실행 성공의 기록이 아니다.

```sh
cd backend
./gradlew test
./gradlew build
./gradlew bootRun
```

`local` 프로필 설정을 구현한 뒤에는 `./gradlew bootRun --args='--spring.profiles.active=local'`을 사용한다. 1단계에 test/verify/verifyContract/verifyProperty/deepTest/mutationSmoke를 구현했다. 일반 test/build는 Unit만 포함하며 현재 실행 수/XML 게이트로0건·실패·skip을 거부한다. 정확한 범위·현재 조합은 README와 docs/versions.md, 실제 실행 근거는 docs/test-evidence.md를 참조한다.

## 기술과 코드 구조

- 단일 Spring Boot 애플리케이션을 `common`, `security`, `resource`, `reservation`, `ratelimit` 등 기능별 패키지로 나눈다. 기존 기본 패키지 아래에 배치하고 테스트도 대응하는 구조를 따른다.
- Java 25를 사용하며 preview 기능은 제외한다. Spring MVC/Security/JDBC를 사용하고, 기본 SQL 접근 방식은 `NamedParameterJdbcTemplate`이다.
- PostgreSQL은 수량·예약·멱등성의 유일한 원본이다. Redis는 요청 제한만 담당하며 잔여 수량 저장이나 분산 락에 사용하지 않는다.
- 스키마는 Flyway 마이그레이션으로 관리한다. JPA/ORM 자동 스키마 생성이나 H2로 PostgreSQL 검증을 대체하지 않는다.
- Controller는 HTTP 변환, Service는 요청 조정, 별도 Bean인 Writer는 트랜잭션, Repository는 SQL을 담당한다. DTO는 해당 기능 패키지의 record로 둔다.
- 거대한 공통 서비스·범용 Repository·불필요한 추상화를 먼저 만들지 않는다.
- Boot BOM 관리 버전을 우선한다. PostgreSQL 18.x, Redis, PIT, 속성 테스트 도구, 계약 검증기, k6의 정확한 버전과 호환성은 실제 실행으로 확인해 기록한다. `latest` 이미지는 사용하지 않는다.
- 과거 JUnit 5·Testcontainers 1.x 예제를 그대로 복사하지 않는다. 도구 후보를 채택 완료나 호환 검증 완료로 표현하지 않는다.

## 반드시 유지할 도메인 규칙

- 대상 이름은 앞뒤 공백 제거 후 1~100자, 최초 수량은 정수 0~1,000이다. 수량 0인 대상도 생성할 수 있다.
- 예약 요청은 항상 수량 1개다. `quantity`나 `userId`를 입력으로 받지 않는다. 사용자 UUID는 인증 결과에서 얻는다.
- 상태 전이는 `CONFIRMED → CANCELLED`만 허용한다. 재활성화와 예약 행 삭제는 하지 않는다.
- 대상별 `0 ≤ available ≤ capacity`, `available + CONFIRMED 예약 수 = capacity`를 유지한다.
- 사용자·생성 키 하나에 예약은 최대 1개다. 같은 사용자는 다른 키로 추가 예약할 수 있다.
- 취소로 복구되는 수량은 예약당 최대 1개다. 중복 취소는 200과 기존 취소 상태·시각을 반환한다.
- 실패한 트랜잭션은 예약과 수량을 부분 반영하지 않는다.

### 멱등성

- `Idempotency-Key`는 `^[A-Za-z0-9_-]{1,64}$`다. 공백 제거와 대소문자 변환을 하지 않는다.
- 키 범위는 `(인증 사용자 UUID, 생성 키)`이며 대상이 달라도 공유된다. 요청의 동일성은 `resourceId`의 UUID 값으로 비교한다.
- 성공한 예약 행을 키 기록으로 사용하고 TTL로 삭제하지 않는다. 품절·입력 오류·503 등 실패한 생성 요청의 키는 보존하지 않는다.
- 같은 키·같은 대상은 최초 201·Location·JSON 필드 값을 재현한다. 최초 `Idempotent-Replayed: false`, 재요청은 `true`다.
- 같은 키·다른 대상은 409 `IDEMPOTENCY_CONFLICT`다. 경쟁에서 실패한 대상의 수량 감소도 롤백해야 한다.
- 취소 후 생성 재요청도 최초 `CONFIRMED` 응답을 재현한다. 현재 `CANCELLED` 상태는 GET에서 확인한다.
- 시각은 UTC를 사용하고 DB는 `timestamptz`로 저장한다. DB 마이크로초 정밀도와 최초·재요청 응답을 맞추고, 저장 후 반환된 값을 사용한다. 순수 단위 테스트에서는 고정 Clock을 사용한다.

### 트랜잭션과 경쟁 처리

- 기본 격리는 READ COMMITTED다. 생성 Service에는 트랜잭션을 두지 않고 별도 Writer Bean에서 수량 차감과 예약 INSERT를 함께 커밋한다. 상위 트랜잭션이나 자기 호출로 경계를 무효화하지 않는다.
- 수량 차감은 `available > 0` 조건의 UPDATE다. 갱신 0건이면 새 SELECT로 사용자·키를 재조회한 뒤 재요청, 충돌, 대상 없음, 품절을 구분한다.
- 유일 제약 `uq_reservations_user_key(user_id, idempotency_key)` 위반은 SQLSTATE `23505`와 제약 이름을 함께 확인한다. Writer 전체 롤백 후 Service에서 새 조회로 처리한다.
- 실패한 트랜잭션 안에서 예외를 삼키고 계속 조회하지 않는다. 다른 PK·FK·연결 오류를 멱등 재요청으로 취급하거나 무제한 재시도하지 않는다.
- 생성과 취소의 갱신 잠금 순서는 `resource → reservation`이다. 취소는 대상 행을 `FOR UPDATE`로 잠근 뒤 소유자·`CONFIRMED` 조건으로 상태를 갱신한다.
- 취소 상태 갱신 1건일 때만 수량을 복구한다. 복구는 `available < capacity` 조건을 사용하고 0건이면 불변식 위반으로 전체 롤백한다.
- 초기 로컬 설정은 lock timeout 2초, statement timeout 5초, 풀 최대 10개, 연결 획득 대기 3초다. DB timeout은 트랜잭션 시작 시 `SET LOCAL`로 적용한다. 장애·잠금 시간 초과는 503과 전체 롤백으로 처리한다.

## API와 보안 계약

| API | 권한 | 성공 |
|---|---|---|
| `POST /api/resources` | ADMIN | 201 + Location |
| `GET /api/resources/{id}` | 인증 사용자 | 200 |
| `POST /api/reservations` | 인증 사용자 + 생성 키 | 201 + Location |
| `GET /api/reservations/{id}` | 소유자 | 200 |
| `POST /api/reservations/{id}/cancel` | 소유자, 본문 없음 | 200 |

- 타인 예약의 조회·취소는 관리자에게도 404다. 존재 여부를 드러내지 않는다.
- 로컬 Basic 인증과 고정 UUID의 ADMIN·USER_A·USER_B를 사용한다. 비밀번호는 환경 변수로 받고 BCrypt로 처리한다. 세션을 사용하지 않는다. 임의의 `X-User-Id`를 신뢰하지 않는다.
- CSRF 비활성화는 localhost 비브라우저 API라는 전제를 기록한다. CORS 전체 허용은 추가하지 않는다.
- 생성 API는 `application/json`만 허용한다. 미지원 Content-Type은 415다. 미정의 필드, 중복 JSON 키, 잘못된 UUID/JSON, capacity의 문자열·소수 입력은 400으로 거부한다. 취소의 비어 있지 않은 본문도 400이다.
- 본문 상한은 8KiB이며 초과는 413이다. Content-Length뿐 아니라 실제 읽기 바이트와 chunked 요청도 검사한다. 크기 제한은 인증 앞에서, 상한 이하 요청은 인증 → 요청 제한 → 입력 검증 → 서비스 순서로 처리한다.
- 오류는 `application/problem+json`과 ProblemDetail의 `code`로 통일한다. 필터·인증 예외에도 동일 형식을 적용한다. 코드별 상세 계약은 계획서 4.2절을 따른다.
- 401에는 `WWW-Authenticate`, 429에는 `Retry-After`를 포함한다. SQL·스택 트레이스·비밀번호를 응답에 노출하지 않는다. 분류하지 못한 내부 결함을 409/503으로 숨기지 않는다.

## Redis 요청 제한

- 인증된 사용자의 생성·취소를 합산해 20회/60초, 최초 요청부터 고정 창을 적용한다. 재요청과 잘못된 입력도 인증 후 해당 경로에 진입하면 횟수에 포함한다.
- 키는 `reservation:write-limit:{userId}`다. Lua로 INCR·최초 PEXPIRE·PTTL을 원자적으로 처리하고 요청마다 TTL을 연장하지 않는다.
- `Retry-After = max(1, ceil(PTTL / 1000))`이다. 초기 Redis 명령 timeout은 200ms다.
- 초과는 429, Redis 장애는 503이며 DB를 변경하지 않는다. 조회와 대상 생성·조회는 Redis에 의존하지 않는다.
- DB 트랜잭션은 제한 통과 뒤 시작한다. DB 실패로 Redis 카운터를 되돌리지 않는다. 테스트·부하의 제한 변경값은 명시적으로 기록한다.

## 작업과 검증 방식

- 요청된 단계의 한 검증 대상부터 진행한다. 전체 계획을 한 번에 구현하지 않는다. 실제 구현 시 계획서에 안내된 `superpowers:executing-plans` 또는 `superpowers:subagent-driven-development` 절차를 적용한다.
- 기능 작업은 규칙·기대 결과 확인 → 테스트 작성 → 의도한 동작 실패 확인 → 최소 구현 → 관련 테스트 실행 → 정리·재검증 순서다. 컴파일 오류만으로 기능 결함을 검증했다고 하지 않는다. 환경 설정은 최소 실행으로 검증한다.
- 실패를 반복 실행·skip·mock·버전 임의 하향·`continue-on-error`로 숨기지 않는다. 원인과 최소 재현 조건부터 찾는다.
- Unit은 Spring 없는 `*Test`, Integration은 실제 PostgreSQL/Redis Testcontainers의 `*IT`로 구분한다. Gradle에서 이름·태그·태스크의 실제 발견 규칙을 구성하고 테스트 0건을 성공으로 취급하지 않는다.
- Contract는 고정 OpenAPI 3.0.3과 실제 HTTP 요청·응답을 검증한다. 자동 명세 생성만으로 대체하지 않는다. Property는 독립 모델·seed 재현·shrinking을 확인한다. PIT는 순수 규칙을 대상으로 하고 SQL·권한은 별도 고의 변경으로 탐지력을 확인한다.
- JUnit 클래스의 병렬 실행 대신 한 동시성 테스트 안에서 독립 요청·트랜잭션을 병행한다. 준비 데이터는 요청 전에 커밋하고 경쟁 테스트 전체에 `@Transactional`을 붙이지 않는다.
- 장벽은 DB 연결·잠금 획득으로 교착을 만들지 않는 위치에 둔다. Future 예외·상한 시간·작업 종료를 확인하고 모든 결과가 모인 뒤 단일 SELECT로 DB 수량 불변식을 검사한다.
- 우선 검증할 경쟁은 마지막 1개에 동일 키, 같은 키로 다른 대상, 중복 취소, 생성·취소 경쟁, UPDATE 후 INSERT 실패다. HTTP 응답과 최종 DB 상태를 함께 확인한다.
- Security 테스트는 실제 Authorization 헤더와 보안 필터를 통과한다. 내부 서비스 검증과 전체 HTTP 경로 검증을 구분한다.
- 테스트 DB는 동적 포트·격리된 데이터로 사용한다. 테스트 초기화는 전용 DB에서 reservations → resources 순서로 수행한다. 로컬 개발 DB·실제 개인정보에 연결하지 않는다. 장애 주입 후크는 테스트 코드에만 둔다.
- k6는 threshold 종료 코드와 최종 DB 검사로 판정한다. 예상 품절 409와 429/503/500을 구분한다. 로컬 성능을 운영 규모의 증거로 일반화하지 않는다.
- 변경과 영향 범위에 맞는 검증을 실행한다. 새 변경·실패·미해결 문제가 없으면 전체 테스트나 부하를 불필요하게 반복하지 않는다.

## 환경·기록·완료 보고

- 로컬 기본 주소는 앱 `127.0.0.1:8080`, PostgreSQL `127.0.0.1:5432`의 `reservation_local`, Redis `127.0.0.1:6379`다. load는 별도 `reservation_load` DB를 사용한다. UTF-8/UTC로 고정한다.
- `.env`와 비밀번호는 커밋하지 않는다. `.env.example`은 변수명만 제공한다. Compose 환경이 호스트 JVM에도 자동 주입된다고 가정하지 않는다.
- 로컬 DB는 named volume으로 보관한다. 기본 종료 절차에 볼륨 삭제를 넣지 않는다.
- 구현하며 `docs/versions.md`, `docs/decisions.md`, `docs/test-evidence.md`, `docs/performance.md`, README·OpenAPI를 실제 상태에 맞게 관리한다. 문서 루트 선택은 일관되게 기록한다.
- 실행 명령·종료 코드·테스트 수·환경 버전·seed·경쟁 조건·미검증 범위를 기록한다. 계획 체크박스는 실제 실행과 검증 후에만 표시한다.
- CI 파일 작성과 외부 CI 성공을 구분한다. push·공개·외부 workflow 실행은 사용자가 별도로 지시했을 때 수행한다.
- 완료 보고에는 변경 이유, 실제 검증 결과, 미완료·미검증 사항, 사용자가 읽을 핵심 코드·SQL·테스트, 다음 한 작업을 담는다. 실행하지 않은 검증을 통과했다고 보고하지 않는다.
