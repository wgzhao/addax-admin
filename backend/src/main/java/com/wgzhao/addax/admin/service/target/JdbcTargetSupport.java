package com.wgzhao.addax.admin.service.target;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.source.SourceAdapter;
import com.wgzhao.addax.admin.service.source.SourceAdapterRegistry;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JDBC target support shared by the RDBMS and Doris adapters.
 *
 * <p>Both adapters read the same target metadata through the JDBC driver, scope a table by catalog or
 * schema in the same way, and carry the same snapshot business-date column into the writer. Keeping
 * that in one place means a Doris target cannot drift from the RDBMS contract rules, and the next
 * JDBC-based target does not have to copy the adapter.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JdbcTargetSupport
{
    private final EtlTargetRepo etlTargetRepo;
    private final ObjectMapper objectMapper;
    private final ColumnMappingService columnMappingService;
    private final SourceAdapterRegistry sourceAdapterRegistry;

    public record ConnectInfo(String url, String username, String password)
    {
    }

    public record ColumnMeta(String name, String typeName, int size, int scale, boolean nullable,
        String defaultValue, boolean autoIncrement)
    {
    }

    public ConnectInfo resolveTargetConnection(VwEtlTableWithSource table)
    {
        if (table == null || table.getTargetId() == null)
        {
            return null;
        }
        return etlTargetRepo.findById(table.getTargetId())
            .map(this::resolveTargetConnection)
            .orElse(null);
    }

    public ConnectInfo resolveTargetConnection(EtlTarget target)
    {
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
            log.warn("Invalid connect_config for target {}: {}", target.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * MySQL and SQL Server treat the "database" as the JDBC catalog; other dialects scope by schema.
     */
    public String catalog(DbType dialect, String db)
    {
        return (dialect == DbType.MYSQL || dialect == DbType.SQLSERVER) && db != null && !db.isBlank()
            ? db : null;
    }

    /**
     * Metadata lookups must use the same namespace as the DDL: SQL Server always writes to dbo, and
     * PostgreSQL resolves through the connection's current schema.
     */
    public String metadataSchema(Connection conn, DbType dialect) throws SQLException
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

    public boolean tableExists(Connection conn, String catalog, String schema, String table) throws SQLException
    {
        try (ResultSet rs = conn.getMetaData().getTables(catalog, schema, table, new String[] {"TABLE"}))
        {
            return rs.next();
        }
    }

    public Map<String, ColumnMeta> describeColumns(Connection conn, String catalog, String schema, String table)
        throws SQLException
    {
        Map<String, ColumnMeta> cols = new LinkedHashMap<>();
        try (ResultSet rs = conn.getMetaData().getColumns(catalog, schema, table, null))
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

    public Set<String> targetUniqueKeys(Connection conn, String catalog, String schema, String table)
        throws SQLException
    {
        Set<String> keys = new LinkedHashSet<>();
        try (ResultSet rs = conn.getMetaData().getIndexInfo(catalog, schema, table, false, false))
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

    public Set<String> targetPrimaryKeys(Connection conn, String catalog, String schema, String table)
        throws SQLException
    {
        Set<String> pk = new LinkedHashSet<>();
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(catalog, schema, table))
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

    /**
     * Read one aggregate (typically MAX of the incremental column) through the target connection.
     * The optional parameter scopes the lookup to one snapshot business date.
     */
    public Object maxValue(ConnectInfo conn, String sql, String parameter)
    {
        try (Connection c = DbUtil.getConnection(conn.url(), conn.username(), conn.password()))
        {
            if (c == null)
            {
                return null;
            }
            try (PreparedStatement stmt = c.prepareStatement(sql))
            {
                if (parameter != null)
                {
                    stmt.setString(1, parameter);
                }
                try (ResultSet rs = stmt.executeQuery())
                {
                    return rs.next() ? rs.getObject(1) : null;
                }
            }
        }
        catch (SQLException e)
        {
            log.warn("Failed to read target aggregate: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Target column names in mapping order, plus the platform-provided snapshot business-date column
     * when it is configured but not mapped. Names are returned bare; each adapter applies the quoting
     * its writer expects (JDBC identifiers for RDBMS, Stream Load header names for Doris).
     */
    public List<String> writeColumns(VwEtlTableWithSource table)
    {
        List<ColumnMappingService.ResolvedColumnMapping> mappings = columnMappingService.resolve(table.getId());
        List<String> columns = new ArrayList<>();
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings)
        {
            String columnName = mapping.targetColumnName();
            if (columnName != null && !columnName.isBlank())
            {
                columns.add(columnName);
            }
        }
        String bizDateColumn = normalizeColumn(table.getRdbmsBusinessDateColumn());
        if (bizDateColumn != null && mappings.stream()
            .noneMatch(mapping -> bizDateColumn.equalsIgnoreCase(mapping.targetColumnName())))
        {
            // The reader must inject one literal column for this writer column. An engine without
            // constant columns (Elasticsearch) would make the positional lists disagree.
            SourceAdapter sourceAdapter = sourceAdapterRegistry.resolve(table);
            if (!sourceAdapter.capabilities().constantColumns())
            {
                throw new ApiException(400, "采集源 " + sourceAdapter.getType()
                    + " 不支持注入常量列，快照业务日期列 " + bizDateColumn + " 必须来自源字段映射");
            }
            columns.add(bizDateColumn);
        }
        return columns;
    }

    private String normalizeColumn(String column)
    {
        return column == null || column.isBlank() ? null : column.trim();
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
