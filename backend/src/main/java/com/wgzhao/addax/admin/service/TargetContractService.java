package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.HiveType;
import com.wgzhao.addax.admin.common.TargetOptions;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import com.wgzhao.addax.admin.utils.RdbmsDdlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.wgzhao.addax.admin.dto.TargetContractReport.ERROR;
import static com.wgzhao.addax.admin.dto.TargetContractReport.READY;
import static com.wgzhao.addax.admin.dto.TargetContractReport.WARNING;

/**
 * Checks whether an externally managed target can safely receive the configured collection contract.
 * It never creates or alters table structure.
 */
@Service
@RequiredArgsConstructor
public class TargetContractService
{
    private final TargetAdapterRegistry targetAdapterRegistry;
    private final ColumnMappingService columnMappingService;
    private final RiskLogService riskLogService;

    public TargetContractReport check(VwEtlTableWithSource table)
    {
        if (table == null)
        {
            throw new IllegalArgumentException("table is required");
        }
        TargetAdapter adapter = targetAdapterRegistry.resolve(table);
        TargetTableMetadata target = adapter.inspectTable(table);
        String fingerprint = fingerprint(target);
        List<TargetContractReport.Finding> findings = new ArrayList<>();
        List<ColumnMappingService.ResolvedColumnMapping> mappings = columnMappingService.resolve(table.getId());

        if (!target.exists())
        {
            findings.add(TargetContractReport.Finding.error(
                "TARGET_TABLE_MISSING", null, table.getTargetDb() + "." + table.getTargetTable(), null,
                "目标表不存在，外部管理模式下必须先创建目标表"));
        }
        else
        {
            Map<String, TargetTableMetadata.TargetColumn> targetColumns = target.columns().stream()
                .collect(Collectors.toMap(
                    c -> key(c.name()),
                    Function.identity(),
                    (first, ignored) -> first));
            Set<String> partitionNames = partitionNames(target);
            Set<String> mappedTargets = new HashSet<>();
            Set<String> platformProvidedTargets = new HashSet<>();

            for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
            {
                String targetKey = key(mapping.targetColumnName());
                mappedTargets.add(targetKey);
                TargetTableMetadata.TargetColumn actual = targetColumns.get(targetKey);
                if (!mapping.sourceActive())
                {
                    findings.add(TargetContractReport.Finding.warning(
                        "SOURCE_COLUMN_MISSING", mapping.targetColumnName(),
                        "source value", "NULL", "源字段 " + mapping.sourceColumnName()
                            + " 已不存在，将向目标字段写入 NULL"));
                }
                if (actual == null)
                {
                    findings.add(TargetContractReport.Finding.error(
                        "TARGET_COLUMN_MISSING", mapping.targetColumnName(),
                        expectedType(mapping.sourceColumn()), null,
                        "目标字段 " + mapping.targetColumnName() + " 不存在"));
                    continue;
                }
                String expectedType = expectedType(mapping.sourceColumn());
                if (expectedType != null && !expectedType.isBlank())
                {
                    String actualType = actual.type();
                    boolean compatible = isRdbmsAdapter(adapter)
                        ? rdbmsCompatible(actual, expectedType)
                        : HiveType.isHiveTypeCompatible(actualType, expectedType);
                    if (!compatible)
                    {
                        findings.add(TargetContractReport.Finding.error(
                            "TARGET_TYPE_INCOMPATIBLE", actual.name(), expectedType, actualType,
                            "目标字段 " + actual.name() + " 的类型 " + actualType
                                + " 无法安全承载期望类型 " + expectedType));
                    }
                    else if (!normalizedType(expectedType).equals(normalizedType(actualType)))
                    {
                        findings.add(TargetContractReport.Finding.warning(
                            "TARGET_TYPE_WIDER", actual.name(), expectedType, actualType,
                            "目标字段 " + actual.name() + " 使用类型 " + actualType
                                + "，兼容但不是精确匹配"));
                    }
                }
            }

            if (isRdbmsAdapter(adapter))
            {
                String businessDateColumn = table.getRdbmsBusinessDateColumn();
                if (businessDateColumn != null && !businessDateColumn.isBlank()
                    && !mappedTargets.contains(key(businessDateColumn)))
                {
                    TargetTableMetadata.TargetColumn dateColumn =
                        targetColumns.get(key(businessDateColumn));
                    if (dateColumn == null)
                    {
                        findings.add(TargetContractReport.Finding.error(
                            "TARGET_BUSINESS_DATE_COLUMN_MISSING", businessDateColumn,
                            "string", null, "目标表缺少配置的快照业务日期列 "
                                + businessDateColumn));
                    }
                    else
                    {
                        platformProvidedTargets.add(key(businessDateColumn));
                        if (!"string".equals(RdbmsDdlUtil.familyOf(dateColumn.type())))
                        {
                            findings.add(TargetContractReport.Finding.error(
                                "TARGET_BUSINESS_DATE_TYPE_INCOMPATIBLE", dateColumn.name(),
                                "string", dateColumn.type(),
                                "快照业务日期列 " + dateColumn.name()
                                    + " 必须使用可承载 yyyyMMdd 的字符串类型"));
                        }
                    }
                }
            }

            for (TargetTableMetadata.TargetColumn column : target.columns())
            {
                if (mappedTargets.contains(key(column.name()))
                    || platformProvidedTargets.contains(key(column.name()))
                    || partitionNames.contains(key(column.name())))
                {
                    continue;
                }
                if (!column.nullable() && !column.autoIncrement() && !hasDefault(column.defaultValue()))
                {
                    findings.add(TargetContractReport.Finding.error(
                        "TARGET_REQUIRED_COLUMN_UNMAPPED", column.name(), "mapping/default", "NOT NULL",
                        "目标字段 " + column.name() + " 非空且无默认值，但没有源字段映射"));
                }
                else
                {
                    findings.add(TargetContractReport.Finding.warning(
                        "TARGET_EXTRA_COLUMN", column.name(), null, column.type(),
                        "目标表包含未映射字段 " + column.name() + "，写入时将保留其默认值或 NULL"));
                }
            }

            if (!isRdbmsAdapter(adapter))
            {
                checkPartitions(table, target, findings);
                checkHiveColumnOrder(mappings, target, findings);
            }
        }

        String status = findings.stream().anyMatch(f -> ERROR.equals(f.severity()))
            ? ERROR
            : findings.isEmpty() ? READY : WARNING;
        if (table.getId() != null)
        {
            for (TargetContractReport.Finding finding : findings)
            {
                riskLogService.recordRisk("TargetContractService", finding.severity(),
                    "目标契约检查: " + finding.message(), table.getId());
            }
        }
        return new TargetContractReport(status, LocalDateTime.now(), fingerprint, target, findings);
    }

