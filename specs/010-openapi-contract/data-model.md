# 데이터·검증 모델

새 DB 엔티티·컬럼·migration은 없다. 기존 PostgreSQL resources/reservations와 Redis 제한 키를 그대로 사용한다. 아래 모델은 테스트 메모리 값이며 제품 DTO를 복제한 기대값 생성기를 만들지 않는다.

| 모델 | 필드 | 관계·제약 |
| --- | --- | --- |
| 고정 계약 | OpenAPI3.0.3, 5 operation, schemas/responses | docs/openapi.yaml 한 원본, 참조 해석/파싱 오류 차단 |
| HttpExchange | method, 실제 raw path, request headers/body 원문, status, response headers/body 원문 | 전송 후 재구성 DTO 대신 실제 값. body null/빈 문자열 구분 보존 |
| ExpectedRequestViolations | 정확한 library key+location multiset, raw rule+location multiset, 예상 거부 상태/code | normal은 모두0. 인스턴스 불변, request만 한정, 무관한 오류/누락 실패 |
| ValidationFailure | request-schema/raw/response-schema/semantic의 단계, key, location | 모든 단계 오류 수집 후 assertion, response 오류 허용 목록 없음 |
| ScenarioExpectation | 예상 HTTP/code, 최초 생성 필드/Location, replay 여부, 판정PTTL | 시험의 사전 기대값/독립 관측, 결과 본문에서 정답 생성 금지 |

## 기존 응답 투영

- ResourceView: id UUID, name 정규화 문자열(코드포인트1~100), capacity/available 정수0~1000. available≤capacity. 생성 직후 둘은 같고 전체 수량식은 최종 DB로 확인한다.
- CreatedReservationView: id/resourceId UUID, status=CONFIRMED, createdAt UTC. 네 필드 필수·추가 필드 거부·cancelledAt 없음.
- ReservationView: id/resourceId/status/createdAt/cancelledAt 다섯 필드 필수. CONFIRMED이면 cancelledAt 명시 null, CANCELLED이면 UTC 값이다. 다른 상태와 추가 필드 거부.
- ProblemDetail: type/title/status/detail/instance/code 필수. HTTP 상태와 status/code 매핑 일치, instance는 실제 요청 경로, type은 유효한 URI 식별자(about:blank 허용). 예시 title/detail/urn의 바이트 동일성은 요구하지 않는다. 민감정보는 고정 테스트 비밀번호/SQL·스택 흔적 비노출 검사를 결합한다. 알 수 없는 JSON 필드는 고정 계약의 additionalProperties 정책에 맞춰 거부하도록 확정한다.

## 시간·헤더 값

시각은 ISO-8601로 파싱 가능하고 offset=UTC이며 실제 기존 Instant 직렬화의 Z 표현을 검사한다. 나노초 값은 1000으로 나누어 떨어져야 한다(최대 마이크로초 정밀도); 소수부를 반드시6자리로 강제하지 않는다. 비UTC offset은 같은 순간이라도 거부한다. 최초·재요청은 JSON 필드 값/UTC 시각이 동일해야 한다.

헤더 이름은 대소문자 무시, Location/Replay/WWW-Authenticate/Retry-After는 해당 응답에서 반드시 존재한다. singleton 헤더의 중복 값을 임의로 첫 값 선택하지 않는다. 제공 Map 경계 전에 중복을 탐지해 실패시키며 일반 전송 헤더(Date 등)는 허용한다. Location은 해당 단건 경로와 응답 id가 같다. Replay 값은 false/true 소문자 문자열이며 시퀀스에서 기대값을 비교한다. Retry-After는 양의 정수이고 test-only 독립 판정PTTL에 max(1,ceil(ms/1000))을 적용한 값과 비교한다.

## 상태 전이와 DB 관측

```text
최초 생성(CONFIRMED) → 같은 키 재현(CONFIRMED)
       ↓ 취소
현재 CANCELLED → 중복취소(CANCELLED, 같은 cancelledAt)
       ├→ 같은 생성 요청: 최초 CONFIRMED 네 필드 재현
       └→ GET: 현재 CANCELLED 다섯 필드
```

위 시퀀스 이후 예약 행1·추가 차감0·복구1이며 available+CONFIRMED수=capacity다. user/key·resourceId·createdAt은 불변이고 실패한 생성 키는 남기지 않는다. TestData 초기화는 전용 DB에서 reservations→resources, 준비는 요청 전 커밋, 모든 요청/작업 종료 뒤 DatabaseAssertions 단일 snapshot으로 불변식을 검사한다. Redis timeout 뒤 queued Lua 카운터 값은 불확실할 수 있어0을 요구하거나 DB 실패 시 보상하지 않는다.
