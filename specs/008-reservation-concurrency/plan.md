# Implementation Plan: 동시 요청의 예약 정합성 검증

**Branch**: `docs/reservation-concurrency-spec` | **Date**: 2026-10-20 | **Spec**: [spec.md](spec.md)

**Input**: `specs/008-reservation-concurrency/spec.md` 및 2026-10-20 사용자 확인1건.

## Summary

작성 worktree: `/Users/joypeb/Desktop/projects/limited-booking-concurrency-plan`. 원래 폴더의 외부 develop 전환을 보존하기 위해 기존 7단계 브랜치를 별도 worktree에서 사용한다. 자동 병합·push는 하지 않는다.

기존 예약 생성·조회·취소 구현을 실제 HTTP와 PostgreSQL로 경쟁시켜 수량 보존·동일 키 재현·충돌 롤백·중복 취소를 검증한다. 일반 경쟁6종, 키 강제 경로2종, 생성·취소 순서 제어2종, 경쟁 중 저장 실패1종을 각각10회 반복한다. 모든 작업 종료와 단일 집계 관측을 인수 조건으로 삼는다. 업무 API·스키마·제품 후크를 추가하지 않는다. 이 문서는 설계 완료이며 구현·실행 완료가 아니다.

## Technical Context

**Language/Version**: Java25, preview 제외. 원래 작업 폴더 Wrapper 파일9.8.0(사용자 변경), 이 별도 worktree의 추적 Wrapper 및 최근 실행 기록9.7.1. 사용자 변경을 옮기거나 되돌리지 않았다. 구현할 폴더의 실제 버전·호환성은 실행 시 기록.

**Primary Dependencies**: 기존 Spring Boot4.1.1 MVC/Security/JDBC, NamedParameterJdbcTemplate, Hikari, Flyway, JUnit Jupiter·AssertJ·MockitoSpyBean·Testcontainers. 새 의존성 없음. 기존 BOM 기록 Jupiter6.0.3/Testcontainers2.0.5는 이번 재해석 결과가 아니다.

**Storage**: PostgreSQL18.6 고정 digest는 ContainerTestSupport 상수 재사용. 동적 DB/port만 사용. Redis는8단계 후속이며 이번에는 postgresOnly.

**Testing**: `ReservationConcurrencyIT` + 필요 지원 Unit/IT, 실제 Basic HTTP·실제 SQL·경로 Spy. 기존 Unit/일반IT 회귀는 verify. Contract/Property/PIT/k6는 이번 범위 밖.

**Target Platform**: localhost CLI/API, 기존 macOS·Docker·JDK 환경. 외부 공개 없음.

**Project Type**: 단일 백엔드의 경쟁 정합성 검증 확장.

**Performance Goals**: 처리량/SLA 목표 없음. 완료 제한은 시험 정지 방지용이다. 100건 회차120초·나머지30초, HTTP9/10초, 장벽5초·Executor 종료10초.

**Constraints**: READ COMMITTED·별도 Writer·resource→reservation 잠금 순서·DB lock2초/statement5초·풀10/3000ms 유지. 클래스 병렬 실행 금지. 테스트 후크는 src/test에만 둔다.

**Scale/Scope**: 11개 시나리오 각10회, 신규 반복 인수110회 예정. 준비·재시도 HTTP 수는 회차 결과에 별도 기록. 고정 사용자·다른 키 조합으로100건을 구성한다.

## Constitution Check

| 원칙/게이트 | 설계 전 | 설계 후 근거 |
| --- | --- | --- |
| I 서비스 경계·계약 보존 | 통과 | Controller/Service/Writer·SQL 호출 유지, 기존 OpenAPI 그대로 |
| II 도메인 근거 | 통과 | 계획서2~5절/7단계·모든 규칙·예약/대상/보안과 실제 코드 대조 |
| III 검증·증거 | 통과 | 경로 관측·반복·실제 HTTP/DB·0건/skip 거부·TDD와 고의 결함 원복 |
| IV 문서 동기화 | 통과 | 본 계획·조사·모델·계약·검증 안내와 knowledge 연결. 실행 기록은 구현 후 docs에 작성 |
| V 미확정 명시 | 통과 | 두 순서 각10회는 사용자 확정. 환경 버전 차이는 미검증으로 명시 |

정당화가 필요한 위반 없음. 헌법의 과거 환경 스냅샷 및 채택일/CI TODO는 기존 기록이며 이번 기능의 새로운 정책 결정이 아니다.

## Project Structure

### Documentation (this feature)

```text
specs/008-reservation-concurrency/
  spec.md
  plan.md
  research.md
  data-model.md
  contracts/concurrency-contract.md
  quickstart.md
  checklists/requirements.md
  tasks.md
```

후속 speckit-tasks에서 [tasks.md](tasks.md)를 생성했다. 작업 생성과 실제 구현·실행 완료는 구분한다.

### Source Code (repository root)

```text
backend/src/main/java/com/limitedbooking/backend/
  reservation/{ReservationController,ReservationService,ReservationWriter,ReservationRepository}.java  (기존)
  resource/ResourceRepository.java  (기존)
backend/src/test/java/com/limitedbooking/backend/
  reservation/ReservationConcurrencyIT.java  (신규 예정)
  reservation/ReservationApiTestSupport.java  (기존 재사용)
  support/ConcurrencyTestSupport.java  (신규 예정)
  support/ConcurrencyTestSupportTest.java  (신규 예정)
  support/DatabaseAssertions.java  (기존 확장 예정)
  support/DatabaseSupportIT.java  (기존 확장 예정)
  support/{TestData,ContainerTestSupport,HttpClientTestSupport}.java  (기존 재사용)
```

