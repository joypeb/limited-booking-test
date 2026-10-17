# 연구 및 결정 기록: 로컬 인증과 예약 대상 생성·조회 API

**작업 브랜치**: `feature/auth-resource-api`

**작성일**: 2026-10-17

**대상 명세**: [spec.md](spec.md)

## 1. 로컬 Basic 인증 계정과 고정 UUID 식별자 구조

- **결정**:
  - `ADMIN`, `USER_A`, `USER_B` 세 계정에 코드 상수로 고정 UUID를 할당하고, 비밀번호는 실행 환경 속성(`ADMIN_PASSWORD`, `USER_A_PASSWORD`, `USER_B_PASSWORD`)에서 주입받아 `BCryptPasswordEncoder` 해시로 보관하는 커스텀 `UserDetails` 구현(`LocalUserPrincipal`)을 사용한다.
  - `LocalIdentityResolver.userId(Authentication): UUID`는 오직 인증된 `Authentication`의 `Principal`(`LocalUserPrincipal`)에서만 고정 UUID를 추출한다. 요청 본문의 `userId`나 임의의 `X-User-Id` 헤더는 읽거나 신뢰하지 않는다.
  - 1단계 기동 확인용 임시 계정 설정(`spring.security.user.*` 및 `TOOLCHAIN_SMOKE_PASSWORD`)은 3개 로컬 계정 비밀번호 속성으로 전환하고, 기존 `BackendApplicationIT` 등 통합 테스트는 테스트용 가짜 비밀번호(`TestBasicAuth` 또는 `@DynamicPropertySource` 주입 값)를 사용하도록 맞춘다.
