# Research: 명령 순서 속성 검증과 변이 탐지력 확인

## R1. 속성 도구

- **Decision**: 기존 QuickTheories 0.26을 그대로 사용한다. `qt().withFixedSeed(seed).withExamples(n).withShrinkCycles(c).forAll(gen).checkAssert(...)`.
- **Rationale**: 1단계에서 Java25/Jupiter6.0.3 최소 실행·seed 재현·반례 축소(100→51)를 이미 확인했다(docs/versions.md, test-evidence.md). 새 도구를 들이면 호환 검증을 다시 해야 한다.
- **Alternatives**: jqwik(JUnit 엔진 추가·Jupiter6 호환 재검증 필요), 직접 작성한 무작위 루프(shrinking 없음 → 10단계 요구 불충족).

## R2. 속성 IT 발견

- **Decision**: `propertyTest` include에 `**/*IT.class`를 추가한다. 태그 필터 `property`는 유지한다. 클래스 이름은 계획서 3.3/6.1의 `ReservationPropertyIT`.
- **Rationale**: 계획서는 실제 DB 속성 검증을 `*IT`로 명명한다. `integrationTest`는 `excludeTags 'contract','property'`이므로 중복 실행되지 않는다. 기존 `ToolchainPropertyTest`(Unit 속성)는 계속 발견된다.
- **Alternatives**: `ReservationPropertyTest`로 이름 변경(Unit/IT 명명 규칙 위반), 새 태스크 추가(6.2 실행 계약 변경 필요).

## R3. 속성 실행 경로

- **Decision**(구현 갱신: T006에서 `NONE`은 보안 필터 체인의 `HttpSecurity` Bean 부재로 컨텍스트 기동 실패 → 실제 Bean 그대로 `MOCK` 웹 환경으로 확정. 아래는 설계 당시 문구): `@SpringBootTest(webEnvironment = NONE)` 컨텍스트에서 `ReservationService.create/get/cancel`, `ResourceService.create`를 순차 호출한다. 인증 UUID는 `TestData.USER_A_ID/USER_B_ID`. 컨테이너는 `ContainerTestSupport.postgresOnly()`, 종료는 기존 `Cleanup` listener 패턴(order 2999, afterTestClass close)을 따른다.
- **Rationale**: 계획서 10단계 “ReservationService/get/cancel과 실제 DB”, “Redis 필터를 우회하는 내부 서비스의 순차 정합성 검증”. HTTP·인증·제한은 계약/보안/제한 IT가 담당한다. 서비스 호출은 호출자 트랜잭션이 없어야 하므로 테스트에 `@Transactional`을 두지 않는다.
- **Alternatives**: HTTP 경로(요청 제한 20회/60초에 걸리고 실행이 느림), Writer 직접 호출(Service의 재현·충돌 분기를 우회).

## R4. 결함 주입과 축소 사례

- **Decision**: US2 실패 재현은 `ReservationService.replay`의 `resourceId` 비교 제거로 만든다. 취소 SQL의 `status='CONFIRMED'` 제거는 순차 경로에서 `CancellationDecision`이 먼저 기존 취소를 반환해 관측되지 않으므로 경쟁 IT 탐지 대상으로 분류한다.
- **Rationale**: 이론적 최소 순서가 2건(같은 키·다른 대상 생성)으로 축소 품질을 판정하기 쉽다. 제품 코드의 일시 변경만으로 재현되며 공개 API에 장애 주입 수단을 추가하지 않는다.
- **Alternatives**: 모델에 고의 결함(실제 구현 탐지력이 아님), `available > 0` 제거(DB CHECK 위반 예외로 실패하며 축소 결과가 capacity 생성값에 의존).

## R5. 변이 대상과 게이트

- **Decision**: `pitest.targetClasses`를 세 규칙 FQCN, `targetTests`를 대응 세 Unit FQCN으로 교체하고 `mutationSmoke`에 탐지율 ≥ 80%·대상 밖 변이 0 검사를 추가한다. 명령 이름 `mutationSmoke`는 유지한다.
- **Rationale**: 계획서 6.2는 `mutationSmoke` 하나만 실행 계약에 둔다. 대상 Unit은 Spring 없는 `*Test`이므로 컨테이너가 기동되지 않는다. 기존 게이트의 NO_COVERAGE/TIMED_OUT 등 무효 상태 거부를 유지한다. 위험(explorer 추정): private 생성자·record/enum 생성 메서드의 NO_COVERAGE, `CancellationDecision`의 `requireNonNull` 제거가 switch NPE로 가려지는 생존. 실제 보고서로 확인하고 생존·무효 결과는 분류 후 테스트 보완 또는 사유 기록한다. 무효 상태를 게이트에서 허용으로 바꾸지 않는다.
- **Alternatives**: PIT `mutationThreshold` 옵션만 사용(보고서 기반 독립 판정과 대상 밖 변이 검사가 없음), 별도 `mutationTest` 태스크(실행 계약 문서 변경 증가).

## R6. 고의 변경 4종의 탐지 예상

| 변경 | 위치 | 예상 탐지 | 비고 |
|---|---|---|---|
| A. 차감 `AND available > 0` 제거 | `ResourceRepository.decrementIfAvailable` | 품절 IT(500/CHECK 위반), 속성 IT | DB CHECK가 음수를 막아 예외가 SOLD_OUT으로 분류되지 않음 |
| B. 취소 `AND status = 'CONFIRMED'` 제거 | `ReservationRepository.markCancelled` | `ReservationConcurrencyIT` 중복 취소 경쟁 | 순차 속성 IT는 관측 불가(R4) |
| C. 소유자 `AND user_id = :userId` 제거 | `ReservationRepository.findByIdAndUser` | 소유권 IT·계약 404·속성 IT | 조회·취소 모두 영향 |
| D. 재요청 대상 비교 제거 | `ReservationService.replay` | 멱등 IT·서비스 Unit·속성 IT | US2 결함 |

예상은 실행으로 확인하며, 실제 실패 테스트 목록을 기록한다. 탐지 0이면 테스트를 보완한다(FR-015).

## R7. seed와 실행 수

- **Decision**: 기본 seed `20261023`, `QT_SEED` 환경 변수로 덮어쓴다(build.gradle의 기존 전달 규칙). 생성 예산 150, 실제 실행 횟수 카운터 ≥ 100을 assert한다.
- **Rationale**: QuickTheories는 중복 입력을 생략할 수 있어 예산과 실제 실행이 다르다(1단계 기록). 명령 목록 생성 공간이 커서 중복은 드물지만 실제 수로 판정한다.
