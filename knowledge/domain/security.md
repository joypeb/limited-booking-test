# security 도메인 지식

## 1. 기준과 현재 상태

- 예정 패키지: `com.limitedbooking.backend.security`. 작성·확인일: 2026-10-13.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절, [API 규칙](../rules/api-and-security.md), [트랜잭션 규칙](../rules/domain-and-transactions.md), [요청 제한 규칙](../rules/rate-limit.md).
- 설계: 기존 확정 규칙. 구현·SQL·테스트: 미구현. 실행 검증: 미실행.

## 2. 목적·책임·경계

localhost 비브라우저 API의 인증·ADMIN 권한·소유권과 공통 본문/오류 필터 계약을 지원한다. 회원가입·비밀번호 재설정·외부 공개·브라우저 인증은 제외한다. UUID는 인증 결과에서만 전달하며 임의 X-User-Id를 신뢰하지 않는다.

## 3. 용어와 데이터

| 용어 | 의미 | 제약·원본 |
| --- | --- | --- |
| ADMIN / USER_A / USER_B | 로컬 고정 계정·UUID | 실제 값·계정 설정은 후속 구현, UUID를 요청에서 받지 않음 |
| 비밀번호 | Basic 자격 증명 | 환경 변수에서 주입·BCrypt 처리, 문서·응답·Git에 비밀값 없음 |
| Principal | 인증 사용자 | 예약 소유권 UUID의 유일한 출처 |
| 본문 | 실제 요청 바이트 | 모든 요청 최대 8192바이트 |

## 4. 불변식과 상태 전이

| 규칙 | 기대값 | 근거 |
| --- | --- | --- |
| security-001 | 타인 예약 조회·취소는 관리자도 404, 존재 여부 비노출 | 계획서 2·4 |
| security-002 | 크기 제한→인증→대상 예약 쓰기 제한→입력 검증→서비스 | 계획서 4 |
| security-003 | 세션 없음·비밀정보 비노출 | API 규칙 |

독립 업무 상태·상태 전이·멱등 키·업무 데이터 테이블은 없다. 인증은 요청마다 적용하고 예약 상태는 reservation이 관리한다.

## 5. 유스케이스와 API 계약

| 내부 인터페이스/API 경계 | 성공·거부 | 데이터 변경 |
| --- | --- | --- |
| AuthenticationEntryPoint | 미인증·잘못된 비밀번호 401 UNAUTHENTICATED, WWW-Authenticate Basic | 업무 무변경 |
| AccessDeniedHandler / 대상 생성 | ADMIN 허용, USER 403 FORBIDDEN | 거부 시 무변경 |
| 예약 조회·취소 소유권 | 소유자 허용, 타인/없음 404 NOT_FOUND | 거부 시 무변경 |
| 본문 제한 필터 | 초과 413 PAYLOAD_TOO_LARGE, 인증보다 먼저 | 업무 무변경 |

오류는 application/problem+json과 type/title/status/detail/instance/code, 생성은 application/json 전용이다. 중복 키·미정의 필드·잘못된 JSON/UUID·숫자 형 변환은 400. 취소 본문은 비어 있어야 한다.

## 6. 멱등성·트랜잭션·경쟁·장애

인증된 UUID로 소유권을 검사한다. 인증 재시도에는 독립 멱등 키가 없으며 예약 생성 키는 reservation 소관이다. 본문 제한은 Content-Length만 의존하지 않고 실제 읽기·chunked를 검사하고 상한 이하 본문을 후속 처리에서 재사용한다. 인증 뒤 예약 쓰기 제한에 진입한 잘못된 입력·재요청도 카운트된다.

CSRF 비활성화는 localhost CLI/API 클라이언트 전제이며 CORS 전체 허용은 추가하지 않는다. 외부 공개 시 TLS·인증·CSRF는 별도 설계다. 필터·인증 예외도 동일 오류 계약이고 SQL·스택·비밀번호를 노출하지 않는다. security가 DB 트랜잭션을 시작하지 않으며 DB/Redis 장애 분류는 관련 기능의 계약을 따른다.

## 7. 검증할 사례와 실제 증거

| 규칙 | 사례 | 기대 | 검증 상태 |
| --- | --- | --- | --- |
| security-001 | 타인 예약 USER/ADMIN 접근 | 404, 무변경 | 후속 실제 Authorization·필터 IT 미실행 |
| security-002 | 미인증+8193바이트 / 상한 이하 미인증 | 413 / 401과 인증 헤더 | 후속 HTTP 미실행 |
| security-003 | 임의 X-User-Id·비밀번호 오류 | UUID 신뢰 안 함·401 | 후속 HTTP 미실행 |

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
