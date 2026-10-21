# resource 도메인 지식

## 1. 기준과 현재 상태

- 도메인·패키지: resource / `com.limitedbooking.backend.resource`.
- 작성: 2026-10-13, 재확인: 2026-10-17.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2·4·5·7절, [도메인 규칙](../rules/domain-and-transactions.md), [API 규칙](../rules/api-and-security.md), [0단계 명세](../../specs/001-define-api-contract/spec.md), [4단계 명세](../../specs/005-auth-resource-api/spec.md).
- 설계 상태: 사용자 A 답변으로 코드 포인트 길이, 2026-10-13 추가 답변으로 Java strip 기준 확정.
- 구현 상태: V1·`ResourceView`·`ResourceRepository`(`find`·`insert`)·`ResourceRules`·`ResourceService`(`create`·`get`)·`ResourceController`(`POST /api/resources`·`GET /api/resources/{id}`) 및 저장·Unit·HTTP 통합 테스트(`SchemaIT`, `ResourceRepositoryIT`, `ResourceRulesTest`, `ResourceApiIT`, `SecurityIT`) 구현·실행 검증됨. 예약 생성·취소에 따른 `available` 차감/복구 SQL은 5~7단계 후속 미구현.

## 2. 목적·책임·경계

ADMIN이 예약 대상·최초 수량을 생성하고 인증 사용자가 단건 조회한다. 수량 변경·삭제·목록·페이지네이션은 범위 밖이다. PostgreSQL이 원본이며 예약 생성·취소와 함께 수량을 변경한다. Redis는 대상 생성·조회의 의존성이 아니다.

## 3. 용어와 데이터

| 필드 | 의미·타입 | 제약·불변 여부 | 근거 |
| --- | --- | --- | --- |
| id | UUID 대상 식별자 | 불변·PK 설계 | 계획서 5.1 |
| name | 문자열 이름 | Java `String.strip()`으로 앞뒤 공백 제거 후 코드 포인트 1~100개, 😀는 1자 | 계획서 2·사용자 A |
| capacity | 최초 수량 정수 | 0~1000, 불변 | 계획서 2 |
| available | 남은 수량 정수 | 최초 capacity, 0~capacity | 계획서 2·5 |
| created_at | 생성 UTC 시각 | timestamptz 설계, ResourceView에는 미포함 | 계획서 4·5 |

스키마의 name varchar(100) NOT NULL, capacity·available 정수 NOT NULL과 범위 CHECK를 구현·검증했다. 대상 삭제·수량 변경은 제공하지 않는다.

Java 25 `String.strip()`의 `Character.isWhitespace(int)` 기준으로 이름 앞뒤 공백을 제거한다. 일반 공백·탭·줄바꿈은 제거하고 내부 공백은 유지한다. U+00A0(NBSP)·U+2007·U+202F는 제거하지 않고 코드 포인트 길이에 포함한다. 제거 후 길이 1~100이며 빈 문자열은 400이다. NBSP만 1개인 이름은 길이1로 허용한다.

## 4. 불변식과 상태 전이

| 규칙 | 내용 | 적용 | 근거 |
| --- | --- | --- | --- |
| resource-001 | 0≤available≤capacity | 커밋된 상태 | 계획서 2 |
| resource-002 | available + CONFIRMED 예약 수 = capacity | 예약·취소와 함께 | 계획서 2 |
| resource-003 | 이름 길이는 공백 제거 후 코드 포인트 수 | 생성 검증 | 사용자 A·계획서 2 |

별도 대상 상태 enum은 없다. 생성 시 available=capacity, 예약 성공 시 -1, 최초 취소 시 +1이며 관련 예약 상태와 원자적으로 변경한다. 재요청·중복 취소·실패는 추가 수량 변경이 없다.

## 5. 유스케이스와 API 계약

| API | 권한·입력 | 성공 | 실패 | 변경 |
| --- | --- | --- | --- | --- |
| POST /api/resources | ADMIN, name·capacity, JSON | 201 ResourceView·Location | 400·401·403·413·415·503 | 대상 생성 |
| GET /api/resources/{id} | 인증 사용자, UUID | 200 ResourceView | 400·401·404·503 | 없음 |

