package com.wgzhao.addax.admin.service.target;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.wgzhao.addax.admin.common.Constants.DEFAULT_TARGET_TYPE;

/**
 * 目标端类型解析器。
 * 通过 etl_table.target_id -> etl_target.target_type 解析目标端类型。
 * 若数据库尚未完成迁移，则回退默认 HIVE。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TargetTypeResolver
{
    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean routingSchemaReady;

    public String resolveTargetType(Long tableId)
    {
        if (tableId == null) {
            return DEFAULT_TARGET_TYPE;
        }
        if (!isRoutingSchemaReady()) {
            return DEFAULT_TARGET_TYPE;
        }
        try {
            String sql = """
                select coalesce(tt.target_type, ?) as target_type
                from etl_table t
                left join etl_target tt on t.target_id = tt.id
                where t.id = ?
                """;
            String targetType = jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    return rs.getString("target_type");
                }
                return null;
            }, DEFAULT_TARGET_TYPE, tableId);
            if (targetType == null || targetType.isBlank()) {
                return DEFAULT_TARGET_TYPE;
            }
            return targetType.trim().toUpperCase(Locale.ROOT);
        }
        catch (Exception e) {
            log.warn("Failed to resolve target type for table {}, fallback to HIVE: {}", tableId, e.getMessage());
            return DEFAULT_TARGET_TYPE;
        }
    }

    private boolean isRoutingSchemaReady()
    {
        if (routingSchemaReady != null) {
            return routingSchemaReady;
        }
        synchronized (this) {
            if (routingSchemaReady != null) {
                return routingSchemaReady;
            }
            routingSchemaReady = checkRoutingSchema();
            if (!routingSchemaReady) {
                log.info("Target routing schema not ready, fallback to default HIVE routing");
            }
            return routingSchemaReady;
        }
    }

    private boolean checkRoutingSchema()
    {
        try {
            Integer hasTargetTable = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public' and table_name = 'etl_target'
                """, Integer.class);
            Integer hasTargetIdColumn = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public' and table_name = 'etl_table' and column_name = 'target_id'
                """, Integer.class);
            return hasTargetTable != null && hasTargetTable > 0
                && hasTargetIdColumn != null && hasTargetIdColumn > 0;
        }
        catch (Exception e) {
            log.warn("Failed to check target routing schema: {}", e.getMessage());
            return false;
        }
    }
}
