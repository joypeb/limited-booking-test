# 구현 계획: 로컬 인증과 예약 대상 생성·조회 API

**작업 브랜치**: `feature/auth-resource-api` | **작성일**: 2026-10-17 | **명세**: [spec.md](spec.md) | **상태**: 구현 및 `./gradlew verify` 검증 완료 (2026-10-17)

**입력**: `/specs/005-auth-resource-api/spec.md` 기능 명세 및 [로컬 개발 계획](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 7절 「4단계: 로컬 인증과 예약 대상 API」

## 요약

[로컬 개발 계획](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 7절 4단계 범위로서, 고정 UUID가 할당된 로컬 Basic 인증 계정(`ADMIN`·`USER_A`·`USER_B`), 인증 앞단의 `8KiB(8,192바이트)` 요청 본문 크기 상한 필터(`RequestBodyLimitFilter`), 엄격 JSON 역직렬화 및 공통 `application/problem+json` 오류 처리(`ApiExceptionHandler`), UTC `Clock` 기반 예약 대상 생성·단건 조회(`ResourceService`·`ResourceController`·`ResourceRepository`)를 구현하고, 실제 보안 필터 체인과 서블릿 경로를 거치는 통합 테스트(`SecurityIT`·`ResourceApiIT`)로 TDD 절차(RED -> GREEN -> `./gradlew verify`)에 따라 검증한다.

## 기술 문맥 (Technical Context)

**언어/버전**: Java 25 (`JavaLanguageVersion.of(25)`, Gradle Toolchain 고정)

**주요 의존성**:
- Spring Boot `4.1.1` (`spring-boot-starter-webmvc`, `spring-boot-starter-security`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`, `spring-boot-starter-flyway`, `spring-boot-starter-actuator`)
- Jackson 3 (`tools.jackson.core:jackson-databind:3.1.5` — `spring-boot-jackson` 기본 포함)
- PostgreSQL JDBC 드라이버 (`org.postgresql:postgresql:42.7.10`), Flyway PostgreSQL (`12.0.3`)
- 테스트: JUnit Jupiter `6.0.3`, AssertJ `3.27.7`, Testcontainers `1.21.4` (`postgresql:1.21.4` — `postgres:18.3`, `redis:8.6.2`)

**저장소**: PostgreSQL 18 (`resources` 테이블에 `INSERT` 및 `SELECT`, Flyway `V1__init.sql` 적용 완료)

**테스트**:
- `./gradlew integrationTest --tests '*SecurityIT' --tests '*ResourceApiIT'` (4단계 통합 검증)
- `./gradlew verify` (단위 테스트 `test` + 통합 테스트 `integrationTest` 전체 게이트)

**대상 플랫폼**: localhost(127.0.0.1) 전용 비브라우저 HTTP/JSON API 서버 (macOS / Linux JVM)

**프로젝트 유형**: 단일 백엔드 웹 서비스 (`backend/`)

**성능 및 처리 목표**:
- 모든 요청에서 인증 단계 진입 전에 `8,192바이트(8KiB)` 초과 본문을 즉시 `413`(`PAYLOAD_TOO_LARGE`)으로 차단한다 (`Content-Length` 있음/없음/`Transfer-Encoding: chunked` 모두 포함).
- `8,192바이트` 이하 본문은 메모리에 한 번만 읽어 재사용 가능한 요청 래퍼로 하위 계층에 전달한다.

**제약 사항**:
- 무상태(Stateless) Basic 인증을 사용하며 서버 세션(`HttpSession` / `JSESSIONID`)을 생성하지 않는다.
- 사용자 식별자(`UUID`)는 오직 인증된 `Authentication`의 `Principal`에서만 가져오며(`LocalIdentityResolver.userId`), 요청 본문의 `userId`나 `X-User-Id` 헤더를 절대 신뢰하지 않는다.
- 알 수 없는 JSON 필드(`userId` 등), 동일 객체 내 중복 JSON 키, 숫자 필드의 암묵적 형 변환(부동소수점·문자열)을 모두 `400`(`INVALID_REQUEST`)으로 거부한다.
- 필터(413·401·403)와 컨트롤러/예외 처리기(400·404·415·503)에서 반환하는 모든 정의된 오류 응답은 `application/problem+json` 형식과 필수 6개 필드(`type`, `title`, `status`, `detail`, `instance`, `code`)를 갖춰야 하며, SQL·스택 트레이스·비밀번호를 노출하지 않는다.
- 실제 환경 비밀번호(`.env` 주입)와 테스트용 가짜 비밀번호를 분리한다.

**규모/범위**:
- 프로덕션 코드 8개 파일 작성·수정 (`SecurityConfig`, `LocalIdentityResolver`, `ClockConfig`, `RequestBodyLimitFilter`, `ApiExceptionHandler`, `ResourceRepository`, `ResourceService`, `ResourceController` — `CreateResourceRequest`, `LocalUserPrincipal`, 404 전용 예외 등 보조 타입은 지정된 파일 내부 중첩 타입으로 두어 별도 최상위 파일을 늘리지 않음)
- 설정 파일 정리 (`application-local.yaml`, `.env.example`)
- 통합 테스트 코드 2개 파일 작성 (`SecurityIT`, `ResourceApiIT`) 및 기존 테스트 지원 클래스(`TestData`, `BackendApplicationIT`) 보강

## 헌법 준수 점검 (Constitution Check)

*게이트: Phase 0 연구 전 통과 필수. Phase 1 설계 후 재점검 완료.*

| 원칙 | 점검 항목 | Phase 0 전 판정 | Phase 1 후 판정 | 근거 |
| --- | --- | --- | --- | --- |
| **I. 도메인 불변식과 보안 경계 우선** | 고정 UUID Principal 추출, 외부 `userId`/`X-User-Id` 불신뢰, 8KiB 상한 인증 앞단 배치, 엄격 JSON 파싱, `problem+json` 일관성, 비밀번호 비노출 | 통과 | 통과 | [research.md](research.md) 1~5절 및 [contracts/auth-resource-contract.md](contracts/auth-resource-contract.md) 1~3절에 반영 |
| **II. 테스트 주도 개발과 실행 증거** | `SecurityIT`·`ResourceApiIT` 실패(RED) 선확인 후 프로덕션 코드 구현(GREEN), `./gradlew verify` 통과 확인 절차 | 통과 | 통과 | [quickstart.md](quickstart.md) 2~4절 및 아래 구현 순서에 고정 |
| **III.실제 의존성 기반 통합 검증** | MockMvc나 보안 필터 우회 없이 실제 서블릿 컨테이너(`RANDOM_PORT`), 실제 `Authorization` 헤더, 실제 PostgreSQL Testcontainers 사용 | 통과 | 통과 | `HttpClientTestSupport` + `ContainerTestSupport` 기반으로 `SecurityIT`·`ResourceApiIT` 설계 |
| **IV. 한국어 문서화·지식 지도·버전 고정** | 모든 명세·계획·계약 문서를 한국어로 작성, Markdown 형식 준수, `knowledge/README.md` 색인 갱신, 의존성 버전 미변경 | 통과 | 통과 | `backend/build.gradle` 의존성 변경 없음, `knowledge/README.md`에 4단계 계획 문서 연결 |
| **V. 단계별 수직 슬라이스와 작업 규율** | 4단계 허용 파일 범위 준수, 5~8단계(예약 생성/멱등/취소 상태 전이/동시성/Redis 요청 제한) 선구현 금지 | 통과 | 통과 | 취소 경로는 4단계 요구사항인 본문 크기(413)·비어 있지 않은 본문(400)·UUID 형식(400)까지만 검증 |

## 프로젝트 구조 (Project Structure)

### 문서 구조 (현재 기능)

```text
specs/005-auth-resource-api/
├── spec.md                              # 기능 명세 ($speckit-specify 산출물)
├── plan.md                              # 구현 계획 (본 문서)
├── research.md                          # Phase 0 기술 결정 기록
├── data-model.md                        # Phase 1 데이터 및 오류 응답 모델
├── quickstart.md                        # Phase 1 TDD 실행 및 검증 가이드
├── contracts/
│   └── auth-resource-contract.md        # Phase 1 Java 인터페이스·HTTP API·테스트 계약
├── checklists/
│   ├── requirements.md                  # 내장 명세 품질 체크리스트
│   └── auth-resource-api.md             # 맞춤 요구사항 품질 검토 목록
└── tasks.md                             # Phase 2 작업 목록 ($speckit-tasks 산출물)
```

### 소스 코드 구조 (저장소 루트 기준)

```text
.env.example                                                                 # [수정] ADMIN_PASSWORD, USER_A_PASSWORD, USER_B_PASSWORD 환경 변수 키 정의
backend/
├── src/
│   ├── main/
│   │   ├── java/com/limitedbooking/backend/
│   │   │   ├── common/
│   │   │   │   ├── ApiExceptionHandler.java                                 # [신규] 공통 problem+json 예외 처리 및 Jackson 엄격 설정 보조
│   │   │   │   ├── ClockConfig.java                                         # [신규] Clock.systemUTC() 빈 설정
│   │   │   │   └── RequestBodyLimitFilter.java                              # [신규] 인증 앞단 8KiB 실제 읽기 상한 필터 + 재사용 요청 래퍼
│   │   │   ├── resource/
│   │   │   │   ├── ResourceController.java                                  # [신규] POST /api/resources, GET /api/resources/{id} 전용
│   │   │   │   ├── ResourceRepository.java                                  # [수정] insert(UUID, String, int, Instant) 추가, 기존 find(UUID) 유지
│   │   │   │   ├── ResourceRules.java                                       # [기존] 3단계 구현 완료 (normalizeName, validateCapacity)
│   │   │   │   ├── ResourceService.java                                     # [신규] create(String, int), get(UUID)
│   │   │   │   └── ResourceView.java                                        # [기존] 2단계 구현 완료
│   │   │   └── security/
│   │   │       ├── LocalIdentityResolver.java                               # [신규] userId(Authentication): UUID
│   │   │       └── SecurityConfig.java                                      # [신규] 무상태 Basic 인증, 고정 UUID 3계정, BCrypt, 401/403 problem+json, 인증 후 취소 요청 형식 검증
│   │   └── resources/
│   │       └── application-local.yaml                                       # [수정] 임시 toolchain 계정 설정을 로컬 인증 속성 구조로 정리
│   └── test/
│       └── java/com/limitedbooking/backend/
│           ├── BackendApplicationIT.java                                    # [수정] 3개 로컬 계정 테스트 비밀번호 프로퍼티에 맞춰 기동 검증 유지
│           ├── resource/
│           │   ├── ResourceApiIT.java                                       # [신규] 예약 대상 생성/조회, 경계값, 엄격 JSON, 415, 취소 비어 있지 않은 본문 400 검증
│           │   └── ResourceRepositoryIT.java                                # [기존] 2단계 통합 테스트 유지
│           ├── security/
│           │   └── SecurityIT.java                                          # [신규] 401/403/201/200, 고정 UUID Principal, X-User-Id 무시, 8KiB 413 우선순위 검증
│           └── support/
│               ├── ContainerTestSupport.java                                # [수정] 테스트용 가짜 비밀번호(@DynamicPropertySource) 공통 주입 지원
│               ├── HttpClientTestSupport.java                               # [기존/보강] Basic Authorization 헤더·chunked 전송 헬퍼 지원
│               └── TestData.java                                            # [수정] ADMIN/USER_A/USER_B 고정 UUID 및 테스트 자격 증명 상수 제공
```

**구조 결정**: [로컬 개발 계획](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 3절 디렉터리 구조와 7절 4단계 작성 파일 목록을 그대로 따른다. 불필요한 추상 계층이나 별도 최상위 보조 클래스를 추가하지 않고, `security`, `common`, `resource` 패키지 안에서 각 클래스가 단일 책임을 갖도록 구성한다. 특히 헌법 원칙 I(`resource`는 예약 대상, `reservation`은 예약·취소 담당)을 준수하기 위해 `ResourceController`는 `/api/resources/**`만 담당하며, 4단계의 취소 경로(`POST /api/reservations/{id}/cancel`) 비어 있지 않은 본문 `400`·UUID 형식 `400` 검증은 인증 직후 필터/보안 계층에서 처리한다.

## 상세 설계 및 파일별 책임

### 1. 보안 및 사용자 식별 (`security` 패키지)

1. **`SecurityConfig`**:
   - `ADMIN`·`USER_A`·`USER_B` 고정 UUID와 역할을 가진 커스텀 `UserDetails`(내부 중첩 불변 Principal 레코드)를 구성한다.
   - 비밀번호는 환경 속성(`ADMIN_PASSWORD`, `USER_A_PASSWORD`, `USER_B_PASSWORD`)에서 주입받아 `BCryptPasswordEncoder`로 인코딩해 보관한다.
   - `SecurityFilterChain`:
     - `SessionCreationPolicy.STATELESS`로 서버 세션을 비활성화한다.
     - localhost에서 사용하는 비브라우저 API 전제와 외부/브라우저 공개 시 재검토 조건을 주석·문서에 명시하고 CSRF를 비활성화한다.
     - CORS 전체 허용(`*`) 설정은 추가하지 않는다.
     - `RequestBodyLimitFilter`를 `BasicAuthenticationFilter`(및 `DisableEncodeUrlFilter`/`SecurityContextHolderFilter` 이후, 인증 실행 이전)보다 앞에 등록해 미인증 요청에서도 8KiB 초과 본문이 `413`으로 먼저 거부되게 한다.
     - 권한 규칙:
       - `/actuator/health`: `permitAll()`
       - `POST /api/resources`: `hasRole("ADMIN")`
       - `GET /api/resources/*` 및 그 외 `/api/**`: `authenticated()`
     - `httpBasic` 및 `exceptionHandling`에 커스텀 `AuthenticationEntryPoint`(`401 UNAUTHENTICATED`, `WWW-Authenticate: Basic realm="reservation-local"`, `application/problem+json`)와 `AccessDeniedHandler`(`403 FORBIDDEN`, `application/problem+json`)를 연결한다.
     - 취소 요청의 비어 있지 않은 본문 검증(`POST /api/reservations/{id}/cancel`):
       - 6단계에서 `ReservationController`가 도입되기 전까지 `resource` 패키지가 `reservation` 경로를 침범하지 않도록, 인증·권한 확인 직후 취소 경로 요청에 대해 캐시된 본문이 비어 있지 않거나(1~8,192바이트) `{id}`가 유효한 UUID 형식이 아니면 즉시 `400`(`INVALID_REQUEST`, `application/problem+json`)을 반환한다.
2. **`LocalIdentityResolver`**:
   - `public UUID userId(Authentication authentication)` 메서드를 제공한다.
   - 인증된 `Authentication`의 `getPrincipal()`에서 고정 `UUID`만 꺼내 반환하며, 요청 본문이나 `X-User-Id` 헤더는 참조하지 않는다.

### 2. 시각 주입·본문 크기 상한·공통 예외 처리 (`common` 패키지)

1. **`ClockConfig`**:
   - `@Configuration`에서 `@Bean public Clock clock()`으로 `Clock.systemUTC()`를 등록한다.
2. **`RequestBodyLimitFilter`**:
   - `OncePerRequestFilter`를 상속하며 최대 허용 본문 크기를 `8,192바이트(8KiB)`로 고정한다.
   - `request.getContentLengthLong() > 8192`이면 스트림을 끝까지 읽지 않고 즉시 `413`(`PAYLOAD_TOO_LARGE`, `application/problem+json`)을 응답한다.
   - 요청 스트림을 읽을 때 최대 `8,193바이트`까지만 읽으며, 실제 읽은 바이트가 `8,192바이트`를 넘는 순간 즉시 `413`(`PAYLOAD_TOO_LARGE`, `application/problem+json`)을 응답한다 (`Content-Length` 누락, 작게 조작된 `Content-Length`, `Transfer-Encoding: chunked` 모두 포괄).
   - `8,192바이트` 이하로 읽힌 바이트 배열은 재사용 가능한 중첩 `HttpServletRequestWrapper`에 담아 `filterChain.doFilter(wrappedRequest, response)`로 전달한다.
3. **`ApiExceptionHandler` (및 Jackson 엄격 설정)**:
   - Jackson 3 `JsonMapperBuilderCustomizer` 빈을 통해 다음을 강제한다:
     - `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` 활성화 (미정의 `userId` 필드 등 거부)
     - `StreamReadFeature.STRICT_DUPLICATE_DETECTION` 활성화 (중복 JSON 키 거부)
     - `MapperFeature.ALLOW_COERCION_OF_SCALARS` 비활성화 및 `DeserializationFeature.ACCEPT_FLOAT_AS_INT` 비활성화, 정수(`LogicalType.Integer`)에 대한 부동소수점·문자열·불리언 강제 변환 거부
   - `@RestControllerAdvice`에서 모든 정의된 오류를 `application/problem+json` 본문(`type`, `title`, `status`, `detail`, `instance`, `code`)으로 변환한다:
     - `HttpMediaTypeNotSupportedException` -> `415 UNSUPPORTED_MEDIA_TYPE`
     - `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MethodArgumentNotValidException`, `IllegalArgumentException` -> `400 INVALID_REQUEST`
     - 예약 대상 미존재 예외(`ResourceService` 내부 중첩 예외 등) -> `404 NOT_FOUND`
     - 본문 상한 초과 예외 -> `413 PAYLOAD_TOO_LARGE`
     - `DataAccessException` (단, 무결성 위반 등 비즈니스 충돌이 아닌 저장소 연결/타임아웃 오류) -> `503 DEPENDENCY_UNAVAILABLE`
   - SQL 원문·스택 트레이스·비밀번호는 `detail`에 절대 포함하지 않으며, 분류되지 않은 내부 런타임 예외는 4xx/503으로 둔갑시키지 않고 `500`으로 드러나게 한다.

### 3. 예약 대상 생성·단건 조회 (`resource` 패키지)

1. **`ResourceRepository`**:
   - 기존 `find(UUID id): Optional<ResourceView>`를 유지한다.
   - `insert(UUID id, String name, int capacity, Instant createdAt): ResourceView`를 추가하여 `resources` 테이블에 `id`, `name`, `capacity`, `available`(`= capacity`), `created_at`을 저장하고 `ResourceView`를 반환한다.
2. **`ResourceService`**:
   - `create(String name, int capacity): ResourceView`:
     - `String normalizedName = ResourceRules.normalizeName(name);`
     - `ResourceRules.validateCapacity(capacity);`
     - `UUID id = UUID.randomUUID();`
     - `Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);`
     - `return resourceRepository.insert(id, normalizedName, capacity, now);`
   - `get(UUID id): ResourceView`:
     - `resourceRepository.find(id)`가 비어 있으면 404 예외(중첩 `ResourceNotFoundException`)를 던지고, 존재하면 `ResourceView`를 반환한다.
3. **`ResourceController`**:
   - `POST /api/resources` (`consumes = MediaType.APPLICATION_JSON_VALUE`):
     - 중첩 `CreateResourceRequest`(`name`, `capacity`) 본문을 바인딩하고 필수값 확인 후 `ResourceService.create`를 호출하며, `ResponseEntity.created(URI.create("/api/resources/" + view.id())).body(view)`로 `201 Created`를 반환한다.
   - `GET /api/resources/{id}`:
     - `ResourceService.get(id)`를 호출해 `200 OK`와 `ResourceView`를 반환한다.

## TDD 구현 및 검증 순서

1. **테스트 지원 및 환경 변수 구조 준비**:
   - `.env.example`과 `application-local.yaml`에 `ADMIN_PASSWORD`, `USER_A_PASSWORD`, `USER_B_PASSWORD` 구조를 반영하고, `ContainerTestSupport`·`TestData`에 테스트 전용 가짜 비밀번호와 `ADMIN`·`USER_A`·`USER_B` 고정 UUID 상수를 정의한다.
2. **실패하는 통합 테스트 먼저 작성 (RED)**:
   - `SecurityIT`: 무인증/오류 비밀번호 `401` + `WWW-Authenticate` + `problem+json`, `USER_A`·`USER_B` 생성 요청 `403` + `problem+json`, `ADMIN` 생성 `201`, 세 계정 단건 조회 `200`, `LocalIdentityResolver.userId` 고정 UUID 추출 및 `X-User-Id` 무시, 미인증 상태의 8,193바이트 요청(일반/길이 헤더 없음/chunked) `413` 우선 반환을 작성한다.
   - `ResourceApiIT`: `capacity = 0/1000` 생성·조회 `201/200`, 이름 앞뒤 공백 정리 및 내부 공백·NBSP·이모지 100개 보존, 8,192바이트 본문 재사용, 빈 이름·공백 이름·101자 이름 `400`, `capacity = -1/1001` `400`, 부동소수점(`1.5`, `1.0`)·문자열(`"10"`) 수량 `400`, 미정의 `userId` 필드·중복 JSON 키·깨진 JSON `400`, 잘못된 UUID `400`, 없는 UUID `404`, 미지원 `Content-Type` `415`, 취소의 비어 있지 않은 본문 `400`을 작성한다.
   - `./gradlew integrationTest --tests '*SecurityIT' --tests '*ResourceApiIT'`를 실행하여 기능 미구현으로 실패(RED)하는 것을 먼저 확인하고 기록한다.
3. **프로덕션 기능 구현 (GREEN)**:
   - `ClockConfig`, `LocalIdentityResolver`, `RequestBodyLimitFilter`, `SecurityConfig`, `ApiExceptionHandler`, `ResourceRepository.insert`, `ResourceService`, `ResourceController`를 차례로 구현한다.
   - `./gradlew integrationTest --tests '*SecurityIT' --tests '*ResourceApiIT'`를 다시 실행해 모든 테스트가 통과(GREEN)하는지 확인한다.
4. **전체 검증 게이트 실행**:
   - `./gradlew verify`를 실행하여 1~4단계 전체 단위·통합 테스트가 실패 0건·건너뜀 0건으로 통과하는지 확인하고, `git diff --check`로 문서·코드 서식을 점검한다.

## 복잡도 추적 (Complexity Tracking)

> **헌법 위반 사항 없음 — 정당화가 필요한 추가 복잡도 없음**

| 위반 항목 | 필요한 이유 | 더 단순한 대안을 기각한 이유 |
| --- | --- | --- |
| 해당 없음 | 모든 구성 요소가 계획서 4단계의 명시적 파일 목록과 헌법 원칙 I~V 범위 안에 있음 | 해당 없음 |
