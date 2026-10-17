# 데이터 모델: 로컬 인증과 예약 대상 생성·조회 API

**작업 브랜치**: `feature/auth-resource-api`

**작성일**: 2026-10-17

**대상 명세**: [spec.md](spec.md)

## 1. 로컬 인증 주체 모델 (`security` 패키지)

### 1.1 고정 로컬 계정 (`ADMIN`, `USER_A`, `USER_B`)

로컬 Basic 인증에 사용하는 3개 계정은 코드 상수로 고정 UUID를 가지며, 비밀번호는 실행 환경 변수에서 주입받아 BCrypt 해시로만 메모리에 보관한다.

| 계정 이름 (`username`) | 역할 (`Role`) | 권한 (`GrantedAuthority`) | 고정 `userId` (`UUID`) | 비밀번호 환경 변수 |
| --- | --- | --- | --- | --- |
| `ADMIN` | 관리자 | `ROLE_ADMIN` | 고정 UUID 상수 (예: `00000000-0000-0000-0000-000000000001`) | `ADMIN_PASSWORD` |
| `USER_A` | 일반 사용자 | `ROLE_USER` | 고정 UUID 상수 (예: `00000000-0000-0000-0000-000000000002`) | `USER_A_PASSWORD` |
| `USER_B` | 일반 사용자 | `ROLE_USER` | 고정 UUID 상수 (예: `00000000-0000-0000-0000-000000000003`) | `USER_B_PASSWORD` |

- **불변식**:
  - 세 계정의 UUID는 서로 중복되지 않으며 프로세스 재시작과 무관하게 항상 동일하다.
  - 비밀번호 원문은 코드·문서·로그·오류 응답에 절대 남기지 않으며, 테스트에서는 실행 환경 비밀번호와 분리된 테스트 전용 가짜 비밀번호를 사용한다.
  - `LocalIdentityResolver.userId(Authentication)`는 인증된 `Authentication`의 `Principal`에서만 `UUID`를 반환하며, 인증 정보가 없거나 알 수 없는 주체이면 인증 오류로 거부한다.

---

## 2. 예약 대상 요청·응답·영속 모델 (`resource` 패키지)

### 2.1 생성 요청 모델 (`ResourceController.CreateResourceRequest`)

`POST /api/resources`의 JSON 요청 본문을 바인딩하는 `ResourceController` 내부 중첩 불변 레코드다 (4단계 지정 파일 목록 외 별도 최상위 파일을 추가하지 않는다).

| 필드 | 타입 | 필수 여부 | 제약 및 검증 규칙 |
| --- | --- | --- | --- |
| `name` | `String` | 필수 | `null` 불가. 앞뒤 일반 공백(U+0020)·탭(`\t`)·줄바꿈(`\n`, `\r`) 제거 후 유니코드 코드 포인트 기준 `1~100자`. 내부 공백과 비줄바꿈 공백(U+00A0·U+2007·U+202F)은 보존 (`ResourceRules.normalizeName`). |
| `capacity` | `Integer` | 필수 | `null` 불가. 정수만 허용하며 부동소수점(`1.5`, `1.0`)·문자열(`"10"`) 등 암묵적 형 변환 거부. 범위 `0 <= capacity <= 1000` (`ResourceRules.validateCapacity`). |

- **엄격 JSON 역직렬화 제약**:
  - `name`, `capacity` 외 정의하지 않은 필드(`userId`, `available`, `quantity` 등)가 포함되면 `400`(`INVALID_REQUEST`)으로 거부한다.
  - 동일 JSON 객체 내 중복 키(예: `{"name":"a","name":"b","capacity":1}`)가 포함되면 `400`(`INVALID_REQUEST`)으로 거부한다.

### 2.2 조회·응답 뷰 모델 (`ResourceView`)

2단계에서 정의된 `com.limitedbooking.backend.resource.ResourceView` 레코드를 그대로 API 응답 및 서비스 반환 타입으로 사용한다.

| 필드 | Java 타입 | JSON 타입 | 설명 및 불변식 |
| --- | --- | --- | --- |
| `id` | `UUID` | `string (uuid)` | 예약 대상 고유 식별자 |
| `name` | `String` | `string` | 정규화된 이름 (`1~100` 코드 포인트) |
| `capacity` | `int` | `integer` | 최초 수량 (`0 <= capacity <= 1000`) |
| `available` | `int` | `integer` | 현재 남은 수량 (`0 <= available <= capacity`, 생성 직후 `available == capacity`) |

