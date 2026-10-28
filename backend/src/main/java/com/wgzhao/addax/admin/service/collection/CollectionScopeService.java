package com.wgzhao.addax.admin.service.collection;

import com.wgzhao.addax.admin.dto.CollectableTableDto;
import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines what "already collected" means and keeps the add-table/create/restore checks on the same
 * route identity.
 *
 * <p>A collection route is {@code (sid, source db, source table, target id, target db)}. Source and
 * target names are compared with the dialect policy, not with a case-blind guess. A route has one
 * {@code etl_table} row for its whole lifecycle: retirement is a state, and re-collecting restores
 * that row rather than inserting a second one.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionScopeService
{
    private static final long POLICY_TTL_MS = 60_000;

    private final EtlTableRepo tableRepo;
    private final EtlSourceRepo sourceRepo;
    private final EtlTargetRepo targetRepo;
    private final Map<String, CachedPolicy> policyCache = new ConcurrentHashMap<>();

    private record CachedPolicy(IdentifierPolicy policy, long expiresAt)
    {
    }

    public record Definition(
        Long id,
        Integer sid,
        String sourceDb,
        String sourceTable,
        String targetDb,
        String targetTable,
        Long targetId,
        LocalDateTime retiredAt,
        String retiredBy)
    {
        public boolean active()
        {
            return retiredAt == null;
        }
    }

    /**
     * Source tables the selected route may still take. Active definitions are omitted; retired ones are
     * returned with their definition id so the UI can restore instead of creating a duplicate.
     */
    public List<CollectableTableDto> collectable(EtlSource source, String dbName, Long targetId, String targetDb)
    {
        EtlTarget target = resolveTarget(targetId, true);
        IdentifierPolicy targetPolicy = IdentifierPolicy.forTarget(target);
        String targetDbKey = isBlank(targetDb) ? null : targetPolicy.key(targetDb.trim());
        try (Connection connection = DriverManager.getConnection(source.getUrl(), source.getUsername(),
            source.getPass() == null ? "" : source.getPass()))
        {
            IdentifierPolicy sourcePolicy = IdentifierPolicy.forSource(connection, dbName);
            Map<String, Definition> definitions = definitions(
                source.getId(), target.getId(), targetDbKey, sourcePolicy, targetPolicy);
            List<TableMetaDto> all = DbUtil.getAllTableMetas(connection, dbName);
            List<CollectableTableDto> result = new ArrayList<>(all.size());
            for (TableMetaDto meta : all)
            {
                Definition definition = definitions.get(sourceKey(sourcePolicy, dbName, meta.name()));
                if (definition == null)
                {
                    result.add(new CollectableTableDto(meta.name(), meta.comment(), meta.approxRowCount(),
                        "AVAILABLE", null, null, null));
                }
                else if (!definition.active())
                {
                    result.add(new CollectableTableDto(meta.name(), meta.comment(), meta.approxRowCount(),
                        "RETIRED", definition.id(), definition.retiredAt(), definition.retiredBy()));
                }
                // An active definition is already collected for this route and must not be offered as new.
            }
            return result;
        }
        catch (SQLException e)
        {
            log.warn("Failed to list collectable tables for source {}: {}", source.getId(), e.getMessage());
            throw new ApiException(500, "获取可采集表失败: " + e.getMessage());
        }
    }

    /**
     * Reject a create that would duplicate an existing route. The retired case is deliberately a
     * different error: the caller must restore the existing definition, not insert another row.
     */
    public void assertCreatable(EtlTable table)
    {
        validateCreatePayload(table);
        table.setTargetDb(table.getTargetDb().trim());
        table.setTargetTable(table.getTargetTable().trim());
        EtlTarget target = resolveTarget(table.getTargetId(), true);
        table.setTargetId(target.getId());
        EtlSource source = sourceRepo.findById(table.getSid())
            .orElseThrow(() -> new ApiException(400, "采集源不存在: " + table.getSid()));

        IdentifierPolicy sourcePolicy = sourcePolicy(source, table.getSourceDb());
        IdentifierPolicy targetPolicy = IdentifierPolicy.forTarget(target);
        Long defaultTargetId = defaultTargetId();
        String sourceKey = sourceKey(sourcePolicy, table.getSourceDb(), table.getSourceTable());
        String targetDbKey = targetPolicy.key(table.getTargetDb().trim());
        String targetTableKey = targetPolicy.key(table.getTargetTable().trim());

        for (EtlTable row : tableRepo.findBySid(table.getSid()))
        {
            if (!belongsToTarget(row.getTargetId(), target.getId(), defaultTargetId))
            {
                continue;
            }
            if (!targetDbKey.equals(targetPolicy.key(row.getTargetDb())))
            {
                continue;
            }
            if (sourceKey.equals(sourceKey(sourcePolicy, row.getSourceDb(), row.getSourceTable())))
            {
                throw duplicateSourceException(target, row);
            }
            // Two different source tables may not own the same physical target table, even across
            // batches. Retired rows do not reserve the target table; the preview still reports it.
            if (row.getRetiredAt() == null
                && targetTableKey.equals(targetPolicy.key(row.getTargetTable())))
            {
                throw new ApiException(409, "目标表已被其他采集定义占用: "
                    + row.getTargetDb() + "." + row.getTargetTable()
                    + "（采集表 ID " + row.getId() + "）");
            }
        }
    }

    /**
     * A retired definition may only be restored when no other active row occupies its route or target
     * table. Other retired rows are historical duplicates and are left for the audit.
     */
    public void assertRestorable(EtlTable table)
    {
        EtlTarget target = resolveTarget(table.getTargetId(), false);
        table.setTargetId(target.getId());
        EtlSource source = sourceRepo.findById(table.getSid())
            .orElseThrow(() -> new ApiException(400, "采集源不存在: " + table.getSid()));

        IdentifierPolicy sourcePolicy = sourcePolicy(source, table.getSourceDb());
        IdentifierPolicy targetPolicy = IdentifierPolicy.forTarget(target);
        Long defaultTargetId = defaultTargetId();
        String sourceKey = sourceKey(sourcePolicy, table.getSourceDb(), table.getSourceTable());
        String targetDbKey = targetPolicy.key(table.getTargetDb());
        String targetTableKey = targetPolicy.key(table.getTargetTable());

        for (EtlTable row : tableRepo.findBySid(table.getSid()))
        {
            if (Objects.equals(row.getId(), table.getId())
                || !belongsToTarget(row.getTargetId(), target.getId(), defaultTargetId)
                || row.getRetiredAt() != null
                || !targetDbKey.equals(targetPolicy.key(row.getTargetDb())))
            {
                continue;
            }
            if (sourceKey.equals(sourceKey(sourcePolicy, row.getSourceDb(), row.getSourceTable()))
                || targetTableKey.equals(targetPolicy.key(row.getTargetTable())))
            {
                throw new ApiException(409, "该采集路由已有活动定义（采集表 ID " + row.getId()
                    + "），不能恢复退役表");
            }
        }
    }

    /**
     * An edit may move a definition onto a route that is already occupied. Retired rows count as
     * occupancy too, because the route is supposed to have exactly one row across its lifecycle.
     */
    public void assertRouteUnique(EtlTable table, Long excludeId)
    {
        requireText(table.getSourceDb(), "sourceDb");
        requireText(table.getSourceTable(), "sourceTable");
        requireText(table.getTargetDb(), "targetDb");
        requireText(table.getTargetTable(), "targetTable");
        table.setTargetDb(table.getTargetDb().trim());
        table.setTargetTable(table.getTargetTable().trim());
        EtlTarget target = resolveTarget(table.getTargetId(), false);
        if (table.getTargetId() == null)
        {
            table.setTargetId(target.getId());
        }
        EtlSource source = sourceRepo.findById(table.getSid())
            .orElseThrow(() -> new ApiException(400, "采集源不存在: " + table.getSid()));

        IdentifierPolicy sourcePolicy = sourcePolicy(source, table.getSourceDb());
        IdentifierPolicy targetPolicy = IdentifierPolicy.forTarget(target);
        Long defaultTargetId = defaultTargetId();
        String sourceKey = sourceKey(sourcePolicy, table.getSourceDb(), table.getSourceTable());
        String targetDbKey = targetPolicy.key(table.getTargetDb());
        String targetTableKey = targetPolicy.key(table.getTargetTable());

        for (EtlTable row : tableRepo.findBySid(table.getSid()))
        {
            if (Objects.equals(row.getId(), excludeId)
                || !belongsToTarget(row.getTargetId(), target.getId(), defaultTargetId)
                || !targetDbKey.equals(targetPolicy.key(row.getTargetDb())))
            {
                continue;
            }
            if (sourceKey.equals(sourceKey(sourcePolicy, row.getSourceDb(), row.getSourceTable())))
            {
                throw duplicateSourceException(target, row);
            }
            if (row.getRetiredAt() == null
                && targetTableKey.equals(targetPolicy.key(row.getTargetTable())))
            {
                throw new ApiException(409, "目标表已被其他采集定义占用: "
                    + row.getTargetDb() + "." + row.getTargetTable()
                    + "（采集表 ID " + row.getId() + "）");
            }
        }
    }

    /**
     * Look up the route before create so the preview can surface a precise finding instead of letting
     * the commit fail later.
     */
    public Definition findDefinition(Integer sid, String sourceDb, String sourceTable,
        Long targetId, String targetDb)
    {
        if (sid == null || isBlank(sourceDb) || isBlank(sourceTable) || targetId == null
            || isBlank(targetDb))
        {
            return null;
        }
        EtlSource source = sourceRepo.findById(sid).orElse(null);
        EtlTarget target = targetRepo.findById(targetId).orElse(null);
        if (source == null || target == null)
        {
            return null;
        }
        IdentifierPolicy sourcePolicy = sourcePolicy(source, sourceDb);
        IdentifierPolicy targetPolicy = IdentifierPolicy.forTarget(target);
        String sourceKey = sourceKey(sourcePolicy, sourceDb, sourceTable);
        String targetDbKey = targetPolicy.key(targetDb.trim());
        Long defaultTargetId = defaultTargetId();
        Definition found = null;
        for (EtlTable row : tableRepo.findBySid(sid))
        {
            if (!belongsToTarget(row.getTargetId(), targetId, defaultTargetId)
                || !targetDbKey.equals(targetPolicy.key(row.getTargetDb()))
                || !sourceKey.equals(sourceKey(sourcePolicy, row.getSourceDb(), row.getSourceTable())))
            {
                continue;
            }
            Definition candidate = toDefinition(row);
            found = found == null ? candidate : prefer(found, candidate);
        }
        return found;
    }

    /**
     * Target identity used by the batch commit's in-batch duplicate check. Hive folds to lower case;
     * an RDBMS target is compared exactly.
     */
    public String targetKey(EtlTable table)
    {
        EtlTarget target = resolveTarget(table.getTargetId(), false);
        IdentifierPolicy policy = IdentifierPolicy.forTarget(target);
        return target.getId() + "\u0000" + policy.key(trim(table.getTargetDb()))
            + "\u0000" + policy.key(trim(table.getTargetTable()));
    }

    public EtlTarget resolveTarget(Long targetId, boolean requireEnabled)
    {
        EtlTarget target;
        if (targetId == null)
        {
            target = defaultTarget();
        }
        else
        {
            target = targetRepo.findById(targetId)
                .orElseThrow(() -> new ApiException(404, "目标端不存在: " + targetId));
        }
        if (target == null)
        {
            throw new ApiException(400, "未配置默认目标端，请先创建并启用一个目标端");
        }
        if (requireEnabled && !Boolean.TRUE.equals(target.getEnabled()))
        {
            throw new ApiException(400, "目标端已禁用: " + target.getId());
        }
        return target;
    }

    private EtlTarget defaultTarget()
    {
        return targetRepo.findFirstByIsDefaultTrueOrderByIdAsc()
            .orElseGet(() -> targetRepo.findByEnabledTrueOrderByIsDefaultDescIdAsc().stream()
                .findFirst().orElse(null));
    }

    private Long defaultTargetId()
    {
        EtlTarget target = defaultTarget();
        return target == null ? null : target.getId();
    }

    private IdentifierPolicy sourcePolicy(EtlSource source, String dbName)
    {
        // A batch create/preview validates many tables against one source. Probing the server for every
        // row would open a connection per table; the policy changes only with server configuration.
        String cacheKey = source.getId() + "\u0000" + source.getUrl() + "\u0000" + dbName;
        long now = System.currentTimeMillis();
        CachedPolicy cached = policyCache.get(cacheKey);
        if (cached != null && cached.expiresAt() > now)
        {
            return cached.policy();
        }
        IdentifierPolicy policy;
        try (Connection connection = DriverManager.getConnection(source.getUrl(), source.getUsername(),
            source.getPass() == null ? "" : source.getPass()))
        {
            policy = IdentifierPolicy.forSource(connection, dbName);
        }
        catch (SQLException e)
        {
            log.warn("Failed to resolve identifier policy for source {}: {}", source.getId(), e.getMessage());
            policy = IdentifierPolicy.EXACT;
        }
        policyCache.put(cacheKey, new CachedPolicy(policy, now + POLICY_TTL_MS));
        return policy;
    }

    private Map<String, Definition> definitions(int sid, long targetId, String targetDbKey,
        IdentifierPolicy sourcePolicy, IdentifierPolicy targetPolicy)
    {
        Long defaultTargetId = defaultTargetId();
        Map<String, Definition> result = new HashMap<>();
        for (EtlTable row : tableRepo.findBySid(sid))
        {
            if (!belongsToTarget(row.getTargetId(), targetId, defaultTargetId)
                || (targetDbKey != null && !targetDbKey.equals(targetPolicy.key(row.getTargetDb()))))
            {
                continue;
            }
            result.merge(sourceKey(sourcePolicy, row.getSourceDb(), row.getSourceTable()),
                toDefinition(row), CollectionScopeService::prefer);
        }
        return result;
    }

    private static Definition prefer(Definition left, Definition right)
    {
        if (left.active() != right.active())
        {
            return left.active() ? left : right;
        }
        if (left.active())
        {
            return Long.compare(left.id(), right.id()) <= 0 ? left : right;
        }
        if (left.retiredAt() == null)
        {
            return right;
        }
        if (right.retiredAt() == null)
        {
            return left;
        }
        int byRetiredAt = left.retiredAt().compareTo(right.retiredAt());
        if (byRetiredAt != 0)
        {
            return byRetiredAt > 0 ? left : right;
        }
        return Long.compare(left.id(), right.id()) <= 0 ? left : right;
    }

    private static Definition toDefinition(EtlTable row)
    {
        return new Definition(row.getId(), row.getSid(), row.getSourceDb(), row.getSourceTable(),
            row.getTargetDb(), row.getTargetTable(), row.getTargetId(), row.getRetiredAt(),
            row.getRetiredBy());
    }

    private static boolean belongsToTarget(Long rowTargetId, long targetId, Long defaultTargetId)
    {
        Long effective = rowTargetId == null ? defaultTargetId : rowTargetId;
        return effective != null && effective.longValue() == targetId;
    }

    private static String sourceKey(IdentifierPolicy policy, String db, String table)
    {
        return policy.key(db) + "\u0000" + policy.key(table);
    }

    private static void validateCreatePayload(EtlTable table)
    {
        if (table == null)
        {
            throw new ApiException(400, "采集表不能为空");
        }
        if (table.getId() != null && table.getId() > 0)
        {
            throw new ApiException(400, "已存在的采集表不能通过创建接口保存");
        }
        if (table.getId() != null && table.getId() == 0)
        {
            table.setId(null);
        }
        if (table.getSid() == null)
        {
            throw new ApiException(400, "sid 不能为空");
        }
        requireText(table.getSourceDb(), "sourceDb");
        requireText(table.getSourceTable(), "sourceTable");
        requireText(table.getTargetDb(), "targetDb");
        requireText(table.getTargetTable(), "targetTable");
    }

    private static void requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new ApiException(400, field + " 不能为空");
        }
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String trim(String value)
    {
        return value == null ? "" : value.trim();
    }

    private ApiException duplicateSourceException(EtlTarget target, EtlTable existing)
    {
        String route = target.getName() + " / " + existing.getTargetDb();
        if (existing.getRetiredAt() == null)
        {
            return new ApiException(409, "该源表已在目标端 " + route + " 采集（采集表 ID "
                + existing.getId() + "），不能重复创建");
        }
        return new ApiException(409, "该源表在目标端 " + route + " 存在已退役定义（采集表 ID "
            + existing.getId() + "），请先恢复；系统不会为同一采集路由新建第二条定义");
    }
}
