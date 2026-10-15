package com.wgzhao.addax.admin.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds the per-source collection calendar ({@code etl_source.calendar}) and exposes it through
 * {@code vw_etl_table_with_source}, which the missing-collection monitor and the target adapters read.
 *
 * <p>Follows the shape of {@link TargetOptionsSchemaInitializer}: one transaction, an advisory lock so
 * concurrent instances cannot interleave, guarded steps so a database installed from
 * {@code scripts/schema.sql} is a no-op, and a failure that aborts startup rather than serving traffic
 * against a half-migrated schema. It runs after the target-options migration because both recreate the
 * same view and both read the shared definition from {@link EtlTableView}.</p>
 */
@Component
@DependsOn("targetOptionsSchemaInitializer")
@RequiredArgsConstructor
@Slf4j
public class CalendarSchemaInitializer
{
    private static final long MIGRATION_LOCK_KEY = 662012002L;

    /** The out-of-the-box calendar every source uses when it has no explicit one. */
    public static final int WORK_DAY_DICT_CODE = 1022;

    private static final String ADD_COLUMN_SQL =
        "alter table public.etl_source add column if not exists calendar varchar(32)";

    private static final String SEED_WORK_DAY_DICT_SQL = """
        insert into public.sys_dict (code, name, classification, remark)
        values (1022, '工作日历', 'FR', '工作日(含调休)日期表，item_key 为 yyyyMMdd 的业务日期')
        on conflict (code) do nothing
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
        if (!exists(connection, "tables", "etl_source", null))
        {
            log.info("etl_source not found, skip calendar migration");
            return;
        }
        statement.execute(ADD_COLUMN_SQL);
        statement.executeUpdate(SEED_WORK_DAY_DICT_SQL);
        if (!exists(connection, "columns", EtlTableView.NAME, "calendar"))
        {
            statement.execute(EtlTableView.DROP_SQL);
            statement.execute(EtlTableView.CREATE_SQL);
            log.info("Recreated {} to expose the source calendar", EtlTableView.NAME);
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
