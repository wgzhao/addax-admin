package com.wgzhao.addax.admin.dto;

import com.wgzhao.addax.admin.model.EtlTarget;

/**
 * Read projection of a target endpoint. The caller decides whether {@code connectConfig} contains
 * the full configuration or only the non-secret writer defaults; the entity is never serialized
 * directly because it contains the HiveServer2 credentials.
 */
public record EtlTargetView(
    Long id,
    String code,
    String name,
    String targetType,
    String connectConfig,
    String writerTemplateKey,
    Boolean enabled,
    Boolean isDefault,
    String remark)
{
    public static EtlTargetView from(EtlTarget target, String connectConfig)
    {
        return new EtlTargetView(
            target.getId(),
            target.getCode(),
            target.getName(),
            target.getTargetType(),
            connectConfig,
            target.getWriterTemplateKey(),
            target.getEnabled(),
            target.getIsDefault(),
            target.getRemark());
    }
}
