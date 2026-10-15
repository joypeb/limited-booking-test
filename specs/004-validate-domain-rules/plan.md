# 구현 계획: 예약 입력과 취소 판단의 기본 규칙 검증

> 구현 담당자는 `superpowers:executing-plans` 또는 `superpowers:subagent-driven-development`로 한 검증 대상씩 구현한다. 이 문서는 Phase 1 설계까지의 산출물이며 상세 작업 목록은 [tasks.md](tasks.md)에 생성돼 있다. 순수 규칙 구현·Unit 검증을 완료했으며 사용자 설명(T029)은 2026-10-16 설명 후 명시적 완료 지시로 처리했다(독립 재설명 시험 미실시).

**브랜치**: `feature/validate-domain-rules` (문서 원본: docs/validate-domain-rules) | **날짜**: 2026-10-15 | **명세**: [spec.md](spec.md)

**목표**: 실제 값을 사용하는 순수 Unit으로 이름·최초 수량·생성 키·취소 판단의 경계를 검증하고 각 assertion의 의미를 설명할 수 있게 한다.

**구조**: 기존 resource·reservation 패키지에 상태 없는 규칙과 불변 키 값 객체를 둔다. 기존 ReservationStatus를 재사용하며 서비스·저장 계층·HTTP를 이번 단계에 추가하지 않는다.

**기술**: Java25·기존 Gradle9.7.1, Jupiter·AssertJ. 신규 라이브러리는 필요하지 않다.

**입력**: `specs/004-validate-domain-rules/spec.md`. setup-plan 출력 BRANCH의 `004-validate-domain-rules`는 활성 기능 식별값이며 실제 Git 브랜치는 위 값이다. 명세·clarify 문서를174da7d에 보존하고 develop 기반 구현 브랜치에e3de6ab로 반영했다.

## 요약

ResourceRules는 이름을 strip한 뒤 코드 포인트 수로 검사하고 정수 capacity를 검증한다. IdempotencyKey는 생성 경로 전체에서 검증되는 record이며 원문 기반 값 동등성을 제공한다. CancellationDecision은 기존 두 예약 상태를 취소 진행·이미 취소됨으로 대응시킨다. 업무 규칙은 명세대로 유지하며 예외 타입·생성 방식·테스트 발견 규칙만 이 계획에서 구체화한다.

조사 근거는 [research.md](research.md), 값과 상태는 [data-model.md](data-model.md), 후속 계층이 사용할 공개 Java 인터페이스와 테스트 행렬은 [규칙 계약](contracts/rules-contract.md), 재현 절차는 [quickstart.md](quickstart.md)를 따른다. 공개 HTTP 변경은 없으며 [기존 OpenAPI](../../docs/openapi.yaml)를 유지한다.

## 기술 맥락

| 항목 | 선택·현재 상태 |
| --- | --- |
| 언어·버전 | Java25, preview 제외. 이번 java --version은 Corretto25.0.4+7-LTS |
| 주요 의존성 | 기존 Boot4.1.1·Gradle Wrapper9.7.1 유지. 순수 규칙은 JDK만 사용 |
| 저장소 | 이번 규칙에 저장소 의존성 없음. 기존 PostgreSQL 업무 V1·Repository와 Redis 구성은 유지 |
| 검증 | 기존 Jupiter6.0.3 기록·AssertJ 사용. 세 `*Test`를 일반 Unit으로 추가, Spring·컨테이너·Mockito 미사용 |
| 실행 플랫폼 | localhost 개발 JVM, UTF-8·UTC, 클래스 병렬 실행 비활성 유지 |
| 프로젝트 유형 | 단일 백엔드의 순수 도메인 규칙과 후속 Java 호출 계약 |
| 성능 목표 | 새 지연·처리량 목표 없음. 문자열 경계의 정확성과 외부 서비스 없는 재현을 인수 |
| 제약 | 수량0~1000, 이름 정리 후 코드 포인트1~100, 키 ASCII 지정 문자1~64·원문 보존, 상태 판단만 수행 |
| 규모·범위 | 새 생산 클래스3·새 Unit3, 기존 상태 enum1 재사용. HTTP·SQL·경쟁·심화 검증은 후속 |

