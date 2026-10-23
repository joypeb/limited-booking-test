# 업무 HTTP 계약과 검사기 계약

본 문서는 후속 구현의 설계다. 고정 HTTP 스키마 원본은 [docs/openapi.yaml](../../../docs/openapi.yaml)이며 여기 별도 YAML 사본을 만들지 않는다.

## 검사기 경계

지정 인터페이스를 그대로 제공한다.

```java
void assertValid(String method, String path,
    Map<String,String> requestHeaders, String requestBody,
    int status, Map<String,String> responseHeaders, String responseBody);
```

기본 검사기는 정상 요청 위반0을 요구한다. 예컨대 `expectInvalidRequest(ExpectedRequestViolations)` 불변 팩터리로 파생 인스턴스를 만들 수 있으나 void 메서드의 인자·반환형을 바꾸지 않는다. 예상위반 설정은 테스트 전용이며 공유 mutable 설정/ThreadLocal/응답 오류 허용을 금지한다.

처리 순서는 (1) method/path operation 선택과 명시 응답 상태 확인 (2) 원문 그대로 SimpleRequest/Response 구성 (3) 요청·응답 개별 schema 보고서 수집 (4) raw strict JSON·입력 보완 판정 (5) 응답 의미 검사 (6) 실제 요청위반 multiset과 예상값 정확 비교 및 모든 오류 합산이다. 요청 보고서가 실패해도 응답 전체 검사를 실행하며, parser/runtime 내부 예외는 검사기 결함으로 실패한다. assertion은 위반 단계·key·location을 보존하되 Authorization 값·비밀번호는 출력하지 않는다.

- 정상/인증·소유권·품절·충돌·장애는 유효 입력을 써 요청위반0을 기대한다. Basic 자격 유효성·권한·기존 키·DB 상태는 스키마의 책임이 아니며 HTTP 기대값으로 검사한다.
- 무효 JSON/UUID/헤더/미디어 타입 사례는 해당 schema key+location과 raw rule+location을 명시한다. 실제 key가 고정 버전에서 확인된 뒤 fixture로 고정하며 포괄 `validation.request.*` 또는 메시지 contains로 허용하지 않는다. 예상 누락·추가·중복 모두 실패한다.
- 중복 키/소수 토큰/strip/취소 본문/8193바이트는 OAS 표현 한계가 있으므로 raw rule 판정도 필요하다. schema0이어도 예상 raw violation이 반드시 관측되어야 한다. library가 별도 오류를 내면 그 오류도 정확히 예상해야 한다.
- 401/413 등 선행 거부는 입력 오류를 서버가 먼저 처리했다고 주장하지 않는다. 가능한 유효 본문으로 원인을 분리하고, 의도적으로 다른 무효 입력을 결합할 때는 그 입력의 정확한 예상위반과 실제 선행 상태를 각각 명시한다.
- 실제 HTTP adapter는 헤더 이름을 case-insensitive 처리한다. 중복 singleton은 거부하고 원문 requestBody를 보존한다. chunked는 chunk framing을 JSON 본문에 섞지 않고 실제 payload 원문과 raw 전송 헤더/바이트 관측을 함께 유지한다. 서버가 조기 종료한 경우 원래 보내려던 body와 실제 수신 관측의 차이를 증거로 남긴다.

## 모든 작업별 적용표

각 셀을 실제 HTTP로 최소1회 검증하고 메서드·경로·status·code·사례ID를 기록한다. 상태 발생을 억지로 추가하지 않는다.

| 작업 | 성공 | 입력/권한/업무 오류 | 전역 본문·형식 | 제한·의존성 |
| --- | --- | --- | --- | --- |
| POST /api/resources | 201·Location | 400·401·403 | 413·415 | 503 DB |
| GET /api/resources/{id} | 200 | 400·401·404 | 413 | 503 DB |
| POST /api/reservations | 201·Location·Replay | 400·401·404·409 SOLD_OUT·409 IDEMPOTENCY_CONFLICT | 413·415 | 429·503 DB·503 Redis |
| GET /api/reservations/{id} | 200 | 400·401·404 | 413 | 503 DB |
| POST /api/reservations/{id}/cancel | 200 | 400·401·404 | 413 | 429·503 DB·503 Redis |

오류 code는 400 INVALID_REQUEST /401 UNAUTHENTICATED /403 FORBIDDEN /404 NOT_FOUND /409 위 두 code /413 PAYLOAD_TOO_LARGE /415 UNSUPPORTED_MEDIA_TYPE /429 RATE_LIMITED /503 DEPENDENCY_UNAVAILABLE로 고정한다. operation에 명시되지 않은 응답과 모든 500은 default가 있어도 실패한다. 성공은 application/json, 오류는 application/problem+json이며 파라미터 charset 허용과 본래 미디어 타입 일치를 구분한다.

### 최소 사례 세분화

