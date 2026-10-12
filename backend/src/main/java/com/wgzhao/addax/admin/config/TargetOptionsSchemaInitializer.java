package com.wgzhao.addax.admin.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-shot, idempotent upgrade of the target-attribute schema.
 *
 * <p>Phase 2 moves the Hive-only table attributes ({@code part_kind/part_name/part_format/
 * storage_format/compress_format}) out of {@code etl_table} into a per-target bag
 * ({@code etl_table.target_options}), which means the legacy columns and the
 * {@code vw_etl_table_with_source} view have to move in lockstep. There is no migration framework in
 * this project, so the upgrade runs on startup exactly like the other schema guards
 * ({@code TargetTypeResolver}, the dict/template initializers). Every step is guarded, so a fresh
 * database installed from {@code scripts/schema.sql} is a no-op.</p>
 *
 * <p>PostgreSQL runs DDL transactionally, but JdbcTemplate auto-commits per statement; the steps are
 * therefore ordered so an interrupted run is safe to repeat: add column → backfill → recreate view →
 * drop legacy columns.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TargetOptionsSchemaInitializer
{
    private static final String VIEW_NAME = "vw_etl_table_with_source";

    private static final String BACKFILL_SQL = """
        update public.etl_table
           set target_options = jsonb_build_object('hdfs', jsonb_build_object(
                 'partKind', coalesce(part_kind, 'D'),
                 'partName', coalesce(part_name, ''),
                 'partFormat', coalesce(nullif(part_format, ''), 'yyyyMMdd'),
                 'storageFormat', coalesce(nullif(storage_format, ''), 'orc'),
                 'compressFormat', coalesce(nullif(compress_format, ''), 'snappy')))
         where target_options is null
        """;

    private static final String DROP_LEGACY_COLUMNS_SQL = """
        alter table public.etl_table
            drop column if exists part_kind,
            drop column if exists part_name,
            drop column if exists part_format,
            drop column if exists storage_format,
            drop column if exists compress_format
        """;

    private static final String RECREATE_VIEW_SQL = """
        drop view if exists public.vw_etl_table_with_source;
        create view public.vw_etl_table_with_source as
         select t.id,
            t.source_db,
            t.source_table,
            t.target_db,
            t.target_table,
            t.target_options,
            t.filter,
            t.kind,
            t.retry_cnt,
            t.start_time,
            t.end_time,
            t.max_runtime,
            t.sid,
            t.duration,
            t.tbl_comment,
            t.status,
            t.split_pk,
            t.auto_pk,
            t.write_mode,
            t.start_at,
            t.target_id,
            t.created_at,
            t.updated_at,
            t.reader_plugin_config,
            t.writer_plugin_config,
            s.code,
            s.name,
            s.url,
            s.username,
            s.pass,
            s.start_at as source_start_at,
            s.enabled,
            s.max_concurrency,
            s.db_type,
            tt.target_type,
            tt.code as target_code,
            tt.name as target_name,
            tt.enabled as target_enabled
           from ((public.etl_table t
             left join public.etl_source s on ((t.sid = s.id)))
             left join public.etl_target tt on ((t.target_id = tt.id)))
        """;

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate()
    {
        if (!tableExists("etl_table"))
        {
            log.info("etl_table not found, skip target_options migration");
            return;
        }
        addTargetOptionsColumn();
        if (columnExists("etl_table", "part_name"))
        {
            backfillFromLegacyColumns();
            recreateView();
            dropLegacyColumns();
            log.info("Migrated etl_table Hive attributes (part_*/storage/compress) into target_options");
        }
        else
        {
            // The view may still predate the new column even when the legacy columns are already gone.
            if (!viewExposesTargetOptions())
            {
                recreateView();
                log.info("Recreated {} to expose target_options", VIEW_NAME);
            }
        }
    }

    private void addTargetOptionsColumn()
    {
        jdbcTemplate.execute("alter table public.etl_table add column if not exists target_options jsonb");
    }

    private void backfillFromLegacyColumns()
    {
        int updated = jdbcTemplate.update(BACKFILL_SQL);
        log.info("Backfilled target_options for {} etl_table row(s)", updated);
    }

    private void recreateView()
    {
        jdbcTemplate.execute(RECREATE_VIEW_SQL);
    }

    private void dropLegacyColumns()
    {
        jdbcTemplate.execute(DROP_LEGACY_COLUMNS_SQL);
    }

    private boolean viewExposesTargetOptions()
    {
        return columnExists(VIEW_NAME, "target_options");
    }

    private boolean tableExists(String name)
    {
        Integer count = jdbcTemplate.queryForObject("""
            select count(*) from information_schema.tables
             where table_schema = 'public' and table_name = ?
            """, Integer.class, name);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column)
    {
        Integer count = jdbcTemplate.queryForObject("""
            select count(*) from information_schema.columns
             where table_schema = 'public' and table_name = ? and column_name = ?
            """, Integer.class, table, column);
        return count != null && count > 0;
    }
}
