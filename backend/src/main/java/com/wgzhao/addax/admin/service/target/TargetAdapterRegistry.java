package com.wgzhao.addax.admin.service.target;

import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.wgzhao.addax.admin.common.Constants.DEFAULT_TARGET_TYPE;

/**
 * 目标端适配器注册中心。
 * 当前默认返回 HDFS 适配器，后续可根据表的目标端配置做动态路由。
 */
@Component
@Slf4j
public class TargetAdapterRegistry
{
    private static final String RDBMS_TARGET_TYPE = "RDBMS";
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
        String targetType = targetTypeResolver.resolveTargetType(table == null ? null : table.getId());
        return getByTypeOrDefault(targetType);
    }

    public TargetAdapter resolveDefault()
    {
        return getByType(DEFAULT_TARGET_TYPE);
    }

    public TargetAdapter getByType(String type)
    {
        TargetAdapter adapter = adaptersByType.get(normalize(type));
        if (adapter == null) {
            throw new IllegalStateException("No target adapter found for type: " + type);
        }
        return adapter;
    }

    private String normalize(String type)
    {
        if (type == null) {
            return "";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private TargetAdapter getByTypeOrDefault(String type)
    {
        TargetAdapter adapter = adaptersByType.get(normalize(type));
        if (adapter != null) {
            return adapter;
        }
        TargetAdapter rdbmsAdapter = adaptersByType.get(RDBMS_TARGET_TYPE);
        if (rdbmsAdapter != null) {
            log.info("No exact target adapter for type {}, using {}", type, RDBMS_TARGET_TYPE);
            return rdbmsAdapter;
        }
        log.warn("No target adapter found for type {}, fallback to {}", type, DEFAULT_TARGET_TYPE);
        return getByType(DEFAULT_TARGET_TYPE);
    }
}
