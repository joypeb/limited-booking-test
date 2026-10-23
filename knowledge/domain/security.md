# security 도메인 지식

## 1. 기준과 현재 상태

- 예정·구현 패키지: `com.limitedbooking.backend.security` 및 공통 웹 경계 `com.limitedbooking.backend.common`. 작성·확인일: 2026-10-13, 4단계 구현·검증일: 2026-10-17.
- 근거: [계획서](<../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절·7절 4단계, [API 규칙](../rules/api-and-security.md), [트랜잭션 규칙](../rules/domain-and-transactions.md), [요청 제한 규칙](../rules/rate-limit.md), [4단계 명세](../../specs/005-auth-resource-api/spec.md).
- 설계: 기존 확정 규칙. 구현·테스트: 4단계 로컬 Basic 인증·고정 UUID Principal(`SecurityConfig`·`LocalIdentityResolver`), 인증 앞단 8KiB 본문 상한 필터(`RequestBodyLimitFilter`), 공통 `application/problem+json` 오류 응답(`ApiExceptionHandler`), 취소 비어 있지 않은 본문/잘못된 UUID `400` 검증 구현 완료. 실행 검증: `SecurityIT`(6건)·`ResourceApiIT`(8건) 및 `./gradlew verify` 통과. 예약 소유권 조회/취소(`security-001`)는6단계 HTTP/DB 검증을 완료했고 Redis 쓰기 제한도8단계 구현·검증을 완료했다.


**9단계 계약 검증 상태(2026-10-23):** 010 T001~T042 구현·실행 인수 완료(PR #12 리뷰 보완 포함). T042 `./gradlew build verifyContract verifyContractDependencies` 종료0, Unit298·일반IT315·Contract102(업무96+프로브6), 실패/오류/skip0. T032 당시 Unit294·IT312·Contract96은 historical 값이다. 제품 변경은 PR #12 리뷰 보완의 `ResourceController` 엄격 UUID binder 1건이며 SQL·Gradle·OpenAPI·정책 변경은 없다. 고정 OpenAPI와 실제 HTTP의 정상 5작업·적용표 전체 오류·응답 사본 훼손44종·실제 DTO 타입 변이 RED→원복 GREEN을 확인했다. Qodana는 사용자 지시로 건너뛴 미실행 후속이다. [010 작업](../../specs/010-openapi-contract/tasks.md)·[실행 근거](../../docs/test-evidence.md)를 따른다. 아래 8단계 상태와 날짜별 이력은 해당 시점 기록이다.

**8단계 검증 상태(2026-10-22):** 2026-10-22 8단계 구현·실행 인수 완료: `./gradlew build verify verifyContractDependencies` 종료0, 일반 Unit193·IT312, 실패/오류/skip0. IT에는 기본 Redis5·HTTP제한37·실제장애12·경쟁110회와 종료지원fixture5가 포함된다. T001~T036/T038 37개 완료, Qodana T037만 사용자 “나중에 할거야” 지시에 따른 미실행 후속이며 이번 완료·PR·병합 게이트에서 제외한다. 이 요약은 구현·실행 인수 시점의 기록이다. 이후 PR·독립 리뷰·병합 결과는 해당 PR과 최종 보고에서 확인한다. [실행 근거](../../docs/test-evidence.md)·[009 작업](../../specs/009-redis-rate-limit/tasks.md)을 따른다. 아래 날짜별 이력은 해당 시점 기록이다.

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

실제 검토·실행 기록은 [증거 문서](../../docs/test-evidence.md)에서 구분한다. 예약 소유권은6단계 구현·검증 완료, Redis 요청 제한도8단계 검증 완료다.

## 8. 구현 연결과 변경 영향

| 책임 | 실제 파일·예정 위치 | 상태 |
| --- | --- | --- |
| 앱 골격·시계 | [BackendApplication](../../backend/src/main/java/com/limitedbooking/backend/BackendApplication.java)·[ClockConfig](../../backend/src/main/java/com/limitedbooking/backend/common/ClockConfig.java) | 구현·실행 검증됨 |
| 로컬 인증·권한·고정 UUID 추출 | [SecurityConfig](../../backend/src/main/java/com/limitedbooking/backend/security/SecurityConfig.java)·[LocalIdentityResolver](../../backend/src/main/java/com/limitedbooking/backend/security/LocalIdentityResolver.java) | 구현·실행 검증됨 |
| 인증 앞단 8KiB 제한·공통 오류·엄격 JSON | [RequestBodyLimitFilter](../../backend/src/main/java/com/limitedbooking/backend/common/RequestBodyLimitFilter.java)·[ApiExceptionHandler](../../backend/src/main/java/com/limitedbooking/backend/common/ApiExceptionHandler.java) | 구현·실행 검증됨 |
| 실제 HTTP 보안·오류 통합 테스트 | [SecurityIT](../../backend/src/test/java/com/limitedbooking/backend/security/SecurityIT.java)·[ResourceApiIT](../../backend/src/test/java/com/limitedbooking/backend/resource/ResourceApiIT.java) | 구현·실행 검증됨 (`SecurityIT` 7건, `ResourceApiIT` 11건, T042) |
| 고정 계약·결정 기록 | [고정 OpenAPI](../../docs/openapi.yaml)·[결정 기록](../../docs/decisions.md) | 4단계 로컬 인증·예약 대상 API 실제 HTTP 검증 완료; 5단계 생성 인증 연동 완료; 소유권6단계 검증 완료; 제한8단계 검증 완료; 9단계 401/403/404·413/415·Problem 자동 계약 대조 완료 |

계약·업무 변경은 계획서·관련 도메인·공통 규칙·OpenAPI·테스트를 함께 검토한다. 서비스 경계·키·수량·권한·실패 무변경은 관련 도메인에 함께 영향을 준다.

## 9. 미확정·미검증 사항과 다음 작업

| 항목 | 영향 | 다음 작업 |
| --- | --- | --- |
| 예약 소유권 조회·취소 (`security-001`) | 타인/관리자/없는 예약404와 거부 시 상태 불변 검증 완료 | 경쟁·요청 제한 검증 완료; 최종 독립검토 |
| Redis 예약 쓰기 제한 필터 | 인증/인가 뒤·취소검증 앞 연결과 실제 장애 검증 완료 | 오케스트레이터 최종검토 |

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

### Qodana null 계약과 설정 설명 (2026-10-20)

LocalUserPrincipal과 필터 doFilterInternal은 상위 Spring JSpecify null 불허 계약을 좁은 범위의 NullMarked로 명시한다. UserDetailsService의 username은 null 불허이므로 알려지지 않은 비-null 사용자명만 기존 인증 실패로 처리한다. 추가 Spring 설정 메타데이터는 reservation.security 비밀번호3개의 타입·설명만 제공하며 환경 변수/Value 바인딩은 유지한다. SQL 예외의 nullable SQLException이 없거나 미분류 SQLSTATE이면 원래 오류를 다시 던지고55P03만 기존503으로 처리한다. [Qodana 분류](../../docs/qodana-review.md)·[실행 증거](../../docs/test-evidence.md)를 참고하며 Qodana 자체 재검사는 미실행이다.


사용자 제공 Qodana 재검사(revision80e1fa0)에서 LocalIdentityResolver의 userId null 분기가 항상 false로 확인되어 해당 중복 조건만 제거했다. null Authentication·미인증·다른 Principal 거부와 고정 UUID 사용은 유지한다. NullMarked는 정적 계약이며 런타임 null 검사 코드를 자동 생성하는 기능은 아니다. 고정 계정 생성 경로에서 계약을 지키는 것을 전제로 한다.


### 2026-10-22 US2 Redis 제한 HTTP 연결

SecurityConfig가 RateLimitFilter·명명된 CancelRequestValidationFilter를 체인에서만 소유하며 인가→제한→취소검증 순서를 명시한다. MVC기본 PathPatternRequestMatcher로 인코딩/잘못된UUID도 집계하고 Controller 본문 방어를 유지한다. 미인증401·실제8KiB초과413은 Redis스크립트0, ERROR dispatch1회에도 집계1회를 실제HTTP로 확인했다. [RateLimitApiIT](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitApiIT.java)37건·[공통 관측](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitHttpTestSupport.java)·[실행 증거](../../docs/test-evidence.md)를 따른다. 실제Redis 장애·503 분류와 기존HTTP/110회 회귀·최종전체게이트는 후속이며 US2 오케스트레이터 중간검토 대기다.


### 2026-10-22 US3 실제 장애·복구 인수

limiter.check 안의확인된Redis의존성만503으로변환하고후속chain은catch밖에유지한다. 실제지연/중단/WRONGTYPE/OOM의503은Controller/Service/TX0·전체행snapshot동일이며기존후속내부500/ERROR dispatch회귀를유지했다. 인터럽트는Unit에서복원·전파를확인했다. [RedisFailureIT 계열](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RedisFailureIT.java)·[공통관측](../../backend/src/test/java/com/limitedbooking/backend/ratelimit/RateLimitHttpTestSupport.java)·[실행증거](../../docs/test-evidence.md)를따른다. US4 기존HTTP/110회·최종전체게이트는후속이며 US3 오케스트레이터 중간검토 대기다.


### 2026-10-22 US4 회귀·최종 인수

기존 HTTP는 기본20/60초·실제 Redis를 사용하고 사례별 ADMIN/A/B 소유키만 초기화한다. 경쟁은 별도10000/60초로110회 모두 실제 HTTP·Redis·DB를 통과하며 최종 단일SQL 수량 snapshot과 종료를 확인했다. 부모 setup 첫 보호검사 전에는 Redis/DB를 접근하지 않으며 종료/전송불확실 fixture에서 reset0을 검증했다. 취소 입력 검증은 SecurityIT, 순수 대상 API는 postgresOnly ResourceApiIT에 둔다. 업무 SQL/스키마·성공/소유권/취소·OpenAPI 계약은 유지한다. [증거](../../docs/test-evidence.md)·[110회 기록](../../docs/concurrency-runs.json)을 따른다.


### 9단계 US2 의존성 계약 연결 (2026-10-23)

[의존성 계약 시험](../../backend/src/test/java/com/limitedbooking/backend/common/ApiContractDependencyIT.java)은 별도 소유 DB/Redis 중단의5API/두쓰기503과 정상DB에서의업무전체행무변경·독립3API정상을 고정명세와 대조한다. 중단DB의사후snapshot과 기존정상DB의timeout롤백증거를 구분한다. 제품·SQL·정책은 유지하며 [T018~T020 실행증거](../../docs/test-evidence.md)를 따른다. T021이후/전체9단계인수·Qodana는미완료다.


### 9단계 계약 검증 최종 인수 (2026-10-23)

[계약 HTTP 시험](../../backend/src/test/java/com/limitedbooking/backend/common/ApiContractIT.java)이 실제 Authorization과 보안 필터로 미인증·틀린 비밀번호 401(Basic WWW-Authenticate), USER의 대상 생성 403, 타인·ADMIN 예약 404, 인증 전 8193바이트 413(Content-Length·chunked)을 검증한다. 모든 오류는 `application/problem+json` 6필드와 status·code·instance 대응, 시험 비밀번호·SQL·스택 비노출을 확인한다. 미명시 500/default는 거부한다. T042에서 SecurityIT7·ResourceApiIT11·OwnershipIT5 회귀도 통과했다(T032 당시 ResourceApiIT8은 historical 값). 앞 절의 미완료 표시는 당시 기록이다. 제품 변경은 PR #12 리뷰 보완의 `ResourceController` 엄격 UUID binder 1건이며 SQL·Gradle·OpenAPI·정책 변경은 없다. [실행 근거](../../docs/test-evidence.md)의 9단계 절과 [010 작업](../../specs/010-openapi-contract/tasks.md)을 따른다. Qodana는 사용자 지시로 건너뛴 미실행 후속이다.
