# 0단계 설계 결정

근거: [원 계획](<../한정 수량 예약 백엔드 로컬 개발 계획.md>) 3·5절, [조사](../specs/001-define-api-contract/research.md). 아래 각 결정은 4줄이며 설계 선택의 기록이다. 실제 도구 채택·실행 호환성 또는 API 구현을 증명하지 않는다.

## JDBC로 SQL과 트랜잭션을 직접 이해한다

- 선택: 기본 SQL 접근은 Spring JDBC의 NamedParameterJdbcTemplate이다. 기존 Java 25·Gradle·com.limitedbooking.backend를 유지한다.
- 이유: available 조건부 UPDATE·제약·잠금·롤백과 별도 Writer 트랜잭션 경계를 직접 읽고 검증하기 위한 학습 목적이다.
- 대안: JPA/ORM의 추상화, JDBC의 JdbcClient가 있지만 기존 선택을 재선정하지 않고 명시적 SQL을 우선한다.
- 영향·한계: SQL·매핑을 직접 관리하며 PostgreSQL 통합 시험이 필요하다. JDBC 의존성·Repository·SQL은 아직 미구현/미실행이다.

## 성공한 예약 행에 생성 키를 보존한다

- 선택: 사용자 UUID·생성 키의 유일 기록은 성공한 예약 행이며 취소 뒤에도 보존한다. 실패 요청은 키를 점유하지 않는다.
- 이유: 불변 id·resourceId·createdAt와 고정 CONFIRMED로 최초 201·Location·JSON 필드 값을 재구성하고 중복 차감을 방지한다.
- 대안: 별도 키 테이블·TTL 캐시는 추가 저장 동기화·실패 응답 보관·만료 정책을 요구하므로 첫 범위에 추가하지 않는다.
- 영향·한계: 실패 원인 해소 후 같은 미사용 키로 성공할 수 있지만 성공 키를 다른 대상/새 예약에 재사용할 수 없다. 저장·응답 재현은 아직 미구현/미실행이며 네트워크의 한 번 전달을 보장하지 않는다.

## Redis는 예약 쓰기의 요청 진입을 제한한다

- 선택: Redis는 인증된 예약 생성·취소 합산의 최초 요청부터 20회/60초 고정 창만 담당하며 PostgreSQL이 수량 원본이다.
- 이유: 인증 뒤 업무 트랜잭션 전에 초과·장애 요청을 거부하고 재요청·입력 오류도 카운트한다. TTL은 연장하지 않는다.
- 대안: 메모리/DB 카운터는 다른 장애·경쟁 특성이 있으며 Redis 수량 저장·분산 락은 수량 원본과 책임을 바꾸므로 채택하지 않는다.
- 영향·한계: Redis 장애는 해당 쓰기에 503·DB 무변경이고 조회·대상 생성/조회는 Redis에 비의존이다. DB 실패로 카운터를 되돌리지 않으며 Redis·Lua·필터는 아직 미구현/미실행이다.

## 1단계 실행 준비

현재 Gradle9.7.1/Boot4.1.1/Java25 골격을 유지한다. 문서 루트는 기존 프로젝트 루트 docs/다. 새 업무 도메인을 추가하지 않고, 프로브와 시험 migration은 도구 확인에만 사용한다. 후보의 설치·취득을 연동 호환성 확인으로 표현하지 않는다.

Compose와 Testcontainers는 `postgres:18.6-bookworm`, `redis:8.10.2-trixie`의 위 취득 digest를 함께 사용한다. arm64 실제 컨테이너 내부 버전 출력에 성공했다. 태그가 바뀌더라도 같은 식별값을 실행하도록 digest까지 고정한다.

QuickTheories0.26의 Core bytecode에서 examples 루프의 생성과 visitedValues 중복 제거를 확인했다. withExamples(100)은 실제 assertion61회여서 최소100회 조건에 실패했다. 기대값을 낮추지 않고 생성 예산을1000으로 늘리고 실제 assertion 횟수≥100을 계속 검사한다. seed와 실제 횟수를 기록한다.

심화 후보는 첫 조합 그대로 최소 사례를 통과했다: QuickTheories0.26, validator core2.46.1, PIT Gradle1.19.0/engine1.22.1/Jupiter1.2.3. Java/JUnit 임의 하향 없이 실행했으며 core validator는 JDK HTTP fixture와 독립 어댑터를 사용한다. 업무 OpenAPI 읽기와 실제 업무 응답 준수를 구분한다.

