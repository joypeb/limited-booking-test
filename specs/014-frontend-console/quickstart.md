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

## 3. 종단 테스트(전용 실행기, DB `reservation_e2e`, Redis DB 1, 백엔드 18080)

```sh
# 저장소 루트에서 README 2단계 환경 변수를 export하고 Compose를 시작한 상태
cd frontend
npx playwright install chromium   # 최초 1회
npm run test:e2e
```

`npm run test:e2e`(= `bash scripts/run-e2e.sh`)는 (1) 필요한 환경 변수 확인, (2) 포트 18080이 비어 있지 않으면 실패, (3) Compose PostgreSQL에 `reservation_e2e`가 없으면 생성, (4) `backend`의 `bootJar`를 만들어 `SERVER_PORT=18080`·`SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/reservation_e2e`·`SPRING_DATA_REDIS_DATABASE=1`·`local` 프로필로 직접 기동, (5) health UP 대기, (6) `E2E_ISOLATED_BACKEND=1 E2E_BACKEND_BASE_URL=http://127.0.0.1:18080 playwright test`, (7) 종료 시 백엔드 종료를 수행한다. 백엔드 로그는 `frontend/e2e-logs/e2e-backend.log`(Playwright가 시작 시 `test-results/`를 비우므로 분리). 지원 코드는 실행기 표식이 없거나 백엔드가 8080이면 즉시 실패한다(개발 DB 보호).

기대: 모든 시나리오 통과, 대화상자 0회, `reservation_local`에 변경 없음. 연속 재실행 시 요청 제한(20회/60초) 때문에 60초 이상 간격을 둔다.

## 4. 백엔드 무변경 확인

```sh
git diff --stat develop -- backend docs/openapi.yaml    # 출력 없음
(cd backend && ./gradlew test)
```
