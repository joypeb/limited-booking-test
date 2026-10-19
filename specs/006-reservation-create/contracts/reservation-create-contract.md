# 5단계 인터페이스·예약 생성 계약

작성일: 2026-10-19. 설계 계약에 따라 아래 인터페이스/테스트를5단계에서 구현했다. 실행 근거는 docs/test-evidence.md를 따른다. [고정 OpenAPI](../../../docs/openapi.yaml)·[계획서](<../../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 4~5절을 변경하지 않는다.

## 외부 인터페이스

`POST /api/reservations`, Basic 인증된 ADMIN·USER_A·USER_B 허용. JSON 본문은 `{"resourceId":"UUID"}`만 허용하며 `Idempotency-Key` 헤더가 필수다. 사용자 UUID는 `LocalIdentityResolver.userId(Authentication)`에서 얻는다. 임의 X-User-Id는 무시하고 본문 userId·quantity 등 미정의 필드는 400으로 거부한다.

### 성공

- 최초 및 재현 모두 `201`, `Content-Type: application/json`, `Location: /api/reservations/{id}`.
- 최초 `Idempotent-Replayed: false`, 재현 `true`.
- JSON 필드는 `id`, `resourceId`, `status`, `createdAt`만 반환한다. status는 항상 CONFIRMED, createdAt은 저장된 UTC 값이다.
- 같은 사용자·키·대상은 JSON 필드 값·Location·201을 재현한다. JSON의 공백·순서·동일 바이트는 보장하지 않는다.
- 취소된 성공 행도 최초 CONFIRMED를 재현하며 cancelledAt를 생성 응답에 추가하지 않는다. Location의 GET 실제 구현은 6단계다.

### 입력/오류

| 조건 | HTTP / code | 추가 조건 |
| --- | --- | --- |
| 키 누락/규칙 위반, resourceId 누락/null/잘못된 UUID, 빈/잘못된 JSON, 중복 필드, 미정의 필드 | 400 INVALID_REQUEST | 키를 strip/lowercase/절단하지 않음 |
| 상한 이하 본문의 미인증/잘못된 자격 증명 | 401 UNAUTHENTICATED | WWW-Authenticate 포함 |
| 성공 키 없고 대상 없음 | 404 NOT_FOUND | 저장 변화 없음 |
| 성공 키 없고 수량 0 | 409 SOLD_OUT | 실패 키 보존 없음 |
| 같은 사용자·키·다른 대상 | 409 IDEMPOTENCY_CONFLICT | 양쪽 요청별 수량 변화 없음; 대상 없음·품절보다 기존 키 비교 우선 |
| 실제 읽은 본문 8192바이트 초과 | 413 PAYLOAD_TOO_LARGE | 인증보다 먼저, 길이 헤더·chunked와 무관 |
| 인증된 생성 요청의 미지원 Content-Type | 415 UNSUPPORTED_MEDIA_TYPE | application/json만 지원 |
| DB 연결·정해진 잠금/문장 timeout·연결 획득 장애 | 503 DEPENDENCY_UNAVAILABLE | 성공으로 기록하지 않고 전체 롤백 |
| 분류하지 못한 내부 결함 | 500 | 민감정보 비노출; 신규 오류 code 발명 금지 |

정의된 오류는 `application/problem+json`, type/title/status/detail/instance/code를 따른다. 500은 기존 고정 계약의 정상/업무 오류 범위 밖 내부 결함으로 탐지한다. 포괄적 예외 핸들러로 409/503에 숨기지 않는다.

향후 Redis 연동 시 인증→제한→입력 검증→서비스 순서다. 재요청도 429 RATE_LIMITED/Retry-After 또는 Redis 503으로 먼저 거부될 수 있다. 이번 5단계 테스트에서는 제한이 아직 미구현이며 해당 429/Redis 장애 인수는 8단계다.

## 내부 제공 인터페이스

| 인터페이스 | 계약 |
| --- | --- |
| `ReservationService.create(UUID userId, UUID resourceId, IdempotencyKey key): CreateReservationResult` | 활성 상위 트랜잭션 없이 진입. 기존 키 선조회→동일 대상 재현/다른 대상 충돌→없으면 외부 Writer 호출 |
| `ReservationWriter.create(UUID userId, UUID resourceId, IdempotencyKey key): CreateReservationResult` | 별도 Bean public 프록시·READ_COMMITTED. timeout 설정→차감→0건 재조회 또는 1건 INSERT RETURNING. 프록시 커밋 완료 뒤 호출자에게 결과 전달 |
| `UniqueViolationClassifier.isReservationUserKeyViolation(Throwable): boolean` | 동일 PSQLException의 23505 및 정확한 제약명 일치만 true; cause/nextException 순환 방지 탐색 |
| `CreateReservationResult` | `CreatedReservationView body`, `boolean replayed` |

Service는 트랜잭션을 시작하지 않고, 상위 트랜잭션이 활성 상태면 IllegalStateException으로 fail-fast한다. Writer에 REQUIRES_NEW로 이를 우회하지 않는다. 입력 null 같은 계약 위반과 내부 불변식 오류를 구분해 임의 400으로 바꾸지 않는다.

### Service의 복구 분기

1. 기존 성공 행이 있으면 resourceId UUID 값 비교만으로 재현/충돌한다. 수량·대상 조회를 먼저 하지 않는다.
2. 없으면 Writer를 호출한다. Writer 내부의 JDBC/RuntimeException을 삼키지 않는다.
3. Writer 프록시가 롤백 후 던진 오류 중 분류기가 true인 경우에만 Service가 새 키 조회를 한다.
4. 조회된 기존 성공은 동일 대상이면 replayed=true, 다른 대상이면 키 충돌이다. 없으면 IllegalStateException으로 내부 불변식 위반을 드러낸다. 반복 재시도하지 않는다.
5. 다른 PK/FK/연결 오류는 원래 실패를 전파한다. DB 의존성 장애의 503 변환은 HTTP 오류 경계의 좁은 책임이다.

### Writer의 UPDATE 0 분기

새 SELECT로 사용자·키를 재조회한다. 있으면 동일 대상 재현/다른 대상 충돌이다. 없으면 대상 find 결과가 없을 때 404, 존재할 때 SOLD_OUT이다. 이 분기는 유일 위반 예외 뒤 실패한 트랜잭션 안에서 조회하는 패턴과 다르다. resource UPDATE→reservation INSERT 잠금 순서를 유지한다.

## 검증 연결 (구현·실행됨; 실제 경쟁은 후속)

| 명세/검증 대상 | 테스트와 관측 |
| --- | --- |
| FR-001~004, US1 | ReservationCreateIT: capacity1 최초 201/Location/false·available0·행1·단일SELECT 보존식; capacity0 품절·없는 UUID404; 다른 키 추가 예약 |
| FR-005~008, US2 | IdempotencyIT: 마지막 수량 재요청·동일 키 다른 대상(존재/품절/없음)409·다른 사용자 동일 키·대소문자 다른 키·JSON/UUID 표기 변경·CANCELLED 준비 행의 CONFIRMED 재현 |
| FR-009, SC-004 | IdempotencyIT: 테스트 전용 Primary 고정 Clock의 나노초 값과 DB 마이크로초 반환값 비교, 최초/재현 정확 일치 |
| FR-010, US3.1~3.2·3.5 | ReservationRollbackIT(T022~T023·T030): 실제 UPDATE 후 저장 실패·의존성 장애의 전체 롤백과 원인 제거 후 동일 키201/false를 검사한다. ReservationCreateIT(T012·T014·T033·T038): 유효 키 K의 잘못된 본문400 후 본문만 바로잡은 동일 키201/false, 품절 대상 A의409 후 같은 K로 예약 가능한 B의201/false를 검사한다. 실패 직후 예약/키 없음·수량 불변과 재시도 후 고유 예약1개·수량 보존식을 각각 대조한다. |
| FR-011 분류 정확성 | UniqueViolationClassifierTest: 대상만 true·다른 PK/FK/연결·이름/상태 누락·순환 false; ReservationRollbackIT의 직접 Writer 호출에서 실제 대상 유일 위반 및 수량 롤백 확인 |
| FR-011 조정/US3.3 | ReservationServiceTest: Writer가 중복 오류를 던진 뒤 Service의 새 조회가 기존 성공으로 재현/충돌하는 순서, 기존 성공 누락은 내부 오류, 비대상 예외 전파. 이는 순수 조정 검사이며 실제 경쟁 검증의 증거가 아님 |
| UPDATE0 키 재조회 | T019의 ReservationWriterTest 작성·기능 RED 후 T020의 최소 구현·GREEN 순서로 0건 이후 새 조회의 동일/충돌/없음 판별; 실제 PostgreSQL 순차 호출의 품절/없음은 ReservationCreateIT. READ COMMITTED 경쟁 관측은 7단계 |
| FR-012, US4 | ReservationCreateIT: 실제 Basic·위조 헤더 무시·입력 경계·크기/형식·공통 오류·무변경, 연결 장애503 및 내부 결함500 비노출 |
| 상위 트랜잭션/프록시·timeout | ReservationRollbackIT: 신규 세 IT의 동적 설정에 풀 최대10·연결 획득 대기3000ms를 명시하고 실제 DataSource 값을 확인한다. local 프로필을 가져오지 않고 유한 종료로 판정한다. 직접 Writer 호출은 Spring 주입 Bean 사용. 상위 TransactionTemplate 안 Service 거부·무변경 검사. 독립 연결의 resource 잠금으로 lock timeout503·롤백 후 동일 키 복구; pg_sleep 등 문장 지연은 테스트 전용 래퍼로 statement timeout503/롤백 확인 |

직접 Writer 유일 위반 시험은 준비된 성공 키와 충분한 available로 UPDATE가 실제 성공한 뒤 유일 위반을 발생시켜 전체 롤백을 관측한다. API 선조회로 충돌만 확인하는 시험과 구분한다. 다른 PK/FK 분류는 Unit과 기존 실제 스키마 검증을 활용하며 필요할 때 직접 쓰기로 대조한다.

순수 테스트에서의 협력 객체 대역은 조정 분기 증거이며 SQL·트랜잭션 검증을 대체하지 않는다. 모든 HTTP/DB 인수는 실제 PostgreSQL을 사용한다. 준비 데이터는 요청 전에 커밋하고 테스트 전체 `@Transactional`을 붙이지 않는다. 종료 후 별도 연결에서 행/수량/키를 검사한다. 테스트 클래스와 Gradle 실행은 순차 처리한다.

## 단계 경계와 기록

5단계는 실제 동시성 반복을 통과했다고 주장하지 않는다. 마지막 수량 동일 키·충분한 수량 UNIQUE·같은 키 다른 대상 경쟁은 7단계의 필수 후속이다. 6단계 취소 API를 이 계획에 끼워 넣지 않는다. 실행 명령·실제 발견 수·실패·skip·주입 원복·미검증을 docs/test-evidence.md에 기록한다.
