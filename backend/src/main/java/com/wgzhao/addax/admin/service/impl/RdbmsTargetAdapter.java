package com.wgzhao.addax.admin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.JourKind;
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
import com.wgzhao.addax.admin.service.RiskLogService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import com.wgzhao.addax.admin.service.target.RdbmsWriterPlugins;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.RdbmsDdlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Service;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;
import static com.wgzhao.addax.admin.utils.DbUtil.getDbType;

@Service
@Slf4j
@RequiredArgsConstructor
public class RdbmsTargetAdapter
    implements TargetAdapter
{
    private static final String DEFAULT_WRITER_TEMPLATE_KEY = "wR";
    private static final String BUSINESS_DATE_COMMENT = "业务日期";

    private final EtlTargetRepo etlTargetRepo;
    private final DictService dictService;
    private final ColumnMappingService columnMappingService;
    private final SystemConfigService configService;
    private final ObjectMapper objectMapper;
    private final EtlJourService jourService;
    private final RiskLogService riskLogService;
    private final RdbmsWriterPlugins rdbmsWriterPlugins;

    @Override
    public String getType()
    {
        return "RDBMS";
    }

    @Override
    public boolean hasDateDimension(VwEtlTableWithSource table)
    {
        return table != null && normalizeColumn(table.getRdbmsBusinessDateColumn()) != null;
    }

    @Override
    public boolean addPartition(long taskId, String db, String table, String partName, String partValue)
    {
        return true;
    }

    @Override
    public TargetTableMetadata inspectTable(VwEtlTableWithSource etlTable)
    {
        ConnectInfo conn = resolveTargetConnection(etlTable);
        if (conn == null)
        {
            throw new ApiException(400, "目标端连接未配置，无法读取目标表元数据");
        }
        DbType dialect = getDbType(conn.url());
        if (!rdbmsWriterPlugins.isSupported(dialect))
        {
            throw new ApiException(400, "Unsupported RDBMS target dialect '" + dialect
                + "'; supported dialects: " + rdbmsWriterPlugins.supportedDialectsText());
        }
        try (Connection connection = DbUtil.getConnection(conn.url(), conn.username(), conn.password()))
        {
            if (connection == null)
            {
                throw new ApiException(400, "无法连接目标数据库");
            }
            if (!tableExists(connection, dialect, etlTable.getTargetDb(), etlTable.getTargetTable()))
            {
                return TargetTableMetadata.missing();
            }
            List<TargetTableMetadata.TargetColumn> columns = new ArrayList<>();
            Map<String, ColumnMeta> described = describeColumns(
                connection, dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
            int ordinal = 0;
            for (Map.Entry<String, ColumnMeta> entry : described.entrySet())
            {
                ColumnMeta meta = entry.getValue();
                columns.add(new TargetTableMetadata.TargetColumn(
                    meta.name(), meta.typeName(), meta.size(), meta.scale(), meta.nullable(),
                    meta.defaultValue(), meta.autoIncrement(), ++ordinal));
            }
            return new TargetTableMetadata(
                true,
                columns,
                List.of(),
                targetPrimaryKeys(connection, dialect, etlTable.getTargetDb(), etlTable.getTargetTable()),
                targetUniqueKeys(connection, dialect, etlTable.getTargetDb(), etlTable.getTargetTable()));
        }
        catch (SQLException e)
        {
            throw new ApiException(400, "读取目标表元数据失败: " + e.getMessage());
        }
    }

    @Override
    public boolean createOrUpdateTable(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null)
        {
            return true;
        }
        ConnectInfo conn = resolveTargetConnection(etlTable);
        if (conn == null)
        {
            // No connection config: nothing we can safely do. Keep collection unblocked and warn.
            log.warn("RDBMS target not configured for table {} ({}.{}), skip create/update",
                etlTable.getId(), etlTable.getTargetDb(), etlTable.getTargetTable());
            return true;
        }
        DbType dialect = getDbType(conn.url());
        if (!rdbmsWriterPlugins.isSupported(dialect))
        {
            log.warn("RDBMS target dialect {} is not supported for table {} ({}.{}), skip create/update",
                dialect, etlTable.getId(), etlTable.getTargetDb(), etlTable.getTargetTable());
            recordRisk(etlTable, "不支持的目标方言 " + dialect + "，Phase 1 仅支持 "
                + rdbmsWriterPlugins.supportedDialectsText() + "，已跳过目标表结构同步");
            return false;
        }
        if (requiresExplicitDatabase(dialect) && (etlTable.getTargetDb() == null || etlTable.getTargetDb().isBlank()))
        {
            recordRisk(etlTable, "目标方言 " + dialect + " 需要明确的目标库/模式名，当前为空，已跳过目标表结构同步");
            return false;
        }
        String tableRef = RdbmsDdlUtil.tableRef(dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
        List<ColumnMappingService.ResolvedColumnMapping> mappings = columnMappingService.resolve(etlTable.getId());
        SourceMeta sourceMeta = loadSourceMeta(etlTable, dialect);
        Map<String, String> desiredTypes = new LinkedHashMap<>();
        String bizDateColumn = normalizeColumn(etlTable.getRdbmsBusinessDateColumn());
        boolean createPrimaryKey = etlTable.isRdbmsCreatePrimaryKey();

        List<String> colDefs = new ArrayList<>();
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
        {
            EtlColumn c = mapping.sourceColumn();
            if (c == null)
            {
                continue;
            }
            String name = mapping.targetColumnName();
            String key = name.toLowerCase(Locale.ROOT);
            String typeExpr = resolveColumnType(dialect, c, sourceMeta.columnTypes());
            desiredTypes.put(key, typeExpr);
            colDefs.add(RdbmsDdlUtil.columnDefinition(dialect, name, typeExpr,
                sourceMeta.notNull().contains(activeName(c).toLowerCase(Locale.ROOT)), c.getColComment()));
        }
        boolean carryBizDate = bizDateColumn != null;
        if (bizDateColumn != null && mappings.stream()
            .noneMatch(mapping -> bizDateColumn.equalsIgnoreCase(mapping.targetColumnName())))
        {
            colDefs.add(RdbmsDdlUtil.columnDefinition(dialect, bizDateColumn, businessDateColumnType(dialect),
                true, BUSINESS_DATE_COMMENT));
        }
        else if (carryBizDate && mappings.stream().noneMatch(mapping ->
            bizDateColumn.equalsIgnoreCase(mapping.targetColumnName()) && mapping.sourceActive()))
        {
            // The physical column exists but the source only has a deleted placeholder for it, so the
            // snapshot date cannot be fed without colliding on the column name.
            recordRisk(etlTable, "目标表已存在同名的日期列 " + bizDateColumn
                + "，但源端该列已被标记删除，快照业务日期无法自动写入，请人工处理");
        }
        List<String> primaryKeys = createPrimaryKey
            ? mapPrimaryKeys(sourceMeta.primaryKeys(), mappings)
            : new ArrayList<>();
        String createSql = RdbmsDdlUtil.createTableIfNotExists(dialect, tableRef, colDefs,
            primaryKeys, etlTable.getTblComment());
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createSql);

        try (Connection c = DbUtil.getConnection(conn.url(), conn.username(), conn.password()))
        {
            if (c == null)
            {
                failJour(etlJour, etlTable, "无法连接目标数据库");
                return false;
            }
            boolean metadataOk = true;
            boolean existedBefore = false;
            try
            {
                existedBefore = tableExists(c, dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
            }
            catch (SQLException e)
            {
                metadataOk = false;
                recordRisk(etlTable, "无法读取目标表元数据（" + e.getMessage() + "），跳过目标表结构比对");
            }
            try (Statement stmt = c.createStatement())
            {
                // MySQL has no "schema" above the database, so parity with the Hive adapter
                // (which does `create database if not exists`) means creating the database here.
                if (dialect == DbType.MYSQL && etlTable.getTargetDb() != null && !etlTable.getTargetDb().isBlank())
                {
                    String createDb = "CREATE DATABASE IF NOT EXISTS " + RdbmsDdlUtil.quoteIdent(dialect, etlTable.getTargetDb())
                        + " DEFAULT CHARSET utf8mb4";
                    log.info("create rdbms target database: {}", createDb);
                    stmt.execute(createDb);
                }
                log.info("create rdbms target table: {}", createSql);
                stmt.execute(createSql);
                jourService.successJour(etlJour);
            }
            if (!metadataOk)
            {
                return true;
            }
            try
            {
                if (existedBefore)
                {
                    applyColumnDiff(c, dialect, tableRef, etlTable, mappings, desiredTypes, sourceMeta.notNull());
                    if (ensureBusinessDateColumn(c, dialect, etlTable, bizDateColumn) && createPrimaryKey)
                    {
                        // The column can be added but not folded into an existing primary key, so a
                        // same-day re-run may duplicate rows until a human widens the key.
                        recordRisk(etlTable, "目标表已存在，快照业务日期列 " + bizDateColumn
                            + " 已补加但不会加入已有主键；同一天重复执行可能产生重复数据，请人工确认或改用 preSql 清理当日数据");
                    }
                }
                else
                {
                    // Separate COMMENT ON COLUMN statements are only needed for a table we just created;
                    // re-issuing them on every refresh fails for tables owned by someone else (e.g. a DBA).
                    applyNewTableComments(c, dialect, tableRef, etlTable, mappings);
                }
                warnOnWriteModeWithoutKey(c, dialect, etlTable);
                warnOnSnapshotMode(etlTable, createPrimaryKey, bizDateColumn, primaryKeys);
            }
            catch (SQLException e)
            {
                // A metadata/DDL failure must not fail the whole refresh, but it must be visible.
                recordRisk(etlTable, "目标表结构比对失败（" + e.getMessage() + "），跳过本次比对");
            }
            return true;
        }
        catch (SQLException e)
        {
            log.warn("Failed to create or update RDBMS table ({}.{})", etlTable.getTargetDb(), etlTable.getTargetTable(), e);
            failJour(etlJour, etlTable, e.getMessage());
            return false;
        }
    }

    @Override
    public Object getMaxValue(VwEtlTableWithSource table, String columnName, String partValue)
    {
        if (table == null || columnName == null || columnName.isBlank())
        {
            return null;
        }
        ConnectInfo conn = resolveTargetConnection(table);
        if (conn == null)
        {
            return null;
        }
        DbType dialect = getDbType(conn.url());
        String tableRef = RdbmsDdlUtil.tableRef(dialect, table.getTargetDb(), table.getTargetTable());
        String sql = String.format("SELECT MAX(%s) FROM %s", RdbmsDdlUtil.quoteIdent(dialect, columnName), tableRef);
        try (Connection c = DbUtil.getConnection(conn.url(), conn.username(), conn.password()))
        {
            if (c == null)
            {
                return null;
            }
            try (Statement stmt = c.createStatement();
                ResultSet rs = stmt.executeQuery(sql))
            {
                if (rs.next())
                {
                    return rs.getObject(1);
                }
            }
        }
        catch (SQLException e)
        {
            log.warn("Failed to get max value for RDBMS target {}.{} on column {}", table.getTargetDb(),
                table.getTargetTable(), columnName, e);
        }
        return null;
    }

    @Override
    public boolean prepareBeforeRun(long taskId, VwEtlTableWithSource table, LocalDate bizDate)
    {
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
        return fillRdbmsWriterJob(table, targetDate);
    }

    private String fillRdbmsWriterJob(VwEtlTableWithSource table, LocalDate targetDate)
    {
        String template = resolveWriterTemplate(table);
        Map<String, String> values = new HashMap<>();
        DbType dbType = resolveTargetDialect(table);
        if (!rdbmsWriterPlugins.isSupported(dbType))
        {
            throw new ApiException(400, "Unsupported RDBMS target dialect '" + dbType + "' for table "
                + table.getId() + "; supported dialects: " + rdbmsWriterPlugins.supportedDialectsText());
        }
        if (requiresExplicitDatabase(dbType)
            && (table.getTargetDb() == null || table.getTargetDb().isBlank()))
        {
            throw new ApiException(400, "targetDb is required for " + dbType + " targets (table "
                + table.getId() + ")");
        }
        String plugin = rdbmsWriterPlugins.pluginName(dbType);
        rdbmsWriterPlugins.warnIfMissing(dbType);

        values.put("name", plugin);
        // Same identifier rules as the DDL side, otherwise a mixed-case table name is created as quoted
        // (case sensitive) but written to unquoted (folded), so the writer cannot find the table. The
        // template embeds these values in JSON, hence the escaping.
        values.put("table", RdbmsDdlUtil.jsonEscape(
            RdbmsDdlUtil.tableRef(dbType, table.getTargetDb(), table.getTargetTable())));

        values.put("targetDb", table.getTargetDb());
        values.put("targetTable", table.getTargetTable());
        values.put("writeMode", resolveWriteMode(table.getWriteMode(), dbType, table.getId()));
        values.put("column", getRdbmsWriteColumns(table, dbType));
        values.putAll(targetDate != null
            ? configService.getBizDateValuesForDate(targetDate)
            : configService.getBizDateValues());
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
        String template = dictService.getWriterTemplate(key);
        if (template == null || template.isBlank()) {
            throw new ApiException(400, "Writer template not found: " + key);
        }
        return template;
    }

    /**
     * The writer's jdbcUrl/table identifiers must follow the *target* dialect, so resolve it from the
     * target connection instead of the source url.
     */
    private DbType resolveTargetDialect(VwEtlTableWithSource table)
    {
        ConnectInfo conn = resolveTargetConnection(table);
        if (conn != null)
        {
            return getDbType(conn.url());
        }
        return getDbType(table.getUrl());
    }

    /**
     * RDBMS targets have no native "overwrite". MySQL maps it to REPLACE INTO, which requires a unique
     * key to actually replace rows; other dialects fall back to insert because a hand-built replace
     * would emit invalid SQL.
     */
    private String resolveWriteMode(String raw, DbType dialect, Long tableId)
    {
        String mode = (raw == null || raw.isBlank()) ? "insert" : raw.trim().toLowerCase(Locale.ROOT);
        if ("overwrite".equals(mode))
        {
            if (dialect == DbType.MYSQL)
            {
                return "replace";
            }
            log.warn("Target dialect {} has no overwrite write mode; table {} falls back to insert. "
                + "Configure an explicit writeMode or preSql for overwrite semantics.", dialect, tableId);
            return "insert";
        }
        if (mode.startsWith("insert") || mode.startsWith("replace") || mode.startsWith("update"))
        {
            return mode;
        }
        log.warn("Unsupported writeMode '{}' for RDBMS target (table {}), fallback to insert", raw, tableId);
        return "insert";
    }

    private void applyColumnDiff(Connection conn, DbType dialect, String tableRef,
        VwEtlTableWithSource etlTable, List<ColumnMappingService.ResolvedColumnMapping> mappings,
        Map<String, String> desiredTypes,
        Set<String> sourceNotNull) throws SQLException
    {
        Map<String, ColumnMeta> current = describeColumns(conn, dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
        Set<String> pk = targetPrimaryKeys(conn, dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
        {
            EtlColumn c = mapping.sourceColumn();
            if (c == null)
            {
                continue;
            }
            String name = mapping.targetColumnName();
            String key = name.toLowerCase(Locale.ROOT);
            String typeExpr = desiredTypes.get(key);
            ColumnMeta cur = current.get(key);
            String ddl;
            if (cur == null)
            {
                ddl = RdbmsDdlUtil.addColumn(dialect, tableRef,
                    RdbmsDdlUtil.columnDefinition(dialect, name, typeExpr,
                        sourceNotNull.contains(activeName(c).toLowerCase(Locale.ROOT)), c.getColComment()));
            }
            else
            {
                if (!mapping.sourceActive())
                {
                    continue;
                }
                boolean familyChanged = !RdbmsDdlUtil.familyOf(cur.typeName()).equals(RdbmsDdlUtil.familyOf(typeExpr));
                boolean widening = RdbmsDdlUtil.isWidening(cur.typeName(), cur.size(), cur.scale(), typeExpr);
                if (!familyChanged && !widening)
                {
                    continue;
                }
                if (pk.contains(key))
                {
                    recordRisk(etlTable, "目标表主键列 " + name + " 需要变更类型，已跳过以避免破坏主键约束，请人工处理");
                    continue;
                }
                // Restate the old DEFAULT only when the type family is unchanged: a default that was
                // valid for the old type (e.g. 'n' on a string) may be invalid for the new one.
                String defaultValue = cur.defaultValue();
                if (familyChanged && RdbmsDdlUtil.hasDefault(defaultValue))
                {
                    recordRisk(etlTable, "列 " + name + " 类型变更，原默认值已不再重述，请人工确认是否需要重新设置");
                    defaultValue = null;
                }
                else if (dialect == DbType.MYSQL && RdbmsDdlUtil.hasDefault(defaultValue)
                    && RdbmsDdlUtil.safeDefault(defaultValue) == null)
                {
                    // MySQL MODIFY COLUMN restates the whole column; an expression we cannot quote back
                    // (e.g. DEFAULT (uuid())) is dropped, so make that visible instead of silent.
                    recordRisk(etlTable, "列 " + name + " 改写类型时无法重述默认值 " + defaultValue
                        + "，默认值已丢失，请人工确认是否需要重新设置");
                }
                ddl = RdbmsDdlUtil.modifyColumn(dialect, tableRef, name, typeExpr, c.getColComment(),
                    !cur.nullable(), defaultValue, cur.autoIncrement());
            }
            executeDdl(conn, etlTable, ddl);
            if (cur == null)
            {
                String commentSql = RdbmsDdlUtil.columnCommentStatement(dialect, tableRef, name, c.getColComment());
                if (commentSql != null)
                {
                    executeDdl(conn, etlTable, commentSql);
                }
            }
        }
    }

    /**
     * Column comments are inline for MySQL and separate statements for PostgreSQL, so a newly created
     * table needs an explicit pass. Run through executeDdl so failures are journalled and auditable
     * instead of aborting the whole refresh.
     */
    private void applyNewTableComments(Connection conn, DbType dialect, String tableRef,
        VwEtlTableWithSource etlTable, List<ColumnMappingService.ResolvedColumnMapping> mappings)
    {
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
        {
            EtlColumn col = mapping.sourceColumn();
            if (col == null)
            {
                continue;
            }
            String commentSql = RdbmsDdlUtil.columnCommentStatement(
                dialect, tableRef, mapping.targetColumnName(), col.getColComment());
            if (commentSql != null)
            {
                executeDdl(conn, etlTable, commentSql);
            }
        }
    }

    /**
     * MySQL and SQL Server resolve an unqualified table through the connection, so an empty target_db
     * either silently lands in the default database or builds a broken ".dbo.table" reference.
     */
    private boolean requiresExplicitDatabase(DbType dialect)
    {
        return dialect == DbType.MYSQL || dialect == DbType.SQLSERVER;
    }

    /**
     * REPLACE/UPDATE need a unique key to locate existing rows; without one MySQL degrades to plain
     * inserts and PG upsert fails, so repeated runs silently duplicate data. Surface that as a risk log.
     */
    private void warnOnWriteModeWithoutKey(Connection conn, DbType dialect, VwEtlTableWithSource etlTable) throws SQLException
    {
        String mode = resolveWriteMode(etlTable.getWriteMode(), dialect, etlTable.getId());
        if (!mode.startsWith("replace") && !mode.startsWith("update"))
        {
            return;
        }
        Set<String> pk = targetPrimaryKeys(conn, dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
        if (pk.isEmpty())
        {
            recordRisk(etlTable, "目标表 " + etlTable.getTargetDb() + "." + etlTable.getTargetTable()
                + " 未定义主键/唯一键，writeMode=" + mode + " 会退化为普通插入，重复执行可能产生重复数据");
        }
    }

    /**
     * The business date column is partition-like: it is constant within a run and is never part of the key,
     * so no primary key can make a snapshot re-run idempotent. Surface the consequence instead of leaving a
     * cross-day overwrite (source-key-only key) or a same-day duplication (no key) silent.
     */
    private void warnOnSnapshotMode(VwEtlTableWithSource etlTable, boolean createPrimaryKey, String bizDateColumn,
        List<String> primaryKeys)
    {
        if (bizDateColumn == null)
        {
            return;
        }
        String notAKeyColumn = "（该列是分区式属性，不参与主键）";
        if (createPrimaryKey && !primaryKeys.isEmpty())
        {
            recordRisk(etlTable, "已配置业务日期列 " + bizDateColumn + notAKeyColumn
                + "，目标主键只按源端主键（" + String.join(", ", primaryKeys) + "）生成；"
                + "不同日期会因主键相同而互相覆盖，跨天采集请关闭主键创建或改用 preSql 清理当日数据");
        }
        else
        {
            recordRisk(etlTable, "已配置业务日期列 " + bizDateColumn + notAKeyColumn
                + "，目标表没有可用唯一键，请确认任务模板用 preSql 按日清理（或自行建唯一键），"
                + "否则同一天的重复执行会追加重复数据");
        }
    }

    /**
     * Business date values are injected by the reader as a compact literal, so the column is a plain
     * string on every dialect; that keeps the DDL, the reader literal and the writer binding aligned.
     */
    private String businessDateColumnType(DbType dialect)
    {
        return dialect == DbType.SQLSERVER ? "nvarchar(8)" : "varchar(8)";
    }

    private String normalizeColumn(String column)
    {
        return column == null || column.isBlank() ? null : column.trim();
    }

    /**
     * Add the snapshot business-date column to a target table created before the option was set.
     */
    /**
     * Add the snapshot business-date column to a target table created before the option was set.
     *
     * @return true when the column had to be added, false when it was already there (or not configured)
     */
    private boolean ensureBusinessDateColumn(Connection conn, DbType dialect, VwEtlTableWithSource etlTable,
        String bizDateColumn) throws SQLException
    {
        if (bizDateColumn == null)
        {
            return false;
        }
        Map<String, ColumnMeta> current = describeColumns(conn, dialect, etlTable.getTargetDb(),
            etlTable.getTargetTable());
        if (current.containsKey(bizDateColumn.toLowerCase(Locale.ROOT)))
        {
            return false;
        }
        String tableRef = RdbmsDdlUtil.tableRef(dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
        executeDdl(conn, etlTable, RdbmsDdlUtil.addColumn(dialect, tableRef,
            RdbmsDdlUtil.columnDefinition(dialect, bizDateColumn, businessDateColumnType(dialect), true, BUSINESS_DATE_COMMENT)));
        return true;
    }

    private String resolveColumnType(DbType dialect, EtlColumn column, Map<String, String> verbatim)
    {
        String name = activeName(column);
        if (name != null)
        {
            String v = verbatim.get(name.toLowerCase(Locale.ROOT));
            if (v != null && !v.isBlank())
            {
                return v;
            }
        }
        return RdbmsDdlUtil.renderType(dialect, column.getSourceType(), column.getDataLength(),
            column.getDataPrecision() == null ? 0 : column.getDataPrecision(),
            column.getDataScale() == null ? 0 : column.getDataScale());
    }

    /**
     * When the source and target share a dialect, reuse the source's verbatim COLUMN_TYPE and its primary
     * key so types that cannot be reconstructed from JDBC metadata (MySQL ENUM/SET values, unsigned,
     * exact lengths) and the key that REPLACE/UPDATE depend on survive. Cross-dialect or unsupported
     * sources fall back to canonical rendering.
     */
    private SourceMeta loadSourceMeta(VwEtlTableWithSource etlTable, DbType targetDialect)
    {
        if (etlTable.getUrl() == null || etlTable.getSourceTable() == null
            || etlTable.getSourceTable().contains("${"))
        {
            return SourceMeta.EMPTY;
        }
        DbType sourceDialect = getDbType(etlTable.getUrl());
        // Verbatim types / NOT NULL are only meaningful when the source and target share the MySQL dialect.
        boolean verbatim = targetDialect == DbType.MYSQL && sourceDialect == DbType.MYSQL && etlTable.getSourceDb() != null;
        String sql = "select column_name, column_type, is_nullable from information_schema.columns "
            + "where table_schema = ? and table_name = ?";
        try (Connection c = DbUtil.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass()))
        {
            if (c == null)
            {
                return SourceMeta.EMPTY;
            }
            Map<String, String> types = new HashMap<>();
            Set<String> notNull = new HashSet<>();
            if (verbatim)
            {
                try (PreparedStatement ps = c.prepareStatement(sql))
                {
                    ps.setString(1, etlTable.getSourceDb());
                    ps.setString(2, etlTable.getSourceTable());
                    try (ResultSet rs = ps.executeQuery())
                    {
                        while (rs.next())
                        {
                            String name = rs.getString(1);
                            String type = rs.getString(2);
                            if (name != null && type != null && !type.isBlank())
                            {
                                String key = name.toLowerCase(Locale.ROOT);
                                types.put(key, type);
                                if ("NO".equalsIgnoreCase(rs.getString(3)))
                                {
                                    notNull.add(key);
                                }
                            }
                        }
                    }
                }
            }
            // The primary key is dialect-agnostic metadata, so collect it for every source dialect.
            String catalog = (sourceDialect == DbType.MYSQL || sourceDialect == DbType.SQLSERVER)
                && etlTable.getSourceDb() != null && !etlTable.getSourceDb().isBlank() ? etlTable.getSourceDb() : null;
            List<String> pk = new ArrayList<>();
            try (ResultSet rs = c.getMetaData().getPrimaryKeys(catalog, null, etlTable.getSourceTable()))
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
            return new SourceMeta(types, notNull, pk);
        }
        catch (SQLException e)
        {
            log.warn("Failed to read source metadata for {}.{}: {}", etlTable.getSourceDb(),
                etlTable.getSourceTable(), e.getMessage());
            return SourceMeta.EMPTY;
        }
    }

    private void executeDdl(Connection conn, VwEtlTableWithSource etlTable, String ddl)
    {
        EtlJour jour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, ddl);
        try (Statement stmt = conn.createStatement())
        {
            log.info("apply rdbms alter: {}", ddl);
            stmt.execute(ddl);
            jourService.successJour(jour);
        }
        catch (SQLException e)
        {
            log.error("Failed to apply alter for {}.{}: {}", etlTable.getTargetDb(), etlTable.getTargetTable(), ddl, e);
            jourService.failJour(jour, e.getMessage());
            recordRisk(etlTable, "Failed to apply alter " + ddl + " on " + etlTable.getTargetDb() + "."
                + etlTable.getTargetTable() + ": " + e.getMessage());
        }
    }

    private boolean tableExists(Connection conn, DbType dialect, String db, String table) throws SQLException
    {
        // MySQL and SQL Server treat the "database" as the JDBC catalog; other dialects scope by schema.
        String catalog = (dialect == DbType.MYSQL || dialect == DbType.SQLSERVER) && db != null && !db.isBlank()
            ? db : null;
        try (ResultSet rs = conn.getMetaData().getTables(
            catalog,
            metadataSchema(conn, dialect), table, new String[] {"TABLE"}))
        {
            return rs.next();
        }
    }

    /**
     * Metadata lookups must use the same namespace as the DDL: SQL Server always writes to dbo, and
     * PostgreSQL resolves through the connection's current schema. A mismatch would either hide the
     * table or mix in a same-named table in another schema that the DDL never touches.
     */
    private String metadataSchema(Connection conn, DbType dialect) throws SQLException
    {
        if (dialect == DbType.SQLSERVER)
        {
            return "dbo";
        }
        if (dialect == DbType.POSTGRESQL)
        {
            return conn.getSchema();
        }
        return null;
    }

    private Map<String, ColumnMeta> describeColumns(Connection conn, DbType dialect, String db, String table) throws SQLException
    {
        String catalog = (dialect == DbType.MYSQL || dialect == DbType.SQLSERVER) && db != null && !db.isBlank()
            ? db : null;
        Map<String, ColumnMeta> cols = new LinkedHashMap<>();
        try (ResultSet rs = conn.getMetaData().getColumns(
            catalog,
            metadataSchema(conn, dialect), table, null))
        {
            while (rs.next())
            {
                String name = rs.getString("COLUMN_NAME");
                if (name == null)
                {
                    continue;
                }
                cols.put(name.toLowerCase(Locale.ROOT), new ColumnMeta(
                    name,
                    rs.getString("TYPE_NAME"),
                    rs.getInt("COLUMN_SIZE"),
                    rs.getInt("DECIMAL_DIGITS"),
                    !"NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                    rs.getString("COLUMN_DEF"),
                    "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"))));
            }
        }
        return cols;
    }

    private Set<String> targetUniqueKeys(Connection conn, DbType dialect, String db, String table) throws SQLException
    {
        String catalog = (dialect == DbType.MYSQL || dialect == DbType.SQLSERVER) && db != null && !db.isBlank()
            ? db : null;
        Set<String> keys = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getIndexInfo(
            catalog, metadataSchema(conn, dialect), table, false, false))
        {
            while (rs.next())
            {
                String name = rs.getString("COLUMN_NAME");
                if (name != null && !rs.getBoolean("NON_UNIQUE"))
                {
                    keys.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return keys;
    }

    private Set<String> targetPrimaryKeys(Connection conn, DbType dialect, String db, String table) throws SQLException
    {
        String catalog = (dialect == DbType.MYSQL || dialect == DbType.SQLSERVER) && db != null && !db.isBlank()
            ? db : null;
        Set<String> pk = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(
            catalog, metadataSchema(conn, dialect), table))
        {
            while (rs.next())
            {
                String name = rs.getString("COLUMN_NAME");
                if (name != null)
                {
                    pk.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return pk;
    }

    private void failJour(EtlJour jour, VwEtlTableWithSource etlTable, String reason)
    {
        jourService.failJour(jour, reason);
        recordRisk(etlTable, "Failed to create/update RDBMS target table " + etlTable.getTargetDb() + "."
            + etlTable.getTargetTable() + ": " + reason);
    }

    private void recordRisk(VwEtlTableWithSource etlTable, String message)
    {
        try
        {
            riskLogService.recordRisk("RdbmsTargetAdapter", "ERROR", message, etlTable.getId());
        }
        catch (Exception e)
        {
            log.warn("Failed to record risk log: {}", e.getMessage());
        }
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

    private ConnectInfo resolveTargetConnection(VwEtlTableWithSource table)
    {
        if (table == null || table.getTargetId() == null)
        {
            return null;
        }
        EtlTarget target = etlTargetRepo.findById(table.getTargetId()).orElse(null);
        if (target == null || target.getConnectConfig() == null || target.getConnectConfig().isBlank())
        {
            return null;
        }
        try
        {
            JsonNode node = objectMapper.readTree(target.getConnectConfig());
            String url = text(node, "url");
            if (url == null || url.isBlank())
            {
                return null;
            }
            return new ConnectInfo(url, text(node, "username"), text(node, "password"));
        }
        catch (Exception e)
        {
            log.warn("Invalid connect_config for target {}: {}", table.getTargetId(), e.getMessage());
            return null;
        }
    }

    private String text(JsonNode node, String key)
    {
        JsonNode n = node.get(key);
        return n == null || n.isNull() ? null : n.asText();
    }

    private record ConnectInfo(String url, String username, String password)
    {
    }

    private record ColumnMeta(String name, String typeName, int size, int scale, boolean nullable,
        String defaultValue, boolean autoIncrement)
    {
    }

    private record SourceMeta(Map<String, String> columnTypes, Set<String> notNull, List<String> primaryKeys)
    {
        private static final SourceMeta EMPTY = new SourceMeta(Map.of(), Set.of(), List.of());
    }

    private String getRdbmsWriteColumns(VwEtlTableWithSource table, DbType dialect)
    {
        List<ColumnMappingService.ResolvedColumnMapping> mappings = columnMappingService.resolve(table.getId());
        List<String> columns = new ArrayList<>();
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings) {
            String columnName = mapping.targetColumnName();
            if (columnName == null || columnName.isBlank()) {
                continue;
            }
            // ${column} is expanded inside a JSON array, so every entry must be a complete JSON string.
            columns.add('"' + RdbmsDdlUtil.jsonEscape(RdbmsDdlUtil.quoteIdent(dialect, columnName)) + '"');
        }
        // Snapshot mode: the reader injects the business date literal last, so the writer must carry the
        // column in the same position for the positional mapping to line up. When the source already has
        // a column with that name the reader does not inject one, so do not repeat it here either.
        String bizDateColumn = normalizeColumn(table.getRdbmsBusinessDateColumn());
        if (bizDateColumn != null && mappings.stream()
            .noneMatch(mapping -> bizDateColumn.equalsIgnoreCase(mapping.targetColumnName())))
        {
            columns.add('"' + RdbmsDdlUtil.jsonEscape(RdbmsDdlUtil.quoteIdent(dialect, bizDateColumn)) + '"');
        }
        return String.join(", ", columns);
    }

    private List<String> mapPrimaryKeys(List<String> sourceKeys,
        List<ColumnMappingService.ResolvedColumnMapping> mappings)
    {
        List<String> result = new ArrayList<>();
        for (String sourceKey : sourceKeys)
        {
            mappings.stream()
                .filter(mapping -> sourceKey.equalsIgnoreCase(mapping.sourceColumnName()))
                .map(ColumnMappingService.ResolvedColumnMapping::targetColumnName)
                .findFirst()
                .ifPresent(result::add);
        }
        return result;
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
