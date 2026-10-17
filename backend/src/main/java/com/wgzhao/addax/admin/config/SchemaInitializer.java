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
 * Startup schema migration for databases created by an older release.
 *
 * <p>There is no migration framework in this project and {@code scripts/schema.sql} is written by hand for
 * fresh installs, so upgrading an existing database happens here. Everything lives in one ordered pipeline
 * on purpose: the steps below all touch {@code vw_etl_table_with_source} (each release exposes another
 * column through it), and a step that recreates the view before the column it selects exists aborts
 * startup. That is exactly how the 4.2.1 upgrade of a 4.2.0 database failed — the target-options migration
 * recreated the view with {@code s.calendar} in it while the calendar migration, which adds that column,
 * was still waiting for its turn in a separate bean.</p>
 *
 * <p>The pipeline keeps the properties of the initializers it replaces:</p>
 * <ul>
 *   <li>it runs in a single transaction behind a PostgreSQL advisory transaction lock, so concurrent
 *       instances cannot interleave, and PostgreSQL's transactional DDL rolls the whole upgrade back on
 *       failure. A failure aborts startup on purpose — serving traffic against a half-migrated schema (a
 *       view without its columns) would be worse than not starting.</li>
 *   <li>every step is guarded and repeatable, so an interrupted run can simply be retried;</li>
 *   <li>it runs from {@code @PostConstruct}, i.e. before the web server accepts requests, before the
 *       scheduled refresh starts, and before any query can reach the columns it adds;</li>
 *   <li>the view is refreshed last, from {@link EtlTableView#CREATE_SQL}, so its definition may reference
 *       every column the earlier steps add.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaInitializer
{
    /**
     * Arbitrary but stable key; any instance running this migration contends for the same lock.
     */
    private static final long MIGRATION_LOCK_KEY = 662012001L;

    /**
     * The out-of-the-box calendar every source uses when it has no explicit one.
     */
    private static final int WORK_DAY_DICT_CODE = 1022;

    private static final String ADD_CALENDAR_COLUMN_SQL =
        "alter table public.etl_source add column if not exists calendar varchar(32)";

    private static final String SEED_WORK_DAY_DICT_SQL = """
        insert into public.sys_dict (code, name, classification, remark)
        values (1022, '工作日历', 'FR', '工作日(含调休)日期表，item_key 为 yyyyMMdd 的业务日期')
        on conflict (code) do nothing
        """;

    private static final String ADD_TARGET_OPTIONS_SQL =
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

    /**
     * Marker of the pre-4.2.1 Hive attributes: present means the target-options move has not run yet.
     */
    private static final String LEGACY_COLUMN = "part_name";

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
        boolean hasSource = exists(connection, "tables", "etl_source", null);
        boolean hasTable = exists(connection, "tables", "etl_table", null);
        if (!hasSource && !hasTable)
        {
            log.info("etl_source/etl_table not found, skip schema migration");
            return;
        }
        if (hasSource)
        {
            statement.execute(ADD_CALENDAR_COLUMN_SQL);
            if (statement.executeUpdate(SEED_WORK_DAY_DICT_SQL) > 0)
            {
                log.info("Seeded the work-day calendar dictionary (code {})", WORK_DAY_DICT_CODE);
            }
        }
        boolean legacyHiveColumns = hasTable && exists(connection, "columns", "etl_table", LEGACY_COLUMN);
        if (hasTable)
        {
            statement.execute(ADD_TARGET_OPTIONS_SQL);
            if (legacyHiveColumns)
            {
                int updated = statement.executeUpdate(BACKFILL_SQL);
                log.info("Backfilled target_options for {} etl_table row(s)", updated);
            }
        }
        if (hasSource && hasTable && exists(connection, "tables", "etl_target", null))
        {
            refreshView(connection, statement);
        }
        if (legacyHiveColumns)
        {
            statement.execute(DROP_LEGACY_COLUMNS_SQL);
            log.info("Migrated etl_table Hive attributes (part_*/storage/compress) into target_options");
        }
    }

    /**
     * Recreates the view when the database holds a definition this build no longer expects.
     *
     * <p>The definition ({@link EtlTableView#CREATE_SQL}) carries its own fingerprint and {@link
     * EtlTableView} stores it as the view's comment, so "is the view current?" is answered by the view
     * itself instead of by a list of columns that someone has to remember to update. A change to the SQL
     * therefore reaches every database on the next start, and an unchanged one is left alone.</p>
     */
    private void refreshView(Connection connection, Statement statement) throws SQLException
    {
        if (EtlTableView.FINGERPRINT.equals(viewComment(connection)))
        {
            return;
        }
        statement.execute(EtlTableView.DROP_SQL);
        statement.execute(EtlTableView.CREATE_SQL);
        statement.execute(EtlTableView.commentSql());
        log.info("Recreated {} to expose the columns this build reads", EtlTableView.NAME);
    }

    /**
     * Comment of an existing {@code vw_etl_table_with_source}, {@code null} when it is missing or has no
     * comment. Looked up through {@code pg_class} rather than {@code obj_description('…'::regclass)}
     * because a missing relation must not raise here.
     */
    private String viewComment(Connection connection) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "select obj_description(c.oid, 'pg_class') from pg_class c "
                + "join pg_namespace n on n.oid = c.relnamespace "
                + "where n.nspname = 'public' and c.relname = ?"))
        {
            ps.setString(1, EtlTableView.NAME);
            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Existence check on the migration's own connection: the DDL above is uncommitted, so a separate pooled
     * connection (JdbcTemplate) would not see it. {@code relation} and {@code name} are constants from this
     * class, not input.
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
