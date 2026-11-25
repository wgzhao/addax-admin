package com.wgzhao.addax.admin.config.schema;

import com.wgzhao.addax.admin.config.EtlTableView;
import com.wgzhao.addax.admin.config.migration.SchemaMigrationStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The idempotent upgrade chain that predates versioned migrations, kept as one recorded Java step.
 *
 * <p>It carries every column and table addition shipped before the migration chain, so it can bring
 * any older database to the baseline in one run. An existing database adopts the chain by executing
 * this step; a fresh database runs it after the baseline snapshot, where most statements are no-ops.
 * The logic must not change after release: the checksum in {@link #checksum()} is recorded, and a
 * later change requires a new numbered step instead.</p>
 *
 * <p>New schema changes belong in {@code db/migration/V####__*.sql}; this class only preserves the
 * historical path. The view is refreshed last, so its definition may reference every column the
 * earlier units add.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacySchemaUpgrade
    implements SchemaMigrationStep
{
    private static final String VERSION = "0003";

    private static final String CHECKSUM = "java:legacy-schema-upgrade-v1";

    private final TargetSchemaMigration targetSchemaMigration;

    @Override
    public String version()
    {
        return VERSION;
    }

    @Override
    public String description()
    {
        return "legacy schema upgrade units";
    }

    @Override
    public String checksum()
    {
        return CHECKSUM;
    }

    @Override
    public void apply(Connection connection, Statement statement) throws SQLException
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
            log.info("etl_source/etl_table not found, skip legacy schema migration");
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
     * itself instead of by a list of columns that someone has to remember to update.</p>
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
