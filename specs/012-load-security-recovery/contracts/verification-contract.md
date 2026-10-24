# Verification Contract: 11단계

판정 규칙의 단일 기준. 테스트 이름은 계획이며 구현 후 실제 이름으로 test-evidence에 연결한다.

## C1. 보안 (US1, FR-001~004)

| ID | 사례 | 근거 테스트 | 상태 |
|---|---|---|---|
| S1 | USER_A·ADMIN → USER_B 예약 GET 404(없는 id와 동일) | `ReservationOwnershipIT.otherUserAndAdminCannotDistinguishMissingReservation` | 기존 |
| S2 | USER_A → USER_B 예약 cancel 404·무변경 | `ReservationOwnershipIT.cancelProtectsOwnershipAndLeavesInventoryUnchanged` | 기존 |
| S3 | 본문 userId/quantity 400·무변경 | `ReservationCreateIT.rejectsInvalidBodiesAndDoesNotReserveValidKey`, `ApiContractIT.invalidInputMatchesExactContract` | 기존 |
| S4 | 예약 생성·조회·취소의 `X-User-Id` 위조 무시 | `SecurityIT.reservationPathsIgnoreSpoofedUserIdHeader`(신규) | 신규 |
| S5 | USER → POST /api/resources 403·대상 0 | `SecurityIT.regularUsersAreForbiddenFromCreatingResourcesAndSpoofedUserIdHeaderIsIgnored` | 기존 |
| S6 | 미인증·잘못된 비밀번호 401·WWW-Authenticate·쿠키 없음 | `SecurityIT.unauthenticatedAndInvalidCredentialsReturn401ProblemJsonWithWwwAuthenticate` | 기존 |
| S7 | 잘못된 UUID 경로/본문 400 | `SecurityIT.rejectsNonEmptyInvalidUuidAndOversizedCancellationBeforeBusinessEntry`, `ReservationOwnershipIT.getRequiresAuthenticationAndValidUuid` | 기존 |
| S8 | 알 수 없는 필드 400 | `ReservationCreateIT.rejectsInvalidBodiesAndDoesNotReserveValidKey`, `ResourceApiIT.rejectsInvalidJsonBodiesWithoutChangingInventory` | 기존 |
| S9 | 8KiB 초과 413(Content-Length·chunked·조작 길이)·인증 전 | `SecurityIT.rejectsPayloadsOver8KiBWith413BeforeAuthenticationIncludingChunkedAndManipulatedLength` | 기존 |
| S10 | 응답 비노출(SQL·스택·비밀번호) | `SecurityIT.assertProblem`, `ReservationCreateIT.assertSafe` 사용 사례 | 기존 |
| S11 | 로그 비노출(비밀번호 원문·Authorization 값) | `SecurityIT.rejectedRequestsDoNotLogPasswordsOrAuthorizationValues`(신규) | 신규 |

S4 기대: USER_A가 `X-User-Id: USER_B_ID`(및 ADMIN_ID)로 생성 → 201, DB `user_id=USER_A_ID`. USER_A GET 200, USER_B GET 404, USER_B cancel 404·무변경, USER_A가 `X-User-Id: USER_B_ID`로 GET/cancel → 200(본인 예약). RED 방법: implementer가 `LocalIdentityResolver`가 헤더를 우선하도록 일시 변경 → 실패 확인 → 원복.

S11 기대: 잘못된 비밀번호 401, 미인증 401, USER 403, 413, 400, 정상 201 요청 동안 캡처한 이벤트에 세 비밀번호 원문·사용한 Basic 값(`Basic ` 뒤 Base64)이 없다. 캡처 이벤트 수 > 0을 함께 확인해 빈 캡처를 통과로 취급하지 않는다. RED 방법: Authorization 헤더 값을 INFO로 출력하는 일시 변경 → 실패 → 원복.

## C2. 의존성 장애 (US2, FR-005~011) — `DependencyFailureIT`

공통: 테스트 소유 PostgreSQL·Redis, 풀 10·연결 획득 3초, 제한 10,000/60초(테스트 설정 명시), HTTP 클라이언트 timeout 20초. 각 사례 전후 DB 스냅샷 비교, 종료 시 단일 SELECT로 보존식 검사. 503 응답은 problem+json·`DEPENDENCY_UNAVAILABLE`·비노출(`ReservationCreateIT.assertSafe` 동등).

| ID | 사례 | 기대 | 상한 |
|---|---|---|---|
| F1 | 연결 차단+종료 중 생성 | 503, 스냅샷 동일 | 6초 |
| F2 | 연결 차단+종료 중 확정 예약 취소 | 503, CONFIRMED·수량 동일 | 6초 |
| F3 | F1·F2 복구 | 같은 키 201·Replayed false·−1, 같은 취소 200·+1(1회) | - |
| F4 | 서버 pause 중 생성 | 503, unpause 후 스냅샷 동일, 같은 키 201 | 12초 |
| F5 | 대상 행 잠금 중 확정 예약 취소 | 503, CONFIRMED·수량 동일, 해제 후 200·+1 | 4초 |
| F6 | 커밋 확인 후 응답 유실 | 재시도 201·같은 id·createdAt·Replayed true, 행1·−1 | 커밋 폴링 5초 |
| F7 | 생성 잠금 시간 초과 | 기존 `ReservationRollbackIT.lockTimeoutIs503AndSameKeyRecovers`(9초) | 기존 |

RED 기대: F4는 소켓 상한 부재로 시간 초과 예상(실제 결함 후보). F1·F2에서 500이면 실제 결함. 첫 실행에서 통과하는 사례는 고의 변경으로 탐지력 확인: F5 = 취소의 `SET LOCAL lock_timeout` 제거(→ 5초 statement timeout으로 상한 초과), F6 = 재현 응답의 replayed 값을 false로 변경, F1~F3 = 503 매핑에서 해당 예외 제거(→ 500).

## C3. 부하 (US3, FR-012~018)

| ID | 판정 | 실패 시 |
|---|---|---|
| L1 low | measure 구간 p95<500ms·unexpected_rate<1%, DB 위반0 | k6 비0 → 미달 기록, 병목 1개 분석 |
| L2 high | created_201==1000·sold_out_409==1000·unexpected==0, DB 확정1000·잔여0 | 결함(정합성) |
| L3 replay | id_mismatch==0·unexpected==0, DB 고유 예약==50 | 결함 |
| L4 rate-limit | 201==20·429==5·Retry-After 정수≥1, DB 예약수==20 | 결함 |
| L5 self-check | `EXPECT_CREATED=999` high 실행 비0 | 판정기 결함 |

각 L1~L4 3회, 매회 reset. 불변식 SQL: 대상별 `available + CONFIRMED != capacity` 또는 범위 위반 행, `(user_id, idempotency_key)` 중복 그룹 → 0행이어야 통과.

## C4. 공통 게이트
- 최종 `./gradlew build deepTest mutationSmoke verifyContractDependencies` 종료0, 그룹별 실행≥1·실패0·skip0. 고의 변경 원복 후 `git diff --exit-code -- backend/src/main`이 고의 변경 부분에 대해 비어 있음.