### 2.3 데이터베이스 매핑 (`resources` 테이블, Flyway `V1__init.sql`)

| 컬럼 | DB 타입 | Java 매핑 | 비고 |
| --- | --- | --- | --- |
| `id` | `UUID PRIMARY KEY` | `UUID` | `UUID.randomUUID()`로 생성 |
| `name` | `VARCHAR(100) NOT NULL` | `String` | `ResourceRules.normalizeName(name)` 결과 저장 |
| `capacity` | `INTEGER NOT NULL` | `int` | `CHECK (capacity BETWEEN 0 AND 1000)` |
| `available` | `INTEGER NOT NULL` | `int` | 생성 시 `capacity`와 동일한 값 저장, `CHECK (available BETWEEN 0 AND capacity)` |
| `created_at` | `TIMESTAMPTZ NOT NULL` | `Instant` | 주입된 UTC `Clock` 기준 `Instant.now(clock).truncatedTo(ChronoUnit.MICROS)` |

---

## 3. 공통 오류 응답 모델 (`common` 패키지)

모든 정의된 오류 응답(필터 단계 401/403/413 및 컨트롤러 단계 400/404/415/503)은 `application/problem+json` 미디어 형식과 [OpenAPI 계약](../../docs/openapi.yaml)의 `Problem` 스키마를 따른다.

### 3.1 `Problem` JSON 구조

| 필드 | JSON 타입 | 필수 여부 | 설명 |
| --- | --- | --- | --- |
| `type` | `string` | 필수 | 문제 유형 URI (기본값 `"about:blank"`) |
| `title` | `string` | 필수 | HTTP 상태에 대응하는 요약 제목 |
| `status` | `integer` | 필수 | HTTP 상태 코드 (`400`, `401`, `403`, `404`, `413`, `415`, `503` 등) |
| `detail` | `string` | 필수 | 내부 구현(SQL·스택 트레이스·비밀번호)을 노출하지 않는 안전한 설명 |
| `instance` | `string` | 필수 | 오류가 발생한 요청 URI 경로 (`request.getRequestURI()`) |
| `code` | `string` | 필수 | 고정 오류 코드 열거값 |

### 3.2 4단계 활성 오류 코드 매핑

| HTTP 상태 | `code` 값 | 발생 조건 | 추가 헤더 |
| --- | --- | --- | --- |
| `400` | `INVALID_REQUEST` | 잘못된 JSON 구문, 알 수 없는 필드(`userId` 등), 중복 JSON 키, 숫자의 암묵적 형 변환(부동소수점·문자열), 필수 필드 누락, `ResourceRules` 이름·수량 검증 실패, 잘못된 UUID 경로 변수, 취소 요청(`/api/reservations/{id}/cancel`)의 비어 있지 않은 본문 | 없음 |
| `401` | `UNAUTHENTICATED` | `Authorization` 헤더 누락, Basic 형식 오류, 알 수 없는 계정 또는 잘못된 비밀번호 | `WWW-Authenticate: Basic realm="reservation-local"` |
| `403` | `FORBIDDEN` | 인증된 일반 사용자(`USER_A`, `USER_B`)가 관리자 전용 경로(`POST /api/resources`) 요청 | 없음 |
| `404` | `NOT_FOUND` | 존재하는 API 경로에서 요청한 예약 대상 UUID가 저장소에 없음 | 없음 |
| `413` | `PAYLOAD_TOO_LARGE` | 요청 본문의 `Content-Length` 또는 실제 읽은 바이트 수가 `8,192바이트(8KiB)`를 초과 | 없음 |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | JSON 본문을 받는 POST 요청의 `Content-Type`이 `application/json`(파라미터 포함 가능)이 아님 | 없음 |
| `503` | `DEPENDENCY_UNAVAILABLE` | PostgreSQL 연결 실패·쿼리 타임아웃 등 `DataAccessException` 발생 시 | 없음 |

- **500 분리 원칙**: 분류되지 않은 내부 런타임 결함(`NullPointerException`, 잘못된 SQL 등)은 임의의 4xx/503 코드로 숨기지 않고 `500`으로 드러나게 한다.