Jupiter 등 의존성 버전은 [버전 기록](../../docs/versions.md)·현재 빌드 대조값이다. 이번 계획 작성 중 Gradle 의존성 해석이나 기능 테스트를 다시 실행하지 않았다.

## 헌법 확인

| 게이트 | 설계 전 | Phase 1 후 재평가 |
| --- | --- | --- |
| I. 서비스 경계·API 호환성 | 통과 | 기능별 순수 규칙만 제공. Writer·Controller·SQL·OpenAPI 변경 없음 |
| II. 도메인 근거 | 통과 | knowledge 안내·규칙 전체·resource/reservation, 계획서2~5절·3단계와 실제 코드 대조. 신규 도메인 없음 |
| III. 검증·증거 | 통과 | 동작 RED→최소 구현→GREEN. Unit 발견/XML/0건·skip 거부를 유지하고 실행·설명 인수를 분리 |
| IV. 문서 동기화 | 통과 | 명세 정책 유지. 이번 문서 목록 등록, 구현 때 도메인·README·docs의 실제 상태 갱신 |
| V. 미확정 정책 | 통과 | clarify 질문0. 예외·record·프로브 보존은 구현 선택으로 조사에 기록, 미해결 결정 없음 |
| 로컬·Git·비밀정보 | 통과 | 설계 시 docs 브랜치 유지, 구현은 develop 기반 feature 브랜치 사용. 외부 게시·개발 DB 접속·볼륨 삭제 없음 |

헌법의 과거 골격 상태·채택일/CI TODO는 현재 코드 및 실행 기록과 구분한다. 해당 TODO는 순수 규칙 설계를 막지 않으며 헌법을 임의 개정하지 않는다. 정당화할 설계 위반은 없다.

## 프로젝트 구조

### 이번 기능 문서

```text
specs/004-validate-domain-rules/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/rules-contract.md
  checklists/requirements.md
  checklists/domain-rules.md   # 요구사항 품질34/34 충족
  tasks.md                    # 작업33/33 수행, T029 사용자 완료 지시 반영
```

계획 명령은 `tasks.md`를 생성하지 않았다. 이후 사용자의 `$speckit-checklist`·`$speckit-tasks` 요청으로 [맞춤 검토 목록](checklists/domain-rules.md)과 [작업 목록](tasks.md)을 생성했으며 요구사항 품질 판정·규칙 구현·Unit 검증을 완료했다.

### 구현 단계의 파일·책임

아래 새 파일3개와 대응 Unit3개를 구현·실행 검증했다.

| 경로 | 처리·책임 |
| --- | --- |
| backend/src/main/java/com/limitedbooking/backend/resource/ResourceRules.java | 신규. public final 클래스, private 생성자, public static normalizeName/validateCapacity |
| backend/src/main/java/com/limitedbooking/backend/reservation/IdempotencyKey.java | 신규. public record(value), 검증 compact constructor·public static parse |
| backend/src/main/java/com/limitedbooking/backend/reservation/CancellationDecision.java | 신규. public enum 두 판단값·public static from |
| backend/src/main/java/com/limitedbooking/backend/reservation/ReservationStatus.java | 기존 CONFIRMED/CANCELLED 그대로 재사용 |
| backend/src/test/java/com/limitedbooking/backend/resource/ResourceRulesTest.java | 신규. 수량·이름 실제 입력의 허용/거부·반환 값 |
| backend/src/test/java/com/limitedbooking/backend/reservation/IdempotencyKeyTest.java | 신규. 키 구문·원문·동등성·생성자 우회 방지 |
| backend/src/test/java/com/limitedbooking/backend/reservation/CancellationDecisionTest.java | 신규. 두 상태·반복 판단·null 내부 오류 |

