# 데이터 모델: 입력 값과 취소 판단

작성일: 2026-10-15. [명세](spec.md)의 개념을 Java 내부 값에 대응한 설계다. 새 테이블·migration·영속 엔티티를 추가하지 않는다. 새 규칙 클래스3개는 구현·Unit 검증됐으며 기존 저장 데이터와 [ReservationStatus](../../backend/src/main/java/com/limitedbooking/backend/reservation/ReservationStatus.java)는 그대로 재사용한다.

## 대상 입력

| 값 | 타입·표현 | 불변식·변환 | 책임 |
| --- | --- | --- | --- |
| 원문 이름 | String 또는 null 입력 | null 거부, strip 후 검사 | ResourceRules.normalizeName |
| 정리된 이름 | String | 코드 포인트1~100, 내부 문자와 NBSP 계열 보존, 재정리 동일 | 위 메서드 반환값이며 별도 DTO 추가 없음 |
| 최초 수량 | int | 0≤capacity≤1000, 보정 없음 | ResourceRules.validateCapacity |

대상 식별자·available·createdAt은 이번 입력 규칙의 데이터가 아니다. 이름·수량이 유효하다는 사실만으로 대상 저장이나 수량 보존식을 보장하지 않는다. 수량 문자열·소수의 형식 거부는 후속 HTTP 입력 경계다.

## 예약 생성 키

`IdempotencyKey(String value)`는 불변 record다. value는 null 불가·ASCII 영문 대소문자/숫자/하이픈/밑줄1~64자이며 원문을 유지한다. public 생성자와 parse가 같은 불변식을 보장하고 value()가 원문을 제공한다. record의 equals/hashCode는 String 값에 기반한다. 같은 원문은 같은 값이며 Ab-1과 ab-1은 다르다. 문자열 intern이나 객체 식별자 동일성은 요구하지 않는다.

관계는 후속 예약 생성의 사용자 UUID·대상 UUID와 함께 사용되는 값이다. 사용자별 유일성·대상 비교·성공 키 보관·TTL·충돌 판정은 이 객체에 넣지 않는다. 기존 StoredReservation의 idempotencyKey String 필드나 Repository의 String 파라미터를 이번 단계에 교체하지 않는다. 후속 저장 경계에서는 value()로 원문을 전달할 수 있다.

## 현재 상태와 판단

| 입력 ReservationStatus | 반환 CancellationDecision | 판단만의 변화 | 후속 처리 의미 |
| --- | --- | --- | --- |
| CONFIRMED | CANCEL_NOW | 없음 | 조건부 취소 갱신을 진행할 수 있음 |
| CANCELLED | ALREADY_CANCELLED | 없음 | 기존 취소 결과를 반환할 분기 |
| null | 정상 판단 없음, NPE | 없음 | 내부 상태 누락, 성공으로 대체하지 않음 |

허용 업무 전이는 기존의 CONFIRMED→CANCELLED뿐이다. 판단 enum은 저장 상태가 아니며 CANCEL_NOW를 DB status나 공개 응답에 사용하지 않는다. 실제 수량 복구는 후속 Writer에서 상태 UPDATE1건일 때만 수행하고 두 상태의 반복 판단 자체로 복구하지 않는다.

## 오류·부작용 경계

무효 이름·수량·키는 IllegalArgumentException, 누락 내부 상태는 NullPointerException을 택한다. 이는 [계약](contracts/rules-contract.md)의 내부 예외 계약이다. 값 객체/정적 규칙에는 Spring Bean·Repository·Redis·Clock·가변 공유 상태를 넣지 않는다. 고정 Pattern만 공유하고 매 호출 Matcher를 사용한다. 상태/수량/시각 변경 없음은 이 구조와 반복 호출로 확인하며 실제 저장 불변식은 후속 IT로 검증한다.
