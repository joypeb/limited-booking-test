# Feature Specification: 고정 OpenAPI와 실제 업무 API 계약 검증

**Feature Branch**: `feature/010-openapi-contract`

**Created**: 2026-10-23

**Status**: Draft — 명세 품질 검토 완료, 구현·실행 미완료

**Input**: 사용자 요청: 계획서 9단계의 고정 OpenAPI 확정, 실제 요청·응답 검증, 고의 훼손·DTO 변경 탐지와 verifyContract 인수. 이번 작성 범위는 specify뿐이다.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 문서대로 정상 API를 사용하는 개발자 (Priority: P1)

API 사용자는 고정된 계약을 보고 대상 생성·조회, 예약 생성·조회·취소의 입력과 응답을 예측할 수 있다. 개발자는 실제 실행 결과가 이 계약을 지키는지 확인한다.

**Why this priority**: 실행 코드에서 자동 생성한 문서만 확인하면 코드와 기대값이 함께 잘못 바뀌어도 계약 이탈을 발견하지 못한다.

**Independent Test**: 격리된 실제 의존성과 인증을 사용하는 HTTP로 다섯 작업을 호출하고, 전송한 요청과 수신한 응답 전체를 고정 계약과 대조한다.

**Acceptance Scenarios**:

1. **Given** ADMIN과 유효한 이름·수량, **When** 대상을 생성하고 인증 사용자로 조회, **Then** 각각 201·200과 id/name/capacity/available, 생성 Location이 계약에 맞는다. 수량 0과 정규화 전 앞뒤 공백이 있는 유효 이름도 허용된다.
2. **Given** 예약 가능한 대상과 유효한 생성 키, **When** 예약 생성 후 소유자가 조회·취소, **Then** 201·200·200, 생성 응답 4필드와 현재 응답 5필드, UTC 시각·상태·cancelledAt 관계가 계약에 맞는다. 최초 생성의 Replay는 false다.
3. **Given** 같은 정상 교환, **When** 헤더 이름의 대소문자나 JSON 필드 순서가 달라짐, **Then** 의미가 같으면 통과하며 실제 원문·필드·헤더를 누락하거나 바꿔 검증하지 않는다.

---

### User Story 2 - 실패 결과도 신뢰하는 API 사용자 (Priority: P1)

사용자는 입력·권한·품절·크기·제한·장애에 따라 문서에 정한 오류와 재시도 정보를 받는다. 개발자는 잘못된 요청이 거부되었다는 사실과 오류 응답 자체의 정확성을 각각 확인한다.

**Why this priority**: 정상 응답만 검사하면 필터의 오류 형식이나 실제 장애·권한 응답의 계약 이탈을 놓친다.

**Independent Test**: 아래 적용표의 모든 작업·상태 조합과 두 409 code를 실제 HTTP로 만들고 요청 위반의 종류 및 응답 상태·본문·헤더를 대조한다.

**Acceptance Scenarios**:

1. **Given** 잘못된 JSON·UUID·생성 키, 미정의/중복 필드, 문자열/소수 capacity 또는 비어 있지 않은 취소 본문, **When** 인증·제한을 통과하여 요청, **Then** 400 INVALID_REQUEST이며 오류 응답 계약을 만족한다. 원래 무효인 요청을 정상으로 간주하거나 검증 전체를 생략하지 않는다.
2. **Given** 미인증·권한 부족·없는 대상·타인 예약, **When** 해당 API를 호출, **Then** 401·403·404와 지정 code를 반환한다. 401은 Basic WWW-Authenticate를 포함하며 타인 예약은 ADMIN에게도 404다.
3. **Given** 품절 또는 성공한 같은 사용자·키의 다른 대상, **When** 생성 요청, **Then** 각각 409 SOLD_OUT·IDEMPOTENCY_CONFLICT다.
4. **Given** 8192바이트 초과 본문 또는 생성 API의 미지원 Content-Type, **When** 요청, **Then** 각각 413 PAYLOAD_TOO_LARGE·415 UNSUPPORTED_MEDIA_TYPE다. 실제 바이트·chunked 상한과 인증 전 413을 유지한다.
5. **Given** 기본 사용자 쓰기 한도 소진 또는 실제 DB/Redis 의존성 장애, **When** 영향받는 요청, **Then** 각각 429 RATE_LIMITED·503 DEPENDENCY_UNAVAILABLE다. 429 Retry-After는 판정 시 PTTL의 올림·최소 1초이며 Redis 장애만으로 조회/대상 생성에 503을 만들지 않는다.
6. **Given** 모든 위 오류, **When** 응답 검사, **Then** application/problem+json, type/title/status/detail/instance/code와 HTTP status·code 대응이 맞고 민감정보가 노출되지 않는다. 예상 밖 500은 default 선언만으로 통과하지 않는다.

