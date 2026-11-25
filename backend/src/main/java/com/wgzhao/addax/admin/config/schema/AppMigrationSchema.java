package com.wgzhao.addax.admin.config.schema;

import com.wgzhao.addax.admin.config.EtlTableView;
import lombok.extern.slf4j.Slf4j;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the marker table one-time backfills use to know they already ran. */
@Slf4j
public final class AppMigrationSchema
{
        public static final String CREATE_APP_MIGRATION_SQL = """
        create table if not exists public.app_migration (
            name varchar(100) primary key,
            applied_at timestamp without time zone not null default current_timestamp
        )
        """;

    public static void ensure(Statement statement) throws SQLException
    {
        statement.execute(CREATE_APP_MIGRATION_SQL);
    }

    private AppMigrationSchema()
    {
    }
}
