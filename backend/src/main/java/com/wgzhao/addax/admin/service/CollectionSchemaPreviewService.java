package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.common.TargetOptions;
import com.wgzhao.addax.admin.dto.CollectionSchemaPreview;
import com.wgzhao.addax.admin.dto.CollectionSchemaPreviewRequest;
import com.wgzhao.addax.admin.dto.SourceColumnMetadata;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.wgzhao.addax.admin.dto.TargetContractReport.ERROR;

/**
 * Builds an external-table mapping preview from live source and target metadata. The preview is advisory;
 * save and execution re-read target metadata to close the preview/save race.
 */
@Service
@RequiredArgsConstructor
public class CollectionSchemaPreviewService
{
    private final EtlSourceRepo sourceRepo;
    private final TargetAdapterRegistry targetAdapterRegistry;

    public List<SourceColumnMetadata> inspectSourceColumns(Integer sourceId, String sourceDb, String sourceTable)
    {
        EtlSource source = sourceRepo.findById(sourceId)
            .orElseThrow(() -> new ApiException(404, "采集源不存在"));
        return inspectSource(source, sourceDb, sourceTable);
    }

    public CollectionSchemaPreview preview(CollectionSchemaPreviewRequest request)
    {
        validate(request);
        EtlSource source = sourceRepo.findById(request.sourceId())
            .orElseThrow(() -> new ApiException(404, "采集源不存在"));
        List<SourceColumnMetadata> sourceColumns = inspectSource(source, request.sourceDb(), request.sourceTable());

        VwEtlTableWithSource table = new VwEtlTableWithSource();
        table.setSourceDb(request.sourceDb());
        table.setSourceTable(request.sourceTable());
        table.setTargetId(request.targetId());
        table.setTargetDb(request.targetDb());
        table.setTargetTable(request.targetTable());
        table.setTargetOptions(request.targetOptions());
        table.setSchemaManagementMode(SchemaManagementMode.from(request.schemaManagementMode()));

        com.wgzhao.addax.admin.service.target.TargetAdapter adapter =
            targetAdapterRegistry.resolve(table);
        TargetTableMetadata target = adapter.inspectTable(table);
        List<TargetContractReport.Finding> findings = new ArrayList<>();
        if (!target.exists())
        {
            findings.add(TargetContractReport.Finding.error(
                "TARGET_TABLE_MISSING", null,
                request.targetDb() + "." + request.targetTable(), null,
                "目标表不存在，外部管理模式下必须先创建目标表"));
            return new CollectionSchemaPreview(sourceColumns, target, List.of(),
                sourceColumns.stream().map(SourceColumnMetadata::name).toList(),
                List.of(), null, request.targetOptions(), findings);
        }

        Map<String, TargetTableMetadata.TargetColumn> targetsByName = new HashMap<>();
        for (TargetTableMetadata.TargetColumn targetColumn : target.columns())
        {
            targetsByName.put(key(targetColumn.name()), targetColumn);
        }

        Set<String> unmatchedTargets = new LinkedHashSet<>();
        for (TargetTableMetadata.TargetColumn targetColumn : target.columns())
        {
            unmatchedTargets.add(targetColumn.name());
        }

        List<CollectionSchemaPreview.MappingSuggestion> suggestions = new ArrayList<>();
        List<String> unmatchedSources = new ArrayList<>();
        for (SourceColumnMetadata sourceColumn : sourceColumns)
        {
            TargetTableMetadata.TargetColumn targetColumn = targetsByName.get(key(sourceColumn.name()));
            if (targetColumn == null)
            {
                unmatchedSources.add(sourceColumn.name());
                suggestions.add(new CollectionSchemaPreview.MappingSuggestion(
                    sourceColumn.name(), null, false, sourceColumn.type(), null,
                    TargetContractReport.Finding.error(
                        "TARGET_COLUMN_MISSING", sourceColumn.name(), sourceColumn.name(), null,
                        "目标表没有同名字段，请指定目标字段")));
                continue;
            }
            unmatchedTargets.remove(targetColumn.name());
            suggestions.add(new CollectionSchemaPreview.MappingSuggestion(
                sourceColumn.name(), targetColumn.name(), false,
                sourceColumn.type(), targetColumn.type(), null));
        }

        Set<String> suggestedTargets = new LinkedHashSet<>();
        for (CollectionSchemaPreview.MappingSuggestion suggestion : suggestions)
        {
            if (suggestion.targetColumnName() != null)
            {
                suggestedTargets.add(key(suggestion.targetColumnName()));
            }
        }
        Set<String> platformProvidedTargets = new LinkedHashSet<>();
        if ("RDBMS".equalsIgnoreCase(adapter.getType()))
        {
            String businessDateColumn = businessDateColumn(request.targetOptions());
            if (businessDateColumn != null && !suggestedTargets.contains(key(businessDateColumn)))
            {
                TargetTableMetadata.TargetColumn dateColumn = targetsByName.get(key(businessDateColumn));
                if (dateColumn == null)
                {
                    findings.add(TargetContractReport.Finding.error(
                        "TARGET_BUSINESS_DATE_COLUMN_MISSING", businessDateColumn,
                        "string", null, "目标表缺少配置的快照业务日期列 " + businessDateColumn));
                }
                else
                {
                    platformProvidedTargets.add(key(businessDateColumn));
                    unmatchedTargets.remove(dateColumn.name());
                    if (!"string".equals(
                        com.wgzhao.addax.admin.utils.RdbmsDdlUtil.familyOf(dateColumn.type())))
                    {
                        findings.add(TargetContractReport.Finding.error(
                            "TARGET_BUSINESS_DATE_TYPE_INCOMPATIBLE", dateColumn.name(),
                            "string", dateColumn.type(),
                            "快照业务日期列必须使用可承载 yyyyMMdd 的字符串类型"));
                    }
                }
            }
        }

        for (TargetTableMetadata.TargetColumn targetColumn : target.columns())
        {
            if (suggestedTargets.contains(key(targetColumn.name()))
                || platformProvidedTargets.contains(key(targetColumn.name())))
            {
                continue;
            }
            if (!targetColumn.nullable() && !targetColumn.autoIncrement()
                && (targetColumn.defaultValue() == null || targetColumn.defaultValue().isBlank()))
            {
                findings.add(TargetContractReport.Finding.error(
                    "TARGET_REQUIRED_COLUMN_UNMAPPED", targetColumn.name(), "mapping/default", "NOT NULL",
                    "目标字段 " + targetColumn.name() + " 非空且无默认值，需要映射"));
            }
            else
            {
                findings.add(TargetContractReport.Finding.warning(
                    "TARGET_EXTRA_COLUMN", targetColumn.name(), null, targetColumn.type(),
                    "目标表包含未映射字段 " + targetColumn.name() + "，将保留默认值或 NULL"));
            }
        }

        validatePartitionPreview(table, target, findings);
        return new CollectionSchemaPreview(
            sourceColumns,
            target,
            suggestions,
            List.copyOf(unmatchedSources),
            List.copyOf(unmatchedTargets),
            new TargetContractServiceFingerprint().fingerprint(target),
            request.targetOptions(),
            findings);
    }

