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
# 결과 디렉터리·meta 자체가 미추적 변경으로 잡히지 않도록 생성 전에 시작 상태를 저장한다.
git_dirty_at_start=false
if [[ -n "$(git status --porcelain)" ]]; then git_dirty_at_start=true; fi
result_dir="docs/performance-runs/$(date -u +%Y%m%d)-$scenario-$label"
# Git이 빈 디렉터리를 보존하지 않으므로 부모 경로도 실행 시 준비한다.
mkdir -p docs/performance-runs
# 기존 증거를 덮어쓰지 않는다. 재실행에는 새 label을 사용한다.
mkdir "$result_dir"
meta="$result_dir/meta.txt"
{
  printf 'started_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'git_commit=%s\n' "$(git rev-parse HEAD)"
  printf 'git_dirty=%s\n' "$git_dirty_at_start"
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
rate_status=0
record_rate_failure() {
  if [[ "$1" -ne 0 && "$rate_status" -eq 0 ]]; then rate_status=$1; fi
}
snapshot_load_db() {
  docker compose exec -T postgres psql -X -qAt -U "$POSTGRES_USER" -d reservation_load -v ON_ERROR_STOP=1 <<'SQL'
SELECT jsonb_build_object(
  'resources', (SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY r.id), '[]'::jsonb) FROM resources r),
  'reservations', (SELECT coalesce(jsonb_agg(to_jsonb(v) ORDER BY v.id), '[]'::jsonb) FROM reservations v)
)::text;
SQL
}
run_rate_phase() {
  local phase=$1
  set +e
  k6 run --summary-mode=full --summary-export "$result_dir/summary-$phase.json" \
    -e "SCENARIO=$scenario" -e "PHASE=$phase" -e "RESOURCE_ID=${resource_id:-}" \
    load/rate-limit.js 2>&1 | tee "$result_dir/k6-$phase.txt"
  local phase_pipeline=("${PIPESTATUS[@]}")
  set -e
  phase_k6_status=${phase_pipeline[0]}
  phase_tee_status=${phase_pipeline[1]}
  record_rate_failure "$phase_k6_status"
  record_rate_failure "$phase_tee_status"
}
if [[ "$scenario" == rate-limit ]]; then
  # reset 이후 지연 없이 같은 60초 창에서 두 단계를 연속 실행한다.
  rate_started=$SECONDS
  run_rate_phase fill
  fill_k6_status=$phase_k6_status
  fill_tee_status=$phase_tee_status
  k6_status=$phase_k6_status
  tee_status=$phase_tee_status
  before_status=not_run
  lookup_status=not_run
  limited_k6_status=not_run
  limited_tee_status=not_run
  after_status=not_run
  compare_status=not_run
  limited_db_unchanged=false
  if [[ "$rate_status" -eq 0 ]]; then
    before_status=0
    snapshot_load_db > "$result_dir/before-limited.txt" 2> "$result_dir/before-limited-error.txt" || before_status=$?
    record_rate_failure "$before_status"
    lookup_status=0
    resource_id=$(docker compose exec -T postgres psql -X -qAt -U "$POSTGRES_USER" -d reservation_load \
      -v ON_ERROR_STOP=1 -c 'SELECT id FROM resources ORDER BY id' 2> "$result_dir/resource-id-error.txt") || lookup_status=$?
    if [[ "$lookup_status" -eq 0 && ! "$resource_id" =~ ^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$ ]]; then
      echo 'fill 뒤 대상 행이 정확히 1건이어야 합니다.' >&2
      lookup_status=1
    fi
    record_rate_failure "$lookup_status"
    if [[ "$rate_status" -eq 0 ]]; then
      run_rate_phase limited
      limited_k6_status=$phase_k6_status
      limited_tee_status=$phase_tee_status
      k6_status=$limited_k6_status
      tee_status=$limited_tee_status
      after_status=0
      snapshot_load_db > "$result_dir/after-limited.txt" 2> "$result_dir/after-limited-error.txt" || after_status=$?
      record_rate_failure "$after_status"
      if [[ "$after_status" -eq 0 ]]; then
        compare_status=0
        cmp -s "$result_dir/before-limited.txt" "$result_dir/after-limited.txt" || compare_status=$?
        if [[ "$compare_status" -eq 0 ]]; then limited_db_unchanged=true; fi
        record_rate_failure "$compare_status"
      fi
    fi
  fi
  rate_elapsed=$((SECONDS - rate_started))
  window_status=0
  if [[ "$rate_elapsed" -ge 60 ]]; then window_status=1; fi
  record_rate_failure "$window_status"
  {
    # 실행하지 못한 단계도 명시하고 모든 판정 코드를 보존한다.
    printf 'fill_k6_exit=%s\nfill_tee_exit=%s\n' "$fill_k6_status" "$fill_tee_status"
    printf 'limited_k6_exit=%s\nlimited_tee_exit=%s\n' "$limited_k6_status" "$limited_tee_status"
    printf 'before_snapshot_exit=%s\nresource_lookup_exit=%s\nafter_snapshot_exit=%s\n' "$before_status" "$lookup_status" "$after_status"
    printf 'limited_db_compare_exit=%s\nlimited_db_unchanged=%s\n' "$compare_status" "$limited_db_unchanged"
    printf 'rate_elapsed_seconds=%s\nrate_window_exit=%s\n' "$rate_elapsed" "$window_status"
  } >> "$meta"
else
  # k6 실패 뒤에도 최종 DB를 검사하고 파이프의 k6·tee 종료 코드를 각각 저장한다.
  set +e
  k6 run --summary-mode=full --summary-export "$result_dir/summary.json" -e "SCENARIO=$scenario" \
    load/reservation-load.js 2>&1 | tee "$result_dir/k6.txt"
  pipeline_status=("${PIPESTATUS[@]}")
  set -e
  k6_status=${pipeline_status[0]}
  tee_status=${pipeline_status[1]}
fi
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
if [[ "$rate_status" -ne 0 ]]; then exit "$rate_status"; fi
if [[ "$k6_status" -ne 0 ]]; then exit "$k6_status"; fi
if [[ "$check_status" -ne 0 ]]; then exit "$check_status"; fi
if [[ "$tee_status" -ne 0 ]]; then exit "$tee_status"; fi
