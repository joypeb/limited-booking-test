# resource 도메인 지식

## 1. 기준과 현재 상태

- 도메인·예정 패키지: resource / `com.limitedbooking.backend.resource`.
- 작성·확인일: 2026-10-13.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2·4·5절, [도메인 규칙](../rules/domain-and-transactions.md), [API 규칙](../rules/api-and-security.md), [0단계 명세](../../specs/001-define-api-contract/spec.md).
- 설계 상태: 사용자 A 답변으로 코드 포인트 길이, 2026-10-13 추가 답변으로 Java strip 기준 확정.
- 구현 상태: 대상 기능·SQL·테스트 미구현. 실행 검증 미실행.

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

스키마는 설계 상태다. name varchar(100) NOT NULL, capacity·available 정수 NOT NULL과 범위 CHECK를 예정한다. 대상 삭제·수량 변경은 제공하지 않는다.

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

ResourceView는 id·name·capacity·available다. 본문 크기 제한·오류 상세는 API 규칙을 따른다. [OpenAPI 초안](../../docs/openapi.yaml)을 작성했다. [작성 설계](../../specs/001-define-api-contract/contracts/api-contract.md)와 대조하며 Java strip 정책까지 반영해 문서 계약 인수를 마쳤다. 실제 HTTP·스키마 자동 검증은 후속 미실행이다.

## 6. 멱등성·트랜잭션·경쟁·장애

대상 생성에는 멱등 키 계약이 없다. 예약 생성의 키·취소 정책은 대상 수량 변경에 영향을 준다. 조건부 available>0 감소, resource→reservation 잠금 순서, 실패 전체 롤백은 [도메인 규칙](../rules/domain-and-transactions.md)을 따른다. DB 장애·timeout은 503, Redis 장애가 정상 DB의 대상 생성·조회를 막지 않는다. 대상 생성 응답 유실에 별도 재현 보장을 추가하지 않는다.

## 7. 검증할 사례와 실제 증거

| 규칙 | 사례 | 기대 | 종류·상태 |
| --- | --- | --- | --- |
| resource-003 | 😀 100개·101개, 앞뒤 공백+100개 | 201·400·201, 저장 이름은 공백 제거 | 문서 기대값, Unit·HTTP 후속 미실행 |
| resource-001 | capacity 0·1000·-1·1001 | 경계 허용·범위 밖 400 | 후속 미실행 |
| resource-002 | 예약·재요청·취소·중복 취소 | 합계 capacity 유지 | 후속 DB·경쟁 미실행 |
| resource-003 | 탭·줄바꿈 제거, NBSP 유지·NBSP만1개 | 이름 정규화·길이 정책대로 허용/거부 | [W01~W07 문서 사례](../../docs/test-evidence.md), 실행 미검증 |
| 권한 | USER 생성·미인증 조회 | 403·401 | 후속 HTTP 미실행 |

기능 실행 증거는 없다. [문서 검토 증거](../../docs/test-evidence.md)에 수동 기대값 대조와 실제 문서 검사 결과를 기록한다.

## 8. 구현 연결과 변경 영향

| 책임 | 파일·심볼 | 상태 |
| --- | --- | --- |
| 앱 골격 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java) | 존재, 대상 기능 없음 |
| 대상 규칙·DTO·Controller·Service·Repository | resource 패키지 예정 | 미구현 |
| SQL·Flyway·대상 테스트 | 후속 단계 예정 | 미구현 |
| 고정 OpenAPI 초안 | [계약](../../docs/openapi.yaml) | 작성·문서 대조됨, 자동 검증 미실행 |

이름 길이 단위의 명확화는 계획서·도메인 규칙·명세·계약 설계에 반영했다. 구현 이후에는 순수 규칙·DTO·OpenAPI·Unit·HTTP 기대값을 함께 검토한다. JDBC 등 선택 이유는 [결정 기록](../../docs/decisions.md)에 작성했다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 이름 길이 실행 검증 미실행 | 코드 포인트와 실제 거부 결과 | 규칙·HTTP 구현 단계에서 경계 시험 |
| API·DB 기능 미구현 | 실제 계약 준수 확인 불가 | 0단계 문서 확정 뒤 도구 준비 |
| strip 실제 실행 미검증 | 탭·줄바꿈·NBSP·코드 포인트 경계의 실제 결과 | 후속 순수 규칙·HTTP 테스트 |

이름 길이 코드 포인트·공백 strip 정책은 사용자 답변으로 확정됐다. 이 문서는 업무 규칙을 바꾸거나 코드 실행 완료를 주장하지 않는다.

### 2026-10-14 저장 길이 보장 범위 명확화

계획서 5.1의 varchar 저장 상한과 요청 입력 거부를 구분한다. 길이는 문자 수이고 이름의 😀는1자다. 비공백 이름101자·키65자는 직접 저장에서 거부하지만 비공백 이름100자/키64자 뒤 일반 공백만 초과하면 상한으로 절단 저장될 수 있다. 이 예외와 명시적 cast의 절단을 원본 요청 검증 보장으로 확대하지 않는다. 이름 strip·최소 길이·키 문자 규칙은 기존 후속 입력 검증에서 수행한다. 기존 스키마 타입·외부 API 계약은 유지하며 관련 직접 저장 사례는 003 명세·데이터 모델에 정의했다. 실제 업무 DB 검증은 미실행이다.

### CHK022 실패 처리 검증 연결

2단계의 정상 연결 고의 SQL 실패에서는 업무 V1의 두 테이블·인덱스가 부분 생성돼 남지 않아야 하고 성공 적용 이력은0건이다. 실패 DB의 증거/종료 후 새 테스트 DB에서 원본 V1로 준비를 재개한다. 테스트 초기화는 대상·예약의 부분 삭제를 남기지 않고 전체 롤백하며, 장애 제거 후 같은 DB의 새 트랜잭션으로 재요청한다. 기존 행/값·구조·이력을 별도 연결에서 비교한다. 업무 예약 생성/취소의 장애·경쟁 보장과는 별개의 테스트 지원 계약이며 실행은 미검증이다. [003 실패 처리 계약](../../specs/003-define-db-schema/contracts/persistence-contract.md)에 근거와 검증 사례를 연결했다.
