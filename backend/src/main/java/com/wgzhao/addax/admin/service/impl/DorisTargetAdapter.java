package com.wgzhao.addax.admin.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.DorisTableOptions;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.common.TargetFamily;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.EtlJourService;
import com.wgzhao.addax.admin.service.RefreshTiming;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.source.SourceAdapter;
import com.wgzhao.addax.admin.service.source.SourceAdapterRegistry;
import com.wgzhao.addax.admin.service.source.SourceRef;
import com.wgzhao.addax.admin.service.source.SourceTableHints;
import com.wgzhao.addax.admin.service.target.DorisTargetSettings;
import com.wgzhao.addax.admin.service.target.JdbcTargetSupport;
import com.wgzhao.addax.admin.service.target.JdbcTargetSupport.ColumnMeta;
import com.wgzhao.addax.admin.service.target.JdbcTargetSupport.ConnectInfo;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import com.wgzhao.addax.admin.service.target.TargetPreparationResult;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.DorisDdlUtil;
import com.wgzhao.addax.admin.utils.DorisDdlUtil.DorisDdlException;
import com.wgzhao.addax.admin.utils.DorisDdlUtil.PlannedColumn;
import com.wgzhao.addax.admin.utils.DorisDdlUtil.TablePlan;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;

/**
 * Doris target adapter.
 *
 * <p>Doris is not an RDBMS dialect: the MySQL protocol is only used for metadata and preSql, while
 * the data path is {@code doriswriter}'s HTTP Stream Load. It therefore has its own adapter type and
 * writer template key ({@code wD}) while keeping {@link TargetFamily#RDBMS}: the table contract,
 * snapshot business-date column, type mapping and R2R job template are the RDBMS ones.</p>
 *
 * <p>Managed tables are created with Doris DDL: an explicit key model, key columns placed first,
 * bucketing and an optional dynamic date partition. Unlike RDBMS, a key or bucketing decision cannot
 * be changed after creation, so an unsupported evolution is reported and skipped instead of emitting
 * a statement Doris would silently reinterpret.</p>
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
    private static final String BUSINESS_DATE_COMMENT = "业务日期";

    private final EtlTargetRepo etlTargetRepo;
    private final DictService dictService;
    private final ColumnMappingService columnMappingService;
    private final SystemConfigService configService;
    private final ObjectMapper objectMapper;
    private final EtlJourService jourService;
    private final SourceAdapterRegistry sourceAdapterRegistry;
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

    @Override
    public boolean supportsManagedSchema()
    {
        return true;
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
        if (!DbUtil.testConnection(settings.serverUrl(), settings.username(), settings.password()))
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
        if (mode == SchemaManagementMode.EXTERNAL)
        {
            // External mode never creates or alters target structure; the contract check owns validation.
            return true;
        }
        return createOrUpdateManaged(etlTable);
    }

    /**
     * Doris managed mode: plan from the column mapping (key model, key columns, bucketing, partition),
     * then create the table or add columns that appeared in the source. A plan that cannot be rendered
     * is a failure, not a partial DDL: a Doris key model cannot be repaired by a later ALTER.
     */
    private boolean createOrUpdateManaged(VwEtlTableWithSource etlTable)
    {
        if (etlTable.getTargetDb() == null || etlTable.getTargetDb().isBlank()
            || etlTable.getTargetTable() == null || etlTable.getTargetTable().isBlank())
        {
            return failPlanning(etlTable, "Doris 目标端必须指定目标库和目标表");
        }
        ConnectInfo conn = metadataConnection(etlTable);
        if (conn == null)
        {
            // Same posture as the RDBMS adapter: keep the collection unblocked and make it visible.
            log.warn("Doris target not configured for table {} ({}.{}), skip create/update",
                etlTable.getId(), etlTable.getTargetDb(), etlTable.getTargetTable());
            return true;
        }

        TablePlan plan;
        try
        {
            List<PlannedColumn> columns = plannedColumns(etlTable);
            DorisTableOptions options = DorisTableOptions.from(etlTable.getTargetOptions());
            plan = DorisDdlUtil.planTable(etlTable.getTargetDb(), etlTable.getTargetTable(), columns,
                options, etlTable.getTblComment());
        }
        catch (DorisDdlException e)
        {
            return failPlanning(etlTable, e.getMessage());
        }
        catch (ApiException e)
        {
            return failPlanning(etlTable, e.getMessage());
        }
        for (String warning : plan.warnings())
        {
            warnTarget(etlTable, warning);
        }

        String tableRef = DorisDdlUtil.tableRef(etlTable.getTargetDb(), etlTable.getTargetTable());
        EtlJour createJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, plan.ddl());
        try (Connection c = DbUtil.getConnection(conn.url(), conn.username(), conn.password()))
        {
            if (c == null)
            {
                jourService.failJour(createJour, "无法连接 Doris FE");
                return false;
            }
            boolean existed;
            try
            {
                existed = tableExists(c, etlTable.getTargetDb(), etlTable.getTargetTable());
            }
            catch (SQLException e)
            {
                jourService.failJour(createJour, "无法读取目标表元数据: " + e.getMessage());
                return false;
            }
            try (Statement stmt = c.createStatement())
            {
                stmt.execute("CREATE DATABASE IF NOT EXISTS " + DorisDdlUtil.quote(etlTable.getTargetDb()));
                log.info("create doris target table: {}", plan.ddl());
                stmt.execute(plan.ddl());
                jourService.successJour(createJour);
            }
            if (existed)
            {
                if (!applyColumnDiff(c, etlTable, tableRef, plan))
                {
                    return false;
                }
                warnOnUnmappedColumns(c, etlTable, plan);
            }
            return true;
        }
        catch (SQLException e)
        {
            log.warn("Failed to create or update Doris table ({}.{})", etlTable.getTargetDb(),
                etlTable.getTargetTable(), e);
            jourService.failJour(createJour, e.getMessage());
            return false;
        }
    }

    /**
     * The target-side columns for the managed model. Source keys come from the source adapter, which
     * owns the dialect-specific metadata; a non-JDBC source already rendered a target type and that
     * spelling is kept when it is a Doris type.
     */
    private List<PlannedColumn> plannedColumns(VwEtlTableWithSource etlTable)
    {
        SourceAdapter sourceAdapter = sourceAdapterRegistry.resolve(etlTable);
        SourceTableHints hints = sourceAdapter.tableHints(SourceRef.of(etlTable),
            etlTable.getSourceDb(), etlTable.getSourceTable());
        Set<String> sourceKeys = new HashSet<>();
        for (String key : hints.primaryKeys())
        {
            if (key != null && !key.isBlank())
            {
                sourceKeys.add(key.trim().toLowerCase(Locale.ROOT));
            }
        }
        boolean useProvidedType = !SourceAdapterRegistry.RDBMS_TYPE.equals(sourceAdapter.getType());

        List<PlannedColumn> columns = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (ColumnMappingService.ResolvedColumnMapping mapping : columnMappingService.resolve(etlTable.getId()))
        {
            EtlColumn column = mapping.sourceColumn();
            String name = mapping.targetColumnName();
            if (column == null || name == null || name.isBlank())
            {
                continue;
            }
            String nameKey = name.trim().toLowerCase(Locale.ROOT);
            if (seen.putIfAbsent(nameKey, Boolean.TRUE) != null)
            {
                throw new DorisDdlException(name, "Doris 目标字段名重复: " + name);
            }
            String activeName = activeName(column);
            String activeKey = activeName == null ? null : activeName.toLowerCase(Locale.ROOT);
            boolean sourceKey = activeKey != null && sourceKeys.contains(activeKey);
            boolean nullable = !sourceKey && (activeKey == null || !hints.notNull().contains(activeKey));
            String type = renderColumnType(column, useProvidedType);
            columns.add(new PlannedColumn(name.trim(), type, nullable, sourceKey,
                column.getColComment(), "SOURCE"));
        }
        String businessDateColumn = normalizeColumn(etlTable.getRdbmsBusinessDateColumn());
        if (businessDateColumn != null && seen.keySet().stream()
            .noneMatch(name -> name.equalsIgnoreCase(businessDateColumn)))
        {
            columns.add(new PlannedColumn(businessDateColumn, "varchar(8)", false, false,
                BUSINESS_DATE_COMMENT, "BUSINESS_DATE"));
        }
        if (columns.isEmpty())
        {
            throw new DorisDdlException(null, "没有可建表的字段映射，无法生成 Doris 表结构");
        }
        return columns;
    }

    private String renderColumnType(EtlColumn column, boolean useProvidedType)
    {
        if (useProvidedType)
        {
            String provided = DorisDdlUtil.normalizeProvidedType(column.getTargetTypeFull());
            if (provided != null)
            {
                return provided;
            }
        }
        return DorisDdlUtil.renderType(column.getSourceType(),
            column.getDataLength(),
            column.getDataPrecision() == null ? 0 : column.getDataPrecision(),
            column.getDataScale() == null ? 0 : column.getDataScale());
    }

    /**
     * Existing managed tables are evolved only in the ways Doris supports: append a value column, or
     * widen a value column. Key columns and key/distribution changes are reported for a human; Doris
     * does not expose them as ordinary schema changes.
     */
    private boolean applyColumnDiff(Connection c, VwEtlTableWithSource etlTable, String tableRef, TablePlan plan)
    {
        Map<String, ColumnMeta> current;
        try
        {
            current = describeColumns(c, etlTable.getTargetDb(), etlTable.getTargetTable());
        }
        catch (SQLException e)
        {
            warnTarget(etlTable, "无法读取目标表元数据（" + e.getMessage() + "），跳过目标表结构比对");
            return true;
        }
        Set<String> keys = new LinkedHashSet<>();
        plan.keyColumns().forEach(key -> keys.add(key.toLowerCase(Locale.ROOT)));
        for (String key : plan.keyColumns())
        {
            if (!current.containsKey(key.toLowerCase(Locale.ROOT)))
            {
                return failPlanning(etlTable, "目标表缺少键列 " + key
                    + "；Doris 不支持事后把列加入键列，请人工重建目标表或改用外部表模式");
            }
        }
        for (PlannedColumn column : plan.orderedColumns())
        {
            String name = column.name();
            String key = name.toLowerCase(Locale.ROOT);
            ColumnMeta cur = current.get(key);
            if (cur == null)
            {
                executeDdl(c, etlTable, DorisDdlUtil.addColumn(tableRef,
                    DorisDdlUtil.columnDefinition(name, column.type(), false, column.comment())));
                continue;
            }
            boolean sameType = DorisDdlUtil.canonicalType(cur.typeName())
                .equals(DorisDdlUtil.canonicalType(column.type()));
            if (keys.contains(key))
            {
                if (!sameType)
                {
                    warnTarget(etlTable, "键列 " + name + " 的目标类型为 " + cur.typeName()
                        + "，计划类型为 " + column.type() + "；Doris 不允许修改键列，请人工处理");
                }
                continue;
            }
            if (sameType)
            {
                continue;
            }
            boolean familyChanged = !RdbmsDdlUtil.familyOf(cur.typeName())
                .equals(RdbmsDdlUtil.familyOf(column.type()));
            boolean widening = RdbmsDdlUtil.isWidening(cur.typeName(), cur.size(), cur.scale(),
                column.type());
            if (!familyChanged && !widening)
            {
                continue;
            }
            if (familyChanged || !widening)
            {
                warnTarget(etlTable, "目标列 " + name + " 当前类型 " + cur.typeName() + "，计划类型 "
                    + column.type() + "；Doris 不支持该自动变更，请人工处理");
                continue;
            }
            executeDdl(c, etlTable, DorisDdlUtil.modifyColumn(tableRef, name, column.type()));
        }
        return true;
    }

    /**
     * A non-nullable target column that is not in the mapping cannot be filled by the Stream Load
     * column list and makes every subsequent load fail; surface it at structure-sync time.
     */
    private void warnOnUnmappedColumns(Connection c, VwEtlTableWithSource etlTable, TablePlan plan)
    {
        Set<String> planned = new HashSet<>();
        plan.orderedColumns().forEach(column -> planned.add(column.name().toLowerCase(Locale.ROOT)));
        try
        {
            for (ColumnMeta meta : describeColumns(c, etlTable.getTargetDb(), etlTable.getTargetTable()).values())
            {
                if (!planned.contains(meta.name().toLowerCase(Locale.ROOT)) && !meta.nullable()
                    && !RdbmsDdlUtil.hasDefault(meta.defaultValue()))
                {
                    warnTarget(etlTable, "目标表存在未映射且非空的列 " + meta.name()
                        + "，Stream Load 无法填充该列，导入会失败；请在目标表设置默认值或人工处理");
                }
            }
        }
        catch (SQLException e)
        {
            log.debug("Failed to inspect unmapped Doris columns for table {}: {}", etlTable.getId(),
                e.getMessage());
        }
    }

    private void executeDdl(Connection conn, VwEtlTableWithSource etlTable, String ddl)
    {
        EtlJour jour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, ddl);
        try (Statement stmt = conn.createStatement())
        {
            log.info("apply doris alter: {}", ddl);
            stmt.execute(ddl);
            jourService.successJour(jour);
        }
        catch (SQLException e)
        {
            log.error("Failed to apply Doris alter for {}.{}: {}", etlTable.getTargetDb(),
                etlTable.getTargetTable(), ddl, e);
            jourService.failJour(jour, e.getMessage());
            warnTarget(etlTable, "Doris 目标表变更失败 " + ddl + ": " + e.getMessage());
        }
    }

    private boolean failPlanning(VwEtlTableWithSource etlTable, String reason)
    {
        EtlJour jour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, "-- " + reason);
        jourService.failJour(jour, reason);
        log.warn("Doris target table {}: {}", etlTable.getId(), reason);
        return false;
    }

    private void warnTarget(VwEtlTableWithSource etlTable, String message)
    {
        log.warn("Doris target table {}: {}", etlTable.getId(), message);
    }

    private String activeName(EtlColumn column)
    {
        String name = column.getColumnName();
        if (name != null && name.startsWith(DELETED_PLACEHOLDER_PREFIX))
        {
            return name.substring(DELETED_PLACEHOLDER_PREFIX.length());
        }
        return name;
    }

    @Override
    public TargetTableMetadata inspectTable(VwEtlTableWithSource etlTable)
    {
        if (etlTable.getTargetDb() == null || etlTable.getTargetDb().isBlank()
            || etlTable.getTargetTable() == null || etlTable.getTargetTable().isBlank())
        {
            throw new ApiException(400, "Doris 目标端必须指定目标库和目标表");
        }
        ConnectInfo conn = metadataConnection(etlTable);
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
        long stageStart = System.nanoTime();
        try {
            return lookupMaxValue(table, columnName, partValue);
        }
        finally {
            RefreshTiming.add(RefreshTiming.Stage.MAX_VALUE, System.nanoTime() - stageStart);
        }
    }

    private Object lookupMaxValue(VwEtlTableWithSource table, String columnName, String partValue)
    {
        if (table == null || columnName == null || columnName.isBlank()
            || table.getTargetDb() == null || table.getTargetDb().isBlank())
        {
            return null;
        }
        ConnectInfo conn = metadataConnection(table);
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
    public TargetPreparationResult prepareBeforeRun(long taskId, Long executionId,
        VwEtlTableWithSource table, LocalDate bizDate)
    {
        // External Doris tables own their partitions; dynamic partition handles the date dimension.
        return TargetPreparationResult.ok();
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
                    // Doris hides implementation columns (__DORIS_DELETE_SIGN__, __DORIS_VERSION__)
                    // in unique tables; they are not part of the mapping contract.
                    if (name == null || name.startsWith("__"))
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

    /**
     * Metadata/DDL connections drop the database path so a managed target can point at a database
     * that does not exist yet; the DDL creates it. The writer still uses the configured URL.
     */
    private ConnectInfo metadataConnection(VwEtlTableWithSource table)
    {
        if (table == null || table.getTargetId() == null)
        {
            return null;
        }
        EtlTarget target = etlTargetRepo.findById(table.getTargetId()).orElse(null);
        if (target == null)
        {
            return null;
        }
        DorisTargetSettings settings = settings(target);
        return new ConnectInfo(settings.serverUrl(), settings.username(), settings.password());
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
