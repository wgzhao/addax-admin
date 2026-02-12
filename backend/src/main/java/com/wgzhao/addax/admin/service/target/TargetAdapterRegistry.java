package com.wgzhao.addax.admin.service.target;

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
 * 当前默认返回 HIVE 适配器，后续可根据表的目标端配置做动态路由。
 */
@Component
@Slf4j
public class TargetAdapterRegistry
{
    public static final String DEFAULT_TARGET_TYPE = "HIVE";

    private final Map<String, TargetAdapter> adaptersByType;

    public TargetAdapterRegistry(List<TargetAdapter> adapters)
    {
        this.adaptersByType = adapters.stream()
            .collect(Collectors.toMap(
                a -> normalize(a.getType()),
                Function.identity(),
                (a, b) -> a));
        log.info("Registered target adapters: {}", adaptersByType.keySet());
    }

    public TargetAdapter resolve(VwEtlTableWithSource table)
    {
        // 第一阶段兼容实现：统一走默认 HIVE 适配器
        return getByType(DEFAULT_TARGET_TYPE);
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
}