## 최종 실행 판단과 남은 범위 (2026-10-14)

- 사용자 지정 작업 공간과 T001 계약에 따라 현재 checkout의 chore 브랜치에서 순차 진행했다. 별도 worktree로 옮기지 않았다. 비용: 다른 작업과 병행 편집하면 충돌할 수 있어 이번 작업은 순차 실행했다.
- 스킬 task-start는 Task N 제목만 지원하므로 Spec Kit의 T001~T042 행을 직접 읽고 ledger와 실행 근거로 추적했다. 비용: 자동 brief 대신 수동 추적이 필요하며 체크42개/실제 근거 대조로 누락을 관리했다.
- 업무 API·UUID 인증·수량/SQL 경쟁·Redis 요청 제한·부하·외부 CI는 명시적 후속 범위다. 비용: 해당 단계 검증 전 업무 기능으로 사용할 수 없다.
- 종료10초 초과 분기 자체와 개발 commit 응답 유실 주입은 미실시로 유지한다. 실제 미종료 HTTP 응답의 bounded cleanup은 새 회귀로 확인했다. 비용: 두 장애 분기의 추가 검증이 필요하다.
- T042의 병합/push 금지 범위에 따라 chore 브랜치와 로컬 커밋을 유지한다. 비용: develop 통합은 별도 작업이다.

독립 리뷰의 종료 상한 누락은 commit be96d7c에서 실제 HTTP 회귀 실패→수정 통과 및 deepTest14건 실패skip0으로 보완했다. 버전 문서의 과거/현재 상태 혼재도 최종 기록 정리에서 해결했다. 미해결 리뷰 Minor는 없다.

## PR 적대적 리뷰의 전체 HTTP 요청 deadline (2026-10-14)

Java25의 HttpRequest.timeout만으로 헤더 이후 문자열 본문 완료 대기를 제한하지 못함을 실제 재현했다. 연결/헤더 timeout을 유지하면서 sendAsync 응답 Future.get(10초)로 전체 본문 완료를 제한하고 timeout·중단 시 원래 Future를 취소한다. 앱 IT와 계약 fixture는 같은 지원 경로를 사용한다. 회귀 실패→수정 통과·최종15건 및 리뷰 PASS를 확인했다. 업무 HTTP 오류/timeout 정책의 구현은 후속 범위다.

## 2단계 저장 기반 구현 결정 (2026-10-15)

- 후보 CREATE를 제약별 RED/GREEN으로 인수하고 완성본을 byte-identical V1로 복사한 뒤 후보 경로를 제거했다. 적용 후 V1은 변경하지 않는다. 도구 프로브의 db/toolchain 위치와 업무 위치는 분리한다.
- SchemaDatabase/SchemaDatabases는 네 인수 클래스의 fresh/reapplied 독립 컨테이너 준비·스냅샷·정리만 공유한다. 생산 저장 계층은 기능별 두 Repository이며 범용 Repository를 만들지 않는다.
- TestData는 명시 DataSource와 REQUIRES_NEW를 사용한다. 반환 전 확정된 준비 데이터 계약을 위해 호출자 트랜잭션 롤백과 분리한다. 별도 연결에서0행인 RED 이후 두 모드 GREEN으로 검증했다. 초기화 두 DELETE도 한 독립 트랜잭션이며 중간 SQL 오류는 전체 롤백한다.
- docs/openapi.yaml은 변경하지 않았다. 업무 V1·읽기 조회의 인수는 API·인증·경쟁·멱등 재현의 완료가 아니다. local DB 실제 V1 적용/전환, 연결 단절/프로세스 중단은 미검증이다.

## 3단계 순수 규칙 설계 결정 (2026-10-15)

[004 계획](../specs/004-validate-domain-rules/plan.md)의 설계 결정이며 아직 구현·실행 검증하지 않았다. 기존 확정 입력·HTTP·저장 정책은 유지한다.