기존 build.gradle·ToolchainProbe·ToolchainSmokeTest·Property·Contract·IT는 유지한다. PIT/Property가 현재 프로브에 의존하므로 이번 단계에서 일부만 삭제하지 않는다. 규칙의 실제 사용 조건은 아래 후속 연동 표로 추적하며 프로브 유지가 업무 검증을 대신하지 않는다.

구현 시 README·knowledge/domain/resource.md·reservation.md·knowledge/README.md·docs/decisions.md·test-evidence.md·versions.md를 실제 코드/실행 상태에 맞춰 갱신한다. 계획서 3단계 체크박스는 대응 실행·설명 확인 후에만 갱신하며 전체 요청 경로 사용 조건은 별도 미완료로 유지한다. OpenAPI 변경 필요성이 생기면 기존 계약을 먼저 대조한다.

## Phase 0: 조사와 결정

읽기 전용 조사 에이전트 두 개로 JDK25 공식 동작과 기존 Gradle·프로브 의존성을 확인했다. [조사](research.md)의 결정·이유·대안을 통합했다. 이름 strip/코드 포인트, 키 전체 일치·검증 생성자, 기존 enum 재사용, 예외 분류, Unit 발견·보고서 게이트와 프로브 보존의 결정이 해소됐다. JDK 문서 확인은 신규 업무 클래스 실행 검증을 뜻하지 않는다.

## Phase 1: 데이터·인터페이스 설계

- `ResourceRules.normalizeName(String): String`: null은 IllegalArgumentException. strip 후 codePointCount(0, length())가1~100일 때 정리한 값을 반환한다. 내부 문자·NBSP 계열을 별도 제거하거나 Unicode 정규화하지 않는다.
- `ResourceRules.validateCapacity(int): void`: 0~1000을 포함해 정상 반환, 나머지는 IllegalArgumentException. 입력을 보정하지 않는다.
- `IdempotencyKey.parse(String): IdempotencyKey`: compact constructor로 위임한다. `record IdempotencyKey(String value)`의 모든 public 생성 경로가 동일 검증을 거친다. private static final Pattern에 계획서의 정규식 `^[A-Za-z0-9_-]{1,64}$`를 두고 matcher.matches()로 검사한다. null/불일치는 IllegalArgumentException. value()·equals/hashCode는 원문 기반이며 raw key를 예외 메시지에 넣지 않는다.
- `CancellationDecision.from(ReservationStatus): CancellationDecision`: Objects.requireNonNull 다음 default 없는 enum switch로 CONFIRMED→CANCEL_NOW, CANCELLED→ALREADY_CANCELLED. null은 프로그래밍 오류인 NullPointerException이며 성공 판단으로 대체하지 않는다. 저장 상태 enum에 판단값을 추가하지 않는다.
- 오류 타입은 Java 내부 계약의 구현 선택이다. 후속 HTTP 경계에서는 해당 입력 검사 호출에서만 무효 입력을 INVALID_REQUEST로 번역하고 모든 IllegalArgumentException/NPE를 일괄400으로 숨기지 않는다. 이번에 예외 핸들러를 구현하지 않는다.
- 시간·UUID·저장 상태를 만들지 않아 Clock 주입이 불필요하다. 상태·수량·시각 부작용 없음은 입력/출력 시그니처·불변 타입·의존성 검토 및 반복 호출 결과로 확인한다.

## 전역 실행 제약과 검토 초점

모든 구현 단위는 명세와 [계약 행렬](contracts/rules-contract.md)을 함께 읽는다. 클래스를 완성한 뒤 테스트를 추가하는 순서로 바꾸지 않는다. 컴파일 가능한 현재 검증 대상의 최소 골격 뒤 실제 assertion 실패를 확인하며, 골격에 아직 시험하지 않은 검증을 선행 구현하지 않는다. 골격·고의 결함을 최종 코드에 남기지 않는다.

특히 다음 다섯 실패 조건을 각 담당 Unit에 포함한다: 😀100개를 UTF-16 길이로 거부, 원문100자 초과지만 strip 후100인 이름 거부, NBSP 제거/금지, 키 끝 줄바꿈 부분 일치 허용, public 키 생성자로 검증 우회. 각각 ResourceRulesTest 또는 IdempotencyKeyTest가 소유한다.

