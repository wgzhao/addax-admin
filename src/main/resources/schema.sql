-- vw_etl_table_with_source 视图 SQL 示例（PostgreSQL）
CREATE OR REPLACE VIEW vw_etl_table_with_source AS
SELECT
    t.id,
    t.source_db,
    t.source_table,
    t.target_db,
    t.target_table,
    t.part_kind,
    t.part_name,
    t.filter,
    t.status,
    t.kind,
    t.update_flag,
    t.create_flag,
    t.retry_cnt,
    t.sid,
    t.duration,
    s.code,
    s.name,
    s.url,
    s.username,
    s.pass,
    s.start_at,
    s.enabled
FROM etl_table t
LEFT JOIN etl_source s ON t.sid = s.id;

