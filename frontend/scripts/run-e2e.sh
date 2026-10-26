#!/usr/bin/env bash
# E2E 전용 실행기: Compose PostgreSQL의 reservation_e2e와 Redis DB 1을 쓰는 백엔드를 18080에 직접 띄우고 Playwright를 실행한다.
# 한글·비ASCII 문자와 맞닿은 변수는 로케일 의존 파싱을 피하도록 항상 ${VAR} 형태로 쓴다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${FRONTEND_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

BACKEND_PORT=18080
BACKEND_BASE_URL="http://127.0.0.1:${BACKEND_PORT}"
HEALTH_URL="${BACKEND_BASE_URL}/actuator/health"
E2E_DB=reservation_e2e
# Playwright가 시작 시 기본 outputDir(test-results/)를 비우므로 로그는 별도 디렉터리에 둔다.
LOG_DIR="${FRONTEND_DIR}/e2e-logs"
LOG_FILE="${LOG_DIR}/e2e-backend.log"
JAR_PATH="${BACKEND_DIR}/build/libs/backend-0.0.1-SNAPSHOT.jar"

# (1) 필수 환경 변수: 값은 출력하지 않고 이름만 알린다.
missing=()
for name in POSTGRES_USER POSTGRES_PASSWORD ADMIN_PASSWORD USER_A_PASSWORD USER_B_PASSWORD; do
  if [ -z "${!name:-}" ]; then
    missing+=("${name}")
  fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "E2E 필수 환경 변수가 없습니다: ${missing[*]}" >&2
  exit 1
fi

# (2) 알 수 없는 백엔드를 쓰지 않도록 18080이 비어 있는지 확인한다.
if curl -s -o /dev/null "${HEALTH_URL}" 2>/dev/null; then
  echo "포트 ${BACKEND_PORT}에 이미 응답하는 백엔드가 있습니다. 알 수 없는 백엔드 사용 방지: E2E를 중단합니다." >&2
  exit 1
fi
if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"${BACKEND_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "포트 ${BACKEND_PORT}가 이미 사용 중입니다. 알 수 없는 백엔드 사용 방지: E2E를 중단합니다." >&2
  exit 1
fi

# (3) Compose PostgreSQL에 전용 DB가 없으면 만든다(로컬 개발 DB reservation_local은 쓰지 않는다).
(cd "${REPO_ROOT}" && docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d reservation_local -tAc "SELECT 1 FROM pg_database WHERE datname='"'"'reservation_e2e'"'"'" | grep -q 1 || createdb -U "$POSTGRES_USER" reservation_e2e')

# (4) 실행 jar를 만들고 18080에 백그라운드로 기동한다.
(cd "${BACKEND_DIR}" && ./gradlew --no-daemon -q bootJar)
mkdir -p "${LOG_DIR}"
SERVER_PORT="${BACKEND_PORT}" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/${E2E_DB}" \
  SPRING_DATA_REDIS_DATABASE=1 \
  java -jar "${JAR_PATH}" --spring.profiles.active=local >"${LOG_FILE}" 2>&1 &
BACKEND_PID=$!

cleanup() {
  # 정리 단계의 kill/wait 실패는 이미 종료된 프로세스일 수 있으므로 무시한다(실패 은폐 목적 아님).
  kill "${BACKEND_PID}" 2>/dev/null || true
  wait "${BACKEND_PID}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# (5) health UP을 최대 180초 기다린다. 그 사이 프로세스가 죽으면 로그를 보여주고 실패한다.
ready=0
for _ in $(seq 1 180); do
  if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
    echo "E2E 백엔드가 기동 중 종료되었습니다. 로그 마지막 30줄:" >&2
    tail -n 30 "${LOG_FILE}" >&2 || true
    exit 1
  fi
  if curl -fsS --max-time 2 "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
    ready=1
    break
  fi
  sleep 1
done
if [ "${ready}" -ne 1 ]; then
  echo "E2E 백엔드 health UP 대기 시간(180초)을 초과했습니다. 로그 마지막 30줄:" >&2
  tail -n 30 "${LOG_FILE}" >&2 || true
  exit 1
fi

# (6) Playwright를 실행하고 그 종료 코드를 그대로 반환한다(set -e와 EXIT trap이 코드를 보존한다).
cd "${FRONTEND_DIR}"
E2E_ISOLATED_BACKEND=1 E2E_BACKEND_BASE_URL="${BACKEND_BASE_URL}" npx playwright test "$@"