---

### User Story 3 - 재전송과 현재 상태를 구분하는 사용자 (Priority: P1)

사용자는 취소 이후에도 생성 재전송에서 최초 성공을 확인하고 조회에서는 현재 취소 상태를 확인한다.

**Why this priority**: 두 응답은 서로 다른 계약이다. 현재 상태를 생성 결과로 재사용하는 회귀를 막는다.

**Independent Test**: 한 사용자의 생성→같은 키 재전송→취소→중복 취소→생성 재전송→GET을 실제 HTTP로 실행하여 응답과 최종 수량을 비교한다.

**Acceptance Scenarios**:

1. **Given** 최초 201 응답, **When** 같은 사용자·키·대상으로 재전송, **Then** 201·Location·JSON 필드 값은 최초와 같고 Replay만 true다. 필드 순서·공백의 바이트 동일성은 요구하지 않는다.
2. **Given** 취소된 예약, **When** 같은 생성 요청 재전송과 소유자 GET, **Then** 생성은 최초 CONFIRMED·createdAt·id/resourceId를 재현하고 cancelledAt 필드를 포함하지 않는다. GET은 CANCELLED와 원래 createdAt·저장된 cancelledAt을 포함한다. 중복 취소는 같은 취소 시각이며 새 예약·추가 차감·추가 복구가 없다.

---

### User Story 4 - 계약 이탈을 놓치지 않는 개발자 (Priority: P2)

개발자는 검사기가 실제 잘못된 응답을 거부하고, 실행 코드의 응답 타입 변경도 검사 실패로 드러난다는 증거를 얻는다.

**Why this priority**: 정상 사례 통과만으로 검사기의 탐지력을 입증할 수 없다.

**Independent Test**: 정상 교환을 기준으로 한 번에 하나씩 응답을 훼손해 거부를 확인하고, 별도로 실제 DTO의 직렬화 결과를 바꾸는 컴파일 가능한 타입 변경을 적용해 실제 HTTP 계약 시험의 실패를 확인한 뒤 원복한다.

**Acceptance Scenarios**:

1. **Given** 정상 응답, **When** 필수 id 제거, UUID/시각/타입 오류, 비UTC 시각, 미정의 status·필드, 필수 헤더 누락/오류, HTTP status와 오류 code 불일치 또는 예상 밖 응답 상태로 각각 변경, **Then** 각 훼손이 관련 계약 위반으로 거부된다.
2. **Given** 기존 정상 응답 DTO, **When** 실제 JSON 숫자를 문자열로 바꾸는 등 계약 이탈을 일으키는 컴파일 가능한 타입 변경, **Then** 실제 HTTP 계약 시험이 스키마 위반으로 실패한다. 컴파일 오류나 응답 사본 수정만으로 대체하지 않는다. 원복 뒤 같은 시험이 통과한다.
3. **Given** 업무 계약 시험이 연결된 검증 명령, **When** 정상 상태로 실행, **Then** 업무 시험이 실제 발견되고 실행 수가 0이 아니며 실패·오류·skip 없이 끝난다. 도구 프로브만의 통과로 완료하지 않는다.

### Edge Cases

