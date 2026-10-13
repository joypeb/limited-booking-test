# 조사와 결정: 0단계 계약 확정

2026-10-13 확인. 프로젝트 정책의 근거는 [명세](spec.md), 계획서 2~5절, 지식 규칙이며 공식 사양 확인은 도구 실행 검증과 다르다. 이번 범위의 미해결 정책은 없다. validator·컨테이너·부하 도구의 버전 선정은 1단계 이후로 범위가 명시돼 있다.

## 1. 범위와 계약 원본

- **결정**: 0단계는 계약·사례·결정 기록을 작성한다. 최종 고정 계약은 프로젝트 루트 `docs/openapi.yaml`이다. 이번 `contracts/api-contract.md`는 그 작성 설계다.
- **이유**: 아직 서버·DB 구현 없이 기대값을 정해야 하며 두 OpenAPI 사본의 충돌을 피한다.
- **대안**: 자동 생성 명세만 사용하거나 전체 백엔드를 먼저 구현하는 방법은 고정 기대값과 단계별 범위를 충족하지 못한다.

## 2. 이름 길이

- **결정**: 사용자 A 답변에 따라 Java `String.strip()`으로 앞뒤 공백 제거 후 코드 포인트 1~100개로 계산한다. `😀` 100개는 허용하고 101개는 거부한다.
- **이유**: UTF-16 저장 단위나 화면의 글자 묶음 수와 구분한다. 원문에 공백이 더해져도 정규화 후 100자면 허용돼야 한다.
- **대안**: UTF-16 길이·화면 글자 수·요청 원문 100자 상한은 선택한 정책과 다르다.
- **설계 영향**: 요청 name에 원문 `maxLength: 100`을 단순 적용하지 않는다. 전처리 후 길이를 description과 사례로 고정하고 응답 name에는 1~100 길이를 선언한다. 스키마는 문자열 전처리를 수행하지 않는다. [JSON Schema 길이](https://datatracker.ietf.org/doc/html/draft-wright-json-schema-validation-00#section-5.6)

- **추가 확정 (2026-10-13)**: Java 25 `String.strip()`의 `Character.isWhitespace(int)` 기준으로 이름 앞뒤 공백을 제거한다. 일반 공백·탭·줄바꿈은 제거하고 내부 공백은 유지한다. U+00A0(NBSP)·U+2007·U+202F는 제거하지 않고 코드 포인트 길이에 포함한다. 제거 후 길이 1~100이며 빈 문자열은 400이다. Java의 표준 기준으로 범위를 고정하며 trim이나 별도의 Unicode White_Space 필터로 대체하지 않는다. [String.strip 공식 문서](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/String.html#strip()), [Character.isWhitespace 공식 문서](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#isWhitespace(int)). 공식 동작 확인과 이 기기의 실행 검증은 구분한다.

## 3. null과 미정의 필드

- **결정**: cancelledAt는 같은 스키마에 string·date-time·nullable true를 선언하고 응답 필수 필드 목록에도 넣는다. 요청 객체는 additionalProperties false다.
- **이유**: null 허용과 필드 존재 의무는 별개이며 추가 필드는 기본적으로 허용된다.
- **대안**: 3.0.3에서 type null·타입 배열을 사용하거나 미정의 필드 거부를 설명만으로 대체하지 않는다. [OpenAPI 3.0.3 스키마](https://spec.openapis.org/oas/v3.0.3#schema-object)

## 4. 선언과 실행 검증의 경계

- **결정**: 중복 JSON 키, 실제 본문 바이트·chunked, UUID 거부, 인증·제한 순서, 취소 상태와 시각, 성공 키 재현은 문서 설명·사례와 후속 실제 요청 시험으로 나눈다.
- **이유**: 파서가 중복을 지운 뒤에는 객체 스키마로 원문 중복을 알 수 없다. 형식 지원도 검증기마다 다를 수 있다. 바이트 상한·상태 전이·소유권을 스키마 선언만으로 실행 증명할 수 없다는 설계 판단이다.
- **대안**: 문자열 길이로 본문 바이트 상한을 대신하거나 계약 validator 통과만으로 모든 정책을 검증했다고 표시하지 않는다. [RFC 8259 객체](https://www.rfc-editor.org/rfc/rfc8259#section-4), [OAS 데이터 타입](https://spec.openapis.org/oas/v3.0.3#data-types), [OAS 요청 본문](https://spec.openapis.org/oas/v3.0.3#request-body-object)

## 5. JDBC 선택의 결정 기록 설계

- **결정**: 기본 SQL 접근은 NamedParameterJdbcTemplate이며 JDBC 선택의 이유·대안을 최종 결정 기록에 3~5줄로 적는다.
- **이유**: 계획서 3·5절의 조건부 UPDATE·제약·잠금·롤백 경계를 직접 읽고 검증하는 학습 목적이다.
- **대안**: JPA/ORM, JdbcClient가 있으나 이번 단계는 기존 선택을 재선정하지 않는다. 기존 Gradle·Java·패키지도 유지한다.

## 6. 성공한 예약에 키 보관

- **결정**: 성공한 예약 행으로 사용자·키를 계속 보존한다. 실패 키는 보존하지 않고 취소 뒤에도 성공 키는 유지한다.
- **이유**: 불변의 예약 ID·대상·생성 시각과 고정 CONFIRMED로 최초 응답을 재구성할 수 있다.
- **대안**: 별도 키 테이블·TTL 캐시는 실패 응답 보관이나 만료 정책을 더 필요로 하므로 첫 범위에 추가하지 않는다.

## 7. Redis 요청 진입 제한

- **결정**: Redis는 인증된 예약 생성·취소의 고정 창 제한만 맡고 장애 시 해당 쓰기를 503으로 거부한다.
- **이유**: 수량 원본은 PostgreSQL이며 제한 거부 뒤에는 업무 트랜잭션을 시작하지 않아야 한다.
- **대안**: 메모리 카운터·DB 카운터·Redis 분산 락 또는 수량 저장은 기존 책임과 다르다. 이번 작업은 도구 비교·구현을 추가하지 않는다.

## 조사·검증 상태

OpenAPI 관련 조사만 별도 조사 에이전트에 위임해 공식 사양을 확인했다. 위 선택은 설계 상태다. API·DB·Redis·validator 실행 호환성과 인간의 설명 능력은 검증하지 않았다. [검토 안내](quickstart.md)의 문서 검사 후 실제 0단계 작업 증거는 `docs/test-evidence.md`에 기록한다.
