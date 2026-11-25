package com.wgzhao.addax.admin.config.schema;

import com.wgzhao.addax.admin.config.EtlTableView;
import lombok.extern.slf4j.Slf4j;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Upgrades the collection source table (calendar, structured connect config, db type). */
@Slf4j
public final class SourceSchema
{
        public static final String ADD_CALENDAR_COLUMN_SQL =
        "alter table public.etl_source add column if not exists calendar varchar(32)";

        public static final String ADD_SOURCE_CONNECT_CONFIG_SQL =
        "alter table public.etl_source add column if not exists connect_config jsonb";

        public static final String WIDEN_SOURCE_URL_SQL =
        "alter table public.etl_source alter column url type varchar(1000)";

        public static final String DROP_SOURCE_CODE_SQL =
        "alter table public.etl_source drop column if exists code";

        public static final String BACKFILL_DB_TYPE_SQL = """
        update public.etl_source
           set db_type = case
                 when url like 'jdbc:mysql:%' then 'mysql'
                 when url like 'jdbc:postgresql:%' then 'postgresql'
                 when url like 'jdbc:oracle:%' then 'oracle'
                 when url like 'jdbc:sqlserver:%' then 'sqlserver'
                 when url like 'jdbc:hive2:%' or url like 'jdbc:hive:%' then 'hive'
                 when url like 'jdbc:db2:%' then 'db2'
                 when url like 'jdbc:sqlite:%' then 'sqlite'
                 when url like 'jdbc:sybase:%' then 'sybase'
                 when url like 'jdbc:clickhouse:%' or url like 'jdbc/chk%' then 'clickhouse'
                 when url like 'mongodb://%' or url like 'mongodb+srv://%' then 'mongodb'
                 when url like 'http://%' or url like 'https://%' then 'elasticsearch'
                 else 'rdbms'
               end
         where db_type is null or btrim(db_type) = ''
        """;

        public static final String SEED_WORK_DAY_DICT_SQL = """
        insert into public.sys_dict (code, name, classification, remark)
        values (1022, '工作日历', 'FR', '工作日(含调休)日期表，item_key 为 yyyyMMdd 的业务日期')
        on conflict (code) do nothing
        """;

        public static final int WORK_DAY_DICT_CODE = 1022;

    public static void upgrade(Connection connection, Statement statement) throws SQLException
    {
        statement.execute(ADD_CALENDAR_COLUMN_SQL);
        statement.execute(ADD_SOURCE_CONNECT_CONFIG_SQL);
        if (SchemaSupport.exists(connection, "columns", "etl_source", "code")) {
            statement.execute(EtlTableView.DROP_SQL);
            statement.execute(DROP_SOURCE_CODE_SQL);
            log.info("Dropped the legacy etl_source.code column");
        }
        // PostgreSQL refuses to alter a column type while a view selects it. Drop the view only when
        // the widening actually runs: an unconditional drop forced a view recreation (and a migration
        // log line) on every single startup of a converged database.
        if (SchemaSupport.needsWidening(connection, "etl_source", "url", 1000)) {
            statement.execute(EtlTableView.DROP_SQL);
            statement.execute(WIDEN_SOURCE_URL_SQL);
        }
        int backfilledTypes = statement.executeUpdate(BACKFILL_DB_TYPE_SQL);
        if (backfilledTypes > 0) {
            log.info("Backfilled db_type for {} etl_source row(s)", backfilledTypes);
        }
        if (statement.executeUpdate(SEED_WORK_DAY_DICT_SQL) > 0) {
            log.info("Seeded the work-day calendar dictionary (code {})", WORK_DAY_DICT_CODE);
        }
    }

    private SourceSchema()
    {
    }
}
