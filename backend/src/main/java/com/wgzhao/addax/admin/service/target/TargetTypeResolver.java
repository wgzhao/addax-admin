package com.wgzhao.addax.admin.service.target;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.wgzhao.addax.admin.common.Constants.DEFAULT_TARGET_TYPE;
import static com.wgzhao.addax.admin.common.Constants.HDFS_TARGET_TYPES;
import static com.wgzhao.addax.admin.common.Constants.RDBMS_TARGET_TYPES;

/**
 * 目标端路由解析器。
 * 优先基于 etl_target.writer_template_key 决定适配器类型，target_type 仅作为展示信息与兜底参考。
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
        return resolveAdapterType(tableId);
    }

    /**
     * 解析目标端适配器类型。
     * 路由优先级：
     * 1) writer_template_key = wH -> HDFS，= wR -> RDBMS
     * 2) target_type 命中已知 HDFS / RDBMS 方言
     * 3) target_type 其他非空值原样返回（由注册中心显式拒绝，避免静默降级）
     * 4) DEFAULT_TARGET_TYPE
     */
    public String resolveAdapterType(Long tableId)
    {
        if (tableId == null) {
            return DEFAULT_TARGET_TYPE;
        }
        if (!isRoutingSchemaReady()) {
            return DEFAULT_TARGET_TYPE;
        }
        try {
            String sql = """
                select tt.target_type, tt.writer_template_key
                from etl_table t
                left join etl_target tt on t.target_id = tt.id
                where t.id = ?
                """;
            RouteInfo route = jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    return new RouteInfo(rs.getString("target_type"), rs.getString("writer_template_key"));
                }
                return null;
            }, tableId);
            if (route == null) {
                return DEFAULT_TARGET_TYPE;
            }
            String writerKey = normalize(route.writerTemplateKey);
            if ("WH".equals(writerKey)) {
                return "HDFS";
            }
            if ("WR".equals(writerKey)) {
                return "RDBMS";
            }
            String targetType = normalize(route.targetType);
            if (HDFS_TARGET_TYPES.contains(targetType)) {
                return "HDFS";
            }
            if (RDBMS_TARGET_TYPES.contains(targetType)) {
                return "RDBMS";
            }
            if (!targetType.isBlank()) {
                return targetType;
            }
            return DEFAULT_TARGET_TYPE;
        }
        catch (Exception e) {
            log.warn("Failed to resolve target type for table {}, fallback to {}: {}", tableId, DEFAULT_TARGET_TYPE, e.getMessage());
            return DEFAULT_TARGET_TYPE;
        }
    }

    /**
     * Resolve before a collection definition exists, when only etl_target.id is available.
     */
    public String resolveAdapterTypeByTargetId(Long targetId)
    {
        if (targetId == null || !isRoutingSchemaReady())
        {
            return DEFAULT_TARGET_TYPE;
        }
        try
        {
            RouteInfo route = jdbcTemplate.query("""
                select target_type, writer_template_key
                  from etl_target
                 where id = ?
                """, rs -> rs.next()
                ? new RouteInfo(rs.getString("target_type"), rs.getString("writer_template_key"))
                : null, targetId);
            return resolveRoute(route);
        }
        catch (Exception e)
        {
            log.warn("Failed to resolve target type for target {}, fallback to {}: {}",
                targetId, DEFAULT_TARGET_TYPE, e.getMessage());
            return DEFAULT_TARGET_TYPE;
        }
    }

    private String resolveRoute(RouteInfo route)
    {
        if (route == null)
        {
            return DEFAULT_TARGET_TYPE;
        }
        String writerKey = normalize(route.writerTemplateKey);
        if ("WH".equals(writerKey))
        {
            return "HDFS";
        }
        if ("WR".equals(writerKey))
        {
            return "RDBMS";
        }
        String targetType = normalize(route.targetType);
        if (HDFS_TARGET_TYPES.contains(targetType))
        {
            return "HDFS";
        }
        if (RDBMS_TARGET_TYPES.contains(targetType))
        {
            return "RDBMS";
        }
        return targetType.isBlank() ? DEFAULT_TARGET_TYPE : targetType;
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
                log.info("Target routing schema not ready, fallback to default {} routing", DEFAULT_TARGET_TYPE);
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

    private String normalize(String s)
    {
        if (s == null) {
            return "";
        }
        return s.trim().toUpperCase(Locale.ROOT);
    }

    private record RouteInfo(String targetType, String writerTemplateKey)
    {
    }
}
