package com.wgzhao.addax.admin.service.source.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.RiskLogService;
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
import com.wgzhao.addax.admin.service.collection.IdentifierPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MongoDB as a collection source. The Addax reader is positional and schema-less: columns are field
 * paths (or quoted constants), there is no SQL alias, no splitPk/autoPk and no NULL literal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoSourceAdapter implements SourceAdapter
{
    private static final String READER_PLUGIN = "mongodbreader";
    private static final int FETCH_SIZE = 20480;

    private final MongoClientFactory clientFactory;
    private final MongoSchemaInspector schemaInspector;
    private final MongoTypeMapper typeMapper;
    private final MongoFilterBuilder filterBuilder;
    private final DictService dictService;
    private final RiskLogService riskLogService;
    private final ObjectMapper objectMapper;

    @Override
    public String getType()
    {
        return SourceAdapterRegistry.MONGODB_TYPE;
    }

    @Override
    public SourceDescriptor descriptor()
    {
        return new SourceDescriptor(getType(), "MongoDB", "从 MongoDB 集合读取文档",
            "MONGODB", List.of("address", "database", "authDb", "options", "username", "password"),
            capabilities(), defaultFilter(), readerPluginInstalled());
    }

    @Override
    public SourceCapabilities capabilities()
    {
        return new SourceCapabilities(FilterFormat.MONGO_QUERY, false, false,
            false, true, false, true, true, READER_PLUGIN);
    }

    @Override
    public void normalize(EtlSource source)
    {
        MongoConnectConfig config = MongoConnectConfig.fromSource(SourceRef.of(source));
        config.validate(source.getUsername(), source.getPass());
        source.setDbType(SourceAdapterRegistry.MONGODB_DB_TYPE);
        source.setUrl(config.canonicalUrl());
        source.setConnectConfig(config.toJson());
    }

    @Override
    public boolean testConnection(SourceRef source)
    {
        MongoConnectConfig config = MongoConnectConfig.fromSource(source);
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
        MongoConnectConfig config = MongoConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        try (MongoClient client = clientFactory.create(source, config))
        {
            List<String> names = new ArrayList<>();
            client.listDatabaseNames().into(names);
            names.sort(Comparator.naturalOrder());
            return names;
        }
        catch (RuntimeException e)
        {
            log.warn("Failed to list MongoDB databases for source {}: {}", source.id(), e.getMessage());
            return List.of(config.database());
        }
    }

    @Override
    public List<TableMetaDto> listTables(SourceRef source, String database)
    {
        MongoConnectConfig config = MongoConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        String dbName = database == null || database.isBlank() ? config.database() : database;
        try (MongoClient client = clientFactory.create(source, config))
        {
            List<String> names = new ArrayList<>();
            client.getDatabase(dbName).listCollectionNames().into(names);
            names.removeIf(name -> name.startsWith("system."));
            names.sort(Comparator.naturalOrder());
            List<TableMetaDto> result = new ArrayList<>(names.size());
            for (String name : names)
            {
                Long count = null;
                try
                {
                    count = client.getDatabase(dbName).getCollection(name).estimatedDocumentCount();
                }
                catch (RuntimeException e)
                {
                    log.debug("estimatedDocumentCount failed for {}.{}: {}", dbName, name, e.getMessage());
                }
                result.add(new TableMetaDto(name, "", count));
            }
            return result;
        }
        catch (RuntimeException e)
        {
            throw new com.wgzhao.addax.admin.service.source.SourceAccessException(
                "读取 MongoDB 集合列表失败: " + e.getMessage(), true, e);
        }
    }

    @Override
    public SourceSchema inspectSchema(SourceRef source, String database, String table, SchemaMode mode)
    {
        MongoConnectConfig config = MongoConnectConfig.fromSource(source);
        String dbName = database == null || database.isBlank() ? config.database() : database;
        return schemaInspector.inspect(source, dbName, table, mode);
    }

    @Override
    public SourceTableHints tableHints(SourceRef source, String database, String table)
    {
        MongoConnectConfig config = MongoConnectConfig.fromSource(source);
        String dbName = database == null || database.isBlank() ? config.database() : database;
        return schemaInspector.tableHints(source, dbName, table);
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
        MongoConnectConfig config = MongoConnectConfig.fromSource(source);
        config.validate(table.getUsername(), table.getPass());

        List<ColumnMappingService.ResolvedColumnMapping> mappings = context.mappings();
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
        {
            if (!mapping.sourceActive())
            {
                throw new ApiException(400, "MongoDB 源不支持 NULL 占位列，请先修复映射: "
                    + mapping.sourceColumnName());
            }
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", READER_PLUGIN);
        ObjectNode parameter = root.putObject("parameter");
        parameter.put("username", table.getUsername() == null ? "" : table.getUsername());
        parameter.put("password", table.getPass() == null ? "" : table.getPass());
        ObjectNode connection = parameter.putObject("connection");
        ArrayNode address = connection.putArray("address");
        config.readerAddresses().forEach(address::add);
        String database = table.getSourceDb() == null || table.getSourceDb().isBlank()
            ? config.database() : table.getSourceDb();
        connection.put("database", database);
        connection.put("authDb", config.authDb());
        connection.put("collection", table.getSourceTable());

        ArrayNode columns = parameter.putArray("column");
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
        {
            columns.add(mapping.sourceColumnName());
        }
        String bizDateColumn = table.getRdbmsBusinessDateColumn();
        if (bizDateColumn != null && !bizDateColumn.isBlank()
            && mappings.stream().noneMatch(mapping ->
                bizDateColumn.trim().equalsIgnoreCase(mapping.targetColumnName())))
        {
            String bizDateValue = context.dateValues().getOrDefault("biz_date_short", "");
            columns.add("'" + bizDateValue + "'");
        }

        String filter = table.getFilter() == null ? "" : table.getFilter();
        // Date placeholders are part of the source filter language; resolve them before parsing the
        // query document. The Addax runtime does not substitute inside a JSON string.
        String substitutedFilter = new org.apache.commons.text.StringSubstitutor(context.dateValues())
            .replace(filter);
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
                        "MongoDB 增量字段 " + column + " 不在字段映射中，请先刷新字段"));
                Object maxValue = context.maxValueLookup().lookup(mapping.targetColumnName(), context.partValue());
                if (maxValue != null)
                {
                    incremental = buildIncrementalPredicate(table, mappings, column, maxValue);
                }
                else
                {
                    riskLogService.recordRisk("JobContentService", "WARN",
                        "无法获取目标表(" + table.getTargetDb() + "." + table.getTargetTable()
                            + ")最大值，MongoDB 采集将不附加增量条件", table.getId());
                }
            }
            query = incremental == null ? defaultFilter() : combineFilters(defaultFilter(), incremental);
        }
        else
        {
            query = normalizeFilter(source, substitutedFilter);
        }
        parameter.put("query", query);
        parameter.put("fetchSize", FETCH_SIZE);
        return root;
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
            log.warn("MongoDB reader 插件配置必须是 JSON 对象，tid={}", table.getId());
            return;
        }
        customConfig.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if ("query".equals(key))
            {
                mergeQuery(readerParameter, value, table, dateValues);
            }
            else if ("connection".equals(key) && value.isObject()
                && readerParameter.get("connection") instanceof ObjectNode current)
            {
                value.fields().forEachRemaining(field -> current.set(field.getKey(), field.getValue()));
            }
            else
            {
                if ("column".equals(key))
                {
                    log.warn("reader_plugin_config 覆盖 column 会破坏 MongoDB 位置映射，tid={}", table.getId());
                }
                readerParameter.set(key, value);
            }
        });
    }

    private void mergeQuery(ObjectNode readerParameter, JsonNode customQuery, VwEtlTableWithSource table,
        Map<String, String> dateValues)
    {
        if (customQuery == null || customQuery.isNull())
        {
            return;
        }
        try
        {
            String base = readerParameter.path("query").asText("{}");
            String extra = customQuery.isTextual()
                ? customQuery.asText() : objectMapper.writeValueAsString(customQuery);
            extra = new org.apache.commons.text.StringSubstitutor(dateValues).replace(extra);
            // Parse now so an invalid custom query fails at refresh time, not inside Addax.
            Document.parse(extra);
            readerParameter.put("query", combineFilters(base, extra));
        }
        catch (ApiException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApiException(400, "reader_plugin_config.query 不是合法的 MongoDB 查询: "
                + e.getMessage());
        }
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
