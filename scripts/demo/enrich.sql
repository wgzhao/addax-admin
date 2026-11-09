-- Deterministic demo scenarios layered on a cloned Addax Admin database.
-- Safe to run repeatedly: the caller drops and recreates the target DB first.

BEGIN;

-- 1. Give the status distribution a realistic mix instead of all-green.
WITH ranked AS (
  SELECT id, row_number() OVER (ORDER BY id) AS rn
  FROM etl_table
  WHERE retired_at IS NULL
)
UPDATE etl_table t
SET status = CASE r.rn
  WHEN 1 THEN 'E'
  WHEN 2 THEN 'E'
  WHEN 3 THEN 'R'
  WHEN 4 THEN 'W'
  WHEN 5 THEN 'N'
  ELSE t.status
END,
updated_at = CURRENT_TIMESTAMP
FROM ranked r
WHERE t.id = r.id AND r.rn <= 5;

-- 2. Create failed journeys so the failure ranking and trend are populated.
INSERT INTO etl_jour (id, tid, execution_id, kind, start_at, duration, status, cmd, error_msg)
SELECT
  nextval(pg_get_serial_sequence('etl_jour', 'id')),
  t.id,
  NULL,
  'COLLECT',
  CURRENT_TIMESTAMP - make_interval(days => (row_number() OVER (ORDER BY t.id))::int),
  12,
  false,
  'demo',
  '演示数据：源端连接超时（connect timed out）'
FROM etl_table t
WHERE t.status = 'E'
ORDER BY t.id
LIMIT 2;

-- 3. Make two tables visibly slow.
WITH slow AS (
  SELECT id
  FROM etl_table
  WHERE retired_at IS NULL
  ORDER BY id DESC
  LIMIT 2
)
UPDATE etl_statistic s
SET take_secs = GREATEST(s.take_secs, 60) * 6,
    total_errors = GREATEST(s.total_errors, 1) + 3
WHERE s.tid IN (SELECT id FROM slow);

-- 4. Leave one recent partition missing so the missing calendar has a story.
WITH missing AS (
  SELECT id
  FROM etl_table
  WHERE retired_at IS NULL
  ORDER BY id DESC
  OFFSET 2 LIMIT 1
)
DELETE FROM etl_statistic s
USING missing m
WHERE s.tid = m.id
  AND s.biz_date = (SELECT max(biz_date) FROM etl_statistic);

-- 5. Seed the notification center with a clean, curated inbox.
-- Cloned databases can carry hundreds of historical success messages; the
-- demo starts from a read state and then adds only the curated alerts.
UPDATE user_notification
SET status = 'READ', read_at = CURRENT_TIMESTAMP
WHERE status = 'UNREAD';

WITH target AS (
  SELECT id, source_db, source_table
  FROM etl_table
  WHERE retired_at IS NULL
  ORDER BY id
  LIMIT 2
)
INSERT INTO user_notification (id, username, title, content, type, status, ref_type, ref_id, created_at)
SELECT
  nextval(pg_get_serial_sequence('user_notification', 'id')),
  COALESCE((SELECT username FROM users ORDER BY username LIMIT 1), 'admin'),
  '采集任务失败',
  '演示数据：表 ' || target.source_db || '.' || target.source_table || ' 连接超时，请查看执行快照与日志。',
  'ERROR',
  'UNREAD',
  'TABLE',
  target.id::text,
  CURRENT_TIMESTAMP - interval '10 minute'
FROM target;

INSERT INTO user_notification (id, username, title, content, type, status, ref_type, ref_id, created_at, read_at)
SELECT
  nextval(pg_get_serial_sequence('user_notification', 'id')),
  COALESCE((SELECT username FROM users ORDER BY username LIMIT 1), 'admin'),
  '采集任务完成',
  '演示数据：今日采集计划已全部完成。',
  'SUCCESS',
  'READ',
  NULL,
  NULL,
  CURRENT_TIMESTAMP - interval '2 minute',
  CURRENT_TIMESTAMP;

COMMIT;

-- Verification queries used by docs/demo/demo-script.md
SELECT status, count(*) FROM etl_table WHERE retired_at IS NULL GROUP BY status ORDER BY status;
SELECT count(*) AS failed_journeys FROM etl_jour WHERE status = false;
SELECT count(*) AS unread_notifications FROM user_notification WHERE status = 'UNREAD';