- 무효 요청에는 요청 스키마 위반이 예상될 수 있지만 정상 요청의 위반을 허용하지 않는다. 예상한 요청 위반과 예상하지 않은 위반·항상 유효해야 하는 오류 응답을 구분하고, 모든 요청 오류를 일괄 무시하지 않는다. 인증·크기 제한이 입력 검증보다 먼저 거부하는 경우도 별도로 판정한다.
- 일반 date-time 형식만으로 UTC를 보장하지 않는다. 계약의 UTC 의미와 저장 마이크로초 정밀도, 최초·재요청 필드 값의 동일성을 확인한다.
- 응답 헤더의 문서상 존재만으로 필수성이 자동 검증된다고 가정하지 않는다. Location은 반환 id에 대응하는 단건 경로, Replay는 최초 false/재전송 true, WWW-Authenticate는 Basic, Retry-After는 양의 정수 초다.
- 8192바이트 이하는 후속 검증 대상이며 8193바이트 이상은 413이다. GET의 초과 본문도 전역 상한의 적용 대상이다.
- status=CONFIRMED이면 cancelledAt이 명시적 null, CANCELLED이면 UTC 값이어야 한다. 생성 응답에는 cancelledAt이 없다.
- 크기·중복 JSON 키·엄격한 숫자 토큰·이름 strip·처리 순서·소유권·수량 보존은 스키마만으로 증명하지 않는다. 실제 HTTP 기대 결과와 기존 도메인 검사를 함께 사용한다.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 고정 docs/openapi.yaml을 독립 기대값으로 확정하고 다섯 업무 작업의 입력·권한·응답·헤더·오류를 계획서 2~5절에 맞춰야 한다. 자동 생성 명세만으로 대체하거나 실행 코드에 맞춰 규칙을 완화하지 않아야 한다. (US1~4)
- **FR-002**: 실제 HTTP의 method, 실제 path, 요청 헤더·본문 원문, 응답 status·헤더·본문 원문을 검증 경계에 모두 전달해야 한다. 정상 교환은 요청과 응답 모두 적합해야 하며 헤더 이름은 대소문자를 구분하지 않는다. (US1)
- **FR-003**: 의도적으로 무효인 요청은 예상 위반·거부 결과를 명시적으로 검증하고, 응답은 해당 오류 계약을 완전히 검증해야 한다. 정상 요청의 위반이나 무관한 요청 위반을 함께 무시해서는 안 된다. (US2, Edge Cases)
- **FR-004**: 아래 적용표의 정상·실패 조합을 각 1회 이상 실제 HTTP로 검증해야 한다. 409의 두 code, DB 장애의 503과 예약 쓰기 Redis 장애의 503을 구분해야 한다. 발생하지 않는 오류를 새로 추가하지 않아야 한다. (US1, US2)
- **FR-005**: 요청·응답의 UUID, 필드 타입, UTC 날짜·시간, enum, 필수·미정의 필드와 상태별 cancelledAt 관계를 검증해야 한다. 유효한 이름 정규화 입력과 capacity=0을 계약 검증기가 잘못 거부하지 않아야 한다. (US1, Edge Cases)
- **FR-006**: 성공 application/json·오류 application/problem+json, 필수 Location·Idempotent-Replayed·WWW-Authenticate·Retry-After의 존재·형식·해당 교환 의미를 검증해야 한다. 임의의 전송 헤더 추가를 미정의 JSON 필드와 혼동하지 않아야 한다. (US1~3)
- **FR-007**: 오류 표준 6필드와 HTTP 상태·code·instance의 대응, 민감정보 비노출 및 필터 발생 오류를 검사해야 한다. 예상 밖 상태나 미분류 내부 결함은 default 응답 선언으로 성공 처리하지 않아야 한다. (US2)
- **FR-008**: 최초·재전송·취소·중복 취소·취소 후 재전송·현재 GET을 연결하여 생성 응답의 불변 값과 현재 취소 상태를 구분하고 추가 예약·수량 변경 없음을 확인해야 한다. (US3)
- **FR-009**: US4의 응답 훼손 종류별로 독립적인 거부 사례를 두고 정상 대조군도 유지해야 한다. 실패가 해당 훼손 때문임을 확인해야 한다. (US4)
- **FR-010**: 실제 DTO 타입의 컴파일 가능한 고의 변경으로 실제 HTTP 계약 시험의 실패를 확인하고 원복 후 통과를 기록해야 한다. 고의 변경을 제품에 남기지 않아야 한다. (US4)
- **FR-011**: 격리된 실제 PostgreSQL·Redis, 실제 Authorization·보안 필터 경로로 검증하고 기존 지원 코드·기본 20회/60초 정책·8KiB 우선순위를 보존해야 한다. 로컬 개발 DB·무관한 Redis 키를 초기화하지 않아야 한다. (US1~4)
- **FR-012**: 업무 계약 시험을 포함한 verifyContract의 실행 수·종료 코드·실패/오류/skip, 도구·의존성 버전, 고의 변경 실패와 원복, 미검증 범위를 기존 docs 기록 체계에 남겨야 한다. 0건·프로브만 실행·예전 결과를 완료로 보지 않아야 한다. (US4)