    public String fingerprint(TargetTableMetadata metadata)
    {
        StringBuilder canonical = new StringBuilder();
        canonical.append(metadata.exists()).append('|');
        Set<String> partitions = partitionNames(metadata);
        metadata.columns().stream()
            .filter(column -> !partitions.contains(key(column.name())))
            .sorted((a, b) -> Integer.compare(a.ordinal(), b.ordinal()))
            .forEach(c -> canonical.append(c.name()).append(':').append(c.type()).append(':')
                .append(c.size()).append(':').append(c.scale()).append(':').append(c.nullable())
                .append(':').append(c.defaultValue()).append(':').append(c.autoIncrement()).append('|'));
        metadata.partitions().forEach(p -> canonical.append("p:").append(p.name()).append(':')
            .append(p.type()).append('|'));
        metadata.primaryKeys().stream().sorted().forEach(k -> canonical.append("pk:").append(k).append('|'));
        metadata.uniqueKeys().stream().sorted().forEach(k -> canonical.append("uk:").append(k).append('|'));
        return sha256(canonical.toString());
    }

    private void checkPartitions(VwEtlTableWithSource table, TargetTableMetadata target,
        List<TargetContractReport.Finding> findings)
    {
        List<TargetOptions.HdfsPartitionBinding> bindings = TargetOptions.hdfsPartitionBindings(table.getTargetOptions());
        Set<String> actual = target.partitions().stream().map(p -> key(p.name())).collect(Collectors.toSet());
        Set<String> configured = bindings.stream().map(p -> key(p.name())).collect(Collectors.toSet());
        long bizDates = bindings.stream().filter(b -> "BIZ_DATE".equalsIgnoreCase(b.valueType())).count();
        if (bizDates > 1)
        {
            findings.add(TargetContractReport.Finding.error(
                "MULTIPLE_BIZ_DATE_PARTITIONS", null, "<=1", String.valueOf(bizDates),
                "最多只能有一个分区键承载业务日期"));
        }
        if (!actual.equals(configured))
        {
            findings.add(TargetContractReport.Finding.error(
                "PARTITION_KEYS_MISMATCH", null, String.join(",", configured), String.join(",", actual),
                "分区键配置与目标表不一致"));
        }
    }

