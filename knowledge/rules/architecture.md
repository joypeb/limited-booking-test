# 구조와 범위 규칙

근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 1~3절, [AGENTS.md](../../AGENTS.md)의 현재 저장소와 계획의 차이. 아래는 개발 규칙이며 구현 완료 기록이 아니다.

## 목적과 범위

- localhost의 CLI/API 클라이언트에서 실행하는 단일 백엔드를 만든다. Java/Spring 이해와 예약·취소·재시도의 정합성·실행 증거를 우선한다.
- 관리자는 예약 대상과 최초 수량을 생성하고 단건 조회한다. 인증 사용자는 수량 1개를 예약하고 자신의 예약을 단건 조회·취소한다.
- Redis는 인증된 사용자의 예약 생성·취소 요청을 제한한다.
- 결제·만료·대기열·알림·Kafka·Kubernetes·마이크로서비스·대상 수량 변경/삭제·회원가입·비밀번호 재설정·목록/페이지네이션은 첫 버전 범위 밖이다.
- 2026-10-26 사용자 지시로 localhost 전용 브라우저 콘솔 `frontend/`(Next.js 16.4.3·Tailwind 4.3.3)를 추가했다. 콘솔은 백엔드의 얇은 클라이언트이며 앱 코드는 `frontend/src/`, 테스트는 `frontend/tests/`·`frontend/e2e/`다. 상세는 [콘솔 도메인 문서](../domain/frontend-console.md).
- 외부 공개·클라우드 배포·그 밖의 브라우저 서비스는 별도 설계와 사용자 지시가 필요하다.

## 기술과 책임

- Java 25를 사용하고 preview 기능을 제외한다. Spring MVC/Security/JDBC, `NamedParameterJdbcTemplate`, PostgreSQL, Redis, Flyway를 사용한다.
- PostgreSQL이 수량·예약·멱등성의 유일한 원본이다. Redis에 잔여 수량을 저장하거나 분산 락을 두지 않는다.
- 기존 기본 패키지 `com.limitedbooking.backend` 아래에 `common`, `security`, `resource`, `reservation`, `ratelimit` 등 기능별 패키지를 둔다. 테스트도 대응하는 구조로 둔다.
- Controller는 HTTP 변환, Service는 요청 조정, 별도 Bean인 Writer는 트랜잭션, Repository는 SQL을 담당한다. DTO는 해당 기능 패키지의 record로 둔다.
- 이름·수량·키·취소 판단 같은 순수 규칙은 Spring 없이 검증하고 실제 요청 처리에서도 사용한다.
- 거대한 공통 서비스·범용 Repository·불필요한 추상화를 먼저 만들지 않는다.
- 스키마 변경은 Flyway 마이그레이션으로 관리한다. JPA/ORM 자동 스키마 생성과 H2로 PostgreSQL 검증을 대체하지 않는다.

## 실제 저장소를 기준으로 작업

- 작업 루트는 `limited-booking/`, 애플리케이션은 `backend/`, 진입점은 `BackendApplication`이다.
- 2026-10-13 파일 확인 기준으로 Java 25·Boot 4.1.1·Gradle Wrapper 9.7.1, 1단계에서 MVC·Security·JDBC·Redis·Flyway·Actuator와 검증 기반을 추가하고 최소 실행을 확인했다. 업무 기능이 구현됐다고 가정하지 않는다.
- 현재 Gradle·패키지·디렉터리를 유지한다. 계획서 6절의 테스트 분류·실행 계약은 명시적인 Gradle 태스크로 구현·기록했다. 빌드 도구 변경은 이유·영향을 제시하고 사용자 지시에 따른다.
- 새 도메인이나 기존 도메인의 첫 개발 전에 [공통 템플릿](../domain/_template.md)으로 지식 문서를 작성한다. 설계 근거와 실제 구현·검증 상태를 구분한다.