- 이름은 strip 후 코드 포인트 수로 검사한다. IdempotencyKey는 검증 compact constructor를 가진 record로 하여 public 생성자 우회를 막고 원문 기반 값 동등성을 유지한다. 취소 판단은 기존 ReservationStatus를 소비하며 새 저장 상태를 추가하지 않는다.
- 무효 이름·수량·키는 내부 Java 호출에서 IllegalArgumentException, 누락된 내부 예약 상태는 NullPointerException으로 구분한다. 정확한 메시지를 계약으로 고정하지 않고 입력 원문을 넣지 않는다. 후속 HTTP에서는 해당 입력 경계의 실패만400으로 변환하고 모든 예외를 일괄 변환하지 않는다.
- 기존 Unit 프로브와 생산 ToolchainProbe는 PIT/Property가 참조하므로 이번에는 유지한다. 세 업무 Unit만 추가하고 빌드 태스크·게이트·버전은 바꾸지 않는다. 프로브 삭제와 업무 Property/PIT 교체는 후속 심화 범위다. 기존 PIT 생성1/탐지1을 업무 규칙80% 증거로 쓰지 않는다.
- 컴파일 가능한 최소 골격에서 실제 assertion RED→최소 구현→동일 Unit GREEN→전체 Unit으로 진행한다. 실제 요청 경로 사용은4~6단계별 미완료 조건으로 추적한다. 선택 이유·대안·공식 근거는 [조사](../specs/004-validate-domain-rules/research.md)에 있다.

## 3단계 ResourceRules 구현 (2026-10-15)

기존 확정 정책대로 null 거부→strip→코드 포인트1~100 검사 순서로 구현했다. NBSP 계열은 유지하며 capacity0~1000을 보정 없이 검증한다. UTF-16 길이·정리 전 길이 제한·초과 절단을 사용하지 않는다. Unit52건과 세 그룹 RED/GREEN은 test-evidence.md를 따른다. HTTP와 저장 경계는4단계에서 연결한다.

## 3단계 IdempotencyKey 구현 (2026-10-15)

record compact constructor에서 null과 고정 Pattern의 matches 전체 일치를 검사해 parse/new 두 경로가 같은 불변식을 보장한다. 원문 value·equals/hashCode를 유지하고 키를 오류 메시지에 노출하지 않는다. 내부 IAE를 후속 입력 경계에서만400으로 번역하며 이번에는 HTTP 예외 처리를 추가하지 않았다.

## 3단계 CancellationDecision 구현 (2026-10-15)

기존 ReservationStatus를 default 없는 switch로 두 판단에 대응하며 Objects.requireNonNull로 내부 상태 누락을 NPE로 드러낸다. 판단을 DB 상태나 응답 enum에 섞지 않고 상태·수량·시간을 만들거나 갱신하지 않는다. 실제 취소 원자성은6단계 Writer의 책임이다.

## 4단계 로컬 인증과 예약 대상 API 구현 결정 (2026-10-17)

- [005 계획](../specs/005-auth-resource-api/plan.md)과 [조사](../specs/005-auth-resource-api/research.md)에 따라 `SecurityConfig` 내부의 커스텀 `LocalUserPrincipal`(`UserDetails` 구현, 불변 `UUID userId` 보유)에 `ADMIN`·`USER_A`·`USER_B` 고정 UUID를 할당하고 환경 변수 주입 비밀번호를 `BCryptPasswordEncoder`로 보관했다. `LocalIdentityResolver.userId(Authentication)`는 오직 `Principal`에서만 UUID를 추출하며 `X-User-Id` 헤더나 본문 `userId`를 신뢰하지 않는다.
- `RequestBodyLimitFilter`를 `BasicAuthenticationFilter`보다 앞에 등록해 `Content-Length > 8192`뿐 아니라 길이 헤더가 없거나 `Transfer-Encoding: chunked`인 요청도 최대 `8,193바이트`까지 직접 읽어 초과 시 인증 전에 즉시 `413 PAYLOAD_TOO_LARGE`(`application/problem+json`)를 반환하고, 상한 이하 바이트는 `HttpServletRequestWrapper`로 재제공한다.
- Spring Boot 4 / Jackson 3(`tools.jackson.databind.json.JsonMapper`)의 `JsonMapperBuilderCustomizer`로 `FAIL_ON_UNKNOWN_PROPERTIES`·`STRICT_DUPLICATE_DETECTION` 활성화, `ALLOW_COERCION_OF_SCALARS`·`ACCEPT_FLOAT_AS_INT` 비활성화 및 `LogicalType.Integer` 스칼라 강제 변환(`CoercionAction.Fail`)을 설정해 미정의 필드·중복 키·부동소수점(`1.5`, `1.0`)·문자열 숫자(`"10"`)를 모두 `400 INVALID_REQUEST`로 거부한다.
- `ApiExceptionHandler`는 `TransientDataAccessException`·`DataAccessResourceFailureException`·`QueryTimeoutException`만 `503 DEPENDENCY_UNAVAILABLE`로 변환하고, 분류되지 않은 SQL 문법 오류나 내부 런타임 결함은 `4xx`/`503`으로 숨기지 않고 `500`으로 드러나게 했다. 6단계 `ReservationController` 구현 전에도 4단계 취소 본문/UUID 검증 계약을 만족하도록 `SecurityConfig`의 인증 뒤 필터에서 `POST /api/reservations/{id}/cancel`의 비어 있지 않은 본문 및 잘못된 UUID를 `400 INVALID_REQUEST`로 거부한다.

