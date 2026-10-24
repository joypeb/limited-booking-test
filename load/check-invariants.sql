\set ON_ERROR_STOP on
BEGIN ISOLATION LEVEL REPEATABLE READ;
DO $$ BEGIN
  IF current_database() <> 'reservation_load' THEN
    RAISE EXCEPTION 'load 전용 DB가 아닙니다';
  END IF;
END $$;
CREATE TEMP VIEW load_inventory AS
SELECT r.id, r.capacity, r.available,
       count(v.id) FILTER (WHERE v.status = 'CONFIRMED') AS confirmed,
       count(v.id) FILTER (WHERE v.status = 'CANCELLED') AS cancelled
FROM resources r LEFT JOIN reservations v ON v.resource_id = r.id
GROUP BY r.id, r.capacity, r.available;

\echo '수량 불변식 위반 행 (0행 기대)'
SELECT * FROM load_inventory
WHERE available + confirmed <> capacity OR available < 0 OR available > capacity;
\echo '사용자·키 중복 그룹 (0행 기대)'
SELECT user_id, idempotency_key, count(*) AS duplicates
FROM reservations GROUP BY user_id, idempotency_key HAVING count(*) > 1;
\echo '대상별 요약'
SELECT * FROM load_inventory ORDER BY id;
\echo '전체 예약·고유 키 요약'
SELECT count(*) AS total_reservations,
       count(DISTINCT (user_id, idempotency_key)) AS unique_user_keys,
       count(DISTINCT idempotency_key) AS distinct_keys
FROM reservations;

SELECT NOT EXISTS (
  SELECT 1 FROM load_inventory
  WHERE available + confirmed <> capacity OR available < 0 OR available > capacity
) AND NOT EXISTS (
  SELECT 1 FROM reservations GROUP BY user_id, idempotency_key HAVING count(*) > 1
) AS invariants_ok \gset
\if :invariants_ok
  \echo '불변식 PASS'
\else
  \echo '불변식 FAIL'
  SELECT 1/0 AS invariant_violation;
\endif

-- run.sh가 전달한 시나리오의 정확한 최종 DB 상태도 판정한다.
\if :{?scenario}
SELECT CASE :'scenario'
  WHEN 'low' THEN count(*) = 10 AND bool_and(capacity = 1000 AND available = 1000 AND confirmed = 0)
  WHEN 'high' THEN count(*) = 1 AND bool_and(capacity = 1000 AND available = 0 AND confirmed = 1000 AND cancelled = 0)
  WHEN 'replay' THEN count(*) = 1 AND bool_and(capacity = 1000 AND available = 950 AND confirmed = 50 AND cancelled = 0)
  WHEN 'rate-limit' THEN count(*) = 1 AND bool_and(capacity = 100 AND available = 80 AND confirmed = 20 AND cancelled = 0)
  ELSE false END AS scenario_ok
FROM load_inventory \gset
\if :scenario_ok
  \echo '시나리오 최종 DB PASS'
\else
  \echo '시나리오 최종 DB FAIL'
  SELECT 1/0 AS scenario_violation;
\endif
\endif
COMMIT;