- 대상 생성: capacity0/1000·strip 전 원문100초과/strip후100·유니코드·NBSP 정상. name 공백/101·capacity -1/1001·문자열/소수(1.0 포함)·누락/null·미정의/중복 키·malformed JSON 거부.
- 예약 생성: 누락/잘못된 resourceId·잘못된 생성 키/누락·quantity/userId 추가·중복 JSON 키·잘못된 JSON 거부. 같은 키 다른 대상과 품절을 각각 생성한다.
- 두 GET와 cancel: 잘못된 UUID400; 없는 식별자404. 예약 타인(USER_B와 ADMIN) 조회/취소404. cancel 공백/JSON 등 0바이트가 아닌 본문400. 대상 생성 USER403, 각 작업 미인증/잘못된 비밀번호401.
- 모든5 작업에서 실제8193바이트413, GET도 본문을 넣어 확인한다. Content-Length/길이 미지정 chunked 각각 검사한다. 생성의8192바이트 유효 JSON은 성공,8193은413; GET/cancel의8192는 다음 검증(예: cancel400)까지 도달함을 확인한다. 멀티바이트·인증없음/틀린 인증+초과413·제한 소진+초과413 포함. body 원문/바이트 관측·오류응답 전체를 검사한다.
- 두 생성 API text/plain415. 예약 생성/취소 각각 기본20/60000ms 소진 후429. 판정 시 PTTL은 test-only 관측으로 확보해 Retry-After 계산과 비교한다. 응답 후 다시 읽은 PTTL과 정수 경계의 부정확한 비교를 하지 않는다.
- DB503은 격리 fixture에서 정상 준비 후 소유 DB 중단·기존 풀 연결 정리로 실제 연결 실패를 만들고 5작업을 검사한다. Redis는 정상으로 유지해 예약 쓰기가 limiter를 통과하도록 한다. read/write DB 경로 전부 실제 JDBC 실패이며 mock 서비스 예외로 대체하지 않는다. 생성/취소 실패 후 business snapshot과 부분 반영 없음은 별도 정상 연결/기존 timeout 회귀에서 검사한다.
- Redis503은 별도 소유 Redis 중단 fixture에서 정상 DB·정상 인증/입력의 생성·취소를 검사한다. 동일 fixture에서 대상 생성·두 GET은 정상, 거부 쓰기 DBsnapshot 동일을 확인한다. 기존 RedisFailureIT의 실제지연/WRONGTYPE/OOM/첫연결/기존연결 및 DB실패 카운터 유지 회귀를 보존한다.

## 스키마 외 필수 검사

- 필수 헤더 존재를 별도 검사: resource201 Location, reservation201 Location+Idempotent-Replayed,401 WWW-Authenticate(Basic),429 Retry-After(양의 정수초).
- Location의 `/api/resources/{id}` 또는 `/api/reservations/{id}`가 body id와 일치한다. Replay는 허용값 false/true와 시퀀스 기대값 모두 확인한다.
- date-time 형식에 UTC Z/offset0·마이크로초 정밀도·최초값 재현을 추가한다. 생성 status는 CONFIRMED, 현재 status와 cancelledAt null 관계를 검사한다.
- Problem6필드와 HTTP/status/code·실제 instance 경로 대응을 확인한다. 예시 문구를 바이트 고정하지 않고 민감정보 패턴/시험 비밀값 유출을 검사한다.
- 헤더의 추가 전송 필드와 JSON의 미정의 필드를 구분한다. 요청 정규화를 핑계로 원문을 strip/UUID 재직렬화하거나 잘못된 숫자를 고쳐 전달하지 않는다.

## 독립 탐지력 인수

정상 실제 교환을 먼저 통과시킨 뒤 매번 사본 하나만 바꾼다. 각 거부는 AssertionError의 단계/key/location이 해당 훼손 때문임을 확인하며 정상 대조군을 유지한다.

1. id 필수값 제거, UUID 잘못된 문자열, 숫자를 문자열로 변경.
2. 시각 malformed, 비UTC, DB 마이크로초를 벗어난 정밀도.
3. 알 수 없는 status/추가 필드, 생성에 cancelledAt 추가, 현재 cancelledAt 누락·상태관계 위반.
4. 각 필수 헤더 제거, 잘못된 미디어 타입·Location 다른 id·Replay 잘못된 값·401 비Basic·Retry-After0/소수/문자열.
5. HTTP/status/code 불일치·instance 다른 경로·예상 밖500/default.
6. 재현 시퀀스에서 허용된 boolean이지만 최초 true/재요청 false, 양의 정수지만 판정PTTL과 다른 Retry-After, 재요청 생성값 변경을 각각 거부한다.

이 응답 훼손 시험과 실제 DTO 타입변이 실험은 별개다. DTO 절차는 quickstart를 따른다. 모든 변경 원복·같은 시험 통과·업무 발견 수>0·최종 build/verifyContract/의존성/기존 회귀가 후속 완료 조건이다.