## 구현 순서와 검증 경계

### 1. 대상 입력 규칙 — FR-001~003·SC-001

**파일/인터페이스**: 표의 ResourceRules.java·ResourceRulesTest.java. 생산 public static 시그니처는 Phase1 그대로 사용한다.

- [X] `acceptsCapacityBoundary`(0·1·1000 정상)와 `rejectsCapacityOutsideRange`(-1·1001 IAE)를 먼저 작성한다. 컴파일용 validateCapacity의 검증 없는 골격에서 거부 assertion이 실패하는지 선택 Unit을 실행한다.
- [X] 경계 비교만 최소 구현하고 같은 수량 사례를 통과시킨다. 이어 `normalizesName`·`rejectsInvalidName`·`countsCodePoints`·`preservesNonBreakingSpaces`에 계약 R-NAME 전체 입력을 그룹별로 추가하고 각각 RED→GREEN으로 진행한다. 정리 전 raw 반환 골격은 앞뒤 공백 반환값 불일치, 길이 검사 없는 골격은101자 거부 실패로 관측한다.
- [X] 핵심 assertion 형태를 유지한다: `assertThat(ResourceRules.normalizeName(" 😀 ")).isEqualTo("😀")`; `assertThatThrownBy(() -> ResourceRules.validateCapacity(-1)).isInstanceOf(IllegalArgumentException.class)`. 모든 값·경계는 계약 행렬에서 가져오며 정확한 예외 문구는 고정하지 않는다.
- [X] `./gradlew test --tests com.limitedbooking.backend.resource.ResourceRulesTest`로 실패/성공·발견 수를 기록한다. 통과 뒤 관련 문서와 한 목적 커밋을 준비한다. backend/에서 실제 실행했으며 RED/GREEN을 증거 문서에 기록했다.

### 2. 생성 키 값 객체 — FR-004~005·SC-002

**파일/인터페이스**: IdempotencyKey.java·IdempotencyKeyTest.java. public record(value)·parse(String)·value()·값 동등성을 제공한다.

- [X] `rejectsInvalidKey`를 작성하고 무검증 생성자/parse 위임 골격에서 null·공백·65자 등의 거부 실패를 확인한다. `preservesValidKey`는1자·64자·허용 문자별1자·혼합 키의 원문을 확인한다.
- [X] canonical constructor 검증을 최소 구현한다. `preservesCaseSensitiveEquality`에 `parse("Ab-1")`와 같은 원문 equals/hashCode 일치, `parse("ab-1")`와 비동등을 확인한다. `rejectsInvalidDirectConstruction`으로 `new IdempotencyKey("bad.key")` 등 public 생성자도 parse와 동일하게 거부하는지 고정한다.
- [X] `rejectsWhitespaceAnywhere`와 `rejectsNonAsciiOrSeparators`에 계약 K-KEY 전체를 포함한다. 직접 생성 경로에도 같은 정상/거부 행렬을 적용한다. `assertThat(IdempotencyKey.parse("Ab-1").value()).isEqualTo("Ab-1")`처럼 값으로 확인하며 인스턴스 동일성은 요구하지 않는다.
- [X] `./gradlew test --tests com.limitedbooking.backend.reservation.IdempotencyKeyTest`로 같은 테스트의 GREEN과 실행 수를 확인하고 관련 문서·커밋을 준비한다.

### 3. 취소 판단 — FR-006·SC-003

**파일/인터페이스**: CancellationDecision.java·CancellationDecisionTest.java, 기존 ReservationStatus 소비.

