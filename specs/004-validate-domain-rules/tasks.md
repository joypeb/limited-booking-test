# 작업 목록: 예약 입력과 취소 판단의 기본 규칙 검증

**입력**: [명세](spec.md), [계획](plan.md), [조사](research.md), [값 모델](data-model.md), [규칙 계약](contracts/rules-contract.md), [검증 안내](quickstart.md).

**상태**: 33/33 수행. 순수 규칙 구현·Unit 검증 완료, T029는 설명 후 사용자 명시적 완료 지시로 처리(독립 재설명 시험 미실시). 작업 체크는 실제 수행·검증 후에만 표시한다. 맞춤 [요구사항 검토 목록](checklists/domain-rules.md)의 체크와는 의미·소유자가 다르다.

**테스트**: 명세 FR-007~008과 원계획 3단계가 명시적으로 테스트 우선 개발을 요구한다. 코드 작성 후 테스트를 붙이지 않는다. 실제 값·Jupiter·AssertJ를 사용하며 Spring·Mockito·컨테이너를 사용하지 않는다.

## 형식과 경로

`- [ ] T번호 [P 선택] [US번호 이야기 작업만] 설명과 정확한 경로`를 사용한다. 경로는 저장소 루트 기준이다. 신규 Java 파일3개와 대응 Unit3개는 구현·실행 검증됨이다. 모든 Gradle 명령의 실행 위치는 `backend/`다.

[P]는 공통 선행 작업을 마친 뒤 서로 다른 파일을 작성할 수 있다는 뜻이다. Gradle 실행·같은 파일 편집·docs/test-evidence.md 갱신·Git 커밋은 병행하지 않는다. 기본은 US1→US2→US3 순차 진행이며 병행 작업을 자동으로 시작하라는 지시가 아니다.

## 1단계: 작업 환경과 문서 인수 준비

**목적**: 기존 변경을 보존하고 구현 전 검토 상태·작업 위치를 명확히 한다. 새 프로젝트·테스트 태스크·인증/DB 기반을 만들지 않는다.

- [X] T001 knowledge/rules/git-flow.md·knowledge/rules/commit.md에 따라 저장소 최상위·HEAD·브랜치·기존 변경을 확인하고 docs/validate-domain-rules의 미커밋 문서 변경을 선별 보존한다. 현재 문서 작업을 목적별 커밋으로 저장한 후 develop 기반 feature/validate-domain-rules에 명시적으로 반영하는 로컬 분리 절차를 수행하고 실제 명령/결과를 docs/test-evidence.md에 남긴다. 사용자 변경을 함께 커밋하거나 reset/clean/stash·외부 게시로 처리하지 않는다.
- [X] T002 specs/004-validate-domain-rules/checklists/domain-rules.md의 검토자 판정과 미해결 요구사항을 읽고 구현 착수 상태를 docs/test-evidence.md에 기록한다. 항목이 미판정이면 검토자에게 상태를 알리고 기존 승인 지침을 적용한다. 구현 담당자가 체크를 임의 변경하거나 목록 생성만으로 검토 통과를 주장하지 않는다.

## 2단계: 공통 선행 조건

**목적**: 세 이야기 모두가 의존하는 기존 도구·경계·기록 계약을 확인한다. 여기서는 업무 규칙을 구현하지 않는다.

- [X] T003 backend/build.gradle과 backend/src/test/resources/junit-platform.properties를 읽어 *Test 및 태그 제외·현재 실행/XML 게이트·UTF-8/UTC·병렬 비활성 계약을 확인한다. ToolchainProbe·ToolchainSmokeTest·Property/PIT 참조와 backend/src/main/java/com/limitedbooking/backend/reservation/ReservationStatus.java의 기존 두 상태를 대조하고 유지할 범위를 docs/test-evidence.md에 기록한다. 빌드·프로브·스키마를 변경하지 않는다.
- [X] T004 backend/gradle/wrapper/gradle-wrapper.properties와 docs/versions.md를 대조하면서 backend/에서 java --version·./gradlew --version을 실행하고 환경·종료 코드를 docs/test-evidence.md에 기록한다. 규칙별 RED/GREEN 기록은 입력·기대 assertion·명령·종료 코드·실행/실패/오류/skip 수·미검증 범위를 사용하며 컴파일/환경 실패는 기능 RED로 세지 않는 기준을 확정한다.

