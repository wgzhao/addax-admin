package com.wgzhao.addax.admin.config.schema;

import com.wgzhao.addax.admin.config.EtlTableView;
import lombok.extern.slf4j.Slf4j;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Purges deprecated dictionary rows that later releases no longer read. */
@Slf4j
public final class DictionarySchema
{
        public static final String PURGE_DEPRECATED_SYS_ITEMS_SQL = """
        delete from public.sys_item
         where (dict_code = 1000 and item_key in ('HIVE_SERVER2', 'HDFS_PREFIX',
                                                  'HDFS_STORAGE_FORMAT', 'HDFS_COMPRESS_FORMAT',
                                                  'HDFS_CONFIG', 'HIVE_CLI', 'AUTH_SOURCE_GRANT_MIGRATED',
                                                  'RUN_LOG'))
            or dict_code in (1011, 1060, 2010, 2015)
        """;

        public static final String PURGE_DEPRECATED_SYS_DICTS_SQL = """
        delete from public.sys_dict where code in (1011, 1060, 2010, 2015)
        """;

    public static void purgeDeprecated(Statement statement) throws SQLException
    {
        int purgedItems = statement.executeUpdate(PURGE_DEPRECATED_SYS_ITEMS_SQL);
        int purgedDicts = statement.executeUpdate(PURGE_DEPRECATED_SYS_DICTS_SQL);
        if (purgedItems > 0 || purgedDicts > 0) {
            log.info("Purged {} deprecated sys_item and {} sys_dict row(s) "
                    + "(legacy Hive/HDFS globals, 1011, 1060, 2010, 2015)",
                purgedItems, purgedDicts);
        }
    }

    private DictionarySchema()
    {
    }
}
