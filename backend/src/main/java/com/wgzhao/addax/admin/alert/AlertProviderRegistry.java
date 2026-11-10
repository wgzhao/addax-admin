package com.wgzhao.addax.admin.alert;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AlertProviderRegistry
{
    private final Map<AlertChannelType, AlertChannelProvider> providers = new EnumMap<>(AlertChannelType.class);

    public AlertProviderRegistry(List<AlertChannelProvider> providerList)
    {
        for (AlertChannelProvider provider : providerList) {
            providers.put(provider.type(), provider);
        }
    }

    public AlertChannelProvider get(AlertChannelType type)
    {
        AlertChannelProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的告警通道类型: " + type);
        }
        return provider;
    }

    public AlertChannelProvider get(String type)
    {
        return get(AlertChannelType.valueOf(type.trim().toUpperCase()));
    }
}
