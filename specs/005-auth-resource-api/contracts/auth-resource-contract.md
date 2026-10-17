# 인터페이스 및 API 계약: 로컬 인증과 예약 대상 생성·조회 API

**작업 브랜치**: `feature/auth-resource-api`

**작성일**: 2026-10-17

**기준 문서**: [로컬 개발 계획](<../../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절 및 7절 4단계, [OpenAPI 계약](../../../docs/openapi.yaml), [API·보안 규칙](../../../knowledge/rules/api-and-security.md)

---

## 1. 제공 Java 인터페이스 계약

### 1.1 `com.limitedbooking.backend.security.LocalIdentityResolver`

```java
package com.limitedbooking.backend.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;

public class LocalIdentityResolver {
    public UUID userId(Authentication authentication);
}
```

- **입력**: Spring Security가 인증 완료한 `Authentication` 객체
- **출력**: 인증 주체(`Principal`)에 고정 할당된 사용자 `UUID` (`ADMIN`, `USER_A`, `USER_B` 중 하나)
- **계약**:
  - `authentication`이 `null`이거나 미인증 상태이거나 `LocalUserPrincipal`이 아니면 인증 예외(`InsufficientAuthenticationException` 등)를 던진다.
  - 요청 헤더(`X-User-Id`)나 요청 본문(`userId`)을 참조하는 메서드를 제공하지 않는다.

### 1.2 `com.limitedbooking.backend.resource.ResourceService`

```java
package com.limitedbooking.backend.resource;

import java.util.UUID;

public class ResourceService {
    public ResourceView create(String name, int capacity);
    public ResourceView get(UUID id);
}
```

- **`create(String name, int capacity): ResourceView`**
  - `ResourceRules.normalizeName(name)`으로 이름을 정리·검증하고, `ResourceRules.validateCapacity(capacity)`로 수량(`0~1000`)을 검증한다. 규칙 위반 시 `IllegalArgumentException`을 던져 `400`(`INVALID_REQUEST`)으로 변환되게 한다.
  - `UUID.randomUUID()`와 주입된 UTC `Clock` 기준 시각(`Instant.now(clock).truncatedTo(ChronoUnit.MICROS)`)으로 `ResourceRepository`에 저장하고(`available = capacity`), 생성된 `ResourceView`를 반환한다.
- **`get(UUID id): ResourceView`**
  - `ResourceRepository.find(id)`를 조회하여 존재하면 `ResourceView`를 반환하고, 존재하지 않으면 404 전용 예외(`ResourceNotFoundException`)를 던져 `404`(`NOT_FOUND`)로 변환되게 한다.

### 1.3 `com.limitedbooking.backend.resource.ResourceRepository` 확장

```java
package com.limitedbooking.backend.resource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class ResourceRepository {
    public Optional<ResourceView> find(UUID id);
    public ResourceView insert(UUID id, String name, int capacity, Instant createdAt);
}
```

- 2단계에서 구현된 `find(UUID id)`를 유지하고, `insert(UUID id, String name, int capacity, Instant createdAt)`를 추가한다 (`available`은 `capacity`와 동일한 값으로 `INSERT`).

---

## 2. 필터 체인 및 요청 처리 우선순위 계약

모든 API 요청은 다음 순서로 검증·처리되어야 하며, 앞 단계가 실패하면 뒷 단계로 진행하지 않는다:

1. **`RequestBodyLimitFilter` (인증 앞단)**:
   - `Content-Length > 8192`이거나 실제 스트림 읽기 바이트 수가 `8,192바이트(8KiB)`를 초과하면(헤더 생략·작게 조작된 헤더·`Transfer-Encoding: chunked` 포함) 즉시 `413`(`PAYLOAD_TOO_LARGE`, `application/problem+json`) 반환.
   - `8,192바이트` 이하 본문은 메모리에 보존하여 재사용 가능한 `HttpServletRequestWrapper`로 후속 필터·컨트롤러에 전달.
2. **Basic 인증 (`BasicAuthenticationFilter` + 커스텀 `AuthenticationEntryPoint`)**:
   - 자격 증명 누락·형식 오류·잘못된 비밀번호 시 `401`(`UNAUTHENTICATED`, `WWW-Authenticate: Basic realm="reservation-local"`, `application/problem+json`) 반환.
3. **권한 확인 (`AuthorizationFilter` + 커스텀 `AccessDeniedHandler`)**:
   - `POST /api/resources`에 `ROLE_ADMIN`이 없는 사용자(`USER_A`, `USER_B`) 접근 시 `403`(`FORBIDDEN`, `application/problem+json`) 반환.
4. **미디어 타입 및 요청 형식 검증**:
   - `POST /api/resources`의 `Content-Type`이 `application/json`이 아니면 `415`(`UNSUPPORTED_MEDIA_TYPE`, `application/problem+json`) 반환.
   - `POST /api/reservations/{id}/cancel`에 8KiB 이하의 비어 있지 않은 본문(1바이트 이상)이 전달되면 `400`(`INVALID_REQUEST`, `application/problem+json`) 반환.
   - 경로 변수 UUID 형식 오류, 깨진 JSON, 알 수 없는 필드(`userId` 등), 중복 JSON 키, 숫자의 암묵적 형 변환(부동소수점·문자열) 시 `400`(`INVALID_REQUEST`, `application/problem+json`) 반환.
5. **도메인 규칙 및 리소스 존재 검증**:
   - `ResourceRules` 위반(`name` 공백·101자 초과, `capacity < 0` 또는 `> 1000`) 시 `400`(`INVALID_REQUEST`) 반환.
   - `GET /api/resources/{id}` 대상 미존재 시 `404`(`NOT_FOUND`) 반환.

---

## 3. HTTP 엔드포인트 계약

### 3.1 `POST /api/resources` (예약 대상 생성)

- **권한**: `ADMIN` 전용 (`USER_A`, `USER_B`는 `403 FORBIDDEN`)
- **요청 헤더**: `Authorization: Basic ...`, `Content-Type: application/json`
- **요청 본문**:

```json
{
  "name": "한정 수량 좌석",
  "capacity": 100
}
```

- **성공 응답 (`201 Created`)**:
  - 헤더: `Location: /api/resources/{id}`, `Content-Type: application/json`
  - 본문:

```json
{
  "id": "38f05248-0d3b-42f5-a0a9-2f3e8c9d0b11",
  "name": "한정 수량 좌석",
  "capacity": 100,
  "available": 100
}
```

- **오류 응답 (`application/problem+json`)**:
  - `413 PAYLOAD_TOO_LARGE`: 본문 > 8,192바이트 (미인증이어도 413 우선)
  - `401 UNAUTHENTICATED`: 미인증 또는 잘못된 비밀번호 (`WWW-Authenticate` 포함)
  - `403 FORBIDDEN`: `USER_A` 또는 `USER_B`로 요청
  - `415 UNSUPPORTED_MEDIA_TYPE`: `Content-Type`이 `application/json`이 아님
  - `400 INVALID_REQUEST`: 빈 이름, 공백 이름, 코드 포인트 101자 이름, `capacity` `-1` 또는 `1001`, 부동소수점(`1.5`), 문자열(`"10"`), 정의하지 않은 필드(`userId` 등), 중복 JSON 키, 깨진 JSON

### 3.2 `GET /api/resources/{id}` (예약 대상 단건 조회)

- **권한**: 인증된 모든 사용자 (`ADMIN`, `USER_A`, `USER_B`)
- **성공 응답 (`200 OK`)**:
  - 헤더: `Content-Type: application/json`
  - 본문: `ResourceView` (`id`, `name`, `capacity`, `available`)
- **오류 응답 (`application/problem+json`)**:
  - `401 UNAUTHENTICATED`: 미인증 또는 잘못된 비밀번호 (`WWW-Authenticate` 포함)
  - `400 INVALID_REQUEST`: `{id}`가 유효한 UUID 형식이 아님
  - `404 NOT_FOUND`: `{id}`에 해당하는 대상이 저장소에 없음

### 3.3 `POST /api/reservations/{id}/cancel` (4단계 요청 검증 범위)

- **권한**: 인증된 사용자 (`ADMIN`, `USER_A`, `USER_B`)
- **4단계 검증 계약**:
  - 본문이 `8,192바이트`를 초과하면 인증 여부와 무관하게 `413 PAYLOAD_TOO_LARGE` 반환.
  - 미인증 시 `401 UNAUTHENTICATED` 반환.
  - 인증된 요청의 본문이 비어 있지 않으면(1~8,192바이트, 공백 문자나 `{}` 포함) `400 INVALID_REQUEST` 반환.
  - `{id}`가 유효한 UUID 형식이 아니면 `400 INVALID_REQUEST` 반환.
  - *(참고: 빈 본문 + 유효한 UUID일 때의 예약 조회·소유자 확인·상태 전이·수량 복구는 6단계에서 구현한다.)*

---

## 4. 통합 테스트 검증 매트릭스 (`SecurityIT`·`ResourceApiIT`)

### 4.1 `SecurityIT` 필수 검증 항목

| 구분 | 시나리오 | 기대 상태 | 기대 헤더 / 본문 검증 |
| --- | --- | --- | --- |
| 인증 실패 | `Authorization` 헤더 없음 | `401` | `WWW-Authenticate` 포함, `application/problem+json`, `code=UNAUTHENTICATED` |
| 인증 실패 | 잘못된 비밀번호 또는 존재하지 않는 계정 | `401` | `WWW-Authenticate` 포함, `application/problem+json`, `code=UNAUTHENTICATED` |
| 권한 거부 | `USER_A`·`USER_B`가 `POST /api/resources` 요청 | `403` | `application/problem+json`, `code=FORBIDDEN`, DB에 대상 미생성 |
| 권한 허용 | `ADMIN`이 `POST /api/resources` 요청 | `201` | `Location: /api/resources/{id}` 포함 |
| 인증 조회 | `ADMIN`·`USER_A`·`USER_B`가 `GET /api/resources/{id}` 요청 | `200` | 동일한 `ResourceView` 반환 |
| 식별자 신뢰 | `LocalIdentityResolver.userId(auth)` 및 `X-User-Id` 헤더 무시 | `200` / 고정 UUID | `ADMIN`·`USER_A`·`USER_B` 각각의 고정 UUID 반환, 외부 `X-User-Id` 미반영 |
| 무상태 세션 | 인증 요청 응답 헤더 확인 | `200` / `201` | `Set-Cookie` (`JSESSIONID`) 미생성 |
| 본문 상한 우선순위 | 미인증 상태에서 8,193바이트 본문 전송 (길이 헤더 있음/없음/chunked) | `413` | `401`보다 먼저 `413`(`code=PAYLOAD_TOO_LARGE`, `application/problem+json`) 반환 |

### 4.2 `ResourceApiIT` 필수 검증 항목

| 구분 | 시나리오 | 기대 상태 | 기대 결과 |
| --- | --- | --- | --- |
| 수량 경계 허용 | `capacity = 0`, `capacity = 1000` 생성 후 조회 | `201` -> `200` | `available == capacity`, `Location` 헤더 일치 |
| 이름 정규화 허용 | 앞뒤 공백·탭·줄바꿈 포함 이름, 내부 공백·NBSP·`😀` 100개 이름 생성 | `201` -> `200` | `ResourceRules.normalizeName` 결과와 동일하게 저장·조회 |
| 본문 상한 허용 | 정확히 `8,192바이트`인 유효한 JSON 요청 | `201` 또는 규칙 결과 | 본문 손실 없이 하위 계층 전달 (이름 길이에 따라 201 또는 400) |
| 수량 경계 거부 | `capacity = -1`, `capacity = 1001` | `400` | `code=INVALID_REQUEST`, DB 미저장 |
| 암묵적 형 변환 거부 | `capacity = 1.5`, `capacity = 1.0`, `capacity = "10"` | `400` | `code=INVALID_REQUEST`, DB 미저장 |
| 이름 검증 거부 | `""`, `"   \t\n"`, 정리 후 101자, `😀` 101개 | `400` | `code=INVALID_REQUEST`, DB 미저장 |
| 엄격 JSON 거부 | 미정의 `userId` 필드 포함, 중복 JSON 키(`{"name":"a","name":"b","capacity":1}`), 깨진 JSON | `400` | `code=INVALID_REQUEST`, DB 미저장 |
| 경로/미존재 검증 | 잘못된 UUID 경로 조회(`400`), 존재하지 않는 UUID 조회(`404`) | `400` / `404` | `code=INVALID_REQUEST` / `code=NOT_FOUND` |
| 미디어/취소 본문 | `text/plain` 생성 요청(`415`), `/api/reservations/{id}/cancel`에 비어 있지 않은 본문 요청(`400`) | `415` / `400` | `code=UNSUPPORTED_MEDIA_TYPE` / `code=INVALID_REQUEST` |
