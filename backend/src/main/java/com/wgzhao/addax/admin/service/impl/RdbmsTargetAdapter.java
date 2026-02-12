package com.wgzhao.addax.admin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.service.ColumnService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;
import static com.wgzhao.addax.admin.utils.DbUtil.getDbType;
import static com.wgzhao.addax.admin.utils.DbUtil.quoteIfNeeded;

@Service
@Slf4j
@RequiredArgsConstructor
public class RdbmsTargetAdapter
    implements TargetAdapter
{
    private static final String DEFAULT_WRITER_TEMPLATE_KEY = "wR";

    private final EtlTargetRepo etlTargetRepo;
    private final DictService dictService;
    private final ColumnService columnService;
    private final SystemConfigService configService;
    private final ObjectMapper objectMapper;

    @Override
    public String getType()
    {
        return "RDBMS";
    }

    @Override
    public boolean addPartition(long taskId, String db, String table, String partName, String partValue)
    {
        return true;
    }

    @Override
    public boolean createOrUpdateTable(VwEtlTableWithSource etlTable)
    {
        return true;
    }

    @Override
    public Long getMaxValue(VwEtlTableWithSource table, String columnName, String partValue)
    {
        return null;
    }

    @Override
    public boolean prepareBeforeRun(long taskId, VwEtlTableWithSource table, String bizDateValue)
    {
        return true;
    }

    @Override
    public String buildWriterJob(VwEtlTableWithSource table)
    {
        return fillRdbmsWriterJob(table);
    }

    private String fillRdbmsWriterJob(VwEtlTableWithSource table)
    {
        String template = resolveWriterTemplate(table);
        Map<String, String> values = new HashMap<>();
        String targetType = resolveTargetType(table);

        values.put("name", targetType.toLowerCase(Locale.ROOT) + "writer");
        DbType dbType = getDbType(table.getUrl());

        if (dbType == DbType.POSTGRESQL) {
            values.put("table", quoteIfNeeded(table.getTargetTable(), dbType));
        }
        else {
            values.put("table", quoteIfNeeded(table.getTargetDb(), dbType) + "." + quoteIfNeeded(table.getTargetTable(), dbType));
        }

        values.put("targetDb", table.getTargetDb());
        values.put("targetTable", table.getTargetTable());
        values.put("writeMode", table.getWriteMode() == null ? "insert" : table.getWriteMode());
        values.put("column", getRdbmsWriteColumns(table));
        values.putAll(configService.getBizDateValues());
        fillConnectionPlaceholders(values, table);

        return new StringSubstitutor(values).replace(template);
    }

    private String resolveWriterTemplate(VwEtlTableWithSource table)
    {
        String key = null;
        if (table != null && table.getTargetId() != null) {
            key = etlTargetRepo.findById(table.getTargetId())
                .map(t -> t.getWriterTemplateKey())
                .orElse(null);
        }
        if (key == null || key.isBlank()) {
            key = DEFAULT_WRITER_TEMPLATE_KEY;
        }
        String template = dictService.getItemValue(5001, key, String.class);
        if (template == null || template.isBlank()) {
            throw new ApiException(400, "Writer template not found: " + key);
        }
        return template;
    }

    private String resolveTargetType(VwEtlTableWithSource table)
    {
        if (table == null || table.getTargetId() == null) {
            return "rdbms";
        }
        return etlTargetRepo.findById(table.getTargetId())
            .map(t -> t.getTargetType())
            .filter(t -> t != null && !t.isBlank())
            .map(t -> t.toUpperCase(Locale.ROOT))
            .orElse("RDBMS");
    }

    private String getRdbmsWriteColumns(VwEtlTableWithSource table)
    {
        List<EtlColumn> columnList = columnService.getColumns(table.getId());
        List<String> columns = new ArrayList<>();
        for (EtlColumn etlColumn : columnList) {
            String columnName = etlColumn.getColumnName();
            if (columnName == null || columnName.isBlank()) {
                continue;
            }
            if (columnName.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                columnName = columnName.substring(DELETED_PLACEHOLDER_PREFIX.length());
            }
            columns.add("\"" + columnName + "\"");
        }
        return String.join(", ", columns);
    }

    private void fillConnectionPlaceholders(Map<String, String> values, VwEtlTableWithSource table)
    {
        if (table == null || table.getTargetId() == null) {
            return;
        }
        etlTargetRepo.findById(table.getTargetId()).ifPresent(target -> {
            String config = target.getConnectConfig();
            if (config == null || config.isBlank()) {
                return;
            }
            try {
                JsonNode node = objectMapper.readTree(config);
                putIfPresent(values, "jdbcUrl", node, "url");
                putIfPresent(values, "username", node, "username");
                putIfPresent(values, "password", node, "password");
            }
            catch (Exception e) {
                log.warn("Invalid connect_config for target {}, ignore placeholders: {}", table.getTargetId(), e.getMessage());
            }
        });
    }

    private void putIfPresent(Map<String, String> values, String key, JsonNode node, String nodeKey)
    {
        JsonNode n = node.get(nodeKey);
        if (n != null && !n.isNull()) {
            String v = n.asText();
            if (v != null && !v.isBlank()) {
                values.put(key, v);
            }
        }
    }
}