ResourceView는 id·name·capacity·available다. 본문 크기 제한·오류 상세는 API 규칙을 따른다. [OpenAPI 초안](../../docs/openapi.yaml)을 작성했다. [작성 설계](../../specs/001-define-api-contract/contracts/api-contract.md)와 대조하며 Java strip 정책까지 반영해 문서 계약 인수를 마쳤고, 4단계 `ResourceApiIT`·`SecurityIT`에서 실제 HTTP 경로로 생성·조회·오류 계약을 검증했다. 업무 스키마는 `SchemaIT`에서 검증했다.

## 6. 멱등성·트랜잭션·경쟁·장애

대상 생성에는 멱등 키 계약이 없다. 예약 생성의 키·취소 정책은 대상 수량 변경에 영향을 준다. 조건부 available>0 감소, resource→reservation 잠금 순서, 실패 전체 롤백은 [도메인 규칙](../rules/domain-and-transactions.md)을 따른다. DB 장애·timeout은 503, Redis 장애가 정상 DB의 대상 생성·조회를 막지 않는다. 대상 생성 응답 유실에 별도 재현 보장을 추가하지 않는다.

## 7. 검증할 사례와 실제 증거

| 규칙 | 사례 | 기대 | 종류·상태 |
| --- | --- | --- | --- |
| resource-003 | 😀 100개·101개, 앞뒤 공백+100개 | 201·400·201, 저장 이름은 공백 제거 | Unit 52건 및 `ResourceApiIT` HTTP 검증 완료 |
| resource-001 | capacity 0·1000·-1·1001, 부동소수점·문자열·중복 키·미정의 `userId` | 경계 허용(201)·범위 밖/엄격 JSON 위반 400 | Unit 52건 및 `ResourceApiIT` HTTP 검증 완료 |
| resource-002 | 예약·재요청·취소·중복 취소 | 합계 capacity 유지 | 후속 5~7단계 DB·경쟁 미실행 |
| resource-003 | 탭·줄바꿈 제거, NBSP 유지·NBSP만1개 | 이름 정규화·길이 정책대로 허용/거부 | [W01~W07 문서 사례](../../docs/test-evidence.md), Unit 및 `ResourceApiIT` HTTP 검증 완료 |
| 권한 | USER 생성·미인증 조회 | 403·401 | `SecurityIT` HTTP 검증 완료 |

[실행 증거](../../docs/test-evidence.md)에 문서 검토와 실제 저장 제약·조회·검사·HTTP 인수를 구분해 기록한다.

## 8. 구현 연결과 변경 영향

| 책임 | 파일·심볼 | 상태 |
| --- | --- | --- |
| 앱 골격·UTC 시계 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java)·[ClockConfig](../../backend/src/main/java/com/limitedbooking/backend/common/ClockConfig.java) | 구현·실행 검증됨 |
| 읽기/생성 DTO·Repository | [ResourceRepository](../../backend/src/main/java/com/limitedbooking/backend/resource/ResourceRepository.java)·[ResourceView](../../backend/src/main/java/com/limitedbooking/backend/resource/ResourceView.java) | `find`·`insert` 구현·실행 검증됨 |
| 입력 규칙 | [ResourceRules](../../backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java)·[ResourceRulesTest](../../backend/src/test/java/com/limitedbooking/backend/resource/ResourceRulesTest.java) | 구현·Unit 52건 및 HTTP 경로 연동 검증됨 |
| Controller·Service | [ResourceController](../../backend/src/main/java/com/limitedbooking/backend/resource/ResourceController.java)·[ResourceService](../../backend/src/main/java/com/limitedbooking/backend/resource/ResourceService.java) | 구현·`ResourceApiIT` 8건·`SecurityIT` 6건 검증됨 |
| 업무 V1·대상 저장/조회/HTTP 테스트 | [V1](../../backend/src/main/resources/db/migration/V1__create_reservation_schema.sql)·`SchemaIT`·`ResourceRepositoryIT`·[ResourceApiIT](../../backend/src/test/java/com/limitedbooking/backend/resource/ResourceApiIT.java) | 구현·실행 검증됨 |
| 고정 OpenAPI 초안 | [계약](../../docs/openapi.yaml) | 작성·문서 대조됨, 9단계 OpenAPI 자동 검증은 후속 |