## 2026-10-19 — 5단계 생성 계획의 트랜잭션·오류·검증 경계

- 상태: [006 계획](../specs/006-reservation-create/plan.md)의 설계 선택이다. 코드 반영·실행 검증 전이며 기존 업무 정책·API·V1은 변경하지 않는다.
- Service는 트랜잭션을 시작하지 않고 활성 상위 트랜잭션을 거부한다. 별도 READ COMMITTED Writer의 전체 롤백 뒤에만 사용자/키 유일 위반을 복구한다. REQUIRES_NEW나 실패 트랜잭션 안 계속 조회는 채택하지 않는다.
- PostgreSQL 오류 구조에서 같은 예외의 SQLSTATE23505·정확한 제약명을 확인한다. 이를 위해 동일 BOM의 드라이버를 runtimeOnly에서 implementation으로 이동할 예정이다. 메시지 파싱·모든 중복/무결성 오류의 성공 재현은 제외한다.
- 시각은 기존 MICROS 절삭과 저장 RETURNING 값을 사용한다. 테스트는 실제 UPDATE/트랜잭션을 유지한 테스트 전용 Repository 실패 주입과 종료 후 별도 연결 상태 검사다. 실제 경쟁 반복은 7단계이며 순수 분기 검증을 경쟁 증거로 취급하지 않는다.
- 공식 근거·대안·미검증 범위는 [조사](../specs/006-reservation-create/research.md), 실행 예정 절차는 [검증 안내](../specs/006-reservation-create/quickstart.md)를 따른다.


## 2026-10-19 — 5단계 구현에서 확인한 오류 전달

- 기존 설계대로 외부 Writer/트랜잭션 밖 Service, UTC MICROS·RETURNING, 성공 행 키 기록을 구현했다. pgJDBC42.7.13은 동일 BOM implementation으로 이동했다.
- 실제 lock timeout은 UncategorizedSQLException/SQLSTATE55P03으로 전달됐다. 기존 transient 매핑에 포괄되지 않아 정확한55P03만503으로 보완했다. Hikari 풀 고갈은 CannotCreateTransactionException의 직접 SQLTransientConnectionException 원인을 확인해503으로 연결한다. 연결SQLState08/SQLNonTransientConnectionException도 실제 연결 실패 범주다. 메시지 파싱이나 모든TransactionException503은 사용하지 않는다.
- INSERT전 실패·문장 지연·풀 소진은 테스트 전용 MockitoSpyBean에서 실제 SQL/트랜잭션을 유지해 주입했다. 별도 연결로 무변경과 동일 키 복구를 검증한다. 실제 경쟁 반복/응답 유실은 이번 단계 인수가 아니다.


### 독립 리뷰 반영: 예약 대상 UUID 원문 검증

Jackson 기본 UUIDDeserializer의 Base6422/24자와 공백trim 허용이 고정 uuid 입력 계약보다 느슨함을 실제HTTP로 재현했다. 요청record의 명시적JsonCreator가 원문36자 UUID 패턴을 확인 후 변환하도록 최소 변경했다. 사용자 정책·OpenAPI는 바꾸지 않으며 대소문자UUID 값 비교는 유지한다.


## 2026-10-20 조회·취소 구현과 탐지력 보완

