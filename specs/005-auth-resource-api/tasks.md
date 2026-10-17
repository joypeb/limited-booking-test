# 작업 목록: 로컬 인증과 예약 대상 생성·조회 API

**입력**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/auth-resource-contract.md](contracts/auth-resource-contract.md), [quickstart.md](quickstart.md)

**상태**: 0/27 미수행 (`$speckit-tasks` 산출물, 구현 및 테스트 실행 전). 작업 체크(`[x]`)는 실제 코드 작성·명령 실행·검증 후에만 표시하며, 맞춤 [요구사항 검토 목록](checklists/auth-resource-api.md)의 검토자 판정 체크와 구분한다.

**테스트 원칙**: [로컬 개발 계획](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 7절 4단계와 명세 FR-010·SC-005에 따라 각 사용자 이야기별로 통합 테스트(`SecurityIT`, `ResourceApiIT`)를 먼저 작성해 기능 미구현 상태의 의도된 실패(RED)를 확인·기록한 뒤 프로덕션 코드를 구현해 통과(GREEN)를 확인한다. 컴파일 오류나 환경 기동 실패는 기능 RED로 세지 않는다.

## 형식과 경로 규칙

- 모든 작업은 `- [ ] T번호 [P 선택] [US번호 이야기 작업만] 설명과 정확한 파일 경로` 형식을 따른다.
- 파일 경로는 저장소 루트(`/Users/joypeb/Desktop/projects/limited-booking`) 기준 상대 경로를 명시하며, 모든 Gradle 명령의 실행 디렉터리는 `backend/`다.
- `[P]`는 선행 작업이 끝난 뒤 서로 다른 파일을 충돌 없이 병행 편집할 수 있음을 뜻한다. Gradle 테스트 실행, 동일 파일 수정, `docs/test-evidence.md` 기록, Git 커밋은 병행하지 않고 순차 수행한다.

---

## Phase 1: Setup (작업 환경 및 로컬 인증 속성 구조 준비)

**목적**: 기존 변경 보존·브랜치 상태 확인, 요구사항 품질 검토 게이트 확인, 3개 로컬 계정 비밀번호 환경 변수 키 구성을 준비한다.

- [X] T001 `knowledge/rules/git-flow.md`와 `knowledge/rules/commit.md`에 따라 저장소 루트에서 현재 브랜치·미커밋 문서를 확인하고 `specs/005-auth-resource-api/` 설계 문서를 보존한 상태에서 `develop` 기반 `feature/auth-resource-api` 작업 브랜치를 준비해 `docs/test-evidence.md`에 기록한다.
- [X] T002 `specs/005-auth-resource-api/checklists/auth-resource-api.md`와 `specs/005-auth-resource-api/checklists/requirements.md`의 검토 상태를 확인하고 구현 착수 전 게이트 상태를 `docs/test-evidence.md`에 기록한다 (구현 담당자가 검토자 소유 체크를 임의 변경하지 않는다).
- [X] T003 [P] `.env.example`과 `backend/src/main/resources/application-local.yaml`에서 1단계 임시 `toolchain` 계정 설정을 `ADMIN_PASSWORD`, `USER_A_PASSWORD`, `USER_B_PASSWORD` 환경 변수 기반 로컬 인증 속성 구조로 갱신한다 ("비밀번호 원문은 코드·문서·로그·오류 응답에 절대 남기지 않으며, 테스트에서는 실행 환경 비밀번호와 분리된 테스트 전용 가짜 비밀번호를 사용한다").

---

## Phase 2: Foundational (공통 기반 및 테스트 지원 인프라)

**목적**: 모든 사용자 이야기가 공통으로 사용하는 UTC `Clock`, 공통 `Problem` 오류 응답 생성 기반, 테스트 전용 가짜 비밀번호·고정 UUID 상수·HTTP 요청 헬퍼를 준비한다.

**⚠️ 중요**: 사용자 이야기(Phase 3~5) 구현 및 통합 테스트 실행 전에 완료되어야 한다.

- [X] T004 [P] `backend/src/main/java/com/limitedbooking/backend/common/ClockConfig.java`에 `@Configuration` 및 `@Bean public Clock clock()`(`Clock.systemUTC()`)을 구현한다.
- [X] T005 [P] `backend/src/test/java/com/limitedbooking/backend/support/TestData.java`, `backend/src/test/java/com/limitedbooking/backend/support/ContainerTestSupport.java`, `backend/src/test/java/com/limitedbooking/backend/support/HttpClientTestSupport.java`, `backend/src/test/java/com/limitedbooking/backend/BackendApplicationIT.java`에 `ADMIN`·`USER_A`·`USER_B` 고정 UUID 상수, 테스트 전용 가짜 비밀번호(`@DynamicPropertySource` 주입), Basic `Authorization` 헤더 생성 및 raw/chunked 본문 전송 헬퍼를 구성한다 ("세 계정의 UUID는 서로 중복되지 않으며 프로세스 재시작과 무관하게 항상 동일하다").
- [X] T006 `backend/src/main/java/com/limitedbooking/backend/common/ApiExceptionHandler.java`에 `application/problem+json` 응답과 필수 6개 속성(`type`(기본값 `"about:blank"`), `title`, `status`, `detail`, `instance`(`request.getRequestURI()`), `code`)을 생성·직렬화하는 공통 헬퍼 및 기본 `@RestControllerAdvice` 골격을 마련한다 ("내부 구현(SQL·스택 트레이스·비밀번호)을 노출하지 않는 안전한 설명").

**체크포인트**: UTC 시계, 공통 `Problem` 응답 구조, 테스트 컨테이너·Basic 인증 헬퍼 준비 완료.

---

## Phase 3: User Story 1 - 로컬 계정 인증과 역할별 권한 확인 (Priority: P1) 🎯 MVP

**목표**: 고정 UUID가 할당된 `ADMIN`·`USER_A`·`USER_B` 로컬 Basic 인증(BCrypt), `Principal` 단일 신뢰(`LocalIdentityResolver.userId`), 무상태 세션·CSRF/CORS 정책, `401`(`UNAUTHENTICATED` + `WWW-Authenticate`)·`403`(`FORBIDDEN`) `application/problem+json` 응답을 제공한다 (FR-001~004, SC-001).

**독립 검증**: 예약 생성·취소 없이도 `SecurityIT`를 실행해 무인증·오류 비밀번호(`401` + `WWW-Authenticate`), 일반 사용자의 생성 요청(`403`), 관리자의 생성 허용(`201`), 세 계정의 조회 허용(`200`), `LocalIdentityResolver.userId` 고정 UUID 반환 및 `X-User-Id` 헤더 무시를 독립적으로 확인한다.

### User Story 1 테스트 작성 및 의도된 실패 확인 (RED) ⚠️

- [ ] T007 [US1] `backend/src/test/java/com/limitedbooking/backend/security/SecurityIT.java`에 무인증/잘못된 비밀번호 `401`(`WWW-Authenticate: Basic realm="reservation-local"`, `application/problem+json`, `code=UNAUTHENTICATED`), `USER_A`·`USER_B`의 `POST /api/resources` 요청 `403`(`application/problem+json`, `code=FORBIDDEN`), `ADMIN`의 `POST /api/resources` `201`, 세 계정의 `GET /api/resources/{id}` `200`, `Set-Cookie`(`JSESSIONID`) 미생성, `LocalIdentityResolver.userId(Authentication)` 고정 UUID 반환 및 `X-User-Id` 헤더 무시 테스트를 작성한다.
- [ ] T008 [US1] `backend/src/main/java/com/limitedbooking/backend/security/LocalIdentityResolver.java`와 `backend/src/main/java/com/limitedbooking/backend/resource/ResourceController.java`에 컴파일 가능한 최소 골격만 둔 상태에서 `backend/`에서 `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT`를 실행해 기능 미구현으로 실패(RED)하는 결과를 확인하고 `docs/test-evidence.md`에 기록한다.

### User Story 1 프로덕션 구현 (GREEN)

- [ ] T009 [P] [US1] `backend/src/main/java/com/limitedbooking/backend/security/LocalIdentityResolver.java`에 `public UUID userId(Authentication authentication)`을 구현한다 ("`LocalIdentityResolver.userId(Authentication)`는 인증된 `Authentication`의 `Principal`에서만 `UUID`를 반환하며, 인증 정보가 없거나 알 수 없는 주체이면 인증 오류로 거부한다. 요청 헤더(`X-User-Id`)나 요청 본문(`userId`)을 참조하는 메서드를 제공하지 않는다").
- [ ] T010 [US1] `backend/src/main/java/com/limitedbooking/backend/security/SecurityConfig.java`에 `ADMIN`(`ROLE_ADMIN`)·`USER_A`(`ROLE_USER`)·`USER_B`(`ROLE_USER`) 고정 UUID Principal과 환경 주입 비밀번호의 `BCryptPasswordEncoder` 보관, `SessionCreationPolicy.STATELESS`, localhost 비브라우저 전제 주석이 포함된 CSRF 비활성화(CORS 와일드카드 미추가), `/actuator/health` 허용·`POST /api/resources` `hasRole("ADMIN")`·그 외 `/api/**` `authenticated()` 권한 규칙, 그리고 `401`(`UNAUTHENTICATED` + `WWW-Authenticate: Basic realm="reservation-local"`)·`403`(`FORBIDDEN`)을 `application/problem+json`으로 작성하는 `AuthenticationEntryPoint`·`AccessDeniedHandler`를 구현한다.

**체크포인트**: 로컬 Basic 인증, 고정 UUID `Principal`, 역할별 접근 제어(`401`/`403` `problem+json`) 준비 완료 (US2 엔드포인트 연결 직후 `SecurityIT` 전체 통과 확인).

---

## Phase 4: User Story 2 - 예약 대상 생성과 단건 조회 (Priority: P1)

**목표**: 관리자가 유효한 이름(`1~100` 코드 포인트)과 최초 수량(`0~1000`)으로 예약 대상을 생성(`201 Created`, `Location: /api/resources/{id}`, `available = capacity`, UTC `created_at`)하고, 인증된 사용자가 UUID로 단건 조회(`200 OK` 또는 미존재 시 `404 NOT_FOUND`)할 수 있게 한다 (FR-005~006, SC-001~002).

**독립 검증**: `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT --tests com.limitedbooking.backend.resource.ResourceApiIT`에서 `ADMIN` 대상 생성(`capacity = 0/1000`, 이름 공백 정규화·NBSP/이모지 보존) 후 `Location` 헤더와 `ResourceView`를 검증하고, `USER_A`·`USER_B` 단건 조회(`200`) 및 미존재 UUID 조회(`404 NOT_FOUND`)를 확인한다.

### User Story 2 테스트 작성 및 의도된 실패 확인 (RED) ⚠️

- [ ] T011 [US2] `backend/src/test/java/com/limitedbooking/backend/resource/ResourceApiIT.java`에 `capacity = 0`·`capacity = 1000` 생성(`201` + `Location: /api/resources/{id}` + `available == capacity`) 후 단건 조회(`200`), 앞뒤 일반 공백·탭·줄바꿈 제거 및 내부 공백·비줄바꿈 공백(`U+00A0`·`U+2007`·`U+202F`)·보충 평면 이모지(`😀` 100개) 보존 생성·조회, 존재하지 않는 UUID 조회(`404 NOT_FOUND` + `application/problem+json`) 테스트를 추가하고 `./gradlew integrationTest --tests com.limitedbooking.backend.resource.ResourceApiIT`에서 미구현 실패(RED)를 확인해 `docs/test-evidence.md`에 기록한다.

### User Story 2 프로덕션 구현 (GREEN)

- [ ] T012 [P] [US2] `backend/src/main/java/com/limitedbooking/backend/resource/ResourceRepository.java`에 `public ResourceView insert(UUID id, String name, int capacity, Instant createdAt)`를 추가한다 (`resources` 테이블에 `id`, `name`, `capacity`, `available = capacity`, `created_at`을 저장하고 `ResourceView`를 반환하며 기존 `find(UUID id)`를 유지한다).
- [ ] T013 [US2] `backend/src/main/java/com/limitedbooking/backend/resource/ResourceService.java`에 `public ResourceView create(String name, int capacity)`(`ResourceRules.normalizeName(name)`, `ResourceRules.validateCapacity(capacity)`, `UUID.randomUUID()`, 주입된 UTC `Clock` 기준 `Instant.now(clock).truncatedTo(ChronoUnit.MICROS)`로 `resourceRepository.insert` 호출)와 `public ResourceView get(UUID id)`(`resourceRepository.find(id)` 미존재 시 404 전용 예외 발생)를 구현한다.
- [ ] T014 [US2] `backend/src/main/java/com/limitedbooking/backend/resource/ResourceController.java`와 `backend/src/main/java/com/limitedbooking/backend/common/ApiExceptionHandler.java`에 `POST /api/resources`(`consumes = MediaType.APPLICATION_JSON_VALUE`, `201 Created`, `Location: /api/resources/{id}`, `ResourceView` 본문), `GET /api/resources/{id}`(`200 OK`, `ResourceView`), 그리고 미존재 대상 예외의 `404`(`NOT_FOUND`, `application/problem+json`) 변환을 구현한다.
- [ ] T015 [US2] `backend/`에서 `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT --tests com.limitedbooking.backend.resource.ResourceApiIT`를 실행해 US1·US2 시나리오가 통과(GREEN)하는지 확인하고 결과를 `docs/test-evidence.md`에 기록한다.

**체크포인트**: US1(인증·권한)과 US2(예약 대상 생성·단건 조회)가 실제 보안 필터 체인과 PostgreSQL 저장소 위에서 독립적으로 동작한다.

---

## Phase 5: User Story 3 - 본문 크기 상한·엄격한 입력 검증·공통 오류 응답 (Priority: P2)

**목표**: 인증 앞단에서 `8,192바이트(8KiB)` 초과 본문을 길이 헤더 유무·chunked 여부와 무관하게 `413`(`PAYLOAD_TOO_LARGE`)으로 차단하고 `8,192바이트` 이하 본문은 재사용 래퍼로 전달하며, 미지원 `Content-Type`(`415`), 알 수 없는 필드(`userId` 등)·중복 JSON 키·숫자 암묵적 형 변환(부동소수점·문자열)·이름/수량 규칙 위반·잘못된 UUID·취소의 비어 있지 않은 본문(`400 INVALID_REQUEST`), 저장소 장애(`503 DEPENDENCY_UNAVAILABLE`)를 모두 `application/problem+json`으로 일관되게 처리한다 (FR-007~009, SC-002~004).

**독립 검증**: `SecurityIT`와 `ResourceApiIT`에서 미인증 상태의 8,193바이트 요청(일반·길이 헤더 없음·chunked)이 `401`보다 먼저 `413`을 반환하는지, 8,192바이트 이하 본문이 정상 재사용되는지, 그리고 모든 경계값·엄격 JSON·미디어 타입·취소 비어 있지 않은 본문 거부 사례가 기대 상태 코드와 `application/problem+json` 6개 필수 속성으로 응답하며 거부된 요청으로 생성되는 대상이 0건인지 확인한다.

### User Story 3 테스트 작성 및 의도된 실패 확인 (RED) ⚠️

- [ ] T016 [P] [US3] `backend/src/test/java/com/limitedbooking/backend/security/SecurityIT.java`에 미인증 및 인증 상태에서 `8,193바이트` 초과 본문(`Content-Length` 포함, 길이 헤더 생략/조작, `Transfer-Encoding: chunked`) 전송 시 인증(`401`)보다 먼저 `413`(`PAYLOAD_TOO_LARGE`, `application/problem+json`)을 반환하는 테스트와 `8,192바이트` 이하 본문이 손실 없이 하위 계층에 재사용되는 테스트를 추가한다.
- [ ] T017 [P] [US3] `backend/src/test/java/com/limitedbooking/backend/resource/ResourceApiIT.java`에 빈 이름·공백만 있는 이름·정리 후 101 코드 포인트(`😀` 101개 포함) 이름, `capacity = -1`·`1001`, 부동소수점 수량(`1.5`, `1.0`), 문자열 수량(`"10"`), 정의하지 않은 필드(`userId` 등), 동일 JSON 객체 내 중복 키(`{"name":"a","name":"b","capacity":1}`), 깨진 JSON 구문, 잘못된 UUID 경로 조회(`400 INVALID_REQUEST`), `text/plain` 등 미지원 `Content-Type` 생성 요청(`415 UNSUPPORTED_MEDIA_TYPE`), 취소 경로(`/api/reservations/{id}/cancel`)의 8KiB 이하 비어 있지 않은 본문(`400 INVALID_REQUEST`) 및 8KiB 초과 본문(`413 PAYLOAD_TOO_LARGE`), 그리고 모든 오류 응답의 `application/problem+json` 6개 필수 속성·민감정보 비노출·거부 시 DB 미저장(0건) 검증을 추가한다.
- [ ] T018 [US3] `backend/`에서 `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT --tests com.limitedbooking.backend.resource.ResourceApiIT`를 실행해 US3 신규 검증 항목들이 기능 미구현으로 실패(RED)하는 것을 확인하고 `docs/test-evidence.md`에 기록한다.

### User Story 3 프로덕션 구현 (GREEN)

- [ ] T019 [P] [US3] `backend/src/main/java/com/limitedbooking/backend/common/RequestBodyLimitFilter.java`를 구현하고 `backend/src/main/java/com/limitedbooking/backend/security/SecurityConfig.java`에서 인증 필터(`BasicAuthenticationFilter`)보다 앞에 등록한다 (`Content-Length > 8192` 또는 실제 스트림 읽기 바이트가 `8,192바이트`를 초과하면 길이 헤더 유무·chunked 여부와 무관하게 즉시 `413 PAYLOAD_TOO_LARGE`와 `application/problem+json`을 반환하고, `8,192바이트` 이하 본문은 `ServletInputStream`·`BufferedReader`를 재제공하는 `HttpServletRequestWrapper`로 감싸 전달한다).
- [ ] T020 [US3] `backend/src/main/java/com/limitedbooking/backend/common/ApiExceptionHandler.java`에 Jackson 3 엄격 역직렬화 설정(`DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` 활성화, `StreamReadFeature.STRICT_DUPLICATE_DETECTION` 활성화, `MapperFeature.ALLOW_COERCION_OF_SCALARS` 비활성화 및 `DeserializationFeature.FAIL_ON_FLOAT_FOR_INTS`·정수 필드 스칼라 강제 형 변환 거부)과 `400`(`INVALID_REQUEST` — 필수 필드 `name`/`capacity` `null`, JSON 파싱/형 변환/미정의 필드/중복 키 오류, 잘못된 UUID 경로 변수, `IllegalArgumentException`), `413`(`PAYLOAD_TOO_LARGE`), `415`(`UNSUPPORTED_MEDIA_TYPE`), `503`(`DEPENDENCY_UNAVAILABLE` — `DataAccessException`) 예외 매핑을 구현한다 ("분류되지 않은 내부 런타임 결함(`NullPointerException`, 잘못된 SQL 등)은 임의의 4xx/503 코드로 숨기지 않고 `500`으로 드러나게 한다").
- [ ] T021 [US3] `backend/src/main/java/com/limitedbooking/backend/resource/ResourceController.java`에서 중첩 `CreateResourceRequest` 필수값(`name == null` 또는 `capacity == null` 시 `400 INVALID_REQUEST`) 검증을 구현하고, `resource`와 `reservation` 도메인 경계를 침범하지 않도록 `backend/src/main/java/com/limitedbooking/backend/security/SecurityConfig.java`(또는 `RequestBodyLimitFilter.java`의 인증 후 검증 단계)에서 `POST /api/reservations/{id}/cancel`의 8KiB 이하 비어 있지 않은 본문 및 잘못된 UUID 경로에 대한 `400`(`INVALID_REQUEST`, `application/problem+json`) 거부를 구현한다.
- [ ] T022 [US3] `backend/`에서 `./gradlew integrationTest --tests com.limitedbooking.backend.security.SecurityIT --tests com.limitedbooking.backend.resource.ResourceApiIT`를 실행해 `SecurityIT`와 `ResourceApiIT`의 모든 테스트가 실패 0건·건너뜀 0건으로 통과(GREEN)하는지 확인하고 `docs/test-evidence.md`에 기록한다.

**체크포인트**: `SecurityIT`와 `ResourceApiIT`의 모든 인증·권한·8KiB 상한·엄격 JSON·예약 대상 API·공통 `problem+json` 검증이 통과한다.

---

## Phase 6: Polish & Cross-Cutting Concerns (전체 검증 게이트·문서 동기화·커밋)

**목적**: 1~4단계 전체 단위·통합 검증 게이트(`./gradlew verify`)를 통과시키고, 지식 문서·결정 기록·실행 증거·계획서 체크박스를 실제 결과와 동기화한 뒤 커밋한다 (FR-010, SC-005).

- [ ] T023 `backend/`에서 `./gradlew verify`를 실행해 기존 1~3단계 단위·통합 테스트와 4단계 `SecurityIT`·`ResourceApiIT` 전체가 종료 코드 `0`, 실패 `0`, 오류 `0`, 건너뜀 `0`으로 통과하는지 확인하고 JUnit XML 결과(`backend/build/test-results/test/TEST-*.xml`, `backend/build/test-results/integrationTest/TEST-*.xml`)와 함께 `docs/test-evidence.md`에 기록한다.
- [ ] T024 [P] `knowledge/domain/security.md`, `knowledge/domain/resource.md`, `knowledge/README.md`, `README.md`, `docs/decisions.md`에 4단계 실제 구현 클래스(`SecurityConfig`, `LocalIdentityResolver`, `ClockConfig`, `RequestBodyLimitFilter`, `ApiExceptionHandler`, `ResourceRepository`, `ResourceService`, `ResourceController`)와 통합 테스트(`SecurityIT`, `ResourceApiIT`) 상태 및 5~8단계 미구현 범위를 반영한다.
- [ ] T025 [P] `한정 수량 예약 백엔드 로컬 개발 계획.md`의 7절 「4단계: 로컬 인증과 예약 대상 API」 체크리스트 항목과 `specs/005-auth-resource-api/spec.md`·`specs/005-auth-resource-api/plan.md`·`specs/005-auth-resource-api/tasks.md`의 완료 상태를 실제 실행 증거(`docs/test-evidence.md`)와 대조해 갱신한다 (`specs/005-auth-resource-api/checklists/auth-resource-api.md`는 검토자 소유 문서이므로 수정하지 않는다).
- [ ] T026 저장소 루트에서 `git diff --check`를 실행해 모든 코드·설정·Markdown 문서의 서식과 공백 오류가 없는지 확인한다.
- [ ] T027 `knowledge/rules/commit.md`에 따라 변경된 파일들을 목적별 단위로 검토·스테이징하고 한국어 커밋 메시지로 커밋한 뒤 최종 `git status`와 커밋 해시를 `docs/test-evidence.md`에 기록한다 (외부 원격 저장소 `push`나 브랜치 병합은 수행하지 않는다).

---

## 의존성과 실행 순서 (Dependencies & Execution Order)

```text
Phase 1 (Setup: T001 → T002 → T003)
  ↓
Phase 2 (Foundational: T004, T005, T006)
  ↓
Phase 3 (US1 - 로컬 인증·권한: T007 → T008(RED) → T009, T010)
  ↓
Phase 4 (US2 - 예약 대상 생성·조회: T011(RED) → T012 → T013 → T014 → T015(GREEN))
  ↓
Phase 5 (US3 - 8KiB 상한·엄격 입력·오류 응답: T016, T017 → T018(RED) → T019, T020, T021 → T022(GREEN))
  ↓
Phase 6 (Polish & Verify: T023(./gradlew verify) → T024, T025 → T026 → T027(Commit))
```

### 사용자 이야기 간 의존성

- **User Story 1 (P1)**: Phase 2 완료 직후 시작. 모든 API 요청의 보안 필터 체인과 고정 UUID Principal을 제공한다.
- **User Story 2 (P1)**: US1의 `SecurityConfig` 보호 경로 위에서 `POST /api/resources`(`201`)와 `GET /api/resources/{id}`(`200`/`404`)를 완성해 US1의 권한 허용 시나리오와 US2의 생성·단건 조회 시나리오를 함께 완결한다.
- **User Story 3 (P2)**: US1·US2의 기본 경로 위에 인증 앞단 `RequestBodyLimitFilter`(`413`), Jackson 3 엄격 역직렬화(`400`), `415`, 취소 비어 있지 않은 본문(`400`) 검증을 결합해 모든 경계 사례를 완결한다.

### 이야기별 병행 가능 예시 (Parallel Opportunities)

| 단계 / 이야기 | 병행 가능한 파일 작업 (`[P]`) | 순차 실행해야 하는 작업 |
| --- | --- | --- |
| Phase 1~2 | `T003`(`.env.example`, `application-local.yaml`), `T004`(`ClockConfig.java`), `T005`(테스트 지원 클래스) | `T001`~`T002` 상태 기록, `T006` `ApiExceptionHandler` 골격 |
| Phase 3 (US1) | `T009`(`LocalIdentityResolver.java`)와 `T010`(`SecurityConfig.java`)은 서로 다른 파일 | `T007` 테스트 작성 → `T008` Gradle RED 실행·기록 |
| Phase 4 (US2) | `T012`(`ResourceRepository.java`) 작성 | `T011` RED 확인 → `T013`(`ResourceService`) → `T014`(`ResourceController`) → `T015` GREEN 실행·기록 |
| Phase 5 (US3) | `T016`(`SecurityIT.java`)과 `T017`(`ResourceApiIT.java`) 테스트 추가, `T019`(`RequestBodyLimitFilter.java`)와 `T020`(`ApiExceptionHandler.java`) 구현 | `T018` RED 실행·기록 → `T019`~`T021` 반영 → `T022` GREEN 실행·기록 |
| Phase 6 (Polish) | `T024`(지식·가이드 문서)와 `T025`(계획서·명세 상태) 편집 | `T023` `./gradlew verify` 실행 → `T026` `git diff --check` → `T027` 커밋 |

---

## 요구사항·인수 기준 추적표

| 명세 요구사항 / 성공 기준 | 담당 작업 ID | 검증 테스트 클래스 |
| --- | --- | --- |
| FR-001 (3계정 고정 UUID, 환경 비밀번호 BCrypt 보관, 테스트 가짜 비밀번호 분리) | T003, T005, T007, T010 | `SecurityIT`, `BackendApplicationIT` |
| FR-002 (무상태 Basic 인증, `401` + `WWW-Authenticate`, 일반 사용자 생성 `403`) | T007, T008, T010, T015 | `SecurityIT` |
| FR-003 (`Principal`에서만 UUID 추출, `X-User-Id` 무시, 본문 `userId` `400`) | T007, T009, T017, T020 | `SecurityIT`, `ResourceApiIT` |
| FR-004 (localhost 비브라우저 전제, 무세션, CSRF 사유 기록, CORS 와일드카드 금지) | T007, T010, T024 | `SecurityIT` |
| FR-005 (관리자 대상 생성 `201`, `Location`, `ResourceRules`, UTC `Clock`) | T004, T011~T015 | `SecurityIT`, `ResourceApiIT` |
| FR-006 (인증 사용자 단건 조회 `200`, 미존재 `404`, 잘못된 UUID `400`) | T011~T015, T017, T020 | `SecurityIT`, `ResourceApiIT` |
| FR-007 (인증 앞단 8KiB 실제 읽기 상한 `413`, chunked 포함, 재사용 래퍼) | T016, T018, T019, T022 | `SecurityIT`, `ResourceApiIT` |
| FR-008 (`415`, 미정의 필드·중복 키·암묵적 형 변환·경계 밖 값·취소 비어 있지 않은 본문 `400`) | T017, T018, T020~T022 | `ResourceApiIT` |
| FR-009 (전 구간 `application/problem+json` 6속성, 민감정보 비노출, `503`/`500` 분리) | T006, T010, T014, T019, T020, T022 | `SecurityIT`, `ResourceApiIT` |
| FR-010, SC-001~005 (TDD RED/GREEN 증거, `./gradlew verify` 통과, 문서 동기화) | T008, T011, T015, T018, T022, T023~T027 | `./gradlew verify` 전체 |

---

## 구현 전략 (Implementation Strategy)

### 1. MVP 우선 (Phase 1 ~ Phase 4: US1 + US2)

1. Phase 1~2(환경 속성, `ClockConfig`, 테스트 헬퍼, `Problem` 응답 골격)를 완료한다.
2. Phase 3(US1)과 Phase 4(US2)를 통해 `SecurityIT`와 `ResourceApiIT`의 기본 인증·권한(`401`/`403`) 및 대상 생성(`201`)·조회(`200`/`404`)가 실제 필터 체인과 DB 위에서 동작하는 첫 수직 슬라이스(MVP)를 완성하고 검증한다.

### 2. 점진적 경계 강화 (Phase 5 ~ Phase 6: US3 + 전체 게이트)

1. Phase 5(US3)에서 인증 앞단 `RequestBodyLimitFilter`(8KiB 상한 `413` + 재사용 래퍼), Jackson 3 엄격 설정, `415`, 취소의 비어 있지 않은 본문 `400`을 추가해 모든 악성/비정상 입력 경계를 닫는다.
2. Phase 6에서 `./gradlew verify`로 1~4단계 전체 회귀 검증을 수행하고 실행 증거와 지식 문서를 동기화한 뒤 커밋한다.
