package com.wgzhao.addax.admin.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.common.TargetFamily;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.target.DorisTargetSettings;
import com.wgzhao.addax.admin.service.target.JdbcTargetSupport;
import com.wgzhao.addax.admin.service.target.JdbcTargetSupport.ColumnMeta;
import com.wgzhao.addax.admin.service.target.JdbcTargetSupport.ConnectInfo;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.RdbmsDdlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Doris target adapter.
 *
 * <p>Doris is not an RDBMS dialect: the MySQL protocol is only used for metadata and preSql, while
 * the data path is {@code doriswriter}'s HTTP Stream Load. It therefore has its own adapter type and
 * writer template key ({@code wD}) while keeping {@link TargetFamily#RDBMS}: the table contract,
 * snapshot business-date column, type mapping and R2R job template are the RDBMS ones.</p>
 *
 * <p>Phase 1 supports externally managed target tables only. Platform-managed DDL needs Doris key
 * models, distribution and partition semantics, which are a separate (and product-visible) decision;
 * the adapter fails explicitly instead of emitting MySQL DDL that Doris would reject.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DorisTargetAdapter
    implements TargetAdapter
{
    public static final String TYPE = "DORIS";
    public static final String WRITER_TEMPLATE_KEY = "wD";
    public static final String WRITER_PLUGIN = "doriswriter";

    private final EtlTargetRepo etlTargetRepo;
    private final DictService dictService;
    private final SystemConfigService configService;
    private final ObjectMapper objectMapper;
    private final JdbcTargetSupport jdbcTargetSupport;

    private final AtomicBoolean pluginMissingWarned = new AtomicBoolean();

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public TargetFamily getFamily()
    {
        return TargetFamily.RDBMS;
    }

    /**
     * Doris DDL is Phase 2. Until key model, distribution and partition policy are explicit, only an
     * externally created table can be written safely.
     */
    @Override
    public boolean supportsManagedSchema()
    {
        return false;
    }

    @Override
    public void validateForSave(EtlTarget target)
    {
        settings(target).requireWritable(target == null ? null : target.getName());
        requirePluginInstalled(target);
    }

    @Override
    public void testConnection(EtlTarget target)
    {
        DorisTargetSettings settings = settings(target);
        // Connectivity is about the JDBC endpoint; loadUrl is a separate HTTP path and is validated
        // when the target is saved and when the writer job is built.
        settings.requireJdbcConfigured(target == null ? null : target.getName());
        if (!DbUtil.testConnection(settings.url(), settings.username(), settings.password()))
        {
            throw new ApiException(400, "Failed to connect Doris FE");
        }
    }

    @Override
    public boolean hasDateDimension(VwEtlTableWithSource table)
    {
        return table != null && normalizeColumn(table.getRdbmsBusinessDateColumn()) != null;
    }

    @Override
    public boolean createOrUpdateTable(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null)
        {
            return true;
        }
        SchemaManagementMode mode = etlTable.getSchemaManagementMode() == null
            ? SchemaManagementMode.MANAGED : etlTable.getSchemaManagementMode();
        if (mode != SchemaManagementMode.EXTERNAL)
        {
            throw new ApiException(400, "Doris 目标端当前仅支持外部表（EXTERNAL）：目标表需由数仓/DBA 创建，"
                + "平台只做契约校验与写入；平台建表将在后续版本提供");
        }
        // External mode never creates or alters target structure; the contract check owns validation.
        return true;
    }

    @Override
    public TargetTableMetadata inspectTable(VwEtlTableWithSource etlTable)
    {
        if (etlTable.getTargetDb() == null || etlTable.getTargetDb().isBlank()
            || etlTable.getTargetTable() == null || etlTable.getTargetTable().isBlank())
        {
            throw new ApiException(400, "Doris 目标端必须指定目标库和目标表");
        }
        ConnectInfo conn = jdbcTargetSupport.resolveTargetConnection(etlTable);
        if (conn == null)
        {
            throw new ApiException(400, "Doris 目标端连接未配置，无法读取目标表元数据");
        }
        try (Connection connection = DbUtil.getConnection(conn.url(), conn.username(), conn.password()))
        {
            if (connection == null)
            {
                throw new ApiException(400, "无法连接 Doris FE");
            }
            if (!tableExists(connection, etlTable.getTargetDb(), etlTable.getTargetTable()))
            {
                return TargetTableMetadata.missing();
            }
            Map<String, ColumnMeta> described = describeColumns(
                connection, etlTable.getTargetDb(), etlTable.getTargetTable());
            List<TargetTableMetadata.TargetColumn> columns = new ArrayList<>();
            int ordinal = 0;
            for (ColumnMeta meta : described.values())
            {
                columns.add(new TargetTableMetadata.TargetColumn(
                    meta.name(), meta.typeName(), meta.size(), meta.scale(), meta.nullable(),
                    meta.defaultValue(), meta.autoIncrement(), ++ordinal));
            }
            // Doris key models (DUPLICATE/UNIQUE/AGGREGATE) are not exposed as JDBC primary/unique
            // constraints. The external contract does not require them; an empty set is honest.
            return new TargetTableMetadata(true, columns, List.of(), Set.of(), Set.of());
        }
        catch (SQLException e)
        {
            throw new ApiException(400, "读取 Doris 目标表元数据失败: " + e.getMessage());
        }
    }

    @Override
    public Object getMaxValue(VwEtlTableWithSource table, String columnName, String partValue)
    {
        if (table == null || columnName == null || columnName.isBlank()
            || table.getTargetDb() == null || table.getTargetDb().isBlank())
        {
            return null;
        }
        ConnectInfo conn = jdbcTargetSupport.resolveTargetConnection(table);
        if (conn == null)
        {
            return null;
        }
        String tableRef = RdbmsDdlUtil.tableRef(DbType.MYSQL, table.getTargetDb(), table.getTargetTable());
        String businessDateColumn = table.getRdbmsBusinessDateColumn();
        boolean scopeByBusinessDate = businessDateColumn != null && !businessDateColumn.isBlank()
            && partValue != null && !partValue.isBlank();
        String where = scopeByBusinessDate
            ? " WHERE " + RdbmsDdlUtil.quoteIdent(DbType.MYSQL, businessDateColumn.trim()) + " = ?"
            : "";
        String sql = String.format("SELECT MAX(%s) FROM %s%s",
            RdbmsDdlUtil.quoteIdent(DbType.MYSQL, columnName), tableRef, where);
        return jdbcTargetSupport.maxValue(conn, sql, scopeByBusinessDate ? partValue : null);
    }

    @Override
    public boolean prepareBeforeRun(long taskId, VwEtlTableWithSource table, LocalDate bizDate)
    {
        // External Doris tables own their partitions; dynamic partition handles the date dimension.
        return true;
    }

    @Override
    public String buildWriterJob(VwEtlTableWithSource table)
    {
        return buildWriterJobForDate(table, null);
    }

    @Override
    public String buildWriterJobForDate(VwEtlTableWithSource table, LocalDate targetDate)
    {
        EtlTarget target = table == null || table.getTargetId() == null
            ? null
            : etlTargetRepo.findById(table.getTargetId()).orElse(null);
        if (target == null)
        {
            throw new ApiException(400, "采集表 " + (table == null ? null : table.getId())
                + " 未关联 Doris 目标端");
        }
        DorisTargetSettings settings = settings(target);
        settings.requireWritable(target.getName());
        warnIfPluginMissing();
        if (table.getTargetDb() == null || table.getTargetDb().isBlank())
        {
            throw new ApiException(400, "Doris 目标端必须指定目标库: 采集表 " + table.getId());
        }
        if (table.getTargetTable() == null || table.getTargetTable().isBlank())
        {
            throw new ApiException(400, "Doris 目标端必须指定目标表: 采集表 " + table.getId());
        }

        String template = dictService.getWriterTemplate(WRITER_TEMPLATE_KEY);
        if (template == null || template.isBlank())
        {
            throw new ApiException(400, "Doris writer 模板未配置: " + WRITER_TEMPLATE_KEY);
        }

        Map<String, String> values = new HashMap<>();
        values.put("username", jsonEscape(settings.username()));
        values.put("password", jsonEscape(settings.password()));
        values.put("jdbcUrl", jsonEscape(settings.url()));
        values.put("targetDb", jsonEscape(table.getTargetDb()));
        values.put("targetTable", jsonEscape(table.getTargetTable()));
        values.put("loadUrl", settings.loadUrl().stream()
            .map(endpoint -> '"' + jsonEscape(endpoint) + '"')
            .collect(Collectors.joining(", ")));
        values.put("batchSize", Long.toString(settings.batchSize()));
        values.put("flushInterval", Long.toString(settings.flushInterval()));
        values.put("flushQueueLength", Integer.toString(settings.flushQueueLength()));
        values.put("loadProps", settings.loadProps().toString());
        values.put("column", writerColumns(table));
        values.putAll(targetDate != null
            ? configService.getBizDateValuesForDate(targetDate)
            : configService.getBizDateValues());

        return new StringSubstitutor(values).replace(template);
    }

    /**
     * The Stream Load {@code columns} header is a comma-separated list of bare names; unlike the JDBC
     * writers it cannot carry quoted identifiers.
     */
    private String writerColumns(VwEtlTableWithSource table)
    {
        List<String> columns = new ArrayList<>();
        for (String columnName : jdbcTargetSupport.writeColumns(table))
        {
            if (columnName.contains(",") || columnName.contains("\n") || columnName.contains("\r"))
            {
                throw new ApiException(400, "Doris Stream Load 列名不能包含逗号或换行: " + columnName);
            }
            columns.add('"' + jsonEscape(columnName) + '"');
        }
        return String.join(", ", columns);
    }

    private boolean tableExists(Connection connection, String db, String table) throws SQLException
    {
        if (db == null || db.isBlank() || table == null || table.isBlank())
        {
            return false;
        }
        String sql = "select count(*) from information_schema.tables "
            + "where table_schema = ? and table_name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql))
        {
            stmt.setString(1, db);
            stmt.setString(2, table);
            try (ResultSet rs = stmt.executeQuery())
            {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Doris information_schema is more precise than the MySQL-Compatibility JDBC metadata for types
     * such as {@code largeint} or {@code datetimev2}, which the contract comparison needs verbatim.
     */
    private Map<String, ColumnMeta> describeColumns(Connection connection, String db, String table)
        throws SQLException
    {
        String sql = "select column_name, column_type, data_type, character_maximum_length, "
            + "numeric_precision, numeric_scale, is_nullable, column_default, extra "
            + "from information_schema.columns "
            + "where table_schema = ? and table_name = ? order by ordinal_position";
        Map<String, ColumnMeta> columns = new LinkedHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql))
        {
            stmt.setString(1, db);
            stmt.setString(2, table);
            try (ResultSet rs = stmt.executeQuery())
            {
                while (rs.next())
                {
                    String name = rs.getString("column_name");
                    if (name == null)
                    {
                        continue;
                    }
                    String typeName = rs.getString("column_type");
                    if (typeName == null || typeName.isBlank())
                    {
                        typeName = rs.getString("data_type");
                    }
                    long charLength = rs.getLong("character_maximum_length");
                    long precision = rs.getLong("numeric_precision");
                    int size = charLength > 0 ? (int) charLength : (int) Math.max(precision, 0);
                    int scale = Math.max(rs.getInt("numeric_scale"), 0);
                    String defaultValue = rs.getString("column_default");
                    if (defaultValue != null && "NULL".equalsIgnoreCase(defaultValue.trim()))
                    {
                        defaultValue = null;
                    }
                    String extra = rs.getString("extra");
                    boolean autoIncrement = extra != null
                        && extra.toLowerCase(Locale.ROOT).contains("auto_increment");
                    columns.put(name.toLowerCase(Locale.ROOT), new ColumnMeta(
                        name, typeName, size, scale,
                        !"NO".equalsIgnoreCase(rs.getString("is_nullable")),
                        defaultValue, autoIncrement));
                }
            }
        }
        return columns;
    }

    private DorisTargetSettings settings(EtlTarget target)
    {
        return DorisTargetSettings.from(target, objectMapper);
    }

    private void requirePluginInstalled(EtlTarget target)
    {
        Path dir = pluginDir();
        if (dir == null)
        {
            // ADDAX_HOME is not visible from this host; do not block configuration on a directory
            // we cannot inspect. The writer-job warning still covers the run-time failure.
            return;
        }
        if (!Files.isDirectory(dir))
        {
            throw new ApiException(400, "addax writer plugin '" + WRITER_PLUGIN + "' is not installed at "
                + dir + "; install it under ${ADDAX_HOME}/plugin/writer"
                + (target == null ? "" : " (target " + target.getName() + ")"));
        }
    }

    private void warnIfPluginMissing()
    {
        Path dir = pluginDir();
        if (dir == null || Files.isDirectory(dir) || !pluginMissingWarned.compareAndSet(false, true))
        {
            return;
        }
        log.warn("addax writer plugin '{}' was not found at {}. Doris collection jobs will fail until "
            + "it is installed.", WRITER_PLUGIN, dir);
    }

    private Path pluginDir()
    {
        try
        {
            String home = dictService.getAddaxHome();
            if (home == null || home.isBlank())
            {
                return null;
            }
            return Path.of(home).resolve("plugin").resolve("writer").resolve(WRITER_PLUGIN);
        }
        catch (Exception e)
        {
            log.debug("Failed to resolve ADDAX_HOME: {}", e.getMessage());
            return null;
        }
    }

    private String normalizeColumn(String column)
    {
        return column == null || column.isBlank() ? null : column.trim();
    }

    private String jsonEscape(String value)
    {
        return RdbmsDdlUtil.jsonEscape(value == null ? "" : value);
    }
}