이름 길이 단위의 명확화는 계획서·도메인 규칙·명세·계약 설계에 반영했다. 구현 이후에는 순수 규칙·DTO·OpenAPI·Unit·HTTP 기대값을 함께 검토한다. JDBC 등 선택 이유는 [결정 기록](../../docs/decisions.md)에 작성했다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 취소의 `available` 복구·실제 경쟁 미구현 | 생성·재현·실패 롤백 보존은5단계 검증 완료; 취소/경쟁 보존 후속 | 6단계 조회·취소,7단계 실제 동시성 검증 |
| 업무 OpenAPI 자동 계약 검증 미실행 | 수동/IT 검증은 완료했으나 validator 자동 대조는 후속 | 9단계 API 계약 및 비정상 입력 테스트에서 검증 |

이름 길이 코드 포인트·공백 strip 정책과 4단계 대상 생성·단건 조회 HTTP API 구현 및 통합 검증을 완료했다. 예약 생성의 조건부 차감·재현 무변경·실패 롤백과 수량 보존은5단계에서 검증했다. 취소 복구·실제 경쟁 보존은6~7단계 후속이다.

### 2026-10-14 저장 길이 보장 범위 명확화

계획서 5.1의 varchar 저장 상한과 요청 입력 거부를 구분한다. 길이는 문자 수이고 이름의 😀는1자다. 비공백 이름101자·키65자는 직접 저장에서 거부하지만 비공백 이름100자/키64자 뒤 일반 공백만 초과하면 상한으로 절단 저장될 수 있다. 이 예외와 명시적 cast의 절단을 원본 요청 검증 보장으로 확대하지 않는다. 이름 strip·최소 길이·키 문자 규칙은 기존 후속 입력 검증에서 수행한다. 기존 스키마 타입·외부 API 계약은 유지하며 관련 직접 저장 사례는 003 명세·데이터 모델에 정의했다. SchemaIT에서 실제 업무 V1의 저장 길이를 검증했다. 입력 API의 인수는 후속이다.

### CHK022 실패 처리 검증 연결

2단계의 정상 연결 고의 SQL 실패에서는 업무 V1의 두 테이블·인덱스가 부분 생성돼 남지 않아야 하고 성공 적용 이력은0건이다. 실패 DB의 증거/종료 후 새 테스트 DB에서 원본 V1로 준비를 재개한다. 테스트 초기화는 대상·예약의 부분 삭제를 남기지 않고 전체 롤백하며, 장애 제거 후 같은 DB의 새 트랜잭션으로 재요청한다. 기존 행/값·구조·이력을 별도 연결에서 비교한다. 업무 예약 생성/취소의 장애·경쟁 보장과는 별개의 테스트 지원 계약이며 SchemaIT/DatabaseSupportIT에서 실행 검증했다. [003 실패 처리 계약](../../specs/003-define-db-schema/contracts/persistence-contract.md)에 근거와 검증 사례를 연결했다.

### 현재 저장 기반 구현·검증 상태 (2026-10-15)

V1·읽기 Repository와 대응 IT는 구현·실행 검증됨이다. 직접 저장 제약의 보장 범위는 스키마에 한정하며 입력 strip 순수 규칙은3단계에서 구현·Unit 검증했다. HTTP·권한·Writer·경쟁·응답 재현은 미구현이다. [TestData](../../backend/src/test/java/com/limitedbooking/backend/support/TestData.java)는 실행 소유 DataSource의 독립 커밋과 한 트랜잭션 삭제를 사용한다. [DatabaseAssertions](../../backend/src/test/java/com/limitedbooking/backend/support/DatabaseAssertions.java)는 단일SELECT에서 capacity/available/확정 수를 검사한다. 보존식 불일치를 스키마가 막는다는 보장은 없으며 실제 업무 쓰기의 보장은 후속이다.

fresh/reapplied 두 독립 DB에서 같은 제약·조회·초기화/검사 사례를 검증했다. 실패사본 V1 전체 DDL 롤백과 새DB복구, 초기화 중간 실패의 전체 행/값·구조/이력 보존 및 같은DB 명시적 재요청은 [증거](../../docs/test-evidence.md)를 따른다. 연결 단절·프로세스 중단·부하는 미검증이다.

### 3단계 순수 규칙 설계 (2026-10-15)

