# 조사: 순수 입력 규칙과 취소 판단

작성일: 2026-10-15. 상태: 설계 선택 완료·신규 규칙 실행 검증 전. [명세](spec.md)와 [계획](plan.md)의 Phase 0 산출물이다. 읽기 전용 두 조사 에이전트의 JDK25 공식 문서 확인·저장소 대조를 통합했다. 아래 예외 타입은 외부 업무 정책 변경이 아닌 내부 Java 계약 선택이다.

## 1. 이름의 공백과 길이

- **결정**: null을 명시 거부하고 String.strip() 후 codePointCount(0, length())로1~100을 검사해 정리한 문자열을 반환한다.
- **이유**: 계획서의 확정 정책이 Character.isWhitespace(int) 기준이다. 이 기준은 U+00A0·U+2007·U+202F를 제거하지 않는다. UTF-16 length()로 길이를 판정하면 😀100개를 잘못 거부한다. 코드 포인트 수는 화면상 결합 문자 묶음 수와 구분한다.
- **대안**: trim()·isBlank()만 검사·UTF-16 길이 제한·원문 maxLength100·Unicode 정규화는 확정 정책을 정확히 표현하지 못하거나 불필요한 변환을 추가한다.
- **근거**: [String.strip](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/String.html#strip()), [Character.isWhitespace](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#isWhitespace(int)), [String.codePointCount](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/String.html#codePointCount(int,int)). 업무 범위는 계획서2절·7절3단계다. JDK 문서의 동작 정의 확인이며 신규 코드 실행 증거는 아니다.

## 2. 생성 키 전체 일치와 불변 값

- **결정**: public record IdempotencyKey(String value), compact constructor에서 null/패턴을 검사하고 parse(String)는 생성자로 위임한다. 고정 Pattern `^[A-Za-z0-9_-]{1,64}$`와 matcher.matches()를 사용하고 원문을 바꾸지 않는다.
- **이유**: matches()는 전체 영역 일치를 요구한다. 이 선택에 따라 유효한 키 끝 줄바꿈도 거부할 수 있다는 설계 결론을 얻었으며 a+LF·a+CRLF를 회귀 사례로 고정한다. String 구성요소의 값 동등성이 같은 원문과 대소문자 차이를 보존한다. constructor에도 검증을 두어 new를 통한 우회를 막는다.
- **대안**: find()는 부분 일치여서 부적합하다. parse에서만 검증하는 record는 public 생성자로 우회할 수 있다. private 생성자의 final 클래스도 가능하지만 수동 equals/hashCode/accessor를 늘릴 이유가 없다. 키 trim·대소문자 변경은 계약 위반이다.
- **근거**: [Matcher.matches](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Matcher.html#matches()), [Record](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Record.html). 같은 사용자·키의 유일성·TTL·멱등 응답 재현은 값 객체가 아닌 후속 저장/서비스의 책임이다.

## 3. 오류 표현과 취소 판단

- **결정**: 이름·키 null/무효와 capacity 범위 밖은 IllegalArgumentException. CancellationDecision.from(null)은 Objects.requireNonNull로 NullPointerException. 메시지는 원문 이름·키를 포함하지 않으며 테스트에서 정확한 문구를 고정하지 않는다.
- **이유**: 외부 입력 무효와 내부 상태 부재를 구별한다. Java가 강제하는 정책이 아니라 이 계획의 작은 구현 선택이다. NullPointerException을 정상 취소 결과로 바꾸거나 공개400으로 일괄 변환하지 않는다.
- **대안**: 공통 업무 예외 계층·HTTP 의존 타입을 지금 도입하면 순수 규칙 범위를 넓힌다. 모든 RuntimeException을 동일 입력 오류로 처리하면 내부 결함을 숨긴다.
- **근거**: 기존 [ReservationStatus](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationStatus.java)는 두 상태만 가진다. [Objects.requireNonNull](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Objects.html#requireNonNull(T,java.lang.String))은 명시 null 검사의 도구다. 두 상태를 default 없는 switch로 대응하며 상태 갱신·수량 복구·현재 시각 생성은 하지 않는다.

## 4. 기존 테스트 발견·게이트

- **결정**: Jupiter의 기존 @Test/@ParameterizedTest·AssertJ로 세 package-private *Test를 작성한다. Spring·컨테이너·Mockito·property/contract 태그 없이 실제 값을 전달한다. build.gradle은 유지한다.
- **이유**: 현재 [build.gradle](../../backend/build.gradle)은 test에 **/*Test.class를 포함하고 contract/property를 제외한다. 현재 실행 listener와 XML의 실행 수 일치·실행≥1·실패/오류/skip0 게이트와 캐시 비활성화를 이미 제공한다. 선택 --tests도 전체 테스트 sourceSet 컴파일 뒤 실행에만 적용된다.
- **대안**: @SpringBootTest·실제 DB나 mock으로 규칙 결과를 대체하면 독립 규칙 검증의 의미가 사라진다. 새 테스트 태스크나 의존성도 필요하지 않다.
- **검증 경계**: 이번 계획은 빌드 정적 대조만 했다. 과거 Unit3·일반IT78은 [기존 기록](../../docs/test-evidence.md)이며 새 규칙 테스트 수가 아니다. 실행 수는 구현 후 실제 XML로 기록한다.

## 5. 도구 프로브와 심화 검사

- **결정**: ToolchainProbe·ToolchainSmokeTest·ToolchainPropertyTest·PIT 설정을 이번에 유지한다. 업무 규칙 Unit을 추가하고 프로브의 교체는 후속 심화 검증 단계로 남긴다.
- **이유**: PIT는 프로브 클래스/Unit을 이름으로 지정하며 Property도 생산 프로브를 직접 참조한다. Unit만 삭제하면 mutationSmoke를 깨고 생산 프로브까지 삭제하면 Property가 컴파일되지 않는다. 관련 구성까지 대체하는 것은 이번 인수 범위에 불필요하다.
- **대안**: 이번에 전면 교체하면 Property/PIT 재설계·검증까지 포함해야 한다. 이름이 비슷한 IT·HTTP fixture·시험 migration을 함께 제거하는 것도 잘못이다.
- **한계**: 현재 mutationSmoke는 생성≥1·탐지≥1·무효 상태0만 검사하며 업무 규칙80% 기준을 검증하지 않는다. 후속에는 세 순수 규칙과 대응 Unit만 PIT 대상으로 삼고 탐지율80% 이상·중요 규칙의 설명되지 않은 생존 변이0을 따로 인수해야 한다. 이번에 해당 검증을 완료했다고 기록하지 않는다.

## 6. 테스트 우선 순서와 후속 실제 사용

- **결정**: 컴파일 가능한 최소 골격에서 규칙별 실제 assertion 실패를 관측하고 최소 구현→같은 선택 Unit 통과→마지막 전체 Unit을 진행한다. 순수 규칙 인수와 실제 요청 경로 연동을 분리한다.
- **이유**: 컴파일 오류나 우연한 NPE는 수량/길이/문자 규칙의 실패 탐지 증거가 아니다. 입력/상태 실제 값으로 검증하며 Clock이나 가짜 저장 모델이 필요하지 않다.
- **대안**: 완성 코드를 먼저 작성하고 테스트 통과만 확인하면 RED 근거가 없다. 호출을 만들기 위해 아직 없는 Controller/Writer를 이번에 추가하면 단계를 앞당기게 된다.
- **결론**: 4단계 ResourceRules, 5단계 IdempotencyKey, 6단계 CancellationDecision의 요청 경로 사용을 각각 추적한다. 그 전에는 전체 연동 완료로 표시하지 않는다. 계획을 막는 미해결 질문은 없다.
