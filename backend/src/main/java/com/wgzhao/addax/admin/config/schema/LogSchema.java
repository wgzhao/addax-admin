package com.wgzhao.addax.admin.config.schema;

import com.wgzhao.addax.admin.config.EtlTableView;
import lombok.extern.slf4j.Slf4j;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Widens log identifiers to match the collection table columns. */
@Slf4j
public final class LogSchema
{
        public static final String WIDEN_LOG_IDENTIFIERS_SQL = """
        do $$
        begin
            if to_regclass('public.etl_table_change_log') is not null then
                alter table public.etl_table_change_log alter column source_db type varchar(64);
                alter table public.etl_table_change_log alter column source_table type varchar(255);
            end if;
            if to_regclass('public.schema_change_log') is not null then
                alter table public.schema_change_log alter column source_table type varchar(255);
            end if;
        end
        $$
        """;

    public static void widenIdentifiers(Statement statement) throws SQLException
    {
        statement.execute(WIDEN_LOG_IDENTIFIERS_SQL);
    }

    private LogSchema()
    {
    }
}
