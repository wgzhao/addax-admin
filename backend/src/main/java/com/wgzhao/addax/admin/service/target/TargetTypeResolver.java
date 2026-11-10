package com.wgzhao.addax.admin.service.target;

import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Locale;

import static com.wgzhao.addax.admin.common.Constants.HDFS_TARGET_TYPES;
import static com.wgzhao.addax.admin.common.Constants.RDBMS_TARGET_TYPES;

/**
 * 目标端路由解析器。
 * 优先基于 etl_target.writer_template_key 决定适配器类型，target_type 仅作为展示信息与兜底参考。
 *
 * <p>HDFS is no longer the fallback. A table without a target, or a target row that no longer
 * exists, is a configuration error: silently routing it to the default (or worse, to HDFS) would
 * write data to a place nobody selected.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TargetTypeResolver
{
    private final JdbcTemplate jdbcTemplate;
    private final EtlTargetRepo etlTargetRepo;
    private volatile Boolean routingSchemaReady;

    public String resolveTargetType(Long tableId)
    {
        return resolveAdapterType(tableId);
    }

    /**
     * 解析采集表的目标端适配器类型。
     */
    public String resolveAdapterType(Long tableId)
    {
        if (tableId == null)
        {
            return defaultTargetType();
        }
        if (!isRoutingSchemaReady())
        {
            throw new ApiException(500, "目标端路由表未就绪: 缺少 etl_target 或 etl_table.target_id");
        }
        RouteInfo route = jdbcTemplate.query("""
            select t.target_id, tt.target_type, tt.writer_template_key
            from etl_table t
            left join etl_target tt on t.target_id = tt.id
            where t.id = ?
            """, rs -> {
            if (!rs.next())
            {
                return null;
            }
            return new RouteInfo(readTargetId(rs),
                rs.getString("target_type"), rs.getString("writer_template_key"));
        }, tableId);
        if (route == null)
        {
            throw new ApiException(404, "采集表不存在: " + tableId);
        }
        if (route.targetId() == null)
        {
            return defaultTargetType();
        }
        if (route.targetType() == null && route.writerTemplateKey() == null)
        {
            throw new ApiException(400, "采集表 " + tableId + " 关联的目标端不存在: " + route.targetId());
        }
        return resolveRoute(route);
    }

    /**
     * Resolve before a collection definition exists, when only etl_target.id is available.
     */
    public String resolveAdapterTypeByTargetId(Long targetId)
    {
        if (targetId == null)
        {
            return defaultTargetType();
        }
        if (!isRoutingSchemaReady())
        {
            throw new ApiException(500, "目标端路由表未就绪: 缺少 etl_target 或 etl_table.target_id");
        }
        RouteInfo route = jdbcTemplate.query("""
            select id as target_id, target_type, writer_template_key
              from etl_target
             where id = ?
            """, rs -> rs.next()
            ? new RouteInfo(readTargetId(rs),
                rs.getString("target_type"), rs.getString("writer_template_key"))
            : null, targetId);
        if (route == null)
        {
            throw new ApiException(400, "目标端不存在: " + targetId);
        }
        return resolveRoute(route);
    }

    /**
     * Pure routing rule shared by table routing and target save/test-connection.
     */
    public String resolveAdapterType(EtlTarget target)
    {
        if (target == null)
        {
            return defaultTargetType();
        }
        String writerKey = normalize(target.getWriterTemplateKey());
        if ("WH".equals(writerKey))
        {
            return "HDFS";
        }
        if ("WR".equals(writerKey))
        {
            return "RDBMS";
        }
        String targetType = normalize(target.getTargetType());
        if (HDFS_TARGET_TYPES.contains(targetType))
        {
            return "HDFS";
        }
        if (RDBMS_TARGET_TYPES.contains(targetType))
        {
            return "RDBMS";
        }
        if (!targetType.isBlank())
        {
            return targetType;
        }
        throw new ApiException(400, "目标端未配置类型: " + target.getName());
    }

    /**
     * etl_table.target_id / etl_target.id are int4. The PostgreSQL driver rejects
     * {@code getObject(column, Long.class)} for int4, so read the primitive and map null explicitly.
     */
    private Long readTargetId(ResultSet rs) throws SQLException
    {
        long value = rs.getLong("target_id");
        return rs.wasNull() ? null : value;
    }

    private String defaultTargetType()
    {
        EtlTarget target = defaultTarget();
        if (target == null)
        {
            throw new ApiException(400, "未配置目标端，请先创建并启用一个目标端");
        }
        return resolveAdapterType(target);
    }

    private EtlTarget defaultTarget()
    {
        return etlTargetRepo.findFirstByIsDefaultTrueOrderByIdAsc()
            .orElseGet(() -> etlTargetRepo.findAllByOrderByIsDefaultDescIdAsc().stream()
                .filter(t -> Boolean.TRUE.equals(t.getEnabled()))
                .findFirst()
                .orElse(null));
    }

    private String resolveRoute(RouteInfo route)
    {
        String writerKey = normalize(route.writerTemplateKey());
        if ("WH".equals(writerKey))
        {
            return "HDFS";
        }
        if ("WR".equals(writerKey))
        {
            return "RDBMS";
        }
        String targetType = normalize(route.targetType());
        if (HDFS_TARGET_TYPES.contains(targetType))
        {
            return "HDFS";
        }
        if (RDBMS_TARGET_TYPES.contains(targetType))
        {
            return "RDBMS";
        }
        if (targetType.isBlank())
        {
            throw new ApiException(400, "目标端 " + route.targetId() + " 未配置类型");
        }
        return targetType;
    }

    private boolean isRoutingSchemaReady()
    {
        if (routingSchemaReady != null)
        {
            return routingSchemaReady;
        }
        synchronized (this) {
            if (routingSchemaReady != null) {
                return routingSchemaReady;
            }
            routingSchemaReady = checkRoutingSchema();
            if (!routingSchemaReady) {
                log.info("Target routing schema not ready, resolving through the configured target");
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

    private record RouteInfo(Long targetId, String targetType, String writerTemplateKey)
    {
    }
}
