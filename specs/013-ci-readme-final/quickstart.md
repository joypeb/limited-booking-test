# Quickstart: 12단계 검증 실행 안내

저장소 루트 기준. 아래는 안내이며 실행 기록이 아니다. 실제 결과는 `docs/test-evidence.md` 12단계 절에 남긴다.

## 정적 검사

```sh
scripts/check-workflows.sh            # C1 W1~W7
scripts/check-readme.sh               # C2 R1~R6
scripts/check-traceability.sh         # C3 T1~T5
```

## 심화 워크플로의 로컬 동등 실행

```sh
cd backend
./gradlew --no-daemon propertyTest mutationSmoke
cd ..
.github/scripts/property-summary.sh backend/build/test-results/propertyTest
```

## 새 환경 재현 (plan D5)

```sh
d=/private/tmp/lb-fresh-$(date -u +%Y%m%dT%H%M%SZ)
git clone <로컬 저장소 경로> "$d" && cd "$d" && git checkout <대상 커밋>
export COMPOSE_PROJECT_NAME=lbfresh
# README Quick Start 1~8 단계를 그대로 실행하고 contracts C4 표와 대조
docker compose -p lbfresh down -v   # 임시 프로젝트만 정리
```

## 최종 기준점 (plan D6, contracts C5)

```sh
cd backend && ./gradlew build deepTest mutationSmoke verifyContractDependencies --rerun-tasks
./gradlew integrationTest --tests '*SecurityIT' --tests '*RateLimitApiIT' --tests '*RedisRateLimitIT' --tests '*RedisFailureIT'
# load/README.md 절차로 low·high·replay(100000)·rate-limit(20) 각 1회
```

## 외부 CI

- verify: Draft PR 생성 시 자동 실행. `gh run list --workflow verify.yml`, `gh run view <id> --log`, `gh run download <id>`.
- deep-tests: main 반영 후 `gh workflow run deep-tests.yml [-f qt_seed=<seed>]`(사용자 승인 후).
