package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.common.TargetOptions;
import com.wgzhao.addax.admin.dto.CollectionPlanPreview;
import com.wgzhao.addax.admin.dto.CollectionSchemaPreview;
import com.wgzhao.addax.admin.dto.CollectionSchemaPreviewRequest;
import com.wgzhao.addax.admin.dto.SourceColumnMetadata;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.service.collection.CollectionScopeService;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.RdbmsDdlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.wgzhao.addax.admin.dto.TargetContractReport.ERROR;
import static com.wgzhao.addax.admin.dto.TargetContractReport.READY;
import static com.wgzhao.addax.admin.dto.TargetContractReport.WARNING;

/**
 * Builds the batch plan shown before creation. MANAGED mode describes the generated schema, while
 * EXTERNAL mode inspects the live target contract and never proposes target DDL.
 */
@Service
@RequiredArgsConstructor
public class CollectionPlanPreviewService
{
    private final CollectionSchemaPreviewService schemaPreviewService;
    private final TargetAdapterRegistry targetAdapterRegistry;
    private final EtlTargetRepo targetRepo;
    private final DictService dictService;
    private final ObjectMapper objectMapper;
    private final CollectionScopeService collectionScopeService;

    public List<CollectionPlanPreview> preview(List<CollectionSchemaPreviewRequest> requests)
    {
        if (requests == null || requests.isEmpty())
        {
            throw new IllegalArgumentException("At least one collection plan is required");
        }
        return requests.stream().map(request -> {
            try
            {
                return previewOne(request);
            }
            catch (RuntimeException e)
            {
                String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName() : e.getMessage();
                TargetContractReport.Finding finding = TargetContractReport.Finding.error(
                    "PLAN_PREVIEW_FAILED", null, "preview", null, "目标计划预检失败: " + message);
                return build(request, ERROR, "BLOCKED", finding.message(), List.of(),
                    TargetTableMetadata.missing(), List.of(), List.of(), List.of(), null, null,
                    List.of(finding));
            }
        }).toList();
    }

    private CollectionPlanPreview previewOne(CollectionSchemaPreviewRequest request)
    {
        SchemaManagementMode mode = SchemaManagementMode.from(request.schemaManagementMode());
        if (mode == SchemaManagementMode.EXTERNAL)
        {
            return externalPreview(request);
        }
        return managedPreview(request);
    }

    private CollectionPlanPreview externalPreview(CollectionSchemaPreviewRequest request)
    {
        CollectionSchemaPreview preview = schemaPreviewService.preview(request);
        List<TargetContractReport.Finding> findings = new ArrayList<>(preview.findings());
        TargetContractReport.Finding existing = existingDefinitionFinding(request);
        if (existing != null)
        {
            findings.add(0, existing);
        }
        preview.suggestions().stream()
            .map(CollectionSchemaPreview.MappingSuggestion::finding)
            .filter(java.util.Objects::nonNull)
            .forEach(findings::add);
        String status = status(findings);
        String action = preview.targetTable().exists() ? "VALIDATE_EXTERNAL" : "BLOCKED";
        List<CollectionPlanPreview.PlannedTargetColumn> columns = preview.targetTable().columns().stream()
            .map(column -> new CollectionPlanPreview.PlannedTargetColumn(
                column.name(), null, column.type(), column.nullable(), false, null,
                "EXISTING", column.ordinal()))
            .toList();
        return build(request, status, action, summary(status, findings), preview.sourceColumns(),
            preview.targetTable(), columns, preview.targetTable().partitions(), preview.suggestions(),
            preview.targetSchemaFingerprint(), null, findings);
    }

    private CollectionPlanPreview managedPreview(CollectionSchemaPreviewRequest request)
    {
        List<SourceColumnMetadata> sourceColumns = schemaPreviewService.inspectSourceColumns(
            request.sourceId(), request.sourceDb(), request.sourceTable());
        VwEtlTableWithSource table = toTable(request);
        TargetAdapter adapter = targetAdapterRegistry.resolve(table);
        TargetTableMetadata actual = adapter.inspectTable(table);
        List<TargetContractReport.Finding> findings = new ArrayList<>();
        TargetContractReport.Finding existing = existingDefinitionFinding(request);
        if (existing != null)
        {
            findings.add(existing);
        }
        List<CollectionPlanPreview.PlannedTargetColumn> planned = planColumns(request, adapter, sourceColumns);
        List<CollectionSchemaPreview.MappingSuggestion> mappings = planned.stream()
            .filter(column -> column.sourceColumnName() != null)
            .map(column -> new CollectionSchemaPreview.MappingSuggestion(
                column.sourceColumnName(), column.name(), false, sourceType(
                    sourceColumns, column.sourceColumnName()), column.type(), null))
            .toList();
        String ddl = buildManagedDdl(request, adapter, planned);
        String action = actual.exists() ? "UPDATE_TABLE" : "CREATE_TABLE";
        if (actual.exists())
        {
            findings.add(TargetContractReport.Finding.warning(
                "MANAGED_TARGET_EXISTS", null, "managed", "exists",
                "目标表已存在，确认后将按计划补充或调整由平台管理的字段"));
        }
        String status = status(findings);
        return build(request, status, action, summary(status, findings), sourceColumns, actual, planned,
            plannedPartitions(request, adapter), mappings, null, ddl, findings);
    }