**선행 조건 완료**: T001~T004가 완료돼야 이야기 작업을 시작한다. T002의 요구사항 검토와 실제 구현 테스트 인수는 구분한다.

## 3단계: US1 — 대상의 유효한 이름과 최초 수량 구분 (P1·MVP)

**목표**: 명세 FR-001~003·SC-001을 충족하는 대상 입력 규칙을 독립 제공한다.

**독립 인수**: `./gradlew test --tests com.limitedbooking.backend.resource.ResourceRulesTest`에서 R-CAPACITY·R-NAME 전체 입력의 정상/거부와 정확한 정리된 이름이 일치한다. 외부 서비스가 필요 없어야 한다.

### 수량 테스트 → 실패 → 구현

- [X] T005 [P] [US1] backend/src/test/java/com/limitedbooking/backend/resource/ResourceRulesTest.java에 acceptsCapacityBoundary(0·1·1000 예외 없음)·rejectsCapacityOutsideRange(-1·1001 IllegalArgumentException)를 파라미터화해 먼저 작성한다. 모델의 제약은 “0≤capacity≤1000, 보정 없음”이다.
- [X] T006 [US1] backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java에 public final/private 생성자와 public static validateCapacity(int): void의 no-op 컴파일 골격만 마련한 뒤 US1 선택 명령에서 -1·1001 거부 assertion이 실제 실패하는지 확인해 docs/test-evidence.md에 기록한다. 컴파일 오류를 RED로 대신하지 않는다.
- [X] T007 [US1] backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java의 validateCapacity에 양끝 포함0~1000 검사와 범위 밖 IllegalArgumentException만 최소 구현하고 같은 수량 테스트 GREEN을 확인해 docs/test-evidence.md에 기록한다. 예외에 입력 원문을 넣거나 메시지 문구를 테스트에 고정하지 않는다.

### 이름 테스트 → 실패 → 구현

- [X] T008 [US1] backend/src/test/java/com/limitedbooking/backend/resource/ResourceRulesTest.java에 normalizesName·rejectsInvalidName을 추가해 null·빈 값·일반 공백/탭/줄바꿈만 있는 값·혼합 공백의 거부, 앞뒤 공백 제거와 내부 공백 유지의 정확한 반환값을 명시한다. 이어 backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java에 public static normalizeName(String): String의 원문 반환 골격만 추가하고 해당 이름 테스트의 실제 assertion RED를 docs/test-evidence.md에 기록한다. 모델의 순서는 “null 거부, strip 후 검사”다.
- [X] T009 [US1] backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java의 normalizeName에 null·정리 후 빈 값의 IllegalArgumentException과 strip 전처리만 최소 구현하고 현재 이름/수량 테스트 GREEN을 기록한다. 길이 규칙을 아직 선행 구현하지 않는다.
- [X] T010 [US1] backend/src/test/java/com/limitedbooking/backend/resource/ResourceRulesTest.java에 countsCodePoints·preservesNonBreakingSpaces·재정리 동일값 사례를 추가한다. R-NAME의 일반 문자1/100/101·😀100/101·가99+😀·앞뒤 공백+100개, U+00A0/U+2007/U+202F 각각 단독/앞뒤/100/101을 고정값으로 명시하고101개 거부의 실제 RED를 docs/test-evidence.md에 기록한다. 모델 제약 “코드 포인트1~100, 내부 문자와 NBSP 계열 보존, 재정리 동일”을 그대로 적용한다.
- [X] T011 [US1] backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java에 strip 결과의 codePointCount(0, length())를 이용한1~100 포함 검사를 최소 구현하고 같은 테스트 전체 GREEN을 기록한다. 길이 초과 절단·추가 NBSP 금지·Unicode 변환을 넣지 않는다.

### 이야기 인수와 기록

