#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
: "${POSTGRES_USER:?POSTGRES_USER 환경 변수가 필요합니다}"
exists=$(docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
  -tAc "SELECT 1 FROM pg_database WHERE datname='reservation_load'")
if [[ "$exists" != 1 ]]; then
  docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
    -c 'CREATE DATABASE reservation_load'
fi
