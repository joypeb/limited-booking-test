# resource 도메인 지식

## 1. 기준과 현재 상태

- 도메인·예정 패키지: resource / `com.limitedbooking.backend.resource`.
- 작성·확인일: 2026-10-13.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2·4·5절, [도메인 규칙](../rules/domain-and-transactions.md), [API 규칙](../rules/api-and-security.md), [0단계 명세](../../specs/001-define-api-contract/spec.md).
- 설계 상태: 기존 정책과 사용자 A 답변으로 이름 길이 코드 포인트 기준 확정.
- 구현 상태: 대상 기능·SQL·테스트 미구현. 실행 검증 미실행.

## 2. 목적·책임·경계

ADMIN이 예약 대상·최초 수량을 생성하고 인증 사용자가 단건 조회한다. 수량 변경·삭제·목록·페이지네이션은 범위 밖이다. PostgreSQL이 원본이며 예약 생성·취소와 함께 수량을 변경한다. Redis는 대상 생성·조회의 의존성이 아니다.

## 3. 용어와 데이터

| 필드 | 의미·타입 | 제약·불변 여부 | 근거 |
| --- | --- | --- | --- |
| id | UUID 대상 식별자 | 불변·PK 설계 | 계획서 5.1 |
| name | 문자열 이름 | 앞뒤 공백 제거 후 코드 포인트 1~100개, 😀는 1자 | 계획서 2·사용자 A |
| capacity | 최초 수량 정수 | 0~1000, 불변 | 계획서 2 |
| available | 남은 수량 정수 | 최초 capacity, 0~capacity | 계획서 2·5 |
| created_at | 생성 UTC 시각 | timestamptz 설계, ResourceView에는 미포함 | 계획서 4·5 |

스키마는 설계 상태다. name varchar(100) NOT NULL, capacity·available 정수 NOT NULL과 범위 CHECK를 예정한다. 대상 삭제·수량 변경은 제공하지 않는다.

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

ResourceView는 id·name·capacity·available다. 본문 크기 제한·오류 상세는 API 규칙을 따른다. 최종 OpenAPI는 아직 없고 [작성 설계](../../specs/001-define-api-contract/contracts/api-contract.md)가 있다.

## 6. 멱등성·트랜잭션·경쟁·장애

대상 생성에는 멱등 키 계약이 없다. 예약 생성의 키·취소 정책은 대상 수량 변경에 영향을 준다. 조건부 available>0 감소, resource→reservation 잠금 순서, 실패 전체 롤백은 [도메인 규칙](../rules/domain-and-transactions.md)을 따른다. DB 장애·timeout은 503, Redis 장애가 정상 DB의 대상 생성·조회를 막지 않는다. 대상 생성 응답 유실에 별도 재현 보장을 추가하지 않는다.

## 7. 검증할 사례와 실제 증거

| 규칙 | 사례 | 기대 | 종류·상태 |
| --- | --- | --- | --- |
| resource-003 | 😀 100개·101개, 앞뒤 공백+100개 | 201·400·201, 저장 이름은 공백 제거 | 문서 기대값, Unit·HTTP 후속 미실행 |
| resource-001 | capacity 0·1000·-1·1001 | 경계 허용·범위 밖 400 | 후속 미실행 |
| resource-002 | 예약·재요청·취소·중복 취소 | 합계 capacity 유지 | 후속 DB·경쟁 미실행 |
| 권한 | USER 생성·미인증 조회 | 403·401 | 후속 HTTP 미실행 |

실제 실행 증거는 없다. 후속 문서 검토·테스트 증거는 프로젝트 루트 `docs/test-evidence.md`에 기록할 예정이다.

## 8. 구현 연결과 변경 영향

| 책임 | 파일·심볼 | 상태 |
| --- | --- | --- |
| 앱 골격 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java) | 존재, 대상 기능 없음 |
| 대상 규칙·DTO·Controller·Service·Repository | resource 패키지 예정 | 미구현 |
| SQL·Flyway·대상 테스트·최종 OpenAPI | 후속 단계 예정 | 미구현 |

이름 길이 단위의 명확화는 계획서·도메인 규칙·명세·계약 설계에 반영했다. 구현 이후에는 순수 규칙·DTO·OpenAPI·Unit·HTTP 기대값을 함께 검토한다. JDBC 등 선택 이유의 최종 기록은 `docs/decisions.md`에 작성 예정이다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 이름 길이 실행 검증 미실행 | 코드 포인트와 실제 거부 결과 | 규칙·HTTP 구현 단계에서 경계 시험 |
| OpenAPI·API·DB 기능 미구현 | 실제 계약 준수 확인 불가 | 먼저 0단계 계약 문서 작성 |

이번 0단계 계획을 막는 미확정 도메인 정책은 없다. 이 문서는 업무 규칙을 바꾸거나 코드 실행 완료를 주장하지 않는다.
