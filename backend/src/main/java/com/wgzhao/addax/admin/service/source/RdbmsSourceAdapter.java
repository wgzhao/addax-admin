package com.wgzhao.addax.admin.service.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.RiskLogService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.collection.IdentifierPolicy;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.RdbmsDdlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The existing JDBC source pipeline, extracted behind {@link SourceAdapter} without changing behavior.
 * Every method here mirrors a method that previously lived in SourceController, SourceService,
 * CollectionScopeService, ColumnService, CollectionSchemaPreviewService or JobContentService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RdbmsSourceAdapter implements SourceAdapter
{
    private final SystemConfigService configService;
    private final DictService dictService;
    private final RiskLogService riskLogService;
    private final ObjectMapper objectMapper;

    @Override
    public String getType()
    {
        return SourceAdapterRegistry.RDBMS_TYPE;
    }

    @Override
    public SourceDescriptor descriptor()
    {
        return new SourceDescriptor(getType(), "关系型数据库", "通过 JDBC 访问的关系型数据库",
            "JDBC_URL", List.of("url", "username", "password"), capabilities(), defaultFilter(), null);
    }

    @Override
    public SourceCapabilities capabilities()
    {
        return new SourceCapabilities(FilterFormat.SQL, true, true, true, true,
            true, false, false, null);
    }

    @Override
    public void normalize(EtlSource source)
    {
        String url = source.getUrl();
        if (url == null || url.isBlank())
        {
            throw new ApiException(400, "关系型数据库采集源需要 JDBC 连接地址");
        }
        if (!url.trim().startsWith("jdbc:"))
        {
            throw new ApiException(400, "关系型数据库采集源的连接地址必须以 jdbc: 开头");
        }
        source.setDbType(DbUtil.getDbType(url).getValue());
        source.setConnectConfig(null);
    }

    @Override
    public boolean testConnection(SourceRef source)
    {
        return DbUtil.testConnection(source.url(), source.username(), source.password());
    }

    @Override
    public IdentifierPolicy identifierPolicy(SourceRef source, String database)
    {
        try (Connection connection = DbUtil.getConnection(source.url(), source.username(), source.password()))
        {
            return IdentifierPolicy.forSource(connection, database);
        }
        catch (SQLException e)
        {
            log.debug("Failed to resolve identifier policy for source {}: {}", source.id(), e.getMessage());
            return IdentifierPolicy.EXACT;
        }
    }

    @Override
    public List<String> listNamespaces(SourceRef source)
    {
        List<String> result = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection(source.url(), source.username(), source.password()))
        {
            if (connection == null)
            {
                throw new SourceAccessException("无法连接采集源数据库", true);
            }
            try (ResultSet catalogs = connection.getMetaData().getCatalogs())
            {
                while (catalogs.next())
                {
                    result.add(catalogs.getString(1));
                }
            }
        }
        catch (SQLException e)
        {
            throw new SourceAccessException("读取数据库列表失败: " + e.getMessage(), false, e);
        }
        return result;
    }

    @Override
    public List<TableMetaDto> listTables(SourceRef source, String database)
    {
        try (Connection connection = DbUtil.getConnection(source.url(), source.username(), source.password()))
        {
            if (connection == null)
            {
                throw new SourceAccessException("无法连接采集源数据库", true);
            }
            return DbUtil.getAllTableMetas(connection, database);
        }
        catch (SQLException e)
        {
            throw new SourceAccessException("读取源表列表失败: " + e.getMessage(), false, e);
        }
    }

    @Override
    public SourceSchema inspectSchema(SourceRef source, String database, String table, SchemaMode mode)
    {
        boolean connected = false;
        try (Connection connection = mode == SchemaMode.COLUMN_REFRESH
            ? DbUtil.getConnectionWithRetry(source.url(), source.username(), source.password())
            : DbUtil.getConnection(source.url(), source.username(), source.password()))
        {
            if (connection == null)
            {
                throw new SourceAccessException("无法连接采集源数据库", true);
            }
            connected = true;
            List<SourceField> fields = mode == SchemaMode.PREVIEW
                ? previewColumns(connection, source, database, table)
                : refreshColumns(connection, source, database, table);
            return new SourceSchema(fields, List.of(), 0, false);
        }
        catch (SourceAccessException e)
        {
            throw e;
        }
        catch (SQLException e)
        {
            // connected means the metadata query failed after a successful login: that is a schema
            // problem, not a source outage, and the caller reports it differently.
            throw new SourceAccessException("读取源表元数据失败: " + DbUtil.describeError(e), !connected, e);
        }
    }

    private List<SourceField> previewColumns(Connection connection, SourceRef source, String db, String tableName)
        throws SQLException
    {
        DbType dialect = DbUtil.getDbType(source.url());
        String catalog = switch (dialect)
        {
            case MYSQL, SQLSERVER -> db;
            default -> null;
        };
        String schema = switch (dialect)
        {
            case POSTGRESQL ->
            {
                String catalogName = connection.getCatalog();
                yield db != null && !db.equalsIgnoreCase(catalogName) ? db : "public";
            }
            case SQLSERVER -> "dbo";
            default -> null;
        };
        Set<String> primaryKeys = new HashSet<>();
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
        return readColumns(connection.getMetaData().getColumns(catalog, schema, tableName, null),
            primaryKeys, false);
    }

    private List<SourceField> refreshColumns(Connection connection, SourceRef source, String db, String tableName)
        throws SQLException
    {
        String catalog = connection.getCatalog();
        catalog = catalog == null || catalog.isEmpty() ? db : catalog;
        String schema = connection.getSchema();
        if (source.url() != null && source.url().startsWith("jdbc:mysql://"))
        {
            catalog = db;
            schema = null;
        }
        return readColumns(connection.getMetaData().getColumns(catalog, schema, tableName, null),
            Set.of(), false);
    }

    private List<SourceField> readColumns(ResultSet rs, Set<String> primaryKeys, boolean lowerCaseSourceTypes)
        throws SQLException
    {
        List<SourceField> fields = new ArrayList<>();
        try (rs)
        {
            while (rs.next())
            {
                String name = rs.getString("COLUMN_NAME");
                if (name == null)
                {
                    continue;
                }
                String typeName = rs.getString("TYPE_NAME");
                String sourceType = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
                int size = rs.getInt("COLUMN_SIZE");
                fields.add(new SourceField(
                    name,
                    sourceType,
                    typeName,
                    size,
                    size,
                    rs.getInt("DECIMAL_DIGITS"),
                    rs.getString("REMARKS"),
                    rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                    primaryKeys.contains(name.toLowerCase(Locale.ROOT)),
                    name,
                    null));
            }
        }
        return fields;
    }

    @Override
    public SourceTableHints tableHints(SourceRef source, String database, String table)
    {
        DbType dialect = DbUtil.getDbType(source.url());
        String catalog = (dialect == DbType.MYSQL || dialect == DbType.SQLSERVER)
            && database != null && !database.isBlank() ? database : null;
        try (Connection connection = DbUtil.getConnection(source.url(), source.username(), source.password()))
        {
            if (connection == null)
            {
                return SourceTableHints.EMPTY;
            }
            List<String> pk = new ArrayList<>();
            try (ResultSet rs = connection.getMetaData().getPrimaryKeys(catalog, null, table))
            {
                while (rs.next())
                {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null && !name.isBlank())
                    {
                        pk.add(name);
                    }
                }
            }
            return new SourceTableHints(pk, Set.of(), Map.of());
        }
        catch (SQLException e)
        {
            log.warn("Failed to read source table hints for {}.{}: {}", database, table, e.getMessage());
            return SourceTableHints.EMPTY;
        }
    }

    @Override
    public ColumnTypeMapping mapColumnType(SourceField field, TargetFamily family,
        DbType targetDialect, TypePurpose purpose)
    {
        String sourceType = field.sourceType() == null ? "" : field.sourceType().toLowerCase(Locale.ROOT);
        if (family == TargetFamily.HDFS)
        {
            Map<String, String> mapping = dictService.getHiveTypeMapping();
            String mapped = mapping.getOrDefault(sourceType, "string");
            if ("decimal".equalsIgnoreCase(mapped))
            {
                int precision = field.dataLength();
                int scale = field.dataScale() == null ? 0 : field.dataScale();
                mapped = "decimal(" + precision + "," + scale + ")";
            }
            return new ColumnTypeMapping(mapped, mapped);
        }
        if (purpose == TypePurpose.ETL_COLUMN)
        {
            // Historical ColumnService behavior for an RDBMS target: keep the source type verbatim.
            return new ColumnTypeMapping(sourceType, sourceType);
        }
        int precision = field.dataPrecision() == null ? field.dataLength() : field.dataPrecision();
        int scale = field.dataScale() == null ? 0 : field.dataScale();
        String rendered = RdbmsDdlUtil.renderType(
            targetDialect == null ? DbType.RDBMS : targetDialect,
            sourceType, field.dataLength(), precision, scale);
        return new ColumnTypeMapping(rendered, rendered);
    }

    @Override
    public String defaultFilter()
    {
        return "1=1";
    }

    @Override
    public String normalizeFilter(SourceRef source, String rawFilter)
    {
        return rawFilter == null || rawFilter.isBlank() ? defaultFilter() : rawFilter;
    }

    @Override
    public boolean isIncrementalFilter(String rawFilter)
    {
        return rawFilter != null && rawFilter.startsWith(Constants.SPECIAL_FILTER_PLACEHOLDER);
    }

    @Override
    public String incrementalColumn(String rawFilter)
    {
        return rawFilter.substring(Constants.SPECIAL_FILTER_PLACEHOLDER.length());
    }

    @Override
    public String buildIncrementalPredicate(VwEtlTableWithSource table,
        List<ColumnMappingService.ResolvedColumnMapping> mappings, String sourceColumn, Object maxValue)
    {
        String quoted = DbUtil.quoteIfNeeded(sourceColumn, DbUtil.getDbType(table.getUrl()));
        try
        {
            Long.parseLong(maxValue.toString());
            return quoted + " > " + maxValue;
        }
        catch (NumberFormatException e)
        {
            return quoted + " > '" + maxValue + "'";
        }
    }

    @Override
    public String combineFilters(String baseFilter, String extraFilter)
    {
        String base = normalizeFilter(null, baseFilter);
        String extra = normalizeFilter(null, extraFilter);
        if ("1=1".equals(base))
        {
            return extra;
        }
        if ("1=1".equals(extra))
        {
            return base;
        }
        return "(" + base + ") AND (" + extra + ")";
    }

    @Override
    public ObjectNode buildReaderJob(VwEtlTableWithSource table, ReaderJobContext context)
    {
        String template = configService.getRdbmsReaderTemplate();
        Map<String, String> values = new HashMap<>();

        String dbTypeValue = table.getDbType();
        if (dbTypeValue == null || dbTypeValue.isBlank())
        {
            dbTypeValue = DbUtil.getDbType(table.getUrl()).getValue();
        }
        values.put("name", readerPluginName(SourceRef.of(table)));
        values.put("username", nullSafe(table.getUsername()));
        values.put("password", nullSafe(table.getPass()));
        values.put("jdbcUrl", nullSafe(table.getUrl()));

        String filter = table.getFilter() == null ? "" : table.getFilter();
        String where;
        if (isIncrementalFilter(filter))
        {
            String column = incrementalColumn(filter);
            Object maxValue = context.lastEtlDate() == null
                ? null
                : context.maxValueLookup().lookup(column, context.partValue());
            if (maxValue == null)
            {
                if (context.lastEtlDate() != null)
                {
                    riskLogService.recordRisk("JobContentService", "WARN",
                        "无法获取目标表(" + table.getTargetDb() + "." + table.getTargetTable()
                            + ")最大值，使用默认过滤 1=1", table.getId());
                }
                where = "1=1";
            }
            else
            {
                where = buildIncrementalPredicate(table, context.mappings(), column, maxValue);
            }
        }
        else
        {
            where = filter;
        }
        values.put("where", where);
        values.put("autoPk", String.valueOf(table.getAutoPk()));
        values.put("splitPk", table.getSplitPk() == null ? "" : table.getSplitPk());
        values.put("fetchSize", "20480");
        values.putAll(context.dateValues());

        DbType dbType = DbUtil.getDbType(table.getUrl());
        String sourceTable = table.getSourceTable();
        if (sourceTable != null && sourceTable.contains("${"))
        {
            sourceTable = new StringSubstitutor(context.dateValues()).replace(sourceTable);
        }
        values.put("table", DbUtil.sourceTableRef(dbType, table.getSourceDb(), sourceTable));

        List<String> srcColumns = new ArrayList<>();
        for (ColumnMappingService.ResolvedColumnMapping mapping : context.mappings())
        {
            String sourceName = mapping.sourceColumnName();
            String targetName = mapping.targetColumnName();
            String expression;
            if (!mapping.sourceActive())
            {
                expression = "NULL as " + outputAlias(targetName);
            }
            else if (sourceName.equalsIgnoreCase(targetName))
            {
                expression = DbUtil.quoteIfNeeded(sourceName, dbType);
            }
            else
            {
                expression = DbUtil.quoteIfNeeded(sourceName, dbType) + " as " + outputAlias(targetName);
            }
            srcColumns.add("\"" + RdbmsDdlUtil.jsonEscape(expression) + "\"");
        }
        String bizDateColumn = table.getRdbmsBusinessDateColumn();
        if (bizDateColumn != null && !bizDateColumn.isBlank()
            && context.mappings().stream().noneMatch(mapping ->
                bizDateColumn.trim().equalsIgnoreCase(mapping.targetColumnName())))
        {
            String bizDateValue = context.dateValues().getOrDefault("biz_date_short", "");
            String expression = "'" + bizDateValue + "' as " + outputAlias(bizDateColumn.trim());
            srcColumns.add("\"" + RdbmsDdlUtil.jsonEscape(expression) + "\"");
        }
        values.put("column", String.join(", ", srcColumns));

        String rendered = new StringSubstitutor(values).replace(template);
        try
        {
            return (ObjectNode) objectMapper.readTree(rendered);
        }
        catch (Exception e)
        {
            throw new ApiException(500, "RDBMS reader 模板无效，请检查系统设置中的 rR 模板: " + e.getMessage());
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
            log.warn("reader 插件配置必须是 JSON 对象，tid={}", table.getId());
            return;
        }
        customConfig.fields().forEachRemaining(entry ->
            readerParameter.set(entry.getKey(), entry.getValue()));
    }

    @Override
    public String readerPluginName(SourceRef source)
    {
        String dbType = source.dbType();
        if (dbType == null || dbType.isBlank())
        {
            dbType = DbUtil.getDbType(source.url()).getValue();
        }
        return dbType + "reader";
    }

    private static String outputAlias(String name)
    {
        if (name != null && name.matches("[A-Za-z_][A-Za-z0-9_$]*"))
        {
            return name;
        }
        return "\"" + (name == null ? "" : name.replace("\"", "\"\"")) + "\"";
    }

    private static String nullSafe(String value)
    {
        return value == null ? "" : value;
    }
}