- [X] `mapsReservationStatus`에 두 enum 값과 기대 판단을 파라미터화한다. null 반환 골격에서 실제 판단 불일치를 확인한 뒤 두 분기만 구현한다.
- [X] `repeatsDecisionWithoutStateChange`는 각 상태를 반복 전달해 같은 결과를 확인한다. `rejectsMissingInternalStatus`는 null의 NPE를 확인한다. 시각/수량 mock을 만들지 않으며 필드·I/O 부재는 코드 검토로 확인한다.
- [X] `./gradlew test --tests com.limitedbooking.backend.reservation.CancellationDecisionTest`의 GREEN을 확인하고 관련 문서·커밋을 준비한다.

### 4. 전체 Unit 인수와 기록 — FR-007~008·SC-004~005

- [X] backend/에서 `./gradlew test`를 한 번 실행해 새 세 클래스와 기존 ToolchainSmokeTest가 발견되는지 XML/현재 결과를 대조한다. 실행>0·실패0·오류0·skip0을 확인하고 업무 규칙 수와 기존 프로브3회는 구분한다. 고정 테스트 총수를 추정해 통과 기록으로 적지 않는다.
- [X] 위 각 RED의 명령·대상 입력·assertion 실패를 GREEN과 함께 docs/test-evidence.md에 남긴다. ClassNotFound·컴파일·환경 실패는 RED로 세지 않는다. 설명 인수는 개발자가 예를 들어 capacity=0의 허용 이유와 각 assertion의 탐지 대상을 설명한 근거로 따로 기록한다. 사용자 답변이 없으면 설명 인수는 미완료다.
- [X] 전체 diff와 문서 링크·공백을 검사한 뒤 목적별 커밋을 작성한다. 문서174da7d를 보존하고 develop에서 feature/validate-domain-rules를 생성해e3de6ab로 반영했다. develop 병합·외부 게시·태그는 수행하지 않았다.

테스트 클래스는 package-private, Spring 어노테이션·상속 지원·property/contract 태그 없이 `*Test` 이름을 쓴다. `--tests`는 실행 선택이며 공유 test sourceSet의 컴파일까지 줄이지 않는다. 기존 build.gradle의 listener/XML/현재 실행 게이트·캐시 비활성화를 유지한다. 이 범위에서 build/verify/deepTest/PIT·DB·부하를 반복할 필요는 없다. 관련 생산 코드·공유 지원 코드 변경이나 실제 실패가 생기면 영향 검증을 추가한다.

## 완료 판정과 후속 연동

| 대상 | 이번 인수 | 후속 사용·미완료 조건 |
| --- | --- | --- |
| ResourceRules | ResourceRulesTest 전체 경계와 반환 값 | 4단계 대상 생성에서 두 메서드 호출, 실제 HTTP 입력/저장 이름 검증 |
| IdempotencyKey | IdempotencyKeyTest 원문·동등성·우회 방지 | 5단계 예약 생성 입력에서 parse 후 사용자 UUID와 함께 Service 전달, 멱등 재현/충돌 IT |
| CancellationDecision | CancellationDecisionTest 두 판단·반복 | 6단계 Writer의 이미 취소됨 분기에 사용, 중복 취소 상태·시각·수량 IT |
| 실행·설명 | Unit 게이트·RED/GREEN 기록·설명 확인 | 요청 경로 사용은 위 세 연동이 검증될 때까지 미완료 |

자체 검토로 FR-001~008·SC-001~005의 담당 검증, 인터페이스 이름·예외 분류·행렬 누락을 대조했다. 위반·미해결 설계 질문은 없다. 현재 순수 규칙 구현·전체 Unit116건을 검증했다. [tasks.md](tasks.md)의 T029는 설명 후 사용자 명시적 완료 지시로 처리했으며 실제 요청 경로 연동은4~6단계에 인수한다. 다음 구현은4단계 로컬 인증과 대상 API다.

실행 인수: ResourceRules52·IdempotencyKey56·CancellationDecision5·프로브3, 합계116/실패0/오류0/skip0. RED/GREEN 명령·원인은 [증거](../../docs/test-evidence.md)에 기록했다. 설명 문단 체크는 2026-10-16 사용자 명시적 완료 지시를 근거로 반영했다. 독립 재설명 시험은 미실시이며 실제 요청 경로 사용은 미완료다.