- 기존5.3 계약대로 별도 Writer READ COMMITTED에서 resource→reservation 잠금 순서와 CONFIRMED 조건UPDATE, available<capacity 복구를 사용했다. GET은 소유자 조건 단일 SELECT이며 별도 트랜잭션 없음. 스키마·OpenAPI·의존성 정책 변경 없음.
- UUID.fromString의 축약형 허용을 HTTP UUID 계약에 노출하지 않도록 예약 Controller 전용 InitBinder로 8-4-4-4-12 텍스트를 검사한다. UUID 대소문자는 허용한다. 기존 생성 body의 엄격 UUID와 일관된다.
- 일반 중복 취소 시험은 조기 반환과 조건UPDATE가 서로를 가려 각 조건의 제거를 탐지하지 못했다. 두 조건을 각각 시험하려고 이미 취소된 대상의 잠금 중 재취소(조기 반환), 낡은 CONFIRMED 최초 조회만 테스트 Spy로 주입(조건UPDATE0)을 추가했다. 실제 PostgreSQL 쓰기·HTTP 응답을 검사하며 각각 고의 변경 실패를 확인했다. 테스트 기대값·도메인 규칙은 유지했다. 실제 경쟁 반복의 대체 증거로 쓰지 않는다.
- 실행 중 기존 작업 브랜치를 유지하고 Spec Kit T001~T032 형식은 tasks.md/실행 기록으로 추적했다. 중간 커밋은 같은 IT를 확장하는 조회·취소 단위로 묶었다. 최종 통합·push는 이번 범위에 포함하지 않는다.


## 2026-10-20 PR6 인코딩 취소 경로의 본문 검증

적대적 리뷰에서 raw URI 필터와 디코딩된 MVC 경로 매칭 차이를 찾았다. `%63ancel`·`%72eservations` 경로와 `{}` 본문에서400 대신200·실제 CANCELLED/available1이 재현됐다. Controller의 취소 Handler 진입 시 상한 필터가 캐시한 실제 본문 바이트를 검사하도록 보완했다. 경로를 직접 디코딩하거나 허용 경로를 좁히는 별도 정책 대신, 매칭된 모든 취소 Handler에서 기존 본문 없음 계약을 보장한다. 인증·크기 상한·트랜잭션·OpenAPI 정책은 유지한다. 같은 인코딩 경로의 빈 본문 정상 취소도 회귀로 확인한다.

## 2026-10-20 — 7단계 경쟁 검증 구현

- 기존 linked worktree·명세 브랜치에서 T001~T038을 순차 실행한다. speckit의 문장형 T번호는 tasks.md와 실행 기록으로 추적한다. 별도 실행 ledger는 무시되는 .superpowers 아래에 둔다. superpowers task-start의 다른 계획 형식으로 문서를 바꾸지 않는다. 이 조정의 위험은 실행 기록 누락이며 완료 체크·명령·XML 대조로 관리한다.
- 제품 코드·의존성·V1·OpenAPI는 변경하지 않았다. 실제 HTTP/Basic/BCrypt·PostgreSQL을 사용하며 C01~C11 각10회를 구현했다. 경로 Spy는 실제 SQL에 위임하고 유일 위반23505/정확한제약명·활성트랜잭션 여부를 구분한다. 최초 빈 조회 두 개의 장벽은 트랜잭션 밖이며 같은 대상의 잠금 이후 양쪽 장벽은 없다.
- 실행 지원은 제한10작업자·공통deadline·모든Future수집·외부장벽해제→종료를 구현한다. 원래 작업 오류와 정리 오류가 겹치면 suppressed로 보존한다. 종료 실패의 최초 원인을 static 클래스 실행 상태에 보관하고 부모setup 첫 보호 검사로 다음 reset/준비/제출을 차단한다. Mockito 자동reset은 끄고 종료 확인 뒤 명시reset한다.
- 기존 서비스가 올바르므로 SQL 조건·대상 비교·취소복구 방어를 각각 한 번씩 고의 변경하여 탐지한 뒤 원복했다. C05 일반 경쟁은 스케줄에 따라 고의 결함 미실행 회차가 있었고9실패/1통과를 그대로 기록한다. 전체 경쟁순서 또는 운영 성능의 증명으로 해석하지 않는다. Redis·업무 자동계약·속성·PIT심화·부하·외부CI는 후속이다.