### 작업별 계약 적용표

| 작업 | 정상 | 실제 발생 가능한 실패 검증 대상 |
| --- | --- | --- |
| POST /api/resources | 201 | 400, 401, 403, 413, 415, 503(DB) |
| GET /api/resources/{id} | 200 | 400, 401, 404, 413(전역 본문 상한), 503(DB) |
| POST /api/reservations | 201 | 400, 401, 404, 409(두 code), 413, 415, 429, 503(DB·Redis) |
| GET /api/reservations/{id} | 200 | 400, 401, 404, 413(전역 본문 상한), 503(DB) |
| POST /api/reservations/{id}/cancel | 200 | 400, 401, 404, 413, 429, 503(DB·Redis) |

### 지정 제약과 제외 범위

- OpenAPI 3.0.3, 제공 인터페이스 `ApiContractValidator.assertValid(String method, String path, Map<String,String> requestHeaders, String requestBody, int status, Map<String,String> responseHeaders, String responseBody): void`, `ApiContractIT`·contract 태그·`./gradlew verifyContract`는 계획서의 지정 제약이다. 아직 신규 업무 검증기로 구현·검증된 것은 아니다. 무효 요청 판정의 세부 설계는 plan에서 정하되 FR-003을 충족해야 한다.
- 기존 Java25·Gradle·패키지·스키마·업무 규칙을 유지한다. 검사기 내부 구성·라이브러리 신규 선택·시험 배치는 이번 명세에서 정하지 않는다.
- 이번 개발 범위는 9단계다. 10단계 Property/PIT 확장, 부하·외부 CI·배포·소비자 주도 계약 도입은 제외한다. DTO 고의 변경 탐지는 9단계 완료 기준이다.
- 최신 사용자 지시 “Qodana는 추후 내가 직접할거야. 그건 스킵한다”에 따라 이번 9단계 Qodana는 사용자 후속이며 완료·PR·병합 게이트에서 제외한다. 미실행을 통과로 기록하지 않는다. 8단계 유예의 자동 승계로 판단한 것이 아니다.
- 이번 산출물은 spec.md·별도 requirements 체크리스트·feature 선택 정보다. 구현·plan·tasks·커밋·push·PR 및 브랜치 변경은 하지 않는다. 최신 Spec Kit 지시를 과거 superpowers 안내보다 우선한다.

### Key Entities *(include if feature involves data)*