- **근거**:
  - [로컬 개발 계획](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2절·5절·7절 4단계와 [security 지식](../../knowledge/domain/security.md)에서 `ADMIN`·`USER_A`·`USER_B` 고정 UUID 할당, BCrypt 보관, 환경 비밀번호와 테스트 가짜 비밀번호 분리, `LocalIdentityResolver.userId(Authentication): UUID` 시그니처가 확정되어 있다.
  - `Principal`에 UUID를 직접 보관하면 데이터베이스 추가 조회 없이 인증 직후부터 요청 제한(8단계 예정)·소유자 검증(5~6단계 예정)에서 동일한 UUID를 일관되게 참조할 수 있다.
- **검토한 대안**:
  - Spring 기본 `User` 객체의 `username`만 두고 매번 별도 맵에서 조회하는 방식: `Principal` 자체에 고정 UUID가 결합되지 않아 우회 참조 가능성이 생기므로 기각.
  - 요청 헤더(`X-User-Id`)나 본문 필드로 사용자 UUID를 전달받는 방식: 헌법 원칙 I과 계획서 2절에서 금지된 식별자 위조 경로이므로 기각.

## 2. 무상태 SecurityFilterChain과 필터 단계 problem+json 응답

- **결정**:
  - `SecurityFilterChain`에서 `SessionCreationPolicy.STATELESS`와 HTTP Basic 인증을 설정하고, localhost 비브라우저 API 전제와 재검토 조건(브라우저/외부 공개 시 TLS·CSRF·인증 방식 재검토)을 코드·문서에 명시한 뒤 CSRF를 비활성화한다. CORS 와일드카드(`*`) 설정은 추가하지 않는다.
  - 접근 제어는 `/actuator/health`를 `permitAll()`(또는 인증 여부와 무관한 기동 확인 경로)로 유지하고, `POST /api/resources`는 `hasRole("ADMIN")`, `GET /api/resources/*` 및 `/api/reservations/**`를 포함한 나머지 API 경로는 `authenticated()`로 보호한다.
  - `AuthenticationEntryPoint`(401 `UNAUTHENTICATED` + `WWW-Authenticate: Basic realm="reservation-local"`)와 `AccessDeniedHandler`(403 `FORBIDDEN`)를 직접 등록해 필터 단계의 인증·권한 예외도 `application/problem+json` 본문(`type`, `title`, `status`, `detail`, `instance`, `code`)으로 직렬화한다.
- **근거**:
  - Spring Security 기본 `BasicAuthenticationEntryPoint`와 `AccessDeniedHandlerImpl`은 `application/problem+json` 본문과 필수 `code` 필드를 생성하지 않는다.
  - [OpenAPI 계약](../../docs/openapi.yaml)과 [API·보안 규칙](../../knowledge/rules/api-and-security.md)에서 401/403을 포함한 모든 정의된 오류 응답이 `Problem`(RFC 9457 호환 + `code`) 스키마와 `401`의 `WWW-Authenticate` 헤더를 반환하도록 요구한다.
- **검토한 대안**:
  - 서블릿 컨테이너 기본 에러 페이지(`/error`) 위임: 필터 체인 응답 포맷이 컨트롤러 예외 처리와 어긋나고 `WWW-Authenticate` 헤더 및 `instance` 경로 제어가 불투명해 기각.

## 3. 인증 앞단 본문 크기 상한 필터(`RequestBodyLimitFilter`)와 재사용 래퍼

- **결정**:
  - `RequestBodyLimitFilter`를 `SecurityFilterChain`의 인증 필터(`BasicAuthenticationFilter`)보다 **앞**에 등록한다.
  - `Content-Length` 헤더가 `8,192바이트(8KiB)`를 초과하면 즉시 `413`(`PAYLOAD_TOO_LARGE`)을 반환하고, 헤더가 없거나 `8,192` 이하이거나 분할 전송(`Transfer-Encoding: chunked`)인 경우에도 실제 입력 스트림에서 최대 `8,193바이트`까지만 읽어 `8,192바이트` 초과 시 즉시 읽기를 중단하고 `413`(`application/problem+json`)을 반환한다.
  - `8,192바이트` 이하로 읽힌 바이트 배열은 재사용 가능한 `HttpServletRequestWrapper`(`ServletInputStream`·`BufferedReader` 재제공)에 감싸 체인 뒤쪽으로 전달한다.
- **근거**:
  - [로컬 개발 계획](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2절 입력 검증 및 처리 순서(`① 8KiB 본문 상한 -> ② Basic 인증 -> ③ 권한 -> ...`)와 7절 4단계 체크리스트에서 인증 앞단 등록, 실제 읽기 8KiB 상한, 길이 헤더 없는 본문·chunked 요청 검사, 재사용 래퍼 전달을 명시하고 있다.
  - 서블릿 입력 스트림은 한 번 소비되면 Jackson 역직렬화에서 다시 읽을 수 없으므로, 필터에서 실제 바이트 수를 검증한 뒤 메모리에 보존된 최대 8KiB 배열을 래퍼로 재제공해야 한다.
- **검토한 대안**:
  - `Content-Length` 헤더만 검사하는 방식: 헤더 생략이나 조작, chunked 요청에서 8KiB 초과 본문을 막을 수 없어 기각.
  - Spring `ContentCachingRequestWrapper` 단독 사용: 하위 계층이 스트림을 읽기 전에는 본문을 미리 검사하지 않으므로 인증 앞단에서 즉시 413으로 차단할 수 없어 기각.

## 4. Spring Boot 4.1.1(Jackson 3) 엄격 JSON 역직렬화 설정

- **결정**:
  - Spring Boot 4.1.1이 사용하는 Jackson 3(`tools.jackson.databind.json.JsonMapper` / `JsonMapperBuilderCustomizer`) 설정에서 다음 세 가지 엄격 옵션을 명시적으로 활성화한다:
    1. 정의하지 않은 알 수 없는 JSON 필드 거부 (`DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` 활성화)
    2. 동일 JSON 객체 내 중복 키 거부 (`StreamReadFeature.STRICT_DUPLICATE_DETECTION` 활성화)
    3. 스칼라·정수 필드에 대한 부동소수점(`1.5`, `1.0`)·문자열(`"10"`)·불리언 등의 암묵적 형 변환 거부 (`MapperFeature.ALLOW_COERCION_OF_SCALARS` 비활성화 및 `DeserializationFeature.FAIL_ON_FLOAT_FOR_INTS` 활성화, 필요 시 `LogicalType.Integer`에 대한 `CoercionInputShape.Float`·`String` 거부 설정)
- **근거**:
  - `backend/build.gradle`의 `spring-boot-starter-webmvc:4.1.1`은 Jackson 3(`tools.jackson.core:jackson-databind:3.1.5`)을 사용한다. Jackson 3의 기본값 일부가 엄격해졌더라도 계획서 4단계 요구사항("알 수 없는 필드·중복 JSON 키·숫자의 암묵적 형 변환을 거부하는 설정을 확인한다")을 보장하려면 빌더 커스터마이저에 명시적으로 고정하고 통합 테스트(`ResourceApiIT`)로 검증해야 한다.
  - 요청 본문에 `userId` 같은 미정의 필드를 넣거나 `capacity: 1.5`를 보냈을 때 조용히 무시하거나 반올림/절삭하지 않고 `400`(`INVALID_REQUEST`)으로 거부해야 한다.
- **검토한 대안**:
  - 컨트롤러에서 `Map<String, Object>`로 수동 파싱: 중복 JSON 키를 Map 단계에서 탐지할 수 없고 DTO 타입 안정성이 떨어져 기각.

## 5. 취소 경로(`/api/reservations/{id}/cancel`)의 비어 있지 않은 본문 400 검증

- **결정**:
  - 4단계에서는 `POST /api/reservations/{id}/cancel` 요청에 대해 `RequestBodyLimitFilter`의 8KiB 상한(초과 시 `413`)과 Basic 인증(`401`)을 거친 뒤, 캐시된 요청 본문이 0바이트보다 크면 즉시 `400`(`INVALID_REQUEST`, `application/problem+json`)으로 거부한다.
  - 취소의 상태 전이·수량 복구·소유자 검증 등 실제 비즈니스 로직은 6단계 범위이므로, 4단계에서는 본문 유무 및 경로 UUID 형식 검증까지를 보장한다.
- **근거**:
  - [로컬 개발 계획](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 7절 4단계 체크리스트("미지원 Content-Type 415와 취소의 비어 있지 않은 본문 400을 검증한다")와 2절 처리 순서("취소 요청은 본문이 비어 있어야 하며, 8KiB 이하의 비어 있지 않은 본문은 400(INVALID_REQUEST)으로, 8KiB를 초과한 본문은 본문 크기 상한 필터에서 413(PAYLOAD_TOO_LARGE)으로 거부한다")를 정확히 충족한다.
- **검토한 대안**:
  - 6단계까지 취소 본문 검증을 미루는 방식: 4단계 체크리스트 완료 기준을 위반하므로 기각.

## 6. UTC Clock 주입과 예약 대상 저장소(`ResourceRepository`) 쓰기 확장

- **결정**:
  - `common/ClockConfig`에서 `@Bean Clock clock()`으로 `Clock.systemUTC()`를 등록하고, `ResourceService`가 이를 주입받아 `Instant.now(clock).truncatedTo(ChronoUnit.MICROS)`로 생성 시각을 만든다.
  - `ResourceRepository`에 `insert(UUID id, String name, int capacity, Instant createdAt)`(또는 `save`) 메서드를 추가해 `NamedParameterJdbcTemplate`으로 `resources` 테이블에 `capacity`와 동일한 초기 `available` 값을 저장하고 `ResourceView`를 반환한다.
  - `ResourceService.create(String name, int capacity): ResourceView`는 3단계에서 검증된 `ResourceRules.normalizeName(name)`과 `ResourceRules.validateCapacity(capacity)`를 먼저 호출한 뒤 저장하며, `ResourceService.get(UUID id): ResourceView`는 `ResourceRepository.find(id)`가 비어 있을 때 404 도메인 예외(`ResourceNotFoundException` 등)를 던진다.
- **근거**:
  - 2단계에서 확정한 PostgreSQL `TIMESTAMPTZ` 마이크로초 정규화 패턴(`ChronoUnit.MICROS`)과 3단계에서 확정한 `ResourceRules` 순수 함수를 그대로 연결해 계층 간 규칙 불일치를 방지한다.
- **검토한 대안**:
  - 데이터베이스 `now()` 기본값에만 의존하는 방식: 계획서 4단계 체크리스트("ClockConfig에서 UTC Clock을 주입하고 시각 생성의 기준으로 사용한다")를 충족하지 못하므로 기각.