**Structure Decision**: 제품 계층은 그대로 두고 새 시험과 동기화·결과 수집 지원은 src/test에 둔다. DatabaseAssertions는 기존 호출을 유지하면서 여러 대상의 수량·확정/전체 예약 수를 단일 SELECT로 반환하는 snapshot API를 추가한다. 신규 위치는 모두 예정이며 실제 파일로 링크하지 않는다.

## Phase 0: 조사 결과

[research.md](research.md)에 로컬 근거·결정·대안을 기록했다. 실제 선조회가 끝난 뒤 트랜잭션 밖에서 기다리는 설계로 연결·행 잠금 보유 장벽을 피한다. 새 기술 도입·외부 문서 기반 버전 선택은 없다.

## Phase 1: 설계와 구현 순서

1. 안전한 실행 지원부터 검증한다. 작업자 준비 수는 min(요청 수,10), 시작 장벽은 첫 작업자들이 준비되면 해제한다. 요청100건 전체의 준비를 기다리지 않는다. 회차 공통 deadline으로 모든 Future를 수집하고 예외·취소·timeout을 누락 없이 판정한다. finally에서 모든 장벽 해제→작업 취소/종료→HTTP 종료를 보장한다. 종료되지 않은 요청이 있는 상태에서 DB reset이나 다음 회차로 진행하지 않는다.
2. DatabaseAssertions의 다중 대상 snapshot과 불일치 탐지를 실제 격리 DB에서 확인한다. LEFT JOIN 집계로 예약0건 대상도 포함하고 확정 수와 전체 수를 분리한다. 두 대상 충돌은 한 SQL에 두 대상을 넣어 관측한다. 사용자·키 중복 검사와 예약 상태/시각 조회는 별도 검사하되 수량 판정의 snapshot을 대체하지 않는다.
3. [계약 표](contracts/concurrency-contract.md)의 C01부터 한 사례씩 테스트→의도한 실패 탐지→최소 수정→재검증한다. 기존 코드가 이미 맞는 경우 구현을 일부러 바꾸지 않고 실제 조건부 차감/취소 조건/재현 비교 중 해당 방어를 하나만 고의로 훼손해 탐지 후 반드시 원복한다. 컴파일 실패·단순 관측 후크 미작성은 업무 결함의 RED 증거가 아니다. 고의 결함과 실제 발견 결함을 구별한다.
4. 강제 경로 Spy는 지정 키·초기 호출만 대상으로 하고 실제 메서드를 호출한다. UPDATE0·트랜잭션 안 재조회 또는23505/uq_reservations_user_key·롤백 후 트랜잭션 밖 재조회를 기록한다. 호출 횟수만으로 성공을 주장하지 않는다. 관측 값과 최종 상태를 함께 검사한다.
5. 생성·취소 순서는 C09/C10의 트랜잭션 밖 대기로 제어한다. 단순 순차 호출을 동시 경쟁으로 기록하지 않는다. 두 요청 진입·대기 도달·선행 완료·해제·후행 판단의 사건 순번을 남긴다. 매 회차 완료된 두 요청과 정확한 최종 상태를 확인한다.
6. C11 실패 주입은 특정 INSERT 진입에서만1회 예외를 던지고 실제 차감 관측·정상 경쟁 요청·롤백·실패키 재시도를 검증한다. 실패 후 Spy/플래그 초기화는 모든 요청 종료 뒤 수행한다.
7. 각 사례를 `@RepeatedTest(10)`로 독립 발견되게 하고 BeforeEach 준비/AfterEach 종료를 사용한다. 신규 반복 인수110회를 XML에서 확인하며 지원 테스트 수는 따로 집계한다. 장벽을 꺼 둔 일반 사례와 경로 강제 사례의 결과를 섞지 않는다.
8. 지정 경쟁 시험·관련 기존 시험 및 일반 verify를 실행한다. 실행 명령·결과 수·시간·환경·풀 적용값·최대 관측 연결 수·각 경로 관측·최종 수량을 docs/test-evidence.md에, 실제 버전은 docs/versions.md에 기록한다. 선택 변경은 docs/decisions.md, 사용 안내는 README·knowledge에 반영한다. 성공한 회차로 실패를 덮지 않는다.

## Resource and Failure Design

작업자10·풀10을 실제 설정과 getMaximumPoolSize/getConnectionTimeout으로 확인한다. 소규모 요청은2개(취소는10개)이며 Hikari active 연결 수를 테스트 전용 sampler로10ms 간격 관측해 최대값과 샘플 간격을 기록한다. 이는 표본 최대값이므로 정확한 피크로 주장하지 않는다. 쿼리/관측을 위해 장벽에서 연결을 추가 획득하지 않는다. HTTP 클라이언트 내부 스레드·서버 스레드 수와 요청 Executor 작업자 수를 구분한다.

장벽 실패·Future 예외·상한 초과 시 회차는 실패다. releaseAll과 shutdownNow 후 awaitTermination10초를 검사하고 서버 측 Spy 진입/퇴장 추적으로 in-flight0도10초 이내 확인한다. 응답 취소만으로 서버 요청 종료를 가정하지 않는다. 정리 실패는 원래 예외에 suppressed로 남기며 원래 실패를 숨기지 않는다. 전체 종료 확인 실패 시 suite를 중단하고 DB reset을 금지한다. 실행 환경이 느리면 먼저 원인을 조사하며 시간/버전/풀을 임의로 바꿔 통과시키지 않는다.

## Complexity Tracking

헌법 위반 없음. 범용 동시성 프레임워크·제품용 관측 추상화·스키마 추가는 필요하지 않다. 테스트 지원은 시작·수집·종료·사건 기록에 한정한다.
