package com.wgzhao.addax.admin.config.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A migration whose DDL lives in {@code db/migration/V####__description.sql}.
 *
 * <p>The script is executed as one simple query, the way PostgreSQL accepts a pg_dump. psql
 * meta-commands are stripped: the migration transaction provides the same safety they guard.</p>
 */
final class SqlMigrationStep
    implements SchemaMigrationStep
{
    private final String version;
    private final String description;
    private final String checksum;
    private final String sql;

    SqlMigrationStep(String version, String description, String checksum, String sql)
    {
        this.version = version;
        this.description = description;
        this.checksum = checksum;
        this.sql = sql;
    }

    @Override
    public String version()
    {
        return version;
    }

    @Override
    public String description()
    {
        return description;
    }

    @Override
    public String checksum()
    {
        return checksum;
    }

    @Override
    public void apply(Connection connection, Statement statement) throws SQLException
    {
        statement.execute(sql.replaceAll("(?m)^[ \\t]*\\\\.*$", ""));
    }
}
