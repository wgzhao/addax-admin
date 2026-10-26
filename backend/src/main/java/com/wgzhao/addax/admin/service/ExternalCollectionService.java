package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.dto.CollectionSchemaPreview;
import com.wgzhao.addax.admin.dto.CollectionSchemaPreviewRequest;
import com.wgzhao.addax.admin.dto.ColumnMappingInput;
import com.wgzhao.addax.admin.dto.ExternalCollectionCreateRequest;
import com.wgzhao.addax.admin.dto.SourceColumnMetadata;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Creates an EXTERNAL collection definition only after a live target check and explicit mapping review.
 */
@Service
@RequiredArgsConstructor
public class ExternalCollectionService
{
    private final CollectionSchemaPreviewService previewService;
    private final TableService tableService;
    private final ColumnService columnService;
    private final ColumnMappingService mappingService;
    private final TargetContractService contractService;

    public EtlTable create(ExternalCollectionCreateRequest request)
    {
        if (request == null || request.table() == null || request.mappings() == null)
        {
            throw new ApiException(400, "table and mappings are required");
        }
        EtlTable table = request.table();
        table.setSchemaManagementMode(SchemaManagementMode.EXTERNAL);
        table.setTargetSchemaFingerprint(request.targetSchemaFingerprint());
        if (table.getDuration() == null)
        {
            table.setDuration(0L);
        }

        CollectionSchemaPreview preview = previewService.preview(new CollectionSchemaPreviewRequest(
            table.getSid(),
            table.getSourceDb(),
            table.getSourceTable(),
            table.getTargetId(),
            table.getTargetDb(),
            table.getTargetTable(),
            SchemaManagementMode.EXTERNAL.name(),
            table.getTargetOptions()));
        if (!preview.targetTable().exists())
        {
            throw new ApiException(409, "目标表不存在，外部管理模式下不能创建采集定义");
        }
        if (preview.findings().stream().anyMatch(
            finding -> TargetContractReport.ERROR.equals(finding.severity())))
        {
            throw new ApiException(409, "目标表预检存在阻断项: " + preview.findings().stream()
                .filter(finding -> TargetContractReport.ERROR.equals(finding.severity()))
                .map(TargetContractReport.Finding::message)
                .limit(3)
                .reduce((a, b) -> a + "; " + b)
                .orElse("unknown error"));
        }
        if (request.targetSchemaFingerprint() != null
            && !request.targetSchemaFingerprint().equals(preview.targetSchemaFingerprint()))
        {
            throw new ApiException(409, "目标表结构在预览后发生变化，请重新预览并确认映射");
        }
        validateMappings(preview, request.mappings());

        EtlTable saved = tableService.createTable(table);
        VwEtlTableWithSource view = tableService.getTableView(saved.getId());
        if (view == null || !columnService.createTableColumns(view))
        {
            throw new ApiException(400, "无法读取源表字段，采集定义未完成初始化");
        }
        mappingService.replace(saved.getId(), request.mappings());
        saved.setTargetSchemaFingerprint(preview.targetSchemaFingerprint());
        tableService.updateTable(saved, null);

        var refresh = tableService.refreshTableResources(saved.getId());
        if (!refresh.success())
        {
            throw new ApiException(409,
                "采集定义已创建但目标契约校验失败，当前处于待处理状态: " + refresh.message());
        }
        return saved;
    }

    private void validateMappings(CollectionSchemaPreview preview, List<ColumnMappingInput> mappings)
    {
        Map<String, SourceColumnMetadata> sources = new HashMap<>();
        preview.sourceColumns().forEach(column -> sources.put(key(column.name()), column));
        Map<String, TargetTableMetadata.TargetColumn> targets = new HashMap<>();
        preview.targetTable().columns().forEach(column -> targets.put(key(column.name()), column));

        Set<String> mappedSources = new HashSet<>();
        Set<String> mappedTargets = new HashSet<>();
        for (ColumnMappingInput mapping : mappings)
        {
            String source = key(mapping.sourceColumnName());
            String target = key(mapping.targetColumnName());
            if (!sources.containsKey(source))
            {
                throw new ApiException(400, "Unknown source column: " + mapping.sourceColumnName());
            }
            if (!targets.containsKey(target))
            {
                throw new ApiException(400, "Unknown target column: " + mapping.targetColumnName());
            }
            if (!mappedSources.add(source))
            {
                throw new ApiException(409, "Duplicate source mapping: " + mapping.sourceColumnName());
            }
            if (!mappedTargets.add(target))
            {
                throw new ApiException(409, "Multiple source columns map to " + mapping.targetColumnName());
            }
        }
        if (!mappedSources.equals(sources.keySet()))
        {
            throw new ApiException(400, "Every source column must be mapped before saving");
        }
        for (TargetTableMetadata.TargetColumn target : preview.targetTable().columns())
        {
            if (!mappedTargets.contains(key(target.name()))
                && !isPlatformProvidedBusinessDate(preview, target.name(), mappedTargets)
                && !target.nullable() && !target.autoIncrement()
                && (target.defaultValue() == null || target.defaultValue().isBlank()))
            {
                throw new ApiException(409, "Required target column is not mapped: " + target.name());
            }
        }
    }

    private boolean isPlatformProvidedBusinessDate(CollectionSchemaPreview preview, String targetName,
        Set<String> mappedTargets)
    {
        if (preview.targetOptions() == null || !preview.targetOptions().has("rdbms"))
        {
            return false;
        }
        var value = preview.targetOptions().path("rdbms").path("businessDateColumn");
        if (!value.isTextual() || value.asText().isBlank())
        {
            return false;
        }
        String businessDateColumn = value.asText().trim();
        return key(targetName).equals(key(businessDateColumn))
            && !mappedTargets.contains(key(businessDateColumn));
    }

    private String key(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