## 2026-10-21 — 최종 리뷰의 종료 격리·증거 보완

- 정적 독립 리뷰가 Important2/Minor2를 제시했다. 종료 전에 Spy를 초기화하거나 이미 설정된 차단 상태를 무시하는 경우, Service 전 전송 실패, 다중 작업 오류 유실을 별도 RED로 재현했다. 테스트 지원/IT만 보완하며 제품코드·API·스키마는 유지한다.
- 계획의 일부 정리 순서(reset→HTTP종료)보다 상위의 “종료 확인 전 reset 금지” 원칙을 우선했다. 모든 종료 확인과 차단 없음 뒤 Spy reset으로 통일한다. 불확실한 전송 실패도 영구 차단하는 보수적 선택은 같은 JVM의 후속 회차가 함께 실패하는 비용이 있지만, 늦은 이전 요청이 새 회차 데이터를 건드리는 위험을 막는다.
- Minor로 분류된 같은 키 요청 식별자 겹침은 경로를 다른 요청의 조회로 잘못 입증할 수 있으므로 필수 수정으로 재분류했다. 고유 호출 ID·정확한 요청/순번/트랜잭션 매칭으로 보완한다. C07 보존 증거 누락도 회차 추적 요구사항에 따라 필수 수정으로 다뤄 해당 차감0/재조회 사건을 JSON에 기록한다. 비용은 관측·기록 코드 증가다. 미룬 Minor는 없다.
- 리뷰의 판단 유보는 단계 범위에 따라 유지한다: 변경 없는 제품의 별도 전체 보안 감사, Redis·자동계약·Property·PIT·k6·외부CI/배포, 모든 스케줄·정확한 연결 피크·운영 성능은 이번 완료 주장에 포함하지 않는다. JUnit이 본문/AfterEach 오류를 결합하는 내부 표현은 별도 검증하지 않았으며 지원 코드 자체의 원인 보존만 검증한다. 이 범위를 전체 보장으로 오해하지 않도록 실행 증거에 한계를 유지한다.

## 2026-10-20 Qodana 지적 처리

- null 계약은 패키지 전체 대신 record·재정의 메서드에 JSpecify NullMarked를 적용한다. nullable SQL accessor는 한 번 조회해 null이면 원래 예외를 던진다. 55P03 외 오류를503으로 감추지 않는다.
- 기존 Value 설정 바인딩을 유지하고 추가 Spring 설정 메타데이터로 비밀번호 프로퍼티3개를 설명한다. 기본값·실제 비밀값을 넣지 않는다.
- 공식 수정판이 확인된 Jackson2/Rhino는 테스트 구성에만 Boot BOM 보안 예외로 정렬한다. 수정판 미확인 json-patch/coreutils는 무조건 제외하지 않으며 잔여 위험으로 관리한다.
- Java25 언어 차원의 public 생략 제안은 Spring Boot main 탐지 호환성을 위해 적용하지 않는다. 경고 억제는 추가하지 않았다.
- 사용자 요청으로 독립 수정 파일을 병행 배정하고 공유 Gradle 실행과 Git은 순차 처리했다. 상세 근거와 검증은 [Qodana 검토](qodana-review.md)와 [증거](test-evidence.md)를 따른다.


## 2026-10-20 잔여 Qodana3건 처리 결정

공식 배포가 확인된 Atlassian openapi-request-validator-core3.0.0으로 전환해 networknt 엔진을 사용한다. 프로젝트는 OpenAPI3.0.3 전용이므로 Swagger2 변환기만 제외한다. 취약2개 모듈의 재유입을 Gradle 해석 그래프 검사로 거부하며 기존 필수 id·참조 해석 및 추가 UUID/타입/미정의필드 탐지로 기능 보존을 검증한다. Rhino도 유입 경로가 사라져 강제 버전을 제거했다. public main은 Boot 호환성을 유지하고 해당 문법 경고만 메서드에 좁게 억제한다. 상세 공식 근거는 [Qodana 검토](qodana-review.md)에 기록한다. 이전 미해결 기록을 이 결정으로 갱신한다.


## 2026-10-21 / 테스트 PostgreSQL의 비TLS 연결 모드 명시

