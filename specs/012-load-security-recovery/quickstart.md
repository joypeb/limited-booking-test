# Quickstart: 11단계 검증 실행

## 통합 테스트 (Docker 실행 중, `backend/`)
```sh
./gradlew integrationTest --tests '*SecurityIT'
./gradlew integrationTest --tests '*DependencyFailureIT'
./gradlew build deepTest mutationSmoke verifyContractDependencies   # 최종 게이트
```

## 로컬 부하 (저장소 루트)
```sh
# 셸에서 비밀번호 환경 변수 설정(커밋 금지): POSTGRES_USER POSTGRES_PASSWORD ADMIN_PASSWORD USER_A_PASSWORD USER_B_PASSWORD
docker compose up -d --wait
./load/prepare-db.sh
# 부하용 제한값으로 앱 실행(별도 터미널)
(cd backend && LOAD_RATE_LIMIT=100000 ./gradlew bootRun --args='--spring.profiles.active=load')
LOAD_RATE_LIMIT=100000 ./load/run.sh low run1     # low|high|replay, 실행 label
LOAD_RATE_LIMIT=100000 EXPECT_CREATED=999 ./load/run.sh high selfcheck   # 비0 종료 기대
# 제한 시험: 앱 종료·8080 해제 확인 후 LOAD_RATE_LIMIT=20 으로 재기동
LOAD_RATE_LIMIT=20 ./load/run.sh rate-limit run1
docker compose down      # 볼륨 삭제 없음
```
결과: `docs/performance-runs/<run-id>/`, 보고서: `docs/performance.md`. 상세 절차는 `load/README.md`.
