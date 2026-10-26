# Quickstart: 로컬 브라우저 예약 콘솔 검증 안내

명령은 안내이며 실행 기록이 아니다. 실제 결과는 `docs/test-evidence.md` 14단계(콘솔) 절에 기록한다.

## 1. 자동 테스트(백엔드 불필요)

```sh
cd frontend
npm ci
npm run lint
npm run typecheck
npm test          # Unit + Proxy + Component, 0건·skip·실패 거부
npm run build
```

기대: 모두 종료 코드 0, 게이트 출력에 총 실행 수 > 0, skip 0.

## 2. 수동 실행(로컬 개발 DB)

```sh
# 저장소 루트, README 절차로 환경 변수 export 후
docker compose up -d --wait --wait-timeout 120
(cd backend && ./gradlew bootRun --args='--spring.profiles.active=local')
# 다른 터미널
cd frontend && npm run dev      # http://127.0.0.1:3000
```

시나리오(SC-001): ADMIN으로 수량 1 대상 생성 → USER_A로 예약 → 같은 키로 다시 보내기(재현 여부 예, 잔여 0 유지) → USER_B로 새 키 예약(409 SOLD_OUT) → USER_A 예약 조회·취소·재취소(같은 취소 시각) → 대상 다시 조회(잔여 1) → USER_B로 예약 조회(404) → 잘못된 비밀번호(401, 대화상자 없음).

## 3. 종단 테스트(전용 DB `reservation_e2e`, Redis DB 1)

```sh
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d reservation_local -tAc "SELECT 1 FROM pg_database WHERE datname='"'"'reservation_e2e'"'"'" | grep -q 1 || createdb -U "$POSTGRES_USER" reservation_e2e'
(cd backend && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/reservation_e2e \
  SPRING_DATA_REDIS_DATABASE=1 ./gradlew bootRun --args='--spring.profiles.active=local')
cd frontend
npx playwright install chromium   # 최초 1회
npm run test:e2e
```

기대: 모든 시나리오 통과, 대화상자 0회, `reservation_local`에 변경 없음. 연속 재실행 시 요청 제한(20회/60초) 때문에 60초 이상 간격을 둔다.

## 4. 백엔드 무변경 확인

```sh
git diff --stat develop -- backend docs/openapi.yaml    # 출력 없음
(cd backend && ./gradlew test)
```