[004 계획](../../specs/004-validate-domain-rules/plan.md)과 [규칙 계약](../../specs/004-validate-domain-rules/contracts/rules-contract.md)에 ResourceRules의 두 public static 메서드·수량/이름 Unit 경계를 설계했다. 무효 입력은 Java 내부에서 IllegalArgumentException으로 거부하며 정확한 메시지는 고정하지 않는다. 순수 Unit과 4단계 실제 요청 경로 사용을 별도 인수한다. ResourceRules·대응 Unit52건의 구현·실행 검증을 완료했다. 개발자 설명 인수는 2026-10-16 설명 후 사용자 완료 지시로 처리했다(독립 재설명 시험 미실시).

### 4단계 대상 생성·단건 조회 HTTP 연동 완료 (2026-10-17)

[005 계획](../../specs/005-auth-resource-api/plan.md)과 [인터페이스 계약](../../specs/005-auth-resource-api/contracts/auth-resource-contract.md)에 따라 `ResourceRepository.insert`, `ResourceService.create/get`(UTC `Clock` 주입 및 `ResourceRules` 호출), `ResourceController`(`POST /api/resources`, `GET /api/resources/{id}`)를 구현하고, `ResourceApiIT`(8건)와 `SecurityIT`(6건)에서 `capacity = 0/1000`, 이름 공백 strip·NBSP/이모지 보존, 미정의 `userId`·중복 키·부동소수점/문자열 수량·잘못된 UUID `400`, 미존재 `404`, `413`/`415`/`503`을 실제 HTTP 요청 경로에서 검증했다. 예약 생성·취소에 따른 `available` 차감/복구는 5~7단계에서 구현한다.


### 2026-10-19 예약 생성 연동

5단계 ResourceRepository.decrementIfAvailable은 available>0 조건 UPDATE를 제공하며 별도 예약 Writer에서 INSERT와 함께 커밋한다. ReservationCreateIT/IdempotencyIT/ReservationRollbackIT에서 수량 보존·재현 무변경·실패 전체 롤백을 실제 PostgreSQL로 확인했다. 수량 복구 취소는6단계 후속이다. 실행 근거는 [증거](../../docs/test-evidence.md)다.


### 2026-10-20 취소 수량 복구 연동

[ResourceRepository](../../backend/src/main/java/com/limitedbooking/backend/resource/ResourceRepository.java)에 `lockForUpdate`(대상 행 SELECT FOR UPDATE)와 `incrementIfBelowCapacity`(available<capacity 조건 +1)를 추가했다. 예약 Writer는 대상 잠금을 예약 상태 갱신보다 먼저 얻고 상태 변경1건일 때만 복구한다. 복구0건·예외는 상태와 수량을 전체 롤백한다. [ReservationCancelIT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationCancelIT.java)의 실제 PostgreSQL·HTTP 검증과 단일 SELECT 보존식 검사로 확인했다. 중복 취소는 추가 복구하지 않는다. 실제 경쟁 반복은7단계 후속이다.


### 7단계 경쟁 검증 연결 (2026-10-20)

경쟁 종료 후 여러 대상의 capacity/available/전체예약수/확정수를 단일 LEFT JOIN 집계로 검사한다. C04 패자수량 원복, C01 초과예약 방지, C05 복구1회와 C11 실패차감 롤백을 실제 PostgreSQL/HTTP 반복으로 확인했다. ResourceRepository 제품 변경은 없고 조건 방어의 고의 변경은 탐지 후 원복했다. [경쟁IT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationConcurrencyIT.java)·[실행증거](../../docs/test-evidence.md)·[회차기록](../../docs/concurrency-runs.json)을 따른다. 앞 절의 후속 표시는 각 과거 확인일의 상태다.


### 2026-10-21 최종 리뷰 보완 확인

최종 일반 verify Unit150·IT257(경쟁110회+지원fixture5 포함), 실패/오류/skip0. 종료/전송실패 차단·모든 오류/결과 보존·호출별 고유ID와 C07 경로기록을 보완했다. 제품·API·스키마는 유지한다. [최종 실행 증거](../../docs/test-evidence.md)·[110회 기록](../../docs/concurrency-runs.json)을 따른다. 이전 날짜의 실행 수는 당시 기록이다. Redis·심화·부하·외부CI는 후속이다.
