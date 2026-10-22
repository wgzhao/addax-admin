package com.wgzhao.addax.admin.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Single source of truth for the {@code vw_etl_table_with_source} definition.
 *
 * <p>{@link SchemaInitializer} recreates the view from this definition, and {@link #FINGERPRINT} — stored
 * as the view's comment — is what tells a database whether the definition it holds is the current one, so
 * adding a column here is enough for every deployment to pick it up on its next start.</p>
 *
 * <p>{@code scripts/schema.sql} holds a hand-maintained copy for fresh installs; keep that one in sync
 * when this changes. It does not carry the fingerprint, so the first start of a fresh install recreates
 * the view once and every start after that leaves it alone.</p>
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
            t.schema_management_mode,
            t.target_schema_fingerprint,
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
            t.retired_at,
            t.retired_by,
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

    /**
     * Fingerprint of {@link #CREATE_SQL}, used as the version marker of the view.
     */
    public static final String FINGERPRINT = fingerprint(CREATE_SQL);

    /**
     * Records {@link #FINGERPRINT} as the view's comment. {@code FINGERPRINT} is hex from {@link
     * HexFormat}, so it needs no quoting or escaping.
     */
    public static String commentSql()
    {
        return "comment on view public." + NAME + " is '" + FINGERPRINT + "'";
    }

    private EtlTableView()
    {
    }

    private static String fingerprint(String ddl)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ddl.getBytes(StandardCharsets.UTF_8));
            return "addax-view:" + HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e)
        {
            // Every JVM is required to ship SHA-256
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