- [X] T012 [US1] backend/src/test/java/com/limitedbooking/backend/resource/ResourceRulesTest.java를 규칙 계약 R-CAPACITY/R-NAME 전체와 대조해 입력 누락·실제 데이터 대신 mock·구현 계산으로 만든 기대값이 없는지 검토한다. US1 선택 명령의 최종 결과와 backend/build/test-results/test/TEST-com.limitedbooking.backend.resource.ResourceRulesTest.xml의 실제 실행>0·실패/오류/skip0을 docs/test-evidence.md에 기록한다. 새 코드 변경 없이 직전 같은 실행으로 증명됐다면 반복하지 않는다.
- [X] T013 [US1] knowledge/domain/resource.md·knowledge/README.md에 실제 ResourceRules/ResourceRulesTest 연결과 순수 규칙 실행 상태를 갱신하고 docs/decisions.md·docs/test-evidence.md에 strip/코드 포인트 선택·RED/GREEN·assertion 탐지 대상을 연결한다. 4단계 HTTP 사용과 사용자 설명 인수는 아직 미완료로 구분한다.
- [X] T014 [US1] knowledge/rules/commit.md에 따라 backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java·backend/src/test/java/com/limitedbooking/backend/resource/ResourceRulesTest.java와 T013의 관련 문서 변경만 경로/조각 단위로 검토·스테이징해 docs/test-evidence.md의 실제 GREEN을 근거로 feat(resource) 목적 커밋을 작성한다. 커밋 메시지 설명은 한국어로 쓰고 외부 push/병합은 하지 않는다.

**체크포인트**: US1만으로 이름·수량 규칙을 독립 시연할 수 있다. 예약 생성·취소·HTTP 사용 완료를 뜻하지 않는다.

## 4단계: US2 — 생성 키의 정확한 값과 경계 보존 (P1)

**목표**: FR-004~005·SC-002에 따라 검증된 키 원문과 값 동등성을 보존한다. US1 생산 코드에 의존하지 않는다.

**독립 인수**: `./gradlew test --tests com.limitedbooking.backend.reservation.IdempotencyKeyTest`에서 K-KEY 전체를 parse와 public 생성자 두 경로에 적용하고 같은 원문/다른 대소문자를 구분한다.

- [X] T015 [P] [US2] backend/src/test/java/com/limitedbooking/backend/reservation/IdempotencyKeyTest.java에 rejectsInvalidKey·preservesValidKey·rejectsWhitespaceAnywhere·rejectsNonAsciiOrSeparators·preservesCaseSensitiveEquality·rejectsInvalidDirectConstruction을 먼저 작성한다. 모델 제약 “value는 null 불가·ASCII 영문 대소문자/숫자/하이픈/밑줄1~64자이며 원문을 유지한다”와 “public 생성자와 parse가 같은 불변식을 보장”을 그대로 적용한다. K-KEY의 null/빈 값·A/a/0/-/_·Ab-1_9·64/65·모든 공백 위치·끝 LF/CRLF·한글/😀/구분문자/NBSP/전각을 두 생성 경로에 포함하고 Ab-1 동일값 equals/hashCode 및 ab-1과의 비동등을 명시한다.
- [X] T016 [US2] backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java에 public record IdempotencyKey(String value)와 생성자로 위임하는 public static parse(String): IdempotencyKey의 무검증 골격을 마련한다. US2 선택 명령에서 잘못된 키가 수용돼 IAE 기대가 실패하는 실제 RED를 docs/test-evidence.md에 기록한다. 이미 성립하는 record 동등성을 억지로 깨지 않는다.
- [X] T017 [US2] backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java의 compact constructor에 null 검사·private static final Pattern `^[A-Za-z0-9_-]{1,64}$`의 matcher.matches() 검증과 IllegalArgumentException을 최소 구현한다. value()·기본 equals/hashCode를 유지하고 원문 전처리·입력값 메시지 노출 없이 두 생성 경로의 같은 테스트 GREEN을 docs/test-evidence.md에 기록한다.
- [X] T018 [US2] backend/src/test/java/com/limitedbooking/backend/reservation/IdempotencyKeyTest.java를 K-KEY 전체와 대조해 직접 생성 우회·끝 줄바꿈·대소문자 경계 누락이 없는지 검토한다. US2 선택 실행의 실제 실행>0·실패/오류/skip0과 backend/build/test-results/test/TEST-com.limitedbooking.backend.reservation.IdempotencyKeyTest.xml을 docs/test-evidence.md에 기록한다. 다른 값의 hashCode가 반드시 다르거나 객체 참조가 같다는 assertion은 요구하지 않는다.
- [X] T019 [US2] knowledge/domain/reservation.md·knowledge/README.md·docs/decisions.md·docs/test-evidence.md에 키 객체의 실제 코드/테스트·실행·예외/동등성 선택을 반영한다. 사용자·키 유일성, 대상 충돌, TTL, HTTP 변환과5단계 요청 경로 사용은 미완료로 유지하고 기존 Repository·StoredReservation의 String을 교체하지 않는다.
- [X] T020 [US2] knowledge/rules/commit.md에 따라 backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java·backend/src/test/java/com/limitedbooking/backend/reservation/IdempotencyKeyTest.java와 T019의 관련 문서 조각만 검토·스테이징하고 docs/test-evidence.md의 GREEN을 근거로 feat(reservation) 키 규칙 목적 커밋을 작성한다. 외부 push/병합은 하지 않는다.

