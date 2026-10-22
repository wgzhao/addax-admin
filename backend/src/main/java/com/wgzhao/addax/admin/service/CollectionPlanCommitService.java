package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.CollectionPlanCommitItem;
import com.wgzhao.addax.admin.dto.CollectionPlanCommitResult;
import com.wgzhao.addax.admin.dto.ColumnMappingInput;
import com.wgzhao.addax.admin.dto.ExternalCollectionCreateRequest;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies confirmed batch plans one table at a time so one failed target cannot abort the rest of
 * the batch. MANAGED initialization is synchronous here; the response describes the actual target
 * table outcome instead of merely acknowledging a queued background refresh.
 */
@Service
@RequiredArgsConstructor
public class CollectionPlanCommitService
{
    private final TableService tableService;
    private final ColumnService columnService;
    private final ColumnMappingService mappingService;
    private final ExternalCollectionService externalCollectionService;

    public List<CollectionPlanCommitResult> commit(List<CollectionPlanCommitItem> items)
    {
        if (items == null || items.isEmpty())
        {
            throw new ApiException(400, "At least one confirmed collection plan is required");
        }
        Set<String> targets = new HashSet<>();
        List<CollectionPlanCommitResult> results = new ArrayList<>();
        for (CollectionPlanCommitItem item : items)
        {
            EtlTable table = item.table();
            String targetKey = (table.getTargetDb() + "." + table.getTargetTable())
                .toLowerCase(Locale.ROOT);
            if (!targets.add(targetKey))
            {
                results.add(CollectionPlanCommitResult.failed(null, table.getSourceTable(),
                    table.getTargetTable(), "同一批次的多个源表解析到了同一个目标表"));
                continue;
            }
            try
            {
                results.add(table.getSchemaManagementMode() == SchemaManagementMode.EXTERNAL
                    ? commitExternal(item)
                    : commitManaged(item));
            }
            catch (RuntimeException e)
            {
                results.add(CollectionPlanCommitResult.failed(null, table.getSourceTable(),
                    table.getTargetTable(), message(e)));
            }
        }
        return results;
    }

    private CollectionPlanCommitResult commitExternal(CollectionPlanCommitItem item)
    {
        EtlTable table = item.table();
        EtlTable saved = externalCollectionService.create(new ExternalCollectionCreateRequest(
            table, item.mappings(), item.targetSchemaFingerprint()));
        return CollectionPlanCommitResult.created(saved.getId(), table.getSourceTable(),
            table.getTargetTable(), "外部目标表校验通过，采集定义已创建");
    }

    private CollectionPlanCommitResult commitManaged(CollectionPlanCommitItem item)
    {
        EtlTable table = item.table();
        table.setSchemaManagementMode(SchemaManagementMode.MANAGED);
        table.setStatus(TableStatus.WAIT_SCHEMA);
        if (table.getDuration() == null)
        {
            table.setDuration(0L);
        }
        EtlTable saved = tableService.createTable(table);
        Long id = saved.getId();
        try
        {
            VwEtlTableWithSource view = tableService.getTableView(id);
            if (view == null)
            {
                throw new ApiException(409, "采集定义已保存，但初始化视图不可见");
            }
            if (!columnService.createTableColumns(view))
            {
                throw new ApiException(409, "无法读取源表字段");
            }
            validateMappings(id, item.mappings());
            mappingService.replace(id, item.mappings());
            var refresh = tableService.refreshTableResources(id);
            if (!refresh.success())
            {
                return CollectionPlanCommitResult.failed(id, table.getSourceTable(),
                    table.getTargetTable(), refresh.message());
            }
            return CollectionPlanCommitResult.created(id, table.getSourceTable(),
                table.getTargetTable(), "目标表和采集任务已初始化");
        }
        catch (RuntimeException e)
        {
            return CollectionPlanCommitResult.failed(id, table.getSourceTable(),
                table.getTargetTable(), message(e));
        }
    }

    private void validateMappings(Long tableId, List<ColumnMappingInput> mappings)
    {
        if (mappings == null || mappings.isEmpty())
        {
            throw new ApiException(400, "At least one column mapping is required");
        }
        List<EtlColumn> columns = columnService.getColumns(tableId);
        Set<String> sources = new HashSet<>();
        Set<String> mappedSources = new HashSet<>();
        Set<String> mappedTargets = new HashSet<>();
        for (EtlColumn column : columns)
        {
            String name = ColumnMappingService.activeName(column.getColumnName());
            if (name != null && !name.isBlank() && !ColumnMappingService.isDeleted(column.getColumnName()))
            {
                sources.add(key(name));
            }
        }
        for (ColumnMappingInput mapping : mappings)
        {
            String source = key(mapping.sourceColumnName());
            String target = key(mapping.targetColumnName());
            if (!sources.contains(source))
            {
                throw new ApiException(400, "Unknown source column: " + mapping.sourceColumnName());
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
        if (!mappedSources.equals(sources))
        {
            throw new ApiException(400, "Every source column must be mapped before creation");
        }
    }

    private String key(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String message(RuntimeException e)
    {
        return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
    }
}
