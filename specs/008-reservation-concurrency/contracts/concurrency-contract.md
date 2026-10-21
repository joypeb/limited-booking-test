# 경쟁 검증 계약

기준: [명세](../spec.md), [계획서](<../../../한정 수량 예약 백엔드 로컬 개발 계획.md>) 2~5절/7단계, [고정 OpenAPI](../../../docs/openapi.yaml). 새 엔드포인트·필드·오류 코드는 없다. 아래 사례는 신규 예정이며 실행 결과가 아니다.

## 사용자 인터페이스

- POST /api/reservations: 실제 Basic 인증, application/json의 resourceId, Idempotency-Key. 성공201/application/json/Location·Idempotent-Replayed, 본문 id/resourceId/status=CONFIRMED/createdAt.
- POST /api/reservations/{id}/cancel: 소유자 Basic 인증, 빈 본문. 성공200/application/json, id/resourceId/status/createdAt/cancelledAt.
- GET /api/reservations/{id}: 소유자200으로 현재 상태 검증. 취소 후 생성 재요청은201/최초CONFIRMED, GET은CANCELLED다.
- 정상 경쟁의 오류는409/SOLD_OUT 또는409/IDEMPOTENCY_CONFLICT만 허용한다. application/problem+json 및 type/title/status/detail/instance/code를 검사하고 SQL/스택/비밀번호 노출을 거부한다. C11의 지정 고의 내부 실패만500을 예상하며503으로 숨기지 않는다. 그 외401/429/503/500/HTTP timeout은 이번 정상 사례 실패다.

## 인수 사례 표

모든 행을 각각10회 반복한다. C01~C06은 일반 경쟁이며 C03은 UNIQUE 실행 보장을 위해 첫 두 선조회를 동기화한다. C07~C10은 소규모 강제 경로, C11은 장애 주입이다. 전부 실제 HTTP·SQL을 사용한다. 총11종/110회 예정이다.

| ID | 초기·요청 | 결과·최종 상태 | 필수 관측·요구사항 |
| --- | --- | --- | --- |
| C01 | capacity10, 다른 user/key 조합100건 | 201×10, SOLD_OUT×90, total/confirmed10, available0 | 시작 장벽·모든 결과; FR001/006~008 |
| C02 | capacity1, 동일 user/key/대상10건 | 모두201·동일 id/Location/본문, false1/true9, total1/available0 | 키 유일·정밀도; FR002 |
| C03 | capacity10, 동일 user/key/대상10건 | 모두201, false1/true9, total1/available9 | 첫 두 선조회 빈 결과·실제 UNIQUE/롤백 후 재조회; FR002/009 |
| C04 | capacity1 대상2개, 동일 user/key 각각1건 | 201×1·충돌409×1, 승자total1/available0, 패자total0/available1 | 첫 두 선조회 빈 결과, 각 실제 차감1·실제 UNIQUE·롤백 후 재조회; FR003 |
| C05 | capacity1, 기존 확정1, 동시 취소10건 | 모두200/CANCELLED·같은 cancelledAt, total1/confirmed0/available1 | 상태 갱신1건·복구 호출1회, 기존 생성 시각 불변; FR004 |
| C06 | capacity1/available0/기존 확정1, 다른 키 생성+취소 | 취소200, 생성201 또는409/SOLD_OUT; 성공시confirmed1/available0, 품절시confirmed0/available1 | 일반 동시 시작·최종 기존CANCELLED; FR005 |
| C07 | capacity1, 최초 키 조회 두 빈 결과, 동일 키2건 | 모두201, false1/true1, total1/available0 | 차감 반환0→활성 트랜잭션 새 키 조회에서 승자 발견; FR009 |
| C08 | capacity2, 최초 키 조회 두 빈 결과, 동일 키2건 | 모두201, total1/confirmed1/available1 | 두 차감1, 실제INSERT23505/uq_reservations_user_key, 롤백 후 비트랜잭션 재조회; FR009 |
| C09 | C06 초기, 생성 첫 조회 뒤 대기→취소 완료→해제 | 생성201·취소200, 기존CANCELLED·새CONFIRMED1/available0 | 두 요청 진입·복구 커밋→생성 판단 순번; FR005 |
| C10 | C06 초기, 취소 Service 진입 대기→생성 품절 완료→해제 | 생성409/SOLD_OUT·취소200, 기존CANCELLED·새행0/available1 | 생성 품절→복구 순번, 종료 후 같은 새 키 재시도201·available0; FR005 |
| C11 | capacity2/available2, 서로 다른 키2건, 한 INSERT만 실패 | 실패500×1·성공201×1, 실패키없음/confirmed1/available1; 실패키 재시도201 뒤confirmed2/available0 | 실제 차감 후 단발 실패, 정상 경쟁키 유지; FR012 |

