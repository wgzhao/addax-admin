package com.wgzhao.addax.admin.config.schema;

import com.wgzhao.addax.admin.config.EtlTableView;
import lombok.extern.slf4j.Slf4j;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Removes notification rows that a retired feature produced. */
@Slf4j
public final class NotificationSchema
{
        public static final String PURGE_LEGACY_COLLECT_NOTIFICATIONS_SQL = """
        delete from public.user_notification
         where type = 'COLLECT' and title = '采集任务完成'
        """;

    public static void purgeLegacy(Statement statement) throws SQLException
    {
        int purged = statement.executeUpdate(PURGE_LEGACY_COLLECT_NOTIFICATIONS_SQL);
        if (purged > 0) {
            log.info("Purged {} legacy collection notification row(s) with the pre-4.4.1 title", purged);
        }
    }

    private NotificationSchema()
    {
    }
}
