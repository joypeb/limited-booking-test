# 빠른 검증 가이드: 로컬 인증과 예약 대상 생성·조회 API

**작업 브랜치**: `feature/auth-resource-api`

**작성일**: 2026-10-17

**대상 명세**: [spec.md](spec.md)

## 1. 사전 준비

- Java 25 툴체인과 Docker 데몬(Testcontainers PostgreSQL·Redis 기동용)이 실행 중이어야 한다.
- 모든 Gradle 명령은 `backend/` 디렉터리에서 실행한다.

```bash
cd /Users/joypeb/Desktop/projects/limited-booking/backend
```

## 2. TDD 1단계 — 실패하는 통합 테스트 먼저 작성·확인 (RED)

1. `SecurityIT`와 `ResourceApiIT`에 4단계 인증·권한·본문 상한·예약 대상 생성/조회·엄격 입력 검증 테스트를 먼저 작성한다.
2. 아직 `SecurityConfig`·`RequestBodyLimitFilter`·`ResourceController`·`ResourceService`·`ApiExceptionHandler`가 구현되지 않은 상태(또는 스텁 상태)에서 다음 명령을 실행해 **기능 미구현으로 실패(RED)** 하는지 먼저 확인하고 기록한다.

```bash
./gradlew integrationTest --tests '*SecurityIT' --tests '*ResourceApiIT'
```

- **확인 사항**:
  - 미구현 상태에서 `404` 또는 기대 상태 코드(`201`, `413`, `403`, `problem+json` 필드) 불일치로 의도된 테스트 실패가 발생하는지 확인한다.
  - 실행 명령·종료 코드·실행 수·실패 수를 기록한다.

## 3. TDD 2단계 — 기능 구현 후 대상 통합 테스트 통과 확인 (GREEN)

`SecurityConfig`·`LocalIdentityResolver`·`ClockConfig`·`RequestBodyLimitFilter`·`ApiExceptionHandler`·`ResourceRepository`·`ResourceService`·`ResourceController`를 구현한 뒤 다시 실행한다.

```bash
./gradlew integrationTest --tests '*SecurityIT' --tests '*ResourceApiIT'
```

- **확인 사항**:
  - `SecurityIT`와 `ResourceApiIT`의 모든 테스트가 실패 0건·건너뜀 0건으로 통과하는지 확인한다.
  - 401/403/413/415/400/404 모든 오류 응답이 `application/problem+json` 형식과 필수 6개 속성(`type`, `title`, `status`, `detail`, `instance`, `code`)을 포함하는지 확인한다.

## 4. TDD 3단계 — 전체 검증 게이트 실행 (`./gradlew verify`)

기존 1~3단계 단위·통합 테스트와 4단계 통합 테스트가 모두 함께 통과하는지 확인한다.

```bash
./gradlew verify
```

- **성공 기준**:
  - 종료 코드 `0`
  - `test` 및 `integrationTest` 전체 `failed = 0`, `skipped = 0`
  - 문서 형식 검사(`git diff --check`) 통과
