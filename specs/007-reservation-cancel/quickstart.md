# 검증 안내: 소유자의 예약 조회·취소

> 아래는 실행 안내이며, 실행 결과를 기록한 것이 아니다. 실제 결과는 구현 후 `docs/test-evidence.md`에 기록한다.

## 전제
- Docker 실행 중(Testcontainers), Java 25, `backend/`에서 실행.

## 지정 검증
```sh
cd backend
./gradlew test integrationTest --tests '*ReservationOwnershipIT' --tests '*ReservationCancelIT'
./gradlew verify
```
기대: 두 IT 모두 1건 이상 실행, 실패·오류·skip 0, 종료 코드 0. 기존 IT 회귀 없음.

## CLI 1회 전체 실행 (계획서 6단계 마지막 항목)
local 프로필 앱을 띄운 뒤 curl로 다음을 차례로 실행한다. 대상 생성(ADMIN) → 대상 조회 → 예약 생성 → 재요청 → 예약 조회 → 취소 → 중복 취소 → 다른 사용자 조회 404 → 원 키 재요청. 비밀번호는 환경 변수에서 읽는다.

기대 결과는 [계약 표](contracts/reservation-cancel-contract.md)를 따른다.