**체크포인트**: 저장·인증·다른 이야기 없이 키의 값/경계를 검증할 수 있다. 성공 요청 멱등성의 구현 완료가 아니다.

## 5단계: US3 — 현재 상태에 맞는 취소 판단 (P2)

**목표**: FR-006·SC-003의 판단 두 가지를 상태 변경 없이 제공한다. 기존 ReservationStatus 외 다른 이야기 생산 코드에 의존하지 않는다.

**독립 인수**: `./gradlew test --tests com.limitedbooking.backend.reservation.CancellationDecisionTest`에서 두 상태·반복 판단·누락 상태 오류를 검증한다. 순수 구조는 코드 검토로 함께 확인한다.

- [X] T021 [P] [US3] backend/src/test/java/com/limitedbooking/backend/reservation/CancellationDecisionTest.java에 mapsReservationStatus·repeatsDecisionWithoutStateChange·rejectsMissingInternalStatus를 먼저 작성한다. 모델의 “CONFIRMED | CANCEL_NOW”, “CANCELLED | ALREADY_CANCELLED”, “null | 정상 판단 없음, NPE”를 그대로 인수값으로 삼고 각 상태 반복 결과를 확인한다.
- [X] T022 [US3] backend/src/main/java/com/limitedbooking/backend/reservation/CancellationDecision.java에 public enum CANCEL_NOW/ALREADY_CANCELLED와 public static from(ReservationStatus): CancellationDecision의 null 반환 골격을 마련한다. 기존 backend/src/main/java/com/limitedbooking/backend/reservation/ReservationStatus.java를 재사용하고 US3 선택 명령에서 두 판단의 실제 값 불일치 RED를 docs/test-evidence.md에 기록한다.
- [X] T023 [US3] backend/src/main/java/com/limitedbooking/backend/reservation/CancellationDecision.java에 Objects.requireNonNull과 default 없는 두 상태 switch만 최소 구현하고 같은 Unit GREEN을 docs/test-evidence.md에 기록한다. 모델의 “판단 enum은 저장 상태가 아니며 CANCEL_NOW를 DB status나 공개 응답에 사용하지 않는다”를 유지한다.
- [X] T024 [US3] backend/src/main/java/com/limitedbooking/backend/reservation/CancellationDecision.java와 대응 backend/src/test/java/com/limitedbooking/backend/reservation/CancellationDecisionTest.java를 검토해 가변 필드·I/O·Clock·저장 의존성과 예약 mock 없이 반복 판단을 검증하는지 확인한다. 선택 실행과 backend/build/test-results/test/TEST-com.limitedbooking.backend.reservation.CancellationDecisionTest.xml의 실제 실행>0·실패/오류/skip0을 docs/test-evidence.md에 기록한다.
- [X] T025 [US3] knowledge/domain/reservation.md·knowledge/README.md·docs/decisions.md·docs/test-evidence.md에 판단의 실제 코드/테스트와 두 상태/내부 null 오류의 인수 범위를 반영한다. 실제 상태 갱신·수량 복구·동일 취소 시각과6단계 이미 취소됨 분기 사용은 미완료로 남긴다.
- [X] T026 [US3] knowledge/rules/commit.md에 따라 backend/src/main/java/com/limitedbooking/backend/reservation/CancellationDecision.java·backend/src/test/java/com/limitedbooking/backend/reservation/CancellationDecisionTest.java와 T025의 관련 문서 조각만 검토·스테이징하고 docs/test-evidence.md의 GREEN을 근거로 feat(reservation) 취소 판단 목적 커밋을 작성한다. 외부 push/병합은 하지 않는다.

## 6단계: 전체 Unit 인수·설명·문서 정리

