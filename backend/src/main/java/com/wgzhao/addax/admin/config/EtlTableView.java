package com.wgzhao.addax.admin.config;

/**
 * Single source of truth for the {@code vw_etl_table_with_source} definition.
 *
 * <p>Several startup initializers have to recreate the view as they add columns, and each recreation
 * rewrites the whole definition — so each of them would otherwise carry its own copy and the last one
 * to run would decide which columns survive. {@code scripts/schema.sql} holds a hand-maintained copy
 * for fresh installs; keep that one in sync when this changes.</p>
 */
public final class EtlTableView
{
    public static final String NAME = "vw_etl_table_with_source";

    public static final String CREATE_SQL = """
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
            s.calendar,
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

    public static final String DROP_SQL = "drop view if exists public." + NAME;

    private EtlTableView()
    {
    }
}
