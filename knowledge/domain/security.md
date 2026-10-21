# security 도메인 지식

## 1. 기준과 현재 상태

- 예정·구현 패키지: `com.limitedbooking.backend.security` 및 공통 웹 경계 `com.limitedbooking.backend.common`. 작성·확인일: 2026-10-13, 4단계 구현·검증일: 2026-10-17.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절·7절 4단계, [API 규칙](../rules/api-and-security.md), [트랜잭션 규칙](../rules/domain-and-transactions.md), [요청 제한 규칙](../rules/rate-limit.md), [4단계 명세](../../specs/005-auth-resource-api/spec.md).
- 설계: 기존 확정 규칙. 구현·테스트: 4단계 로컬 Basic 인증·고정 UUID Principal(`SecurityConfig`·`LocalIdentityResolver`), 인증 앞단 8KiB 본문 상한 필터(`RequestBodyLimitFilter`), 공통 `application/problem+json` 오류 응답(`ApiExceptionHandler`), 취소 비어 있지 않은 본문/잘못된 UUID `400` 검증 구현 완료. 실행 검증: `SecurityIT`(6건)·`ResourceApiIT`(8건) 및 `./gradlew verify` 통과. 예약 소유권 조회/취소(`security-001`)는6단계 HTTP/DB 검증을 완료했고 Redis 쓰기 제한은8단계 후속이다.

## 2. 목적·책임·경계

localhost 비브라우저 API의 인증·ADMIN 권한·소유권과 공통 본문/오류 필터 계약을 지원한다. 회원가입·비밀번호 재설정·외부 공개·브라우저 인증은 제외한다. UUID는 인증 결과에서만 전달하며 임의 X-User-Id를 신뢰하지 않는다.

## 3. 용어와 데이터

| 용어 | 의미 | 제약·원본 |
| --- | --- | --- |
| ADMIN / USER_A / USER_B | 로컬 고정 계정·UUID | 고정 UUID(`ADMIN`: `00000000-0000-0000-0000-000000000001`, `USER_A`: `...0002`, `USER_B`: `...0003`) 할당 완료, UUID를 요청에서 받지 않음 |
| 비밀번호 | Basic 자격 증명 | 환경 변수(`ADMIN_PASSWORD`, `USER_A_PASSWORD`, `USER_B_PASSWORD`)에서 주입·BCrypt 보관, 테스트는 가짜 비밀번호 분리, 문서·응답·Git에 비밀값 없음 |
| Principal | 인증 사용자 | 예약 소유권 UUID의 유일한 출처 (`LocalIdentityResolver.userId(Authentication)`) |
| 본문 | 실제 요청 바이트 | 모든 요청 최대 8192바이트 (`RequestBodyLimitFilter`에서 인증 앞단 검사 및 재사용 래퍼 전달) |

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
| security-001 | 타인 예약 USER/ADMIN 접근 | 404, 무변경 | `ReservationOwnershipIT`·`ReservationCancelIT` 실행 검증 완료 |
| security-002 | 미인증+8193바이트(일반/chunked/길이 헤더 생략) / 상한 이하 미인증 / 8192바이트 재사용 | 413 / 401과 인증 헤더 / 본문 재사용 | `SecurityIT`·`ResourceApiIT` 실행 검증 완료 (Redis 제한은 8단계 후속) |
| security-003 | 임의 X-User-Id 무시·본문 `userId` 400·비밀번호 오류 401·무세션·민감정보 비노출 | UUID 신뢰 안 함·400·401·`JSESSIONID` 미생성 | `SecurityIT`·`ResourceApiIT` 실행 검증 완료 |

실제 검토·실행 기록은 [증거 문서](../../docs/test-evidence.md)에서 구분한다. 예약 소유권은6단계 구현·검증 완료, Redis 요청 제한은8단계 후속이다.

## 8. 구현 연결과 변경 영향

| 책임 | 실제 파일·예정 위치 | 상태 |
| --- | --- | --- |
| 앱 골격·시계 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java)·[ClockConfig](../../backend/src/main/java/com/limitedbooking/backend/common/ClockConfig.java) | 구현·실행 검증됨 |
| 로컬 인증·권한·고정 UUID 추출 | [SecurityConfig](../../backend/src/main/java/com/limitedbooking/backend/security/SecurityConfig.java)·[LocalIdentityResolver](../../backend/src/main/java/com/limitedbooking/backend/security/LocalIdentityResolver.java) | 구현·실행 검증됨 |
| 인증 앞단 8KiB 제한·공통 오류·엄격 JSON | [RequestBodyLimitFilter](../../backend/src/main/java/com/limitedbooking/backend/common/RequestBodyLimitFilter.java)·[ApiExceptionHandler](../../backend/src/main/java/com/limitedbooking/backend/common/ApiExceptionHandler.java) | 구현·실행 검증됨 |
| 실제 HTTP 보안·오류 통합 테스트 | [SecurityIT](../../backend/src/test/java/com/limitedbooking/backend/security/SecurityIT.java)·[ResourceApiIT](../../backend/src/test/java/com/limitedbooking/backend/resource/ResourceApiIT.java) | 구현·실행 검증됨 (`SecurityIT` 6건, `ResourceApiIT` 8건) |
| 고정 계약·결정 기록 | [OpenAPI 초안](../../docs/openapi.yaml)·[결정 기록](../../docs/decisions.md) | 4단계 로컬 인증·예약 대상 API 실제 HTTP 검증 완료; 5단계 생성 인증 연동 완료; 소유권6단계 검증 완료; 제한8단계 후속 |

