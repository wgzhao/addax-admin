package com.wgzhao.addax.admin.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One-shot upgrade of the target-attribute schema (Phase 2 of #66).
 *
 * <p>The five Hive-only columns ({@code part_kind / part_name / part_format / storage_format /
 * compress_format}) move into a per-target bag ({@code etl_table.target_options}), so the legacy columns
 * and the {@code vw_etl_table_with_source} view have to change in lockstep. There is no migration
 * framework in this project, and {@code scripts/schema.sql} is written by hand for fresh installs, so the
 * upgrade for an existing database runs here.</p>
 *
 * <p>This is the only startup DDL in the project, so it is deliberately conservative:</p>
 * <ul>
 *   <li>it runs in a single transaction that starts by taking a PostgreSQL advisory transaction lock, so
 *       concurrent instances cannot interleave and PostgreSQL's transactional DDL rolls the whole upgrade
 *       back on failure. A failure aborts startup on purpose — serving traffic against a half-migrated
 *       schema (a view without its columns) would be worse than not starting.</li>
 *   <li>the steps are ordered and guarded (add column → backfill → recreate view → drop legacy columns), so
 *       a database installed from {@code scripts/schema.sql} is a no-op and an interrupted run is safe to
 *       repeat.</li>
 *   <li>it runs from {@code @PostConstruct}, i.e. before the web server accepts requests and before the
 *       scheduled refresh starts.</li>
 *   <li>the view definition below is duplicated from {@code scripts/schema.sql} on purpose; keep the two in
 *       sync whenever the view changes.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TargetOptionsSchemaInitializer
{
    /**
     * Arbitrary but stable key; any instance running this migration contends for the same lock.
     */
    private static final long MIGRATION_LOCK_KEY = 662012001L;

    private static final String VIEW_NAME = "vw_etl_table_with_source";

    private static final String ADD_COLUMN_SQL =
        "alter table public.etl_table add column if not exists target_options jsonb";

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

    private static final String DROP_VIEW_SQL =
        "drop view if exists public.vw_etl_table_with_source";

    private static final String CREATE_VIEW_SQL = """
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
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement())
            {
                statement.execute("select pg_advisory_xact_lock(" + MIGRATION_LOCK_KEY + ")");
                upgrade(connection, statement);
                connection.commit();
                return null;
            }
            catch (SQLException | RuntimeException e)
            {
                connection.rollback();
                throw e;
            }
            finally
            {
                connection.setAutoCommit(autoCommit);
            }
        });
    }

    private void upgrade(Connection connection, Statement statement) throws SQLException
    {
        if (!exists(connection, "tables", "etl_table", null))
        {
            log.info("etl_table not found, skip target_options migration");
            return;
        }
        statement.execute(ADD_COLUMN_SQL);
        if (exists(connection, "columns", "etl_table", "part_name"))
        {
            int updated = statement.executeUpdate(BACKFILL_SQL);
            log.info("Backfilled target_options for {} etl_table row(s)", updated);
            statement.execute(DROP_VIEW_SQL);
            statement.execute(CREATE_VIEW_SQL);
            statement.execute(DROP_LEGACY_COLUMNS_SQL);
            log.info("Migrated etl_table Hive attributes (part_*/storage/compress) into target_options");
        }
        else if (!exists(connection, "columns", VIEW_NAME, "target_options"))
        {
            // The legacy columns are gone but the view may still predate the new column.
            statement.execute(DROP_VIEW_SQL);
            statement.execute(CREATE_VIEW_SQL);
            log.info("Recreated {} to expose target_options", VIEW_NAME);
        }
    }

    /**
     * Existence check on the migration's own connection: the DDL above is uncommitted, so a separate pooled
     * connection (JdbcTemplate) would not see it. {@code relation} is a constant from this class, not input.
     */
    private boolean exists(Connection connection, String relation, String name, String column)
        throws SQLException
    {
        String sql = "select count(*) from information_schema." + relation
            + " where table_schema = 'public' and table_name = ?"
            + (column == null ? "" : " and column_name = ?");
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, name);
            if (column != null)
            {
                ps.setString(2, column);
            }
            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
