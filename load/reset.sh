#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
: "${POSTGRES_USER:?POSTGRES_USER 환경 변수가 필요합니다}"
# 대상 DB는 인자로 받지 않는다. 개발 DB와 named volume은 보존한다.
docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d reservation_load <<'SQL'
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'reservation_load' THEN
    RAISE EXCEPTION 'load 전용 DB가 아닙니다';
  END IF;
END $$;
DELETE FROM reservations;
DELETE FROM resources;
COMMIT;
SQL
docker compose exec -T redis redis-cli DEL \
  reservation:write-limit:00000000-0000-0000-0000-000000000002 \
  reservation:write-limit:00000000-0000-0000-0000-000000000003
