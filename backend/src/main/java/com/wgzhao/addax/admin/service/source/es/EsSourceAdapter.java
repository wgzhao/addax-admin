package com.wgzhao.addax.admin.service.source.es;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.RiskLogService;
import com.wgzhao.addax.admin.service.collection.IdentifierPolicy;
import com.wgzhao.addax.admin.service.source.ColumnTypeMapping;
import com.wgzhao.addax.admin.service.source.FilterFormat;
import com.wgzhao.addax.admin.service.source.ReaderJobContext;
import com.wgzhao.addax.admin.service.source.SchemaMode;
import com.wgzhao.addax.admin.service.source.SourceAdapter;
import com.wgzhao.addax.admin.service.source.SourceAdapterRegistry;
import com.wgzhao.addax.admin.service.source.SourceCapabilities;
import com.wgzhao.addax.admin.service.source.SourceDescriptor;
import com.wgzhao.addax.admin.service.source.SourceField;
import com.wgzhao.addax.admin.service.source.SourceRef;
import com.wgzhao.addax.admin.service.source.SourceSchema;
import com.wgzhao.addax.admin.service.source.SourceTableHints;
import com.wgzhao.addax.admin.common.TargetFamily;
import com.wgzhao.addax.admin.service.source.TypePurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch as a collection source. The Addax reader is positional and schema-less: columns are
 * top-level {@code _source} keys, there is no SQL alias, no splitPk/autoPk and no constant column.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EsSourceAdapter
    implements SourceAdapter
{
    public static final String ES_NAMESPACE = "indices";

    private static final String READER_PLUGIN = "elasticsearchreader";

    private final EsClientFactory clientFactory;
    private final EsSchemaInspector schemaInspector;
    private final EsTypeMapper typeMapper;
    private final EsFilterBuilder filterBuilder;
    private final DictService dictService;
    private final RiskLogService riskLogService;
    private final ObjectMapper objectMapper;

    @Override
    public String getType()
    {
        return SourceAdapterRegistry.ELASTICSEARCH_TYPE;
    }

    @Override
    public SourceDescriptor descriptor()
    {
        return new SourceDescriptor(getType(), "Elasticsearch", "从 Elasticsearch 索引读取文档",
            "ELASTICSEARCH", List.of("endpoints", "options", "username", "password"),
            capabilities(), defaultFilter(), readerPluginInstalled());
    }

    @Override
    public SourceCapabilities capabilities()
    {
        return new SourceCapabilities(FilterFormat.ES_DSL, false, false,
            false, false, false, true, true, READER_PLUGIN);
    }

    @Override
    public void normalize(EtlSource source)
    {
        EsConnectConfig config = EsConnectConfig.fromSource(SourceRef.of(source));
        config.validate(source.getUsername(), source.getPass());
        source.setDbType(SourceAdapterRegistry.ELASTICSEARCH_DB_TYPE);
        source.setUrl(config.canonicalUrl());
        source.setConnectConfig(config.toJson());
    }

    @Override
    public boolean testConnection(SourceRef source)
    {
        EsConnectConfig config = EsConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        return clientFactory.testConnection(source, config);
    }

    @Override
    public IdentifierPolicy identifierPolicy(SourceRef source, String database)
    {
        return IdentifierPolicy.EXACT;
    }

    @Override
    public List<String> listNamespaces(SourceRef source)
    {
        EsConnectConfig config = EsConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        clientFactory.ping(source, config);
        return List.of(ES_NAMESPACE);
    }

    @Override
    public List<TableMetaDto> listTables(SourceRef source, String database)
    {
        EsConnectConfig config = EsConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        return schemaInspector.listTables(source, database);
    }

    @Override
    public SourceSchema inspectSchema(SourceRef source, String database, String table, SchemaMode mode)
    {
        EsConnectConfig config = EsConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        return schemaInspector.inspect(source, database, table, mode);
    }

    @Override
    public SourceTableHints tableHints(SourceRef source, String database, String table)
    {
        return SourceTableHints.EMPTY;
    }

    @Override
    public ColumnTypeMapping mapColumnType(SourceField field, TargetFamily family,
        DbType targetDialect, TypePurpose purpose)
    {
        return typeMapper.map(field, family, targetDialect, purpose);
    }

    @Override
    public String defaultFilter()
    {
        return filterBuilder.defaultFilter();
    }

    @Override
    public String normalizeFilter(SourceRef source, String rawFilter)
    {
        return filterBuilder.normalize(source, rawFilter);
    }

    @Override
    public boolean isIncrementalFilter(String rawFilter)
    {
        return filterBuilder.isIncrementalFilter(rawFilter);
    }

    @Override
    public String incrementalColumn(String rawFilter)
    {
        return filterBuilder.incrementalColumn(rawFilter);
    }

    @Override
    public String buildIncrementalPredicate(VwEtlTableWithSource table,
        List<ColumnMappingService.ResolvedColumnMapping> mappings, String sourceColumn, Object maxValue)
    {
        return filterBuilder.buildIncrementalPredicate(table, mappings, sourceColumn, maxValue);
    }

    @Override
    public String combineFilters(String baseFilter, String extraFilter)
    {
        return filterBuilder.combine(baseFilter, extraFilter);
    }

    @Override
    public ObjectNode buildReaderJob(VwEtlTableWithSource table, ReaderJobContext context)
    {
        SourceRef source = SourceRef.of(table);
        EsConnectConfig config = EsConnectConfig.fromSource(source);
        config.validate(table.getUsername(), table.getPass());

        List<ColumnMappingService.ResolvedColumnMapping> mappings = context.mappings();
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
        {
            if (!mapping.sourceActive())
            {
                throw new ApiException(400, "Elasticsearch 源不支持 NULL 占位列，请先修复映射: "
                    + mapping.sourceColumnName());
            }
        }
        rejectBusinessDateConstant(table, mappings);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", READER_PLUGIN);
        ObjectNode parameter = root.putObject("parameter");
        parameter.put("endpoint", config.readerEndpoint());
        parameter.put("accessId", table.getUsername() == null ? "" : table.getUsername());
        parameter.put("accessKey", table.getPass() == null ? "" : table.getPass());
        parameter.put("index", table.getSourceTable());
        parameter.put("searchType", config.searchType());
        parameter.put("scroll", config.scroll());
        parameter.put("batchSize", config.batchSize());
        parameter.put("timeout", config.readerTimeoutSeconds());
        parameter.put("multiThread", config.multiThread());
        parameter.put("compression", config.compression());
        parameter.put("discovery", config.discovery());

        ArrayNode columns = parameter.putArray("column");
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
        {
            columns.add(mapping.sourceColumnName());
        }

        String filter = table.getFilter() == null ? "" : table.getFilter();
        // Date placeholders are part of the source filter language; the Addax runtime does not
        // substitute inside a JSON string.
        String substitutedFilter = new StringSubstitutor(context.dateValues()).replace(filter);
        String query;
        if (isIncrementalFilter(substitutedFilter))
        {
            String column = incrementalColumn(substitutedFilter);
            String incremental = null;
            if (context.lastEtlDate() != null)
            {
                ColumnMappingService.ResolvedColumnMapping mapping = mappings.stream()
                    .filter(item -> column.equals(item.sourceColumnName()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(400,
                        "Elasticsearch 增量字段 " + column + " 不在字段映射中，请先刷新字段"));
                Object maxValue = context.maxValueLookup().lookup(mapping.targetColumnName(), context.partValue());
                if (maxValue != null)
                {
                    incremental = buildIncrementalPredicate(table, mappings, column, maxValue);
                }
                else
                {
                    riskLogService.recordRisk("JobContentService", "WARN",
                        "无法获取目标表(" + table.getTargetDb() + "." + table.getTargetTable()
                            + ")最大值，Elasticsearch 采集将不附加增量条件", table.getId());
                }
            }
            query = incremental == null ? defaultFilter() : combineFilters(defaultFilter(), incremental);
        }
        else
        {
            query = normalizeFilter(source, substitutedFilter);
        }

        ArrayNode search = parameter.putArray("search");
        ObjectNode body = search.addObject();
        body.set("query", readQuery(query));
        body.put("size", config.batchSize());
        return root;
    }

    /**
     * elasticsearchreader has no constant-column syntax. A business date literal would make the
     * reader/writer positional lists disagree, so the only correct source for that column is a real
     * field from the index.
     */
    private void rejectBusinessDateConstant(VwEtlTableWithSource table,
        List<ColumnMappingService.ResolvedColumnMapping> mappings)
    {
        String businessDateColumn = table.getRdbmsBusinessDateColumn();
        if (businessDateColumn == null || businessDateColumn.isBlank())
        {
            return;
        }
        boolean mapped = mappings.stream()
            .anyMatch(mapping -> businessDateColumn.trim().equalsIgnoreCase(mapping.targetColumnName()));
        if (!mapped)
        {
            throw new ApiException(400, "Elasticsearch 采集源不能注入常量列；快照业务日期列 "
                + businessDateColumn + " 必须由索引中的源字段映射提供，或取消该目标列配置");
        }
    }

    private JsonNode readQuery(String query)
    {
        try
        {
            JsonNode node = objectMapper.readTree(query);
            if (node == null || !node.isObject())
            {
                throw new ApiException(400, "Elasticsearch 过滤条件必须是 JSON 对象");
            }
            return node;
        }
        catch (ApiException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApiException(400, "Elasticsearch 过滤条件不是合法的 JSON: " + e.getMessage());
        }
    }

    @Override
    public void mergeReaderPluginConfig(ObjectNode readerParameter, JsonNode customConfig,
        VwEtlTableWithSource table, Map<String, String> dateValues)
    {
        if (customConfig == null || customConfig.isNull())
        {
            return;
        }
        if (!customConfig.isObject())
        {
            log.warn("Elasticsearch reader 插件配置必须是 JSON 对象，tid={}", table.getId());
            return;
        }
        customConfig.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            switch (key)
            {
                case "search" -> mergeSearch(readerParameter, value, dateValues);
                case "query" -> mergeQuery(readerParameter, value, dateValues);
                case "column" -> log.warn(
                    "reader_plugin_config 覆盖 column 会破坏 Elasticsearch 位置映射，tid={}", table.getId());
                default -> readerParameter.set(key, value);
            }
        });
    }

    private void mergeSearch(ObjectNode readerParameter, JsonNode customSearch, Map<String, String> dateValues)
    {
        JsonNode search = customSearch;
        if (search != null && search.isTextual())
        {
            search = readQuery(search.asText());
        }
        if (search == null || !search.isObject())
        {
            throw new ApiException(400, "reader_plugin_config.search 必须是 JSON 对象（单个 search body）");
        }
        ObjectNode body = searchBody(readerParameter);
        JsonNode customQuery = search.get("query");
        if (customQuery != null)
        {
            mergeQuery(readerParameter, customQuery, dateValues);
        }
        var fields = search.properties().iterator();
        while (fields.hasNext())
        {
            Map.Entry<String, JsonNode> entry = fields.next();
            if ("query".equals(entry.getKey()))
            {
                continue;
            }
            body.set(entry.getKey(), substitute(entry.getValue(), dateValues));
        }
    }

    private void mergeQuery(ObjectNode readerParameter, JsonNode customQuery, Map<String, String> dateValues)
    {
        if (customQuery == null || customQuery.isNull())
        {
            return;
        }
        String extra = customQuery.isTextual()
            ? customQuery.asText() : customQuery.toString();
        extra = new StringSubstitutor(dateValues).replace(extra);
        JsonNode parsed = readQuery(extra);
        ObjectNode body = searchBody(readerParameter);
        String base = body.path("query").isMissingNode() || body.path("query").isNull()
            ? defaultFilter() : body.path("query").toString();
        try
        {
            body.set("query", objectMapper.readTree(combineFilters(base, parsed.toString())));
        }
        catch (Exception e)
        {
            throw new ApiException(400, "reader_plugin_config.query 无法合并: " + e.getMessage());
        }
    }

    private JsonNode substitute(JsonNode value, Map<String, String> dateValues)
    {
        if (value.isTextual())
        {
            return objectMapper.getNodeFactory().textNode(
                new StringSubstitutor(dateValues).replace(value.asText()));
        }
        return value;
    }

    private ObjectNode searchBody(ObjectNode readerParameter)
    {
        if (readerParameter.get("search") instanceof ArrayNode array
            && !array.isEmpty() && array.get(0) instanceof ObjectNode body)
        {
            return body;
        }
        throw new ApiException(500, "任务模板缺少 Elasticsearch search body");
    }

    @Override
    public String readerPluginName(SourceRef source)
    {
        return READER_PLUGIN;
    }

    private Boolean readerPluginInstalled()
    {
        try
        {
            Path plugin = Path.of(dictService.getAddaxHome(), "plugin", "reader", READER_PLUGIN);
            return Files.isDirectory(plugin);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }
}
