#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ $# -ne 2 ]]; then
  echo '사용법: ./load/run.sh <low|high|replay|rate-limit> <run-label>' >&2
  exit 2
fi
scenario=$1
label=$2
case "$scenario" in low|high|replay|rate-limit) ;; *) echo '잘못된 시나리오' >&2; exit 2 ;; esac
if [[ ! "$label" =~ ^[A-Za-z0-9_-]{1,64}$ ]]; then
  echo 'run-label은 ASCII 영문·숫자·밑줄·하이픈 1~64자여야 합니다.' >&2
  exit 2
fi
: "${POSTGRES_USER:?POSTGRES_USER 환경 변수가 필요합니다}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD 환경 변수가 필요합니다}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD 환경 변수가 필요합니다}"
: "${USER_A_PASSWORD:?USER_A_PASSWORD 환경 변수가 필요합니다}"
: "${USER_B_PASSWORD:?USER_B_PASSWORD 환경 변수가 필요합니다}"
: "${LOAD_RATE_LIMIT:?현재 실행 중인 앱과 같은 LOAD_RATE_LIMIT 기록값이 필요합니다}"
expected_limit=100000
if [[ "$scenario" == rate-limit ]]; then expected_limit=20; fi
if [[ "$LOAD_RATE_LIMIT" != "$expected_limit" || "${LOAD_RATE_WINDOW:-60000ms}" != 60000ms ]]; then
  echo "이 시나리오는 LOAD_RATE_LIMIT=$expected_limit, LOAD_RATE_WINDOW=60000ms가 필요합니다." >&2
  exit 2
fi
export BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
if [[ ! "$BASE_URL" =~ ^http://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]+)?/?$ ]]; then
  echo 'BASE_URL은 localhost HTTP 주소여야 합니다.' >&2
  exit 2
fi
export RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
if [[ ! "$RUN_ID" =~ ^[A-Za-z0-9_-]{1,32}$ ]]; then
  echo 'RUN_ID는 ASCII 영문·숫자·밑줄·하이픈 1~32자여야 합니다.' >&2
  exit 2
fi
# 값을 argv로 넘기지 않고 k6가 상속하는 환경 변수로 제공한다.
export ADMIN_PASSWORD USER_A_PASSWORD USER_B_PASSWORD
for executable in docker k6 java git; do
  command -v "$executable" >/dev/null || { echo "필수 명령 없음: $executable" >&2; exit 2; }
done
result_dir="docs/performance-runs/$(date -u +%Y%m%d)-$scenario-$label"
# Git이 빈 디렉터리를 보존하지 않으므로 부모 경로도 실행 시 준비한다.
mkdir -p docs/performance-runs
# 기존 증거를 덮어쓰지 않는다. 재실행에는 새 label을 사용한다.
mkdir "$result_dir"
meta="$result_dir/meta.txt"
{
  printf 'started_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'git_commit=%s\n' "$(git rev-parse HEAD)"
  if [[ -n "$(git status --porcelain)" ]]; then printf 'git_dirty=true\n'; else printf 'git_dirty=false\n'; fi
  printf 'scenario=%s\nlabel=%s\nrun_id=%s\nbase_url=%s\n' "$scenario" "$label" "$RUN_ID" "$BASE_URL"
  printf 'load_rate_limit_recorded=%s\nload_rate_window_recorded=%s\n' "$LOAD_RATE_LIMIT" "${LOAD_RATE_WINDOW:-60000ms}"
  printf 'expect_created=%s\nexpect_sold_out=%s\n' "${EXPECT_CREATED:-1000}" "${EXPECT_SOLD_OUT:-1000}"
  printf 'k6_version=%s\n' "$(k6 version)"
  printf 'java_version=%s\n' "$(java -version 2>&1 | sed -n '1p')"
  printf 'architecture=%s\n' "$(uname -m)"
  printf 'compose_images:\n'
  docker compose config --images
} > "$meta"
reset_status=0
if ./load/reset.sh > "$result_dir/reset.txt" 2>&1; then
  cat "$result_dir/reset.txt"
else
  reset_status=$?
  cat "$result_dir/reset.txt" >&2
  printf 'reset_exit=%s\nk6_exit=not_run\ncheck_exit=not_run\n' "$reset_status" >> "$meta"
  exit "$reset_status"
fi
script=load/reservation-load.js
if [[ "$scenario" == rate-limit ]]; then script=load/rate-limit.js; fi
# k6 실패 뒤에도 최종 DB를 검사하고 파이프의 k6·tee 종료 코드를 각각 저장한다.
set +e
k6 run --summary-mode=full --summary-export "$result_dir/summary.json" -e "SCENARIO=$scenario" "$script" 2>&1 | tee "$result_dir/k6.txt"
pipeline_status=("${PIPESTATUS[@]}")
set -e
k6_status=${pipeline_status[0]}
tee_status=${pipeline_status[1]}
check_status=0
if docker compose exec -T postgres psql -X -U "$POSTGRES_USER" -d reservation_load \
  -v ON_ERROR_STOP=1 -v "scenario=$scenario" < load/check-invariants.sql > "$result_dir/check.txt" 2>&1; then
  cat "$result_dir/check.txt"
else
  check_status=$?
  cat "$result_dir/check.txt" >&2
fi
{
  printf 'finished_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'reset_exit=%s\nk6_exit=%s\ncheck_exit=%s\ntee_exit=%s\n' "$reset_status" "$k6_status" "$check_status" "$tee_status"
} >> "$meta"
printf '결과: %s\n' "$result_dir"
if [[ "$k6_status" -ne 0 ]]; then exit "$k6_status"; fi
if [[ "$check_status" -ne 0 ]]; then exit "$check_status"; fi
if [[ "$tee_status" -ne 0 ]]; then exit "$tee_status"; fi
