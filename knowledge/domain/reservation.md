# reservation 도메인 지식

## 1. 기준과 현재 상태

- 예정 패키지: `com.limitedbooking.backend.reservation`. 작성·확인일: 2026-10-13.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절, [API 규칙](../rules/api-and-security.md), [트랜잭션 규칙](../rules/domain-and-transactions.md), [요청 제한 규칙](../rules/rate-limit.md).
- 설계: 기존 확정 규칙. 구현·SQL·테스트: 미구현. 실행 검증: 미실행.

## 2. 목적·책임·경계

예약의 생성·소유자 조회·취소를 담당한다. 수량 변경은 resource와 하나의 PostgreSQL 트랜잭션으로 반영한다. 결제·만료·대기열·목록·재활성화·행 삭제는 제외한다. Redis는 진입 제한만 담당한다.

## 3. 용어와 데이터

| 필드 | 의미·타입 | 허용값·불변 여부 | 근거 |
| --- | --- | --- | --- |
| id / resourceId | UUID 예약/대상 | 필수·불변, resourceId는 존재하는 대상 FK | 계획서 5.1 |
| userId | 인증 UUID | 클라이언트 입력·응답 아님, 불변 | 계획서 2 |
| idempotencyKey | 문자열 | ASCII 영문·숫자·밑줄·하이픈 1~64, 변환 없음 | 계획서 2 |
| status | 문자열 | CONFIRMED 또는 CANCELLED | 계획서 5.1 |
| createdAt | UTC Instant / timestamptz | 저장된 마이크로초 값·불변 | 계획서 5.1 |
| cancelledAt | UTC 시각/null | CONFIRMED는 null, CANCELLED는 필수 시각 | 계획서 4.1 |

설계 제약은 `uq_reservations_user_key(user_id,idempotency_key)`, 상태/취소 시각 CHECK, resource_id 인덱스다. 성공 행·키를 TTL로 삭제하지 않는다. 실제 스키마는 아직 없다.

## 4. 불변식과 상태 전이

| 규칙 | 적용 경계 | 기대값 |
| --- | --- | --- |
| reservation-001 | 커밋된 상태 | available + CONFIRMED 수 = capacity, 0≤available≤capacity |
| reservation-002 | 사용자·키 | 최대 예약 1개, 다른 키로 추가 예약 가능 |
| reservation-003 | 취소 | 예약당 복구 최대 1회 |

| 현재 상태 | 명령 | 다음 상태 | 수량 변화 | 응답 |
| --- | --- | --- | --- | --- |
| 없음 | 생성 성공 | CONFIRMED | -1 | 201 |
| CONFIRMED | 소유자 취소 | CANCELLED | +1 | 200 |
| CANCELLED | 중복 취소 | CANCELLED | 0 | 200, 동일 취소 시각 |
| 어느 성공 상태든 | 같은 키·대상 생성 | 유지 | 0 | 최초 CONFIRMED·201 재현 |
| 어느 성공 상태든 | 같은 키·다른 대상 | 유지 | 0 | 409 IDEMPOTENCY_CONFLICT |

## 5. 유스케이스와 API 계약

| API | 인증·입력 | 성공 | 실패 | 변경 |
| --- | --- | --- | --- | --- |
| POST /api/reservations | 인증 UUID, resourceId·Idempotency-Key | 201 CreatedReservationView, Location, Idempotent-Replayed | 400·401·404·409·413·415·429·503 | 수량-1·행 INSERT 함께 |
| GET /api/reservations/{id} | 소유자·UUID | 200 ReservationView | 400·401·404·503, 공통 본문 초과 413 | 없음 |
| POST /api/reservations/{id}/cancel | 소유자·UUID·본문 없음 | 200 ReservationView | 400·401·404·413·429·503 | 최초만 상태·수량+1 함께 |

타인 접근은 관리자도 404다. 생성 응답은 id/resourceId/status/createdAt이며 현재 응답에 필수 cancelledAt가 추가된다. userId·quantity를 입력받지 않는다.

## 6. 멱등성·트랜잭션·경쟁·장애