    private void checkHiveColumnOrder(List<ColumnMappingService.ResolvedColumnMapping> mappings,
        TargetTableMetadata target, List<TargetContractReport.Finding> findings)
    {
        Set<String> mappedTargets = mappings.stream()
            .map(mapping -> key(mapping.targetColumnName()))
            .collect(Collectors.toSet());
        Set<String> partitionNames = partitionNames(target);
        List<String> configuredOrder = mappings.stream()
            .map(mapping -> key(mapping.targetColumnName()))
            .filter(mappedTargets::contains)
            .toList();
        List<String> actualOrder = target.columns().stream()
            .map(column -> key(column.name()))
            .filter(column -> !partitionNames.contains(column))
            .filter(mappedTargets::contains)
            .toList();
        if (!actualOrder.equals(configuredOrder))
        {
            findings.add(TargetContractReport.Finding.error(
                "TARGET_COLUMN_ORDER_MISMATCH", null,
                String.join(",", configuredOrder), String.join(",", actualOrder),
                "HDFS 目标字段顺序必须与映射顺序一致，否则文件列会错位"));
        }
        boolean sawUnmapped = false;
        for (TargetTableMetadata.TargetColumn column : target.columns())
        {
            if (partitionNames.contains(key(column.name())))
            {
                continue;
            }
            if (!mappedTargets.contains(key(column.name())))
            {
                sawUnmapped = true;
            }
            else if (sawUnmapped)
            {
                findings.add(TargetContractReport.Finding.error(
                    "TARGET_EXTRA_COLUMN_NOT_AT_END", column.name(), "trailing extras", "interleaved",
                    "未映射的 HDFS 目标字段必须放在映射字段之后"));
                break;
            }
        }
    }

    private boolean rdbmsCompatible(TargetTableMetadata.TargetColumn actual, String expected)
    {
        String actualFamily = RdbmsDdlUtil.familyOf(actual.type());
        String expectedFamily = RdbmsDdlUtil.familyOf(expected);
        if (!actualFamily.equals(expectedFamily))
        {
            return false;
        }
        return !RdbmsDdlUtil.isWidening(actual.type(), actual.size(), actual.scale(), expected);
    }

    private boolean isRdbmsAdapter(TargetAdapter adapter)
    {
        return "RDBMS".equalsIgnoreCase(adapter.getType());
    }

    private String expectedType(EtlColumn column)
    {
        if (column == null)
        {
            return null;
        }
        return column.getTargetTypeFull() == null || column.getTargetTypeFull().isBlank()
            ? column.getTargetType() : column.getTargetTypeFull();
    }

    private boolean hasDefault(String value)
    {
        return value != null && !value.isBlank();
    }

    private String normalizedType(String value)
    {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String key(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> partitionNames(TargetTableMetadata metadata)
    {
        return metadata.partitions().stream()
            .map(partition -> key(partition.name()))
            .collect(Collectors.toSet());
    }

    private String sha256(String value)
    {
        try
        {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
