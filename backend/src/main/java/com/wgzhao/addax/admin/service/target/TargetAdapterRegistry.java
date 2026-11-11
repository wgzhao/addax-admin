package com.wgzhao.addax.admin.service.target;

import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 目标端适配器注册中心。
 * 按已注册的类型精确匹配；未知类型显式报错，避免静默降级到 RDBMS 产生错误的任务。
 */
@Component
@Slf4j
public class TargetAdapterRegistry
{
    private final Map<String, TargetAdapter> adaptersByType;
    private final TargetTypeResolver targetTypeResolver;

    public TargetAdapterRegistry(List<TargetAdapter> adapters, TargetTypeResolver targetTypeResolver)
    {
        this.targetTypeResolver = targetTypeResolver;
        this.adaptersByType = adapters.stream()
            .collect(Collectors.toMap(
                a -> normalize(a.getType()),
                Function.identity(),
                (a, b) -> a));
        log.info("Registered target adapters: {}", adaptersByType.keySet());
    }

    public TargetAdapter resolve(VwEtlTableWithSource table)
    {
        String targetType = table == null
            ? targetTypeResolver.resolveAdapterType((Long) null)
            : table.getId() == null
                ? targetTypeResolver.resolveAdapterTypeByTargetId(table.getTargetId())
                : targetTypeResolver.resolveAdapterType(table.getId());
        return getByType(targetType);
    }

    /**
     * Resolve for target CRUD/test-connection, where only the target row exists.
     */
    public TargetAdapter resolveForTarget(EtlTarget target)
    {
        return getByType(targetTypeResolver.resolveAdapterType(target));
    }

    public TargetAdapter getByType(String type)
    {
        TargetAdapter adapter = adaptersByType.get(normalize(type));
        if (adapter == null)
        {
            throw new IllegalStateException("No target adapter found for type: " + type
                + ". Registered types: " + adaptersByType.keySet()
                + "; RDBMS targets must use a dialect that is implemented end to end.");
        }
        return adapter;
    }

    private String normalize(String type)
    {
        if (type == null)
        {
            return "";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

}
