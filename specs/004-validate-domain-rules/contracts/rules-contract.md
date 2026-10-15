# 순수 규칙 Java 호출 계약

작성일: 2026-10-15. [명세](../spec.md)·[모델](../data-model.md)·[계획](../plan.md)에 대한 후속 계층 제공 계약이다. 아래 신규 인터페이스는 구현·Unit 검증됐으며 기존 공개 HTTP 계약은 변경하지 않는다. 모든 메서드는 동기 호출·외부 I/O 없음·입력 보정 없는 거부를 원칙으로 한다.

## 인터페이스

| 클래스·공개 시그니처 | 정상 반환 | 실패 |
| --- | --- | --- |
| resource.ResourceRules.normalizeName(String name): String, static | strip 후 코드 포인트1~100의 이름 | null·정리 후0/101이상: IllegalArgumentException |
| resource.ResourceRules.validateCapacity(int capacity): void, static | 0~1000이면 정상 종료 | 범위 밖: IllegalArgumentException |
| reservation.IdempotencyKey.parse(String value): IdempotencyKey, static | 검증된 원문 value를 가진 record | null·구문 무효: IllegalArgumentException |
| reservation.IdempotencyKey(String value), public canonical constructor | parse와 같은 불변식 | parse와 같은 거부 |
| reservation.IdempotencyKey.value(): String | 허용 원문 그대로 | 유효한 인스턴스에서 null 없음 |
| reservation.CancellationDecision.from(ReservationStatus status): CancellationDecision, static | CONFIRMED→CANCEL_NOW, CANCELLED→ALREADY_CANCELLED | null: NullPointerException |

패키지 접두사는 모두 `com.limitedbooking.backend`다. ResourceRules는 인스턴스 생성 불가 final 유틸리티, IdempotencyKey는 값 기반 equals/hashCode를 제공하는 record, CancellationDecision은 CANCEL_NOW/ALREADY_CANCELLED 두 값의 enum이다. ReservationStatus는 기존 두 값을 유지한다. 모든 예외의 정확한 메시지는 계약 대상이 아니며 입력 원문을 메시지에 포함하지 않는다.

## R-CAPACITY: 수량 행렬 — ResourceRulesTest

| 입력 | 기대 |
| --- | --- |
| -1·1001 | 각각 IllegalArgumentException |
| 0·1·1000 | 각각 예외 없이 정상 종료 |

## R-NAME: 이름 행렬 — ResourceRulesTest

길이는 strip 후 코드 포인트 기준이다. 아래 `문자×N`은 같은 문자 N회 반복을 뜻한다. 실패는 모두 IllegalArgumentException이다.

| 입력 | 기대 |
| --- | --- |
| null·빈 문자열·일반 공백만·탭만·줄바꿈만·공백/탭/줄바꿈 혼합 | 각각 거부 |
| `  예약 대상  `·앞뒤 탭/줄바꿈+`예약 대상` | `예약 대상`, 내부 공백 유지 |
| 일반 문자×1·×100·×101 | 앞 두 값 원문 반환, 마지막 거부 |
| 😀×100·×101 | 100개 원문 반환, 101개 거부 |
| 가×99+😀 | 원문 반환, 100 코드 포인트 |
| 앞뒤 일반 공백+일반 문자×100 또는 😀×100 | 허용, 공백 제거된100개 반환 |
| U+00A0·U+2007·U+202F 각각 단독 | 그대로 반환, 길이1 |
| 위 세 문자 각각을 `이름` 앞뒤에 붙임 | 붙인 문자를 포함한 원문 유지 |
| 위 세 문자 각각×100·×101 | 각각100개 허용·101개 거부 |
| 각 정상 결과를 다시 normalizeName에 전달 | 같은 값, 객체 참조 동일성은 요구하지 않음 |

공백 범위 전체는 계획서2절의 Java25 strip/isWhitespace 정책이다. NBSP 계열을 추가 금지하거나 정리 후 초과 이름을 잘라내지 않는다.

## K-KEY: 생성 키 행렬 — IdempotencyKeyTest

아래 정상/거부 행렬은 parse와 public constructor 모두에 적용한다. 실패는 모두 IllegalArgumentException이다.

| 입력·비교 | 기대 |
| --- | --- |
| null·빈 문자열 | 각각 거부 |
| `A`·`a`·`0`·`-`·`_` | 각각 원문 허용 |
| 허용 문자 혼합 `Ab-1_9`·허용 문자64개 | 원문 허용 |
| 허용 문자65개 | 거부 |
| 일반 공백만·탭만·줄바꿈만·앞뒤/내부 공백·탭·줄바꿈 | 각각 거부, 제거 후 수용 금지 |
| 유효 키 뒤 LF 또는 CRLF | 각각 거부 |
| 한글·😀·`.`·`/`·`:`·U+00A0·전각 영문·전각 숫자를 각각 포함 | 각각 거부 |
| 같은 원문 `Ab-1`의 두 인스턴스 | equals 참·hashCode 동일·value 원문 유지 |
| `Ab-1`과 `ab-1` | 둘 다 허용, equals 거짓, 각 value 원문 유지 |
| new IdempotencyKey의 정상/거부 입력 | parse와 동일, 우회 생성 불가 |

특정 서로 다른 값의 hashCode가 다를 필요는 없다. 정규식은 `^[A-Za-z0-9_-]{1,64}$`이며 matcher.matches()로 전체 영역을 검사한다. 부분 검색이나 키 전처리는 사용하지 않는다.

## C-DECISION: 판단 행렬 — CancellationDecisionTest

| 입력·작업 | 기대 |
| --- | --- |
| CONFIRMED | CANCEL_NOW |
| CANCELLED | ALREADY_CANCELLED |
| 각 상태 반복 호출 | 매번 같은 판단, 상태/수량/시각 변경 없음 |
| null | NPE, 정상 판단 없음 |

순수성은 반복 결과와 코드의 가변 필드·I/O·시간/저장 의존성 부재로 검토한다. 사용하지 않는 예약 mock·가짜 수량 모델을 만들지 않는다.

## 후속 호출자와 오류 경계

| 단계 | 후속 사용 | 인수 증거·현재 상태 |
| --- | --- | --- |
| 4단계 대상 생성 | ResourceRules의 두 메서드로 입력 검사 후 정리된 이름 사용 | ResourceApiIT의 실제400/정상 이름·수량, 미구현 |
| 5단계 예약 생성 | 입력 경계에서 parse 후 인증 UUID·대상 UUID와 Service 전달 | ReservationApiIT의 키 거부·정상/재요청/충돌, 미구현 |
| 6단계 취소 | Writer가 읽은 기존 상태를 from으로 판단해 이미 취소됨 분기에서 기존 결과 반환 | 취소 IT의 동일 상태/시각·추가복구0, 미구현 |

입력 경계의 해당 검증 실패만 INVALID_REQUEST로 번역한다. 모든 IAE/NPE를 전역400으로 바꿔 내부 오류를 숨기지 않는다. 키 충돌·품절·404·429/503을 이 규칙의 예외로 대신하지 않는다. 순수 규칙 인수와 후속 HTTP·트랜잭션 인수는 별개이며 실제 사용 조건을 완료로 표시하지 않는다.
