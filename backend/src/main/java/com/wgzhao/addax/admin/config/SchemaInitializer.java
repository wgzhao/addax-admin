package com.wgzhao.addax.admin.config;

import com.wgzhao.addax.admin.config.schema.AlertSchema;
import com.wgzhao.addax.admin.config.schema.AppMigrationSchema;
import com.wgzhao.addax.admin.config.schema.AuditSchema;
import com.wgzhao.addax.admin.config.schema.DictionarySchema;
import com.wgzhao.addax.admin.config.schema.ExecutionSchema;
import com.wgzhao.addax.admin.config.schema.FailureSchema;
import com.wgzhao.addax.admin.config.schema.NotificationSchema;
import com.wgzhao.addax.admin.config.schema.ReconcileSchema;
import com.wgzhao.addax.admin.config.schema.SchemaSupport;
import com.wgzhao.addax.admin.config.schema.SourceSchema;
import com.wgzhao.addax.admin.config.schema.TableSchema;
import com.wgzhao.addax.admin.config.schema.TargetSchemaMigration;
import com.wgzhao.addax.admin.config.schema.UserSchema;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Startup schema creation and migration.
 *
 * <p>There is no migration framework in this project and {@code scripts/schema.sql} is written by hand for
 * fresh installs. Both jobs happen here: an empty database is initialized from the scripts bundled in the
 * jar, and an existing database is upgraded. Everything lives in one ordered pipeline
 * on purpose: the steps below all touch {@code vw_etl_table_with_source} (each release exposes another
 * column through it), and a step that recreates the view before the column it selects exists aborts
 * startup. That is exactly how the 4.2.1 upgrade of a 4.2.0 database failed — the target-options migration
 * recreated the view with {@code s.calendar} in it while the calendar migration, which adds that column,
 * was still waiting for its turn in a separate bean.</p>
 *
 * <p>The DDL itself lives in the {@code config.schema} units, one per table group. This class owns the
 * order; each unit owns its statements and the conditions that guard them. The pipeline keeps the
 * properties of the initializers it replaces:</p>
 * <ul>
 *   <li>it runs in a single transaction behind a PostgreSQL advisory transaction lock, so concurrent
 *       instances cannot interleave, and PostgreSQL's transactional DDL rolls the whole upgrade back on
 *       failure. A failure aborts startup on purpose — serving traffic against a half-migrated schema (a
 *       view without its columns) would be worse than not starting;</li>
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
     * Fresh-install scripts, bundled from {@code scripts/} at build time. A database initialized from the
     * jar cannot disagree with the jar about the schema; the previous Docker setup ran them from a host
     * directory, so an image older than the downloaded {@code schema.sql} failed on startup (issue #125).
     */
    private static final String SCHEMA_RESOURCE = "/schema.sql";

    private static final String DATA_RESOURCE = "/data.sql";

    private final JdbcTemplate jdbcTemplate;
    private final TargetSchemaMigration targetSchemaMigration;

    @PostConstruct
    public void migrate()
    {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement())
            {
                statement.execute("select pg_advisory_xact_lock(" + MIGRATION_LOCK_KEY + ")");
                initializeEmptyDatabase(connection, statement);
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

    /**
     * Creates the schema for a database that has no tables yet.
     *
     * <p>Runs inside the same transaction and advisory lock as the upgrade, so a failed initialization
     * leaves an empty database behind and the next start simply retries. Existing databases never take
     * this path: the upgrade pipeline below is what moves them forward.</p>
     */
    private void initializeEmptyDatabase(Connection connection, Statement statement) throws SQLException
    {
        if (!isPublicSchemaEmpty(connection))
        {
            return;
        }
        log.info("Empty database detected, creating the schema from the bundled {}", SCHEMA_RESOURCE);
        try
        {
            executeScript(statement, SCHEMA_RESOURCE);
            // schema.sql is a pg_dump and clears search_path; data.sql calls a seed function unqualified.
            statement.execute("set search_path = public");
            executeScript(statement, DATA_RESOURCE);
        }
        finally
        {
            resetSessionSettings(connection);
        }
        log.info("Bundled schema.sql and data.sql applied");
    }

    /**
     * The dump's SET statements are session-scoped and outlive the transaction. Leaking them into a
     * pooled connection (an empty search_path, suppressed notices, disabled timeouts) would break
     * unrelated statements later, so put the session back to its defaults once the scripts are done.
     * A failed script leaves the transaction aborted, where RESET cannot run; the startup failure
     * already aborts the process and a new pool is built on the next start.
     */
    private void resetSessionSettings(Connection connection)
    {
        try (Statement reset = connection.createStatement())
        {
            reset.execute("reset all");
        }
        catch (SQLException e)
        {
            log.debug("Could not reset the session settings after database initialization", e);
        }
    }

    private boolean isPublicSchemaEmpty(Connection connection) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "select count(*) from information_schema.tables where table_schema = 'public'");
            ResultSet rs = ps.executeQuery())
        {
            return rs.next() && rs.getLong(1) == 0;
        }
    }

    /**
     * Executes a bundled script through one statement, which PostgreSQL runs as a single simple query and
     * therefore accepts the whole file including the dollar-quoted function bodies.
     */
    private void executeScript(Statement statement, String resource) throws SQLException
    {
        String sql;
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(resource))
        {
            if (in == null)
            {
                throw new SQLException("Bundled resource " + resource + " is missing from the application jar");
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new SQLException("Failed to read the bundled " + resource, e);
        }
        // pg_dump emits psql meta-commands (restrict/unrestrict) for its own safety; they are not SQL
        // and the script already runs inside the migration transaction that provides the same guarantee.
        statement.execute(sql.replaceAll("(?m)^[ \\t]*\\\\.*$", ""));
    }

    /**
     * The ordered upgrade pipeline. Units are called in the order their columns become visible to the
     * view; changing that order can recreate the view without a column a later unit adds.
     */
    private void upgrade(Connection connection, Statement statement) throws SQLException
    {
        AuditSchema.ensure(statement);
        AppMigrationSchema.ensure(statement);
        // Alerts do not depend on the collection schema; create them before the etl table check so an
        // installation without sources can still alert.
        AlertSchema.ensure(statement);
        // Authentication state is independent of the collection schema; upgrade it even on an
        // installation that has never created a source.
        UserSchema.upgrade(connection, statement);
        NotificationSchema.purgeLegacy(statement);

        boolean hasSource = SchemaSupport.exists(connection, "tables", "etl_source", null);
        boolean hasTable = SchemaSupport.exists(connection, "tables", "etl_table", null);
        if (!hasSource && !hasTable)
        {
            log.info("etl_source/etl_table not found, skip schema migration");
            return;
        }
        if (hasSource)
        {
            SourceSchema.upgrade(connection, statement);
        }
        boolean legacyHiveColumns = hasTable && TableSchema.hasLegacyHiveColumns(connection);
        if (hasTable)
        {
            TableSchema.upgradeColumns(connection, statement, hasSource);
            ExecutionSchema.ensureTable(statement);
            FailureSchema.ensureTable(statement);
            ReconcileSchema.ensureTable(statement);
            if (legacyHiveColumns)
            {
                TableSchema.backfillLegacyHiveOptions(statement);
            }
        }
        if (hasTable && SchemaSupport.exists(connection, "tables", "etl_statistic", null)
            && SchemaSupport.exists(connection, "tables", "etl_jour", null)
            && SchemaSupport.exists(connection, "tables", "addax_log", null))
        {
            ExecutionSchema.upgradeContext(statement);
        }
        if (hasSource && hasTable && SchemaSupport.exists(connection, "tables", "etl_target", null))
        {
            TableSchema.upgradeTargetScope(connection, statement);
            targetSchemaMigration.migrateConnectKeys(connection);
            targetSchemaMigration.migrateHdfsConfig(connection);
            TableSchema.dropKindColumn(connection, statement);
            if (SchemaSupport.exists(connection, "tables", "sys_item", null))
            {
                DictionarySchema.purgeDeprecated(statement);
            }
            refreshView(connection, statement);
        }
        if (legacyHiveColumns)
        {
            TableSchema.dropLegacyColumns(statement);
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
}
