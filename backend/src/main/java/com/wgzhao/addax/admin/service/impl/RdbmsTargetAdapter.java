package com.wgzhao.addax.admin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.service.ColumnService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.EtlJourService;
import com.wgzhao.addax.admin.service.RiskLogService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDate;

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
    private final EtlJourService jourService;
    private final RiskLogService riskLogService;

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
        String tableRef = RdbmsDdlUtil.tableRef(dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
        List<EtlColumn> columns = columnService.getColumns(etlTable.getId());
        Map<String, String> verbatim = loadVerbatimSourceTypes(etlTable, dialect);
        Map<String, String> desiredTypes = new LinkedHashMap<>();

        List<String> colDefs = new ArrayList<>();
        for (EtlColumn c : columns)
        {
            String name = activeName(c);
            String typeExpr = resolveColumnType(dialect, c, verbatim);
            desiredTypes.put(name.toLowerCase(Locale.ROOT), typeExpr);
            colDefs.add(RdbmsDdlUtil.columnDefinition(dialect, name, typeExpr, c.getColComment()));
        }
        String createSql = RdbmsDdlUtil.createTableIfNotExists(dialect, tableRef, colDefs, etlTable.getTblComment());
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createSql);

        try (Connection c = DbUtil.getConnection(conn.url(), conn.username(), conn.password()))
        {
            if (c == null)
            {
                failJour(etlJour, etlTable, "无法连接目标数据库");
                return false;
            }
            boolean existedBefore = tableExists(c, dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
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
            if (!existedBefore)
            {
                log.info("RDBMS target table {}.{} was newly created; skip alter diff.",
                    etlTable.getTargetDb(), etlTable.getTargetTable());
                return true;
            }
            applyColumnDiff(c, dialect, tableRef, etlTable, columns, desiredTypes);
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
    public boolean prepareBeforeRun(long taskId, VwEtlTableWithSource table, String bizDateValue)
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
        String targetType = resolveTargetType(table);

        values.put("name", targetType.toLowerCase(Locale.ROOT) + "writer");
        DbType dbType = resolveTargetDialect(table);

        if (dbType == DbType.POSTGRESQL) {
            values.put("table", quoteIfNeeded(table.getTargetTable(), dbType));
        }
        else {
            values.put("table", quoteIfNeeded(table.getTargetDb(), dbType) + "." + quoteIfNeeded(table.getTargetTable(), dbType));
        }

        values.put("targetDb", table.getTargetDb());
        values.put("targetTable", table.getTargetTable());
        values.put("writeMode", resolveWriteMode(table.getWriteMode(), dbType, table.getId()));
        values.put("column", getRdbmsWriteColumns(table));
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
     * RDBMS targets have no native "overwrite". MySQL maps it to replace (delete+insert by key);
     * other dialects fall back to insert because a hand-built replace would emit invalid SQL.
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
        VwEtlTableWithSource etlTable, List<EtlColumn> columns, Map<String, String> desiredTypes) throws SQLException
    {
        Map<String, String> current = describeColumns(conn, dialect, etlTable.getTargetDb(), etlTable.getTargetTable());
        for (EtlColumn c : columns)
        {
            String name = activeName(c);
            String key = name.toLowerCase(Locale.ROOT);
            String typeExpr = desiredTypes.get(key);
            String currentType = current.get(key);
            String ddl;
            if (currentType == null)
            {
                ddl = RdbmsDdlUtil.addColumn(dialect, tableRef,
                    RdbmsDdlUtil.columnDefinition(dialect, name, typeExpr, c.getColComment()));
            }
            else if (!c.getColumnName().startsWith(DELETED_PLACEHOLDER_PREFIX)
                && !RdbmsDdlUtil.typeMatches(currentType, typeExpr))
            {
                ddl = RdbmsDdlUtil.modifyColumn(dialect, tableRef, name, typeExpr, c.getColComment());
            }
            else
            {
                continue;
            }
            executeDdl(conn, etlTable, ddl);
        }
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
     * When the source and target share a dialect, reuse the source's verbatim COLUMN_TYPE so types that
     * cannot be reconstructed from JDBC metadata (MySQL ENUM/SET values, unsigned, exact lengths) survive.
     * Cross-dialect or unsupported sources fall back to canonical rendering.
     */
    private Map<String, String> loadVerbatimSourceTypes(VwEtlTableWithSource etlTable, DbType targetDialect)
    {
        if (targetDialect != DbType.MYSQL || etlTable.getUrl() == null
            || etlTable.getSourceDb() == null || etlTable.getSourceTable() == null
            || etlTable.getSourceTable().contains("${")
            || getDbType(etlTable.getUrl()) != DbType.MYSQL)
        {
            return Map.of();
        }
        String sql = "select column_name, column_type from information_schema.columns "
            + "where table_schema = ? and table_name = ?";
        try (Connection c = DbUtil.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass()))
        {
            if (c == null)
            {
                return Map.of();
            }
            try (PreparedStatement ps = c.prepareStatement(sql))
            {
                ps.setString(1, etlTable.getSourceDb());
                ps.setString(2, etlTable.getSourceTable());
                Map<String, String> types = new HashMap<>();
                try (ResultSet rs = ps.executeQuery())
                {
                    while (rs.next())
                    {
                        String name = rs.getString(1);
                        String type = rs.getString(2);
                        if (name != null && type != null && !type.isBlank())
                        {
                            types.put(name.toLowerCase(Locale.ROOT), type);
                        }
                    }
                }
                return types;
            }
        }
        catch (SQLException e)
        {
            log.warn("Failed to read verbatim source column types for {}.{}: {}", etlTable.getSourceDb(),
                etlTable.getSourceTable(), e.getMessage());
            return Map.of();
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

    private boolean tableExists(Connection conn, DbType dialect, String db, String table)
    {
        try (ResultSet rs = conn.getMetaData().getTables(
            dialect == DbType.MYSQL && db != null && !db.isBlank() ? db : null,
            dialect == DbType.POSTGRESQL && db != null && !db.isBlank() ? db : null,
            table, new String[] {"TABLE"}))
        {
            return rs.next();
        }
        catch (SQLException e)
        {
            log.warn("Failed to check existence of {}.{}: {}", db, table, e.getMessage());
            return false;
        }
    }

    private Map<String, String> describeColumns(Connection conn, DbType dialect, String db, String table)
    {
        Map<String, String> cols = new LinkedHashMap<>();
        try (ResultSet rs = conn.getMetaData().getColumns(
            dialect == DbType.MYSQL && db != null && !db.isBlank() ? db : null,
            dialect == DbType.POSTGRESQL && db != null && !db.isBlank() ? db : null,
            table, null))
        {
            while (rs.next())
            {
                String name = rs.getString("COLUMN_NAME");
                if (name != null)
                {
                    cols.put(name.toLowerCase(Locale.ROOT), rs.getString("TYPE_NAME"));
                }
            }
        }
        catch (SQLException e)
        {
            log.warn("Failed to describe columns of {}.{}: {}", db, table, e.getMessage());
        }
        return cols;
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