- **고정 업무 계약**: 다섯 작업의 요청·응답·오류·헤더와 상호 관계를 정의하는 독립 기대값.
- **실제 HTTP 교환**: 요청 메서드·경로·헤더·원문과 응답 상태·헤더·원문의 한 쌍.
- **계약 판정**: 정상 교환의 적합성 또는 의도한 무효 요청/훼손 응답의 예상 위반과 예상하지 않은 위반의 구분.
- **최초 생성 결과와 현재 예약**: 같은 예약 식별자를 공유하지만 최초 CONFIRMED와 현재 CANCELLED를 각각 표현하는 결과. 새 저장 엔티티를 추가하지 않는다.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 다섯 작업의 정상 결과 및 적용표의 모든 오류 조합을 각각 1회 이상 관측하고 상태·code·필드·헤더 불일치는 0건이다.
- **SC-002**: 명시한 정상 교환의 오거부, 무효 요청의 예상 위반 누락, 잘못된 오류 응답의 통과는 각각 0건이다.
- **SC-003**: US4에 열거한 각 독립 훼손이 100% 거부되고 정상 대조군은 100% 통과한다. 실제 응답 타입 고의 변경은 관련 시험 실패, 원복은 같은 시험 통과로 확인한다.
- **SC-004**: 취소 후 재전송은 최초 생성 필드 값·Location을 100% 재현하고 현재 조회는 취소 상태를 반환한다. 추가 예약·차감·복구는 0건이다.
- **SC-005**: 업무 계약 검증의 발견 수가 0보다 크고 예정한 검증 범위 누락·실패·오류·skip 및 미원복 고의 변경이 0건이며 결과를 재현할 명령과 관측 기록이 남는다.

## Assumptions

- 근거는 [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절·7절9단계, [API와 보안](../../knowledge/rules/api-and-security.md)·[개발과 검증](../../knowledge/rules/development-and-testing.md) 규칙 및 resource/reservation/security/ratelimit 도메인 문서다. 새 업무 도메인을 추가하지 않는다.
- 코드 대조(2026-10-23): [기존 검증기](../../backend/src/test/java/com/limitedbooking/backend/support/ToolchainContractValidator.java)는 본문 없는 GET 프로브 전용이며, [기존 계약 IT](../../backend/src/test/java/com/limitedbooking/backend/common/ToolchainContractIT.java)는 프로브 훼손 탐지와 업무 명세 파싱·참조 해석을 검사한다. 이것을 업무 전체 HTTP 검증 완료로 보지 않는다.
- [Gradle](../../backend/build.gradle)은 docs/openapi.yaml을 business.openapi.yaml로 테스트 리소스에 복사하고 contract 태그·의존성 재유입 검사·실행 수/XML 게이트·verifyContract를 이미 제공한다. 기존 검사 라이브러리와 리소스 연결을 재사용 가능하나 본문 전달·UTC·필수 헤더·예상 밖 상태·무효 요청 판정은 추가 검증 대상이다.
- ResourceView·CreatedReservationView·ReservationView, 기존 실제 HTTP/컨테이너·본문 크기·제한 키·DB 상태 검사 지원 및 장애 시험을 재사용한다. 새 업무 API·정책·스키마로 확장하지 않는다.
- 계획서 4.1의 GET “주요 실패” 표에는 413이 없으나 같은 절의 모든 본문 상한 규칙과 고정 OpenAPI에는 GET 413이 명시되어 있다. 전역 상한의 적용으로 해석해 적용표에 포함했으며 새 오류 정책이 아니다.
- OpenAPI의 date-time·선언된 응답 헤더·default만으로 UTC·필수 헤더·정확한 기대 상태를 보장한다고 가정하지 않는다. 스키마 외 의미 검증이 필요한 기존 계약이며 plan에서 보완 수단을 정한다.
- 이름 strip·중복 키·숫자 토큰·실제 바이트 상한 등 표현 한계는 실제 요청의 거부 결과로 보완한다. 오류 title/detail의 예시 문구를 새로운 바이트 일치 계약으로 만들지 않는다.
- 명세·체크리스트 작성은 실행 검증이 아니다. Docker/인증 등 메인 사전 확인과 이번 문서 검토를 구분하며, verifyContract·업무 HTTP·고의 변경은 이번 specify에서 실행하지 않았다. 미확정 업무 정책과 필수 추가 질문은 없다.