**선행**: 세 이야기 구현/개별 검증 완료. FR-007~008·SC-004~005를 이야기별 기록과 합쳐 인수한다.

- [X] T027 backend/에서 ./gradlew test를 실행해 새 세 Unit과 기존 ToolchainSmokeTest만 일반 Unit으로 발견되는지 backend/build/test-results/test/TEST-*.xml과 현재 listener 합계로 대조하고 docs/test-evidence.md에 기록한다. 각 신규 클래스 실행>0·전체 실패/오류/skip0·합계 일치, 기존 프로브3회와 신규 invocation 수 구분을 요구한다. backend/build.gradle의 게이트/태그/캐시 정책은 바꾸지 않는다.
- [X] T028 specs/004-validate-domain-rules/quickstart.md·specs/004-validate-domain-rules/contracts/rules-contract.md 전체와 새 세 Unit의 assertion을 대조해 허용/거부/반환값/동등성/판단별 탐지 대상 및 실제 RED/GREEN 증거를 docs/test-evidence.md에 연결한다. 누락은 담당 테스트에 보완하고 변경된 범위만 재검증한다. 기존 프로브를 업무 PIT/Property 검증으로 주장하지 않는다.
- [X] T029 docs/test-evidence.md에 개발자가 설명한 경계값 최소1개의 입력·기대 결과·이유와 전체 assertion의 확인 대상/실패 조건에 대한 확인 근거를 기록한다. 사용자 답변/확인 없이 예시 문장만으로 완료하지 않으며 답변 대기 중이면 이 작업은 미완료로 남기고 독립 문서 정리는 계속한다.
- [X] T030 README.md·docs/versions.md·docs/decisions.md·knowledge/README.md·knowledge/domain/resource.md·knowledge/domain/reservation.md를 실제 새 클래스·Unit 명령/범위/실행 수와 동기화한다. 한정 수량 예약 백엔드 로컬 개발 계획.md의3단계 체크는 대응 실행/설명 증거가 있는 항목만 반영하고4~6단계 요청 경로 사용은 별도 미완료로 명시한다. 버전·OpenAPI·DB/Redis 계약을 임의 변경하지 않는다.
- [X] T031 specs/004-validate-domain-rules/의 spec.md·plan.md·data-model.md·contracts/rules-contract.md·quickstart.md에서 상태·문서 트리·미구현 표현을 실제 코드와 실행 증거에 맞춰 갱신한다. 순수 규칙 구현·Unit 실행·개발자 설명 인수를 각각 구분하고, 실제 요청 경로 사용은 후속4~6단계에서 검증될 때까지 미완료로 유지한다. checklists/domain-rules.md는 읽기 전용으로 보존하며 기능 테스트 통과를 근거로 판정 표시를 변경하지 않는다.
- [X] T032 specs/004-validate-domain-rules/tasks.md의 FR/SC 추적표·완료 표시와 docs/test-evidence.md를 대조하고 Markdown 링크/공백·git diff --check·고의 골격 잔류·변경 범위를 검토한다. 독립 코드 리뷰에서 검증된 결함이 나오면 관련 테스트의 실패→수정→재검증 근거를 남긴다. 사용자 설명·요청 경로·심화 미검증은 숨기지 않는다.
- [X] T033 knowledge/rules/commit.md에 따라 T027~T032의 실제 수행 결과에 해당하는 문서/보완 변경만 검토·스테이징해 목적 커밋으로 저장하고 최종 Git 상태·핵심 코드/테스트·미완료/미검증·다음4단계 작업을 docs/test-evidence.md와 완료 보고에 남긴다. T029가 미완료면 전체 완료를 주장하지 않고 유지하며 push/PR·병합·태그는 수행하지 않는다.

T029 인수 근거(2026-10-16): 사용자가 경계값과 오류 탐지 설명을 요청해 안내한 뒤 “이제 T029처리해”라고 명시적으로 지시했다. 이 최신 지시를 근거로 설명 인수를 처리했으며 사용자 독립 재설명 능력 검증을 완료했다고 주장하지 않는다. 상세 대화 요약·한계는 [실행 증거](../../docs/test-evidence.md)의 T029 완료 기록을 따른다.

## 의존성과 실행 순서