    /**
     * A target table that exists is not the same thing as a collection definition for this route.
     * Report the definition explicitly so the wizard does not tell the user "already collected" based
     * on target metadata alone.
     */
    private TargetContractReport.Finding existingDefinitionFinding(CollectionSchemaPreviewRequest request)
    {
        CollectionScopeService.Definition existing = collectionScopeService.findDefinition(
            request.sourceId(), request.sourceDb(), request.sourceTable(),
            request.targetId(), request.targetDb());
        if (existing == null)
        {
            return null;
        }
        if (existing.active())
        {
            return TargetContractReport.Finding.error("SOURCE_ALREADY_COLLECTED", null, "collection", null,
                "该源表已在目标端采集（采集表 ID " + existing.id() + "），不能重复创建");
        }
        return TargetContractReport.Finding.error("SOURCE_DEFINITION_RETIRED", null, "collection", null,
            "该源表存在已退役定义（采集表 ID " + existing.id() + "），请先恢复");
    }

    private CollectionPlanPreview build(
        CollectionSchemaPreviewRequest request,
        String status,
        String action,
        String summary,
        List<SourceColumnMetadata> sourceColumns,
        TargetTableMetadata actual,
        List<CollectionPlanPreview.PlannedTargetColumn> plannedColumns,
        List<TargetTableMetadata.TargetPartition> plannedPartitions,
        List<CollectionSchemaPreview.MappingSuggestion> mappings,
        String fingerprint,
        String planDdl,
        List<TargetContractReport.Finding> findings)
    {
        return new CollectionPlanPreview(
            request.sourceDb(),
            request.sourceTable(),
            request.targetDb(),
            request.targetTable(),
            request.schemaManagementMode(),
            status,
            action,
            summary,
            sourceColumns,
            actual,
            plannedColumns,
            plannedPartitions,
            mappings,
            fingerprint,
            request.targetOptions(),
            planDdl,
            findings);
    }

    private List<CollectionPlanPreview.PlannedTargetColumn> planColumns(
        CollectionSchemaPreviewRequest request, TargetAdapter adapter, List<SourceColumnMetadata> sourceColumns)
    {
        List<CollectionPlanPreview.PlannedTargetColumn> columns = new ArrayList<>();
        int ordinal = 0;
        for (SourceColumnMetadata source : sourceColumns)
        {
            boolean primaryKey = "RDBMS".equalsIgnoreCase(adapter.getType())
                && createPrimaryKey(request.targetOptions()) && source.primaryKey();
            columns.add(new CollectionPlanPreview.PlannedTargetColumn(
                source.name(), source.name(), targetType(request, adapter, source),
                source.nullable(), primaryKey, source.comment(), "SOURCE", ++ordinal));
        }
        if ("RDBMS".equalsIgnoreCase(adapter.getType()))
        {
            String businessDateColumn = businessDateColumn(request.targetOptions());
            if (businessDateColumn != null && columns.stream()
                .noneMatch(column -> businessDateColumn.equalsIgnoreCase(column.name())))
            {
                columns.add(new CollectionPlanPreview.PlannedTargetColumn(
                    businessDateColumn, null, "varchar(8)", false, false, "业务日期",
                    "BUSINESS_DATE", ++ordinal));
            }
        }
        return columns;
    }

    private String targetType(CollectionSchemaPreviewRequest request, TargetAdapter adapter,
        SourceColumnMetadata source)
    {
        if (!"HDFS".equalsIgnoreCase(adapter.getType()))
        {
            return RdbmsDdlUtil.renderType(targetDialect(request.targetId()), source.type(),
                source.size(), source.precision() == null ? source.size() : source.precision(),
                source.scale() == null ? 0 : source.scale());
        }
        Map<String, String> hiveMapping = dictService.getHiveTypeMapping();
        String mapped = hiveMapping.getOrDefault(source.type().toLowerCase(Locale.ROOT), "string");
        if ("decimal".equalsIgnoreCase(mapped))
        {
            int precision = source.size();
            int scale = source.scale() == null ? 0 : source.scale();
            return "decimal(" + precision + "," + scale + ")";
        }
        return mapped;
    }

    private List<TargetTableMetadata.TargetPartition> plannedPartitions(
        CollectionSchemaPreviewRequest request, TargetAdapter adapter)
    {
        if (!"HDFS".equalsIgnoreCase(adapter.getType()))
        {
            return List.of();
        }
        return TargetOptions.hdfsPartitionBindings(request.targetOptions()).stream()
            .map(binding -> new TargetTableMetadata.TargetPartition(binding.name(), "string"))
            .toList();
    }