C06에서 품절인 회차도 완료 후 같은 새 키 재시도201과 수량 복구 재사용을 확인한다. 준비·재시도 요청은 경쟁 요청 수와 구분한다. 모든 사례는 FR006/007/010/011/013/014와 SC005/006을 공통 적용한다. C01→SC001, C02/03/07/08→SC002, C04→SC003, C05/06/09/10→SC004.

## 테스트 관측 인터페이스

Spy는 callRealMethod 결과를 관측하며 해당 회차의 키·대상·호출 phase로 필터한다. 선조회 장벽은 실제 조회 반환 뒤 트랜잭션 밖에서만 기다린다. C03은 첫2요청만 동기화하고 이후 요청은 기다리지 않는다. C04에서도 같은 장벽으로 충분하며 서로 다른 자원에 대한 두 차감1을 따로 확인한다. INSERT 예외를 관측한 후 그대로 다시 던져 실제 Writer 롤백을 보존한다. UNIQUE 분류는 같은 PSQLException의23505와 정확한 제약명을 모두 확인한다.

C09의 생성 선조회 대기와 C10의 취소 Service 진입 대기는 트랜잭션 밖임을 검사한다. C10의 Service Spy는 실제 cancel 호출 전에만 대기하고 실제 메서드에 위임한다. request별 사건 순번은 원자 증가값으로 수집하며 신호 도착만으로 커밋을 추정하지 않고 선행 HTTP 응답·별도 DB 상태를 확인한다. 대기 중 다른 작업 종료에 필요한 자원·행 잠금을 보유하면 설계 위반이다.

## 종료·발견 계약

공통 deadline(100건120초/그 외30초) 안 모든 Future 결과·예외를 모은다. 첫 예외에서 나머지 작업을 잊지 않는다. 실패 시 장벽을 모두 열고 종료·서버 in-flight0을 확인한다. 다음 회차 reset 전 종료가 확인되어야 한다. 장벽5초·Executor/HTTP 종료 각각10초. 재실행 성공으로 실패를 덮지 않는다.

Gradle 일반 IT 발견은 *IT, contract/property 태그 제외다. 신규 클래스는 별도 태그로 제외하지 않는다. 반복은 @RepeatedTest(10)로 XML110회가 발견돼야 하며0건·skip은 실패다. 지원 Unit/IT 추가 수와 기존 전체 수는 실제 실행에서 확인한다.

### 종료 실패 후 초기화 차단 (U1 보완)

Executor·서버 요청·HTTP 클라이언트의 종료 확인 실패는 최초 원인을 클래스 단위 차단 상태에 보존한다. 회차마다 상태를 해제하지 않는다. ReservationApiTestSupport.setup의 첫 동작에 기본 무동작 보호 메서드를 두고 경쟁 클래스에서 재정의하여 부모 DB reset 전에 실패시킨다. 자식 BeforeEach 검사만으로 대체하지 않는다. 이후 회차는 reset·준비·요청 제출 각각0회이며 최초 실패 원인을 유지해야 한다. setup 차단으로 client가 없으면 cleanup은 null을 안전하게 처리하고 회차 소유 자원만 정리한다. JUnit 전체 중단에 의존하지 않고 후속 회차는 실패로 판정하며 skip이나 성공으로 숨기지 않는다. 실제 부모 setup 순서는 별도 상태를 소유한 테스트 fixture로 검증하고110회 경쟁 인수와 지원 검증 수를 구분한다. 이는 설계·후속 검증 기준이며 아직 실행하지 않았다.


### 2026-10-21 최종 리뷰 반영

종료 확인 전 초기화 금지 원칙을 우선하여 Executor·서버·HTTP 종료 확인과 기존 차단 상태 확인을 모두 통과한 뒤에만 Spy를 초기화한다. Service 진입 전의 서버 처리는 Service in-flight만으로 관측할 수 없으므로 전송 실패/timeout 등 요청 결과 예외도 최초 원인을 보존하고 다음 부모 setup을 차단한다. 후속 reset·준비·제출 금지는 종료 확인 실패 때와 동일하다. 이는 회차 격리 보완이며 제품/API/DB 계약 변경이 아니다.

실행 함수가 종료 실패를 던져도 최초 오류·다른 작업 오류·수집한 전체 결과를 함께 보존한다. 생성 키와 호출별 고유 요청 식별자를 분리하고 UNIQUE 뒤 비트랜잭션 조회 및 C07의 차감0 뒤 트랜잭션 안 조회는 같은 요청 ID·후속 순번으로 연결한다. 보존 JSON에도 C07의 차감0/성공 재조회 사건을 남긴다. 추가 Unit/fixture 수는110회 반복과 별도로 실제 실행 기록을 따른다.
