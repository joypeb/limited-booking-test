# Data Model: 11단계 검증 데이터

업무 스키마는 변경하지 않는다(V1 그대로). 아래는 검증용 개념이다.

## 보안 사례
| 필드 | 값 |
|---|---|
| actor | ADMIN·USER_A·USER_B·미인증·잘못된 비밀번호 |
| request | 메서드·경로·헤더(`Authorization`·`X-User-Id`·`Content-Type`·`Idempotency-Key`)·본문 |
| expected | 상태·`code`·필수 헤더, DB 스냅샷 동일 여부 |
| secrets | 비밀번호 원문 3종, Base64 Authorization 값 → 응답·로그에서 부재 |

## 장애 사례
| 종류 | 주입 | 해제 | 상한 | 복구 기대 |
|---|---|---|---|---|
| 연결 중단 | ALLOW_CONNECTIONS false + terminate | ALLOW_CONNECTIONS true | 6초 | 같은 키 201·Replayed false, 취소 200·+1 |
| 응답 정지 | container pause | unpause | 12초 | 같은 키 201 |
| 취소 잠금 | 별도 연결 FOR UPDATE | rollback | 4초 | 취소 200·+1 |
| 응답 유실 | raw socket close(커밋 확인 후) | 해당 없음 | 커밋 폴링 5초 | 같은 키 201·같은 id·Replayed true |

DB 스냅샷 = (대상별 capacity·available, 예약 행 수, 예약별 status·cancelled_at).

## 부하 시나리오
| 이름 | executor | VU | 양 | 대상 | 제한값 | 예상 상태 |
|---|---|---|---|---|---|---|
| low | constant-vus | 10 | 30s 준비 + 2m 측정 | 10개×1000 | 100000/60s | 201·200 |
| high | shared-iterations | 50 | 2000회 | 1개×1000 | 100000/60s | 201×1000·409 SOLD_OUT×1000 |
| replay | per-vu-iterations | 10 | VU당 50회(키5×10) | 1개×1000 | 100000/60s | 201(최초 false·재현 true) |
| rate-limit | per-vu-iterations | 1 | 25회 | 1개×100 | 20/60s | 201×20·429×5 |

## 부하 실행 기록 (docs/performance-runs/<run-id>/)
- `meta.txt`: 날짜·commit·시나리오·실행 번호·제한값·이미지·JDK·k6 버전
- `summary.json`: k6 `--summary-export`
- `check.txt`: 불변식 검사 출력(위반 0행 기대)과 시나리오별 집계(확정 수·잔여·고유 키 수)