계약·업무 변경은 계획서·관련 도메인·공통 규칙·OpenAPI·테스트를 함께 검토한다. 서비스 경계·키·수량·권한·실패 무변경은 관련 도메인에 함께 영향을 준다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 예약 소유권 조회·취소 (`security-001`) | 타인/관리자/없는 예약404와 거부 시 상태 불변 검증 완료 | 후속 경쟁·요청 제한 검증 |
| Redis 예약 쓰기 제한 필터 미구현 | 인증 뒤 요청 제한 단계 미검증 | 8단계 Redis 고정 창 요청 제한 구현 시 보안 필터 체인 뒤에 연결 |

기존 규칙 이외의 새 정책을 추가하지 않았다.


### 2026-10-19 예약 생성 연동

5단계 ReservationController는 LocalIdentityResolver에서 인증 UUID를 얻는다. ReservationCreateIT에서3계정 생성·X-User-Id 무시·미정의 userId/quantity400·엄격JSON·401/WWW-Authenticate·8192/8193와 길이 없는chunked·입력/인증 우선순위를 확인했다. ApiExceptionHandler는 정확한55P03·실제 연결 획득 실패를503으로 추가 연결하며 다른 SQL/트랜잭션 내부결함500을 유지한다. 소유자 GET/취소·Redis는6~8단계 후속이다. 실행 근거는 [증거](../../docs/test-evidence.md)다.


### 2026-10-20 조회·취소 소유권 검증

`ReservationOwnershipIT`5건은 실제 Authorization·보안 필터·소유자 SQL을 통해 본인200, 타인/ADMIN/없는 예약404, 미인증401/WWW-Authenticate, 잘못된 UUID400, 인증 앞 취소8193바이트413과 상태·수량 무변경을 확인한다. `ReservationCancelIT`는8192 이하 비어 있지 않은 취소 본문400을 검사한다. Controller 전용 UUID 바인더는 축약형도400으로 거부한다. 고정 계약은 유지하며 [실행 증거](../../docs/test-evidence.md)를 따른다.


### PR #6 인코딩 취소 경로 검증 보완 (2026-10-20)

원시 URI 접두사·접미사로 분기하는 취소 검증 필터는 MVC의 경로 디코딩과 차이가 있다. 신규 취소 Controller에서 매칭된 모든 경로의 캐시 본문0바이트를 다시 검사해 인코딩 경로도400/무변경을 보장한다. 인증·8KiB 상한 필터의 순서는 유지한다. ReservationCancelIT의 두 인코딩 경로 회귀가 실제 보안 필터와 HTTP·DB를 통과한다.


### 7단계 경쟁 검증 연결 (2026-10-20)

C01~C11 각10회는 실제 Authorization·Basic/BCrypt·보안 필터·Controller·Service를 통과한다. 내부 서비스 시험으로 대체하지 않았다. 타인/ADMIN404·취소후생성재현은 기존 소유권/취소회귀로 유지한다. 테스트 종료 관측은 Service 진입/퇴장과 HTTP작업/클라이언트 종료를 함께 확인한다. 후속 Redis 제한 도입 후에도 전체HTTP경로를 유지하고 경쟁전용10000건/60초·키초기화를 적용해야 한다. [경쟁IT](../../backend/src/test/java/com/limitedbooking/backend/reservation/ReservationConcurrencyIT.java)·[실행증거](../../docs/test-evidence.md)·[회차기록](../../docs/concurrency-runs.json)을 따른다. 앞 절의 후속 표시는 각 과거 확인일의 상태다.


### 2026-10-21 최종 리뷰 보완 확인

최종 일반 verify Unit150·IT257(경쟁110회+지원fixture5 포함), 실패/오류/skip0. 종료/전송실패 차단·모든 오류/결과 보존·호출별 고유ID와 C07 경로기록을 보완했다. 제품·API·스키마는 유지한다. [최종 실행 증거](../../docs/test-evidence.md)·[110회 기록](../../docs/concurrency-runs.json)을 따른다. 이전 날짜의 실행 수는 당시 기록이다. Redis·심화·부하·외부CI는 후속이다.
