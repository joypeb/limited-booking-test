# 0단계 설계 결정

근거: [원 계획](<../한정 수량 예약 백엔드 로컬 개발 계획.md>) 3·5절, [조사](../specs/001-define-api-contract/research.md). 아래 각 결정은 4줄이며 설계 선택의 기록이다. 실제 도구 채택·실행 호환성 또는 API 구현을 증명하지 않는다.

## JDBC로 SQL과 트랜잭션을 직접 이해한다

- 선택: 기본 SQL 접근은 Spring JDBC의 NamedParameterJdbcTemplate이다. 기존 Java 25·Gradle·com.limitedbooking.backend를 유지한다.
- 이유: available 조건부 UPDATE·제약·잠금·롤백과 별도 Writer 트랜잭션 경계를 직접 읽고 검증하기 위한 학습 목적이다.
- 대안: JPA/ORM의 추상화, JDBC의 JdbcClient가 있지만 기존 선택을 재선정하지 않고 명시적 SQL을 우선한다.
- 영향·한계: SQL·매핑을 직접 관리하며 PostgreSQL 통합 시험이 필요하다. JDBC 의존성·Repository·SQL은 아직 미구현/미실행이다.

## 성공한 예약 행에 생성 키를 보존한다

- 선택: 사용자 UUID·생성 키의 유일 기록은 성공한 예약 행이며 취소 뒤에도 보존한다. 실패 요청은 키를 점유하지 않는다.
- 이유: 불변 id·resourceId·createdAt와 고정 CONFIRMED로 최초 201·Location·JSON 필드 값을 재구성하고 중복 차감을 방지한다.
- 대안: 별도 키 테이블·TTL 캐시는 추가 저장 동기화·실패 응답 보관·만료 정책을 요구하므로 첫 범위에 추가하지 않는다.
- 영향·한계: 실패 원인 해소 후 같은 미사용 키로 성공할 수 있지만 성공 키를 다른 대상/새 예약에 재사용할 수 없다. 저장·응답 재현은 아직 미구현/미실행이며 네트워크의 한 번 전달을 보장하지 않는다.

## Redis는 예약 쓰기의 요청 진입을 제한한다

- 선택: Redis는 인증된 예약 생성·취소 합산의 최초 요청부터 20회/60초 고정 창만 담당하며 PostgreSQL이 수량 원본이다.
- 이유: 인증 뒤 업무 트랜잭션 전에 초과·장애 요청을 거부하고 재요청·입력 오류도 카운트한다. TTL은 연장하지 않는다.
- 대안: 메모리/DB 카운터는 다른 장애·경쟁 특성이 있으며 Redis 수량 저장·분산 락은 수량 원본과 책임을 바꾸므로 채택하지 않는다.
- 영향·한계: Redis 장애는 해당 쓰기에 503·DB 무변경이고 조회·대상 생성/조회는 Redis에 비의존이다. DB 실패로 카운터를 되돌리지 않으며 Redis·Lua·필터는 아직 미구현/미실행이다.

## 1단계 실행 준비

현재 Gradle9.7.1/Boot4.1.1/Java25 골격을 유지한다. 문서 루트는 기존 프로젝트 루트 docs/다. 새 업무 도메인을 추가하지 않고, 프로브와 시험 migration은 도구 확인에만 사용한다. 후보의 설치·취득을 연동 호환성 확인으로 표현하지 않는다.

Compose와 Testcontainers는 `postgres:18.6-bookworm`, `redis:8.10.2-trixie`의 위 취득 digest를 함께 사용한다. arm64 실제 컨테이너 내부 버전 출력에 성공했다. 태그가 바뀌더라도 같은 식별값을 실행하도록 digest까지 고정한다.

QuickTheories0.26의 Core bytecode에서 examples 루프의 생성과 visitedValues 중복 제거를 확인했다. withExamples(100)은 실제 assertion61회여서 최소100회 조건에 실패했다. 기대값을 낮추지 않고 생성 예산을1000으로 늘리고 실제 assertion 횟수≥100을 계속 검사한다. seed와 실제 횟수를 기록한다.

심화 후보는 첫 조합 그대로 최소 사례를 통과했다: QuickTheories0.26, validator core2.46.1, PIT Gradle1.19.0/engine1.22.1/Jupiter1.2.3. Java/JUnit 임의 하향 없이 실행했으며 core validator는 JDK HTTP fixture와 독립 어댑터를 사용한다. 업무 OpenAPI 읽기와 실제 업무 응답 준수를 구분한다.

## 최종 실행 판단과 남은 범위 (2026-10-14)

- 사용자 지정 작업 공간과 T001 계약에 따라 현재 checkout의 chore 브랜치에서 순차 진행했다. 별도 worktree로 옮기지 않았다. 비용: 다른 작업과 병행 편집하면 충돌할 수 있어 이번 작업은 순차 실행했다.
- 스킬 task-start는 Task N 제목만 지원하므로 Spec Kit의 T001~T042 행을 직접 읽고 ledger와 실행 근거로 추적했다. 비용: 자동 brief 대신 수동 추적이 필요하며 체크42개/실제 근거 대조로 누락을 관리했다.
- 업무 API·UUID 인증·수량/SQL 경쟁·Redis 요청 제한·부하·외부 CI는 명시적 후속 범위다. 비용: 해당 단계 검증 전 업무 기능으로 사용할 수 없다.
- 종료10초 초과 분기 자체와 개발 commit 응답 유실 주입은 미실시로 유지한다. 실제 미종료 HTTP 응답의 bounded cleanup은 새 회귀로 확인했다. 비용: 두 장애 분기의 추가 검증이 필요하다.
- T042의 병합/push 금지 범위에 따라 chore 브랜치와 로컬 커밋을 유지한다. 비용: develop 통합은 별도 작업이다.

독립 리뷰의 종료 상한 누락은 commit be96d7c에서 실제 HTTP 회귀 실패→수정 통과 및 deepTest14건 실패skip0으로 보완했다. 버전 문서의 과거/현재 상태 혼재도 최종 기록 정리에서 해결했다. 미해결 리뷰 Minor는 없다.