    private List<SourceColumnMetadata> inspectSource(EtlSource source, String db, String tableName)
    {
        try (Connection connection = DbUtil.getConnection(source.getUrl(), source.getUsername(), source.getPass()))
        {
            if (connection == null)
            {
                throw new ApiException(400, "无法连接采集源数据库");
            }
            DbType dialect = DbUtil.getDbType(source.getUrl());
            String catalog = switch (dialect)
            {
                case MYSQL, SQLSERVER -> db;
                default -> null;
            };
            String schema = switch (dialect)
            {
                case POSTGRESQL ->
                {
                    // The batch UI selects a JDBC catalog while PostgreSQL metadata is schema-scoped.
                    String catalogName = connection.getCatalog();
                    yield db != null && !db.equalsIgnoreCase(catalogName) ? db : "public";
                }
                case SQLSERVER -> "dbo";
                default -> null;
            };
            java.util.Set<String> primaryKeys = new java.util.HashSet<>();
            try (ResultSet rs = connection.getMetaData().getPrimaryKeys(catalog, schema, tableName))
            {
                while (rs.next())
                {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null)
                    {
                        primaryKeys.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
            List<SourceColumnMetadata> columns = new ArrayList<>();
            try (ResultSet rs = connection.getMetaData().getColumns(catalog, schema, tableName, null))
            {
                while (rs.next())
                {
                    String name = rs.getString("COLUMN_NAME");
                    if (name == null)
                    {
                        continue;
                    }
                    int size = rs.getInt("COLUMN_SIZE");
                    columns.add(new SourceColumnMetadata(
                        name,
                        rs.getString("TYPE_NAME"),
                        size,
                        size,
                        rs.getInt("DECIMAL_DIGITS"),
                        rs.getString("REMARKS"),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        primaryKeys.contains(name.toLowerCase(Locale.ROOT))));
                }
            }
            if (columns.isEmpty())
            {
                throw new ApiException(400, "源表不存在或没有可见字段: " + db + "." + tableName);
            }
            return columns;
        }
        catch (SQLException e)
        {
            throw new ApiException(400, "读取源表元数据失败: " + e.getMessage());
        }
    }

    private void validatePartitionPreview(VwEtlTableWithSource table, TargetTableMetadata target,
        List<TargetContractReport.Finding> findings)
    {
        List<TargetOptions.HdfsPartitionBinding> bindings = TargetOptions.hdfsPartitionBindings(table.getTargetOptions());
        Set<String> actual = target.partitions().stream()
            .map(partition -> key(partition.name()))
            .collect(java.util.stream.Collectors.toSet());
        Set<String> configured = bindings.stream()
            .map(partition -> key(partition.name()))
            .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(configured))
        {
            findings.add(TargetContractReport.Finding.error(
                "PARTITION_KEYS_MISMATCH", null,
                String.join(",", configured), String.join(",", actual),
                "分区键配置与目标表不一致"));
        }
    }

    private void validate(CollectionSchemaPreviewRequest request)
    {
        if (request == null || request.sourceId() == null)
        {
            throw new ApiException(400, "sourceId is required");
        }
        if (request.targetId() == null)
        {
            throw new ApiException(400, "targetId is required");
        }
        for (String value : List.of(request.sourceDb(), request.sourceTable(),
            request.targetDb(), request.targetTable()))
        {
            if (value == null || value.isBlank())
            {
                throw new ApiException(400, "source/target db and table are required");
            }
            if (value.contains("${"))
            {
                throw new ApiException(400, "动态表名需要先确定业务日期后再做字段映射预览");
            }
        }
    }

    private String key(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String businessDateColumn(com.fasterxml.jackson.databind.JsonNode targetOptions)
    {
        if (targetOptions == null || !targetOptions.has("rdbms"))
        {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode value =
            targetOptions.path("rdbms").path("businessDateColumn");
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    /**
     * Keep fingerprint calculation in one place without exposing it through a stateful service call.
     */
    private static class TargetContractServiceFingerprint
    {
        private String fingerprint(TargetTableMetadata metadata)
        {
            StringBuilder canonical = new StringBuilder();
            canonical.append(metadata.exists()).append('|');
            metadata.columns().stream()
                .sorted((a, b) -> Integer.compare(a.ordinal(), b.ordinal()))
                .forEach(c -> canonical.append(c.name()).append(':').append(c.type()).append(':')
                    .append(c.size()).append(':').append(c.scale()).append(':').append(c.nullable())
                    .append(':').append(c.defaultValue()).append(':').append(c.autoIncrement()).append('|'));
            metadata.partitions().forEach(p -> canonical.append("p:").append(p.name()).append(':')
                .append(p.type()).append('|'));
            metadata.primaryKeys().stream().sorted().forEach(k -> canonical.append("pk:").append(k).append('|'));
            metadata.uniqueKeys().stream().sorted().forEach(k -> canonical.append("uk:").append(k).append('|'));
            try
            {
                return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            catch (java.security.NoSuchAlgorithmException e)
            {
                throw new IllegalStateException("SHA-256 is unavailable", e);
            }
        }
    }
}
