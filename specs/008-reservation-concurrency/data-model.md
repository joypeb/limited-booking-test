# 데이터·검증 모델

## 기존 업무 엔터티

스키마 변경·새 migration은 없다. [V1](../../backend/src/main/resources/db/migration/V1__create_reservation_schema.sql)과 [명세](spec.md)의 상태 규칙을 유지한다.

| 엔터티 | 속성·관계 | 검증 규칙 |
| --- | --- | --- |
| resources | id UUID, name, capacity, available, created_at | 0≤available≤capacity≤1000; available+CONFIRMED 수=capacity |
| reservations | id, resource_id FK, user_id, idempotency_key, status, created_at, cancelled_at | user_id/key 유일, CONFIRMED→CANCELLED만 가능, 행 유지 |
| 생성 결과 | id/resourceId/status=CONFIRMED/createdAt, Location, Replay | 최초 false1건·재현 true, 동일 UUID·저장 UTC 마이크로초 시각·JSON 필드 값 |
| 현재 예약 | id/resourceId/status/createdAt/cancelledAt | CONFIRMED의 cancelledAt null, CANCELLED의 시각 필수·중복 취소 불변 |

성공 키는 예약 행에 보존하고 실패 키는 보존하지 않는다. 새 키로 같은 사용자의 추가 예약은 허용한다. 타인/관리자의 예약 접근404와 인증 UUID 출처는 기존 계약 그대로다.

## 테스트 메모리 모델 (신규 예정)

업무 DB 테이블로 만들지 않는다. 회차별 인스턴스로 생성하며 스레드 안전한 수집 구조를 쓴다.

| 모델 | 필드 | 관계·제약 |
| --- | --- | --- |
| ScenarioRun | scenarioId, repetition, capacity/초기 상태, deadline, workers, pool 설정 | 회차별 독립 대상·키·관측기 |
| RequestOutcome | requestId, 사용자 별칭, key, resourceId, 동작, status/code, JSON 필드, Location/Replay, 예외, 시작/종료 순번 | 제출 수와 결과 수 동일, 비밀번호·Authorization 원문 저장 금지 |
| PathEvent | 단조 sequence, request 식별, phase, 트랜잭션 여부, 실제 반환 건수, SQLSTATE/제약명 | 선조회/차감/INSERT 예외/재조회·커밋 완료 증거 구분 |
| InventorySnapshot | resourceId, capacity, available, totalCount, confirmedCount | 여러 대상도 단일 SELECT로 얻고 대상 누락·음수·불일치 실패 |
| CleanupResult | 제출/종료 수, Executor 종료, 서버 inFlight, HTTP 종료, 정리 예외 | 종료 확인 전 reset/다음 회차 금지 |

차단 상태는 CleanupResult의 회차별 결과와 별개로 클래스 실행 전체에서 유지하는 테스트 메모리 상태다. 최초 종료 실패 원인만 원자적으로 보존하고 회차 setup/cleanup으로 해제하지 않는다. 보호 검사 실패는 DB reset·준비·요청 제출0회와 원인 보존으로 판정한다. 제품 DB에는 저장하지 않는다.

## 전이와 판정

- 일반 생성 성공: available -1, CONFIRMED 행+1을 함께 커밋한다.
- 중복 생성: 최초 저장 속성 재현, 추가 행·수량 차감0. UNIQUE 실패 경로의 잠정 차감은 롤백된다.
- 첫 취소: CONFIRMED→CANCELLED와 available+1 함께 커밋. 중복 취소는 추가 복구0.
- 생성·취소 성공 순서: 기존 CANCELLED + 새 CONFIRMED1 + available0.
- 생성·취소 품절 순서: 기존 CANCELLED + 새 행0 + available1. 동일 새 키 재시도 후 CONFIRMED1 + available0.
- 차감 후 저장 실패: 실패키 행0, 정상 경쟁키 행1, capacity2/available1. 실패키 재시도 후 confirmed2/available0.

## Snapshot 설계

DatabaseAssertions 확장은 입력 대상 ID 목록을 받아 resources LEFT JOIN reservations GROUP BY로 capacity·available·전체 행수·CONFIRMED 행수를 한 SELECT에 집계한다. totalCount와 confirmedCount를 분리하여 CANCELLED도 남는지 확인한다. 입력 ID와 결과 ID 집합이 일치해야 한다. 빈 목록은 입력 오류로 거부한다. 기존 단건 assertInventoryConserved는 기존 계약을 유지한다.

집계 결과는 모든 요청과 서버 처리 종료 뒤에만 최종 판정에 사용한다. 상태·시각·사용자키 유일성은 별도 조회/제약 및 응답과 대조하며 수량 보존의 단일 관측을 분리 쿼리로 대체하지 않는다. 최종 판정 후 재시도가 있으면 재시도 전·후 snapshot을 각각 기록한다.


### 2026-10-21 최종 리뷰 반영

종료 확인 전 초기화 금지 원칙을 우선하여 Executor·서버·HTTP 종료 확인과 기존 차단 상태 확인을 모두 통과한 뒤에만 Spy를 초기화한다. Service 진입 전의 서버 처리는 Service in-flight만으로 관측할 수 없으므로 전송 실패/timeout 등 요청 결과 예외도 최초 원인을 보존하고 다음 부모 setup을 차단한다. 후속 reset·준비·제출 금지는 종료 확인 실패 때와 동일하다. 이는 회차 격리 보완이며 제품/API/DB 계약 변경이 아니다.

실행 함수가 종료 실패를 던져도 최초 오류·다른 작업 오류·수집한 전체 결과를 함께 보존한다. 생성 키와 호출별 고유 요청 식별자를 분리하고 UNIQUE 뒤 비트랜잭션 조회 및 C07의 차감0 뒤 트랜잭션 안 조회는 같은 요청 ID·후속 순번으로 연결한다. 보존 JSON에도 C07의 차감0/성공 재조회 사건을 남긴다. 추가 Unit/fixture 수는110회 반복과 별도로 실제 실행 기록을 따른다.