- 대상은 `ContainerTestSupport`가 생성·소유하는 로컬 Testcontainers PostgreSQL뿐이다. 고정 이미지의 `SHOW ssl`은 off였고 TLS 인증서도 구성하지 않는다. pgJDBC의 기본 sslmode는 prefer여서 기존에는 SSLRequest→N을 거쳐 평문 연결했다. [공식 연결 옵션](https://jdbc.postgresql.org/documentation/use/)과 [SSL 모드 설명](https://jdbc.postgresql.org/documentation/ssl/)을 확인했다.
- 테스트 JDBC URL에 `sslmode=disable`을 명시해 이미 평문인 fixture의 연결 방식을 일치시킨다. 이 URL은 컨테이너 준비 확인·직접 JDBC·Flyway·Spring/Hikari가 공유한다. 앱 기본/local 설정·외부 DB·인증서 검증 정책은 변경하지 않는다.
- 이는 최초08P01의 비정상 응답 발생원을 규명한 수정이 아니라 해당 테스트에서 불필요한 SSL 협상 경로를 제거하는 제한된 대응이다. 수정 전 관측 전체 검증과 새 컨테이너5개/SSLRequest250건도 정상이라 최초 실패를 재현했다고 주장하지 않는다. 정상 재실행을 근본 원인 해결의 증거로 삼지 않는다.
- 대안인 timeout 증가·무조건 재시도·SQLSTATE08P01 무시·버전 변경은 근거가 없어 적용하지 않았다. TLS 연결 자체는 이번 fixture의 검증 범위가 아니며 향후 TLS를 시험하려면 서버 인증서와 클라이언트 검증을 별도 설계한다.
- Ruling: 최초 실패 원인은 미확정 상태로 보존하고 fixture의 비TLS 계약만 명시한다. 이 판단이 틀리면 협상 이후의 다른 연결 오류는 여전히 발생할 수 있으며, 그런 실패는 기존 검증 게이트에서 그대로 실패한다.


## 2026-10-22 US1 Redis 제한 기반
승인된009 설계대로 StringRedisTemplate/재사용 DefaultRedisScript<List>·단일Lua의 INCR/최초PEXPIRE/PTTL을 사용한다. Java는 두 Long·count>=1/PTTL>=0을 확인하고 나눗셈·나머지로 올림하여 overflow를 피한다. 양수 정수 밀리초 설정을 record 생성 시 검증하며 모든 프로필 기본20/60000ms·명령/연결200ms다. 만료 인수만 독립3/1000ms 컨텍스트다. 기존 경쟁지원의 전체 결과/종료/StopState를 재사용했다. HTTP 필터·장애 분류는 아직 연결하지 않았고 SQL/스키마/OpenAPI는 유지한다. 최신 지시의 US1 중간검토 게이트에서 멈춘다. [실행 근거](test-evidence.md)·[009작업](../specs/009-redis-rate-limit/tasks.md) 참조.


### 2026-10-22 사용자 지정 Qodana 후속 이관

사용자가 Qodana 인증·실행에 대해 “나중에 할거야”라고 명시했다. 이에 T037은 미완료 후속으로 유지하며 이번 8단계 완료·PR·병합 게이트에서 제외한다. Qodana는 미실행이며 통과·지적0으로 기록하지 않는다. qodana.yaml의 JVM2026.2·Java25·recommended·any0·라이선스 설정은 유지한다. 나머지 기능·경쟁·장애·build/verify/의존성 검사와 독립리뷰 게이트는 그대로다.


### 2026-10-22 US2 인증 뒤 입력 검증 전 제한

RateLimitFilter와 명명된 CancelRequestValidationFilter는 SecurityConfig가 체인에서만 소유한다. 인가→제한→취소검증을 명시하고 PathPatternRequestMatcher로 MVC와 같은 디코딩 경로를 매칭해 잘못된UUID·인코딩 경로도 집계한다. Controller의 본문 방어는 유지한다. Redis 예외 분류는 US3 후속이므로 이번 필터는 광범위catch로 내부결함/후속체인을503으로 바꾸지 않는다. 실제HTTP37건 인수·요청별스크립트/PTTL/Controller/Service/TX/종료 관측·전체행snapshot을 [증거](test-evidence.md)에 기록했다. 기존 postgresOnly 전환은 US4 후속이다.


### 2026-10-22 US3 좁은 Redis 분류와 실제 장애 수단

Spring래퍼전체를503으로분류하지않고limiter.check 안의확인된원인을검사한다. 전송·명령timeout·실제WRONGTYPE·정형OOM는의존성실패이며직렬화(전송원인포함)·미확인명령/스크립트·미분류내부결함은전파한다. 인터럽트는복원하고후속chain은catch밖이다. 기본200ms·20/60000ms는유지했다. 실제CLIENT PAUSE는인증완료후테스트spy의limiter진입에서관리연결OK를확인해각요청의timeout을입증한다. 앱과독립인관리연결3초/복구PING5초/종료future각10초를사용한다. 실제timeout후count0/1모두가능하므로보상하지않는다. 중단시험은기존연결/첫접속전중단을각소유컨테이너/컨텍스트로분리해동적포트재사용을피했다. 기본60초실제만료로성공키독립을확인했다. [실행근거](test-evidence.md)와[009작업](../specs/009-redis-rate-limit/tasks.md)을따른다.


US3 오케스트레이터 코드검토의 OOM누락은 실제HTTP500 RED 후503 GREEN으로보완했다. Redis8.10.2 noeviction/maxmemory거부의정형메시지와RedisCommandExecutionException타입을확인했고해당형태만추가했다. 공식근거와실제SDK형태는[009조사](../specs/009-redis-rate-limit/research.md),기능실행/설정원복/회귀37·장애12·Unit37 근거는[실행증거](test-evidence.md)를따른다.


## 8단계 기존 HTTP와 종료보호를 유지하는 최종 인수 (2026-10-22)

- 선택: 예약/보안 HTTP는 실제 Redis와 소유키 초기화를 쓰고 경쟁만10000/60초로 분리한다. 부모setup 첫 StopState검사 전 Redis/DB를 접근하지 않는다. 순수DB/ResourceAPI는 Redis를 강제하지 않는다.
- 이유: 기존 성공·실패·수량·멱등성 기대값을 유지하며 제한 연결의 회귀와 종료 불확실 후 데이터 초기화0을 증명한다. 혼재한 취소 입력 사례만 ResourceApiIT에서 SecurityIT로 옮겼다.
- 대안: 429/503로 업무기대값 대체·제한mock·FLUSHALL·내부서비스대체·순서검사 전에 reset을 수행하는 방식은 제외했다.
- 결과·한계: 2026-10-22 8단계 구현·실행 인수 완료: `./gradlew build verify verifyContractDependencies` 종료0, 일반 Unit193·IT312, 실패/오류/skip0. IT에는 기본 Redis5·HTTP제한37·실제장애12·경쟁110회와 종료지원fixture5가 포함된다. T001~T036/T038 37개 완료, Qodana T037만 사용자 “나중에 할거야” 지시에 따른 미실행 후속이며 이번 완료·PR·병합 게이트에서 제외한다. 오케스트레이터 최종 독립검토·PR·병합은 아직 수행하지 않았다. 이번8단계는 최신 사용자 지시의 Spec Kit를 적용했고 과거 superpowers 기준/실행증거는 보존한다. 운영성능/모든 경쟁순서의 증명이 아니며 업무전체 자동계약/Property/PIT·부하·외부CI는 후속이다.

## 9단계 의존성 계약 fixture 격리 (2026-10-23, T018~T020)

실제 DB 중단과 Redis 중단을 `ApiContractDependencyIT.java`의 두 별도 contract 클래스·소유 컨테이너·Spring 컨텍스트로 분리한다. 각 시험이 정상 준비를 사전 커밋한 뒤 해당 소유 의존성만 유한하게 중단한다. 중단 후 재기동 포트나 개발 DB로 우회하지 않는다. 서비스 예외 mock 대신 실제 HTTP·Basic·필터와 원문 검사기를 사용한다. DB 중단은 풀 기존 연결을 비우고 Redis 통과/5API503을 검사하며, 중단 DB의 사후 snapshot을 주장하지 않는다. Redis 중단은 정상 DB의 전체 업무 행 단일 SQL snapshot으로 두 쓰기 무변경을 확인하고 독립3API 성공도 검사한다. DB 실제 timeout 롤백·카운터 유지는 기존 회귀와 별개 근거로 연결한다. 제품·SQL·정책 변경 없음. 실행 수/RED·GREEN/미검증은 [증거](test-evidence.md)의 T018~T020 절을 따른다.