키 범위는 인증 사용자·키이며 대상 UUID 값으로 비교한다. 실패 키는 남기지 않는다. 취소 후에도 성공 키를 유지하고 최초 201·Location·JSON 필드 값을 재현한다. 최초 Replay=false, 재요청=true다.

Service는 트랜잭션 없이 기존 키를 확인하고 별도 Writer Bean에서 READ COMMITTED로 조건부 available>0 UPDATE와 INSERT를 커밋한다. UPDATE 0이면 새 SELECT로 키·대상·품절을 구분한다. SQLSTATE 23505와 제약 이름을 모두 확인한 유일 위반만 Writer 전체 롤백 후 Service의 새 조회로 처리한다. 다른 제약·접속 오류를 재요청으로 간주하지 않는다.

취소는 소유자 조회 후 resource FOR UPDATE→소유자·CONFIRMED 조건 예약 갱신 순서다. 갱신 1건만 available<capacity 조건으로 복구하며 복구 0건이면 전체 롤백한다. 잠금 순서는 resource→reservation이다. 자기 호출·상위 트랜잭션·실패 트랜잭션 내 계속 조회는 금지한다.

DB timeout은 트랜잭션 시작 SET LOCAL로 lock 2초·statement 5초, 풀 최대 10·획득 3초 설계다. 의존성 장애·timeout은 503·전체 롤백, 내부 불변식 결함은 500으로 탐지한다. 커밋 뒤 응답 유실은 동일 키 재요청으로 복구한다. 크기 제한→인증→예약 쓰기 제한→입력 검증→서비스 순서이며 429/Redis 503은 DB 무변경이다.

## 7. 검증할 사례와 실제 증거

| 규칙 | 사례 | 기대 결과 | 검증 상태 |
| --- | --- | --- | --- |
| reservation-001~003 | capacity=1 예약·재요청·취소·재요청·중복 취소 | 추가 예약 없음, 복구 1회, 수량식 유지 | 수동 사례 작성·대조됨, HTTP/DB 미실행 |
| reservation-002 | 마지막 1개 동일 키 / 다른 대상 동일 키 경쟁 | 한 성공 행 / 한 성공과 충돌, 패자 수량 롤백 | 후속 IT 예정 |
| reservation-003 | 중복 취소·생성/취소 경쟁 | 동일 취소 시각·수량식 유지 | 후속 IT 예정 |
| 롤백 | UPDATE 뒤 INSERT 실패 | 행·수량 부분 반영 없음 | 후속 IT 예정 |

실제 검토·실행 기록은 [증거 문서](../../docs/test-evidence.md)에서 구분한다. 위 후속 테스트는 아직 존재하거나 실행된 것으로 간주하지 않는다.

## 8. 구현 연결과 변경 영향

| 책임 | 실제 파일·예정 위치 | 상태 |
| --- | --- | --- |
| 앱 골격 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java) | 존재, 해당 기능 없음 |
| 규칙·DTO·Controller·Service·Writer·Repository·필터 | 해당 기능 패키지 예정 | 미구현 |
| SQL·마이그레이션·실제 HTTP/DB 테스트 | 후속 단계 예정 | 미구현 |
| 고정 계약·결정 기록 | [OpenAPI 초안](../../docs/openapi.yaml)·[결정 기록](../../docs/decisions.md) | 작성됨, 이름 공백 범위·본인 설명 확인 대기; 실행 미검증 |

계약·업무 변경은 계획서·관련 도메인·공통 규칙·OpenAPI·테스트를 함께 검토한다. 서비스 경계·키·수량·권한·실패 무변경은 관련 도메인에 함께 영향을 준다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 기능·저장·필터 미구현 | 실제 응답·경쟁·장애 검증 불가 | 0단계 고정 계약·사례 작성 뒤 1단계 도구 준비 |
| 실행 미검증 | 계약 선언이 실제 동작 증거가 아님 | 후속 실제 HTTP·의존성 시험 |

기존 규칙 이외의 새 정책을 추가하지 않았다.