    private String buildManagedDdl(CollectionSchemaPreviewRequest request, TargetAdapter adapter,
        List<CollectionPlanPreview.PlannedTargetColumn> columns)
    {
        if (!"RDBMS".equalsIgnoreCase(adapter.getType()))
        {
            String partitions = plannedPartitions(request, adapter).isEmpty()
                ? ""
                : "\nPARTITIONED BY (" + plannedPartitions(request, adapter).stream()
                    .map(partition -> "`" + partition.name() + "` " + partition.type())
                    .reduce((a, b) -> a + ", " + b).orElse("") + ")";
            return "CREATE EXTERNAL TABLE `" + request.targetDb() + "`.`" + request.targetTable() + "` (\n"
                + columns.stream()
                    .map(column -> "  `" + column.name() + "` " + column.type())
                    .reduce((a, b) -> a + ",\n" + b).orElse("")
                + "\n)" + partitions;
        }
        DbType dialect = targetDialect(request.targetId());
        List<String> definitions = new ArrayList<>();
        for (CollectionPlanPreview.PlannedTargetColumn column : columns)
        {
            definitions.add(RdbmsDdlUtil.columnDefinition(dialect, column.name(), column.type(),
                !column.nullable(), column.comment()));
        }
        String tableRef = RdbmsDdlUtil.tableRef(dialect, request.targetDb(), request.targetTable());
        List<String> primaryKeys = columns.stream()
            .filter(CollectionPlanPreview.PlannedTargetColumn::primaryKey)
            .map(CollectionPlanPreview.PlannedTargetColumn::name)
            .toList();
        String createTable = RdbmsDdlUtil.createTableIfNotExists(
            dialect, tableRef, definitions, primaryKeys, null);
        if (dialect == DbType.MYSQL && request.targetDb() != null && !request.targetDb().isBlank())
        {
            return "CREATE DATABASE IF NOT EXISTS " + RdbmsDdlUtil.quoteIdent(dialect, request.targetDb())
                + " DEFAULT CHARSET utf8mb4;\n\n" + createTable;
        }
        return createTable;
    }

    private DbType targetDialect(Long targetId)
    {
        EtlTarget target = targetRepo.findById(targetId).orElse(null);
        if (target == null || target.getConnectConfig() == null || target.getConnectConfig().isBlank())
        {
            return DbType.RDBMS;
        }
        try
        {
            JsonNode node = objectMapper.readTree(target.getConnectConfig());
            JsonNode url = node.get("url");
            return url == null || url.asText().isBlank() ? DbType.RDBMS : DbUtil.getDbType(url.asText());
        }
        catch (Exception ignored)
        {
            return DbType.RDBMS;
        }
    }

    private VwEtlTableWithSource toTable(CollectionSchemaPreviewRequest request)
    {
        VwEtlTableWithSource table = new VwEtlTableWithSource();
        table.setSourceDb(request.sourceDb());
        table.setSourceTable(request.sourceTable());
        table.setTargetId(request.targetId());
        table.setTargetDb(request.targetDb());
        table.setTargetTable(request.targetTable());
        table.setTargetOptions(request.targetOptions());
        table.setSchemaManagementMode(SchemaManagementMode.from(request.schemaManagementMode()));
        return table;
    }

    private String businessDateColumn(JsonNode targetOptions)
    {
        if (targetOptions == null || !targetOptions.has("rdbms"))
        {
            return null;
        }
        JsonNode value = targetOptions.path("rdbms").path("businessDateColumn");
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private boolean createPrimaryKey(JsonNode targetOptions)
    {
        if (targetOptions == null || !targetOptions.has("rdbms"))
        {
            return false;
        }
        JsonNode value = targetOptions.path("rdbms").path("createPrimaryKey");
        return value.isBoolean() ? value.asBoolean() : true;
    }

    private String sourceType(List<SourceColumnMetadata> columns, String sourceName)
    {
        return columns.stream()
            .filter(column -> column.name().equalsIgnoreCase(sourceName))
            .map(SourceColumnMetadata::type)
            .findFirst()
            .orElse(null);
    }

    private String status(List<TargetContractReport.Finding> findings)
    {
        if (findings.stream().anyMatch(finding -> ERROR.equals(finding.severity())))
        {
            return ERROR;
        }
        return findings.isEmpty() ? READY : WARNING;
    }

    private String summary(String status, List<TargetContractReport.Finding> findings)
    {
        if (findings.isEmpty())
        {
            return "目标计划校验通过";
        }
        return findings.stream()
            .map(TargetContractReport.Finding::message)
            .limit(3)
            .reduce((a, b) -> a + "; " + b)
            .orElse(status);
    }
}