```text
T001 → T002 → T003 → T004
                      ├─ US1: T005 → T006 → T007 → T008 → T009 → T010 → T011 → T012 → T013 → T014
                      ├─ US2: T015 → T016 → T017 → T018 → T019 → T020
                      └─ US3: T021 → T022 → T023 → T024 → T025 → T026
세 이야기 코드/검증 완료 → T027 → T028 → T029(사용자 설명)
                                      └─ T030 → T031 → T032 → T033
```

T029와 T030은 같은 시점에 기록 파일을 편집하지 않도록 순차 저장한다. 설명 답변 대기와 독립 문서 정리는 구분하며 전체 완료는 T029를 포함해 판정한다. 공통 문서/커밋은 기본 US1→US2→US3 순서로 직렬화한다. 작업 번호는 권장 순서를 뜻하며 각 이야기에는 다른 이야기 구현의 의미적 의존성이 없다.

### 이야기별 병행 예시

| 이야기 | 가능한 병행 작성 | 그 뒤 순차 수행 |
| --- | --- | --- |
| US1 | T005의 ResourceRulesTest.java 작성은 T015/T021과 파일이 다름 | T006~T014의 RED/구현/GREEN·공통 기록·커밋 |
| US2 | T015의 IdempotencyKeyTest.java 작성은 T005/T021과 파일이 다름 | T016~T020의 RED/구현/GREEN·공통 기록·커밋 |
| US3 | T021의 CancellationDecisionTest.java 작성은 T005/T015와 파일이 다름 | T022~T026의 RED/구현/GREEN·공통 기록·커밋 |

병행 테스트 초안에 미존재 타입 참조가 남아 있으면 공유 sourceSet 컴파일이 막힌다. 기본 순차 실행을 권장하며, 실제 병행 시 세 초안과 컴파일 골격 준비를 조율한 후 하나의 Gradle 실행 담당자만 테스트한다. 미완성 초안을 skip/exclude로 숨기거나 컴파일 실패를 RED로 세지 않는다. 한 이야기 안의 같은 테스트/생산 파일 작업에는 [P]를 붙이지 않았다.

## 요구사항·인수 추적

| 명세 | 담당 작업·증거 |
| --- | --- |
| FR-001·SC-001 수량 | T005~T007·T012, R-CAPACITY |
| FR-002~003·SC-001 이름 | T008~T012, R-NAME 전체·반환 값·재정리 |
| FR-004~005·SC-002 키 | T015~T018, K-KEY 양 생성 경로·값 동등성 |
| FR-006·SC-003 취소 판단 | T021~T024, C-DECISION·순수 구조 |
| FR-007·SC-004 독립 검증 | 세 Unit·T003~T004·T027~T028 |
| FR-008·SC-005 실행/설명 | 각 RED/GREEN·T013/T019/T025·T027~T033, T029 설명 별도 |
| 전제의 실제 요청 경로 사용 | T030~T031에서 미완료 추적,4~6단계 실제 구현/HTTP 인수는 범위 밖 |

## 구현 전략과 인수 경계

권장 MVP는 공통 선행 작업 뒤 US1(T005~T014)이다. 이 결과만으로 대상 입력 규칙을 독립 검증할 수 있다. 이후 US2·US3를 각각 인수하고 전체 Unit과 설명 기록을 마친다. 배포/외부 공개 단계는 없다.

각 이야기의 최초 동작 RED는 검증 없는 골격에서 확인한다. 이미 일반 구현으로 만족하는 후속 회귀 assertion을 추가할 때 억지로 생산 코드를 망가뜨려 RED를 만들지 말고 기존 동작 보장으로 기록한다. 컴파일 골격·고의 오류는 최종 코드에 남기지 않는다.

세 신규 클래스/테스트·docs/knowledge만 기본 변경 범위다. 기존 ReservationStatus·Repository·StoredReservation·Flyway V1·OpenAPI·프로브/PIT·의존성을 유지한다. 기존 계획과 충돌하는 변경이 필요하면 근거·영향을 먼저 기록하고 계약/테스트/문서를 함께 갱신한다. 새 변경·실패가 없다면 IT/Contract/Property/PIT/부하를 추가 반복하지 않는다.

맞춤 체크리스트는 요구사항 품질, tasks 체크는 실제 작업 완료, 원계획 체크는 대응 단계 인수를 나타낸다. 어느 하나를 다른 것의 대리 증거로 쓰지 않는다.
