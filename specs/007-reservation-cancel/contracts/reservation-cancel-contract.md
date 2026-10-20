# 계약: 예약 조회·취소

정본은 [docs/openapi.yaml](../../../docs/openapi.yaml)의 `getReservation`·`cancelReservation`이다. 이 문서는 명세 요구와 검증 사례를 연결할 뿐, 계약을 변경하지 않는다.

| 사례 | 요청 | 기대 | 명세 | 검증 IT |
| --- | --- | --- | --- | --- |
| 본인 조회 | GET USER_A | 200 ReservationView, cancelledAt null | FR-001 | Ownership |
| 타인/관리자 조회 | GET USER_B·ADMIN | 404 `NOT_FOUND` problem+json | FR-002 | Ownership |
| 없는 UUID | GET/cancel | 404 `NOT_FOUND` | FR-002 | Ownership |
| 미인증 | GET/cancel | 401 `UNAUTHENTICATED` + WWW-Authenticate | FR-003 | Ownership |
| 잘못된 UUID | GET/cancel | 400 `INVALID_REQUEST` | FR-003 | Ownership |
| 비어 있지 않은 본문 | cancel | 400 `INVALID_REQUEST`, 상태 불변 | FR-003 | Cancel |
| 타인/관리자 취소 | cancel USER_B·ADMIN | 404, 상태·수량 불변 | FR-002 | Ownership |
| 최초 취소 | cancel USER_A | 200 CANCELLED, cancelledAt 값, available+1 | FR-004 | Cancel |
| 중복 취소 | cancel 재전송 | 200, cancelledAt 동일, available 불변 | FR-005 | Cancel |
| 복구 예외 | 테스트 주입 | 500, CONFIRMED·null·available 0 | FR-004 | Cancel |
| 복구 0건 | 테스트 주입 | 500 내부 오류, 전체 롤백 | FR-006 | Cancel |
| 취소 후 원 키 생성 | POST 같은 키 | 201 replayed=true CONFIRMED, GET은 CANCELLED | FR-007 | Cancel |
| 품절 키 재시도 | 409 후 취소 → POST | 201 replayed=false, available 0 | FR-007 | Cancel |

오류 본문에는 SQL·스택 트레이스·비밀번호가 없어야 한다.
