package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.QuoteChars;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wgzhao.addax.admin.common.Constants.SQL_RESERVED_KEYWORDS;

/**
 * 数据库工具类。
 * 提供 JDBC 连接测试、数据库类型识别、表/字段注释获取等通用方法。
 */
@Slf4j
public class DbUtil
{
    // test jdbc is connected or not
    public static boolean testConnection(String url, String username, String password)
    {
        Connection connection = getConnection(url, username, password);
        if (connection == null) {
            return false;
        }
        else {
            // close it first
            try {
                connection.close();
            }
            catch (SQLException e) {
                log.warn("Failed to close the connection", e);
            }
            return true;
        }
    }

    public static Connection getConnection(String url, String username, String password)
    {
        try {
            return DriverManager.getConnection(url, username, password);
        }
        catch (SQLException e) {
            log.error("Failed to connect database, url={}", url, e);
            return null;
        }
    }

    public static DbType getDbType(String jdbcUrl)
    {
        // Use DbType's jdbcKindMap to resolve the DB type. This keeps all jdbc prefixes
        // centralized in DbType. For backward compatibility we keep ClickHouse mapped
        // to HIVE (so existing quote behavior remains unchanged).
        Map<String, String> kindMap = DbType.jdbcKindMap();
        for (var entry : kindMap.entrySet()) {
            if (jdbcUrl.startsWith(entry.getKey())) {
                String val = entry.getValue();
                return DbType.fromValue(val).orElse(DbType.RDBMS);
            }
        }
        return DbType.RDBMS;
    }

    public static String getColumnComment(Connection conn, String dbName, String tableName, String columnName)
    {
        try {
            String dbType = conn.getMetaData().getDatabaseProductName().toLowerCase();
            String sql = null;
            if (dbType.contains("mysql")) {
                sql = "SELECT COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND COLUMN_NAME=?";
            }
            else if (dbType.contains("postgresql")) {
                sql = "SELECT pgd.description FROM pg_catalog.pg_description pgd " +
                    "JOIN pg_catalog.pg_class c ON c.oid = pgd.objoid " +
                    "JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid AND a.attnum = pgd.objsubid " +
                    "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?";
            }
            else if (dbType.contains("oracle")) {
                sql = "SELECT COMMENTS FROM ALL_COL_COMMENTS WHERE OWNER=? AND TABLE_NAME=? AND COLUMN_NAME=?";
            }
            else if (dbType.contains("microsoft sql server") || dbType.contains("sql server")) {
                sql = "SELECT CAST(value AS VARCHAR) FROM fn_listextendedproperty ('MS_Description', 'schema', ?, 'table', ?, 'column', ?)";
            }
            else if (dbType.contains("db2")) {
                sql = "SELECT REMARKS FROM SYSCAT.COLUMNS WHERE TABSCHEMA=? AND TABNAME=? AND COLNAME=?";
            }
            else if (dbType.contains("clickhouse")) {
                // ClickHouse 通过 system.columns 提供列注释
                sql = "SELECT comment FROM system.columns WHERE database=? AND table=? AND name=?";
            }
            else {
                return "";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbName);
                ps.setString(2, tableName);
                ps.setString(3, columnName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString(1);
                        return result == null ? "" : result;
                    }
                }
            }
        }
        catch (SQLException e) {
            log.warn("getColumnComment error", e);
            return "";
        }
        return "";
    }

    /**
     * 获取表注释
     *
     * @param conn {@link Connection}数据库连接
     * @param dbName 数据库名
     * @param tableName 表名
     * @return 表注释
     */
    public static String getTableComment(Connection conn, String dbName, String tableName)
    {
        try {
            String dbType = conn.getMetaData().getDatabaseProductName().toLowerCase();
            String sql = null;
            if (dbType.contains("mysql")) {
                sql = "SELECT TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?";
            }
            else if (dbType.contains("postgresql")) {
                sql = "SELECT obj_description(c.oid) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname=? AND c.relname=?";
            }
            else if (dbType.contains("oracle")) {
                sql = "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=? AND TABLE_NAME=?";
            }
            else if (dbType.contains("microsoft sql server") || dbType.contains("sql server")) {
                sql = "SELECT CAST(value AS VARCHAR) FROM fn_listextendedproperty ('MS_Description', 'schema', ?, 'table', ?, NULL)";
            }
            else if (dbType.contains("db2")) {
                sql = "SELECT REMARKS FROM SYSCAT.TABLES WHERE TABSCHEMA=? AND TABNAME=?";
            }
            else if (dbType.contains("clickhouse")) {
                // ClickHouse: 优先从 system.tables.comment 读取，否则回退到 create_table_query 解析
                String comment = getClickHouseTableComment(conn, dbName, tableName);
                return comment == null ? "" : comment;
            }
            else {
                return "";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbName);
                ps.setString(2, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString(1);
                        return result == null ? "" : result;
                    }
                }
            }
        }
        catch (SQLException e) {
            // 可以记录日志
            log.warn("getTableComment error", e);
            return "";
        }
        return "";
    }

    // 获取表注释的另外一种实现
    public static String getTableCommentAlt(Connection connection, String url, String dbName, String tableName)
    {
        String result = null;
        String sql = null;
        // MySQL: information_schema.tables
        if (url.startsWith("jdbc:mysql")) {
            sql = "SELECT TABLE_COMMENT FROM information_schema.tables WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        }
        // PostgreSQL: pg_class + pg_namespace + obj_description
        else if (url.startsWith("jdbc:postgresql")) {
            sql = """
                SELECT obj_description(c.oid) AS comment
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relkind='r' AND n.nspname=? AND c.relname = ?
                """;
        }
        // Oracle: ALL_TAB_COMMENTS（含 owner）
        else if (url.startsWith("jdbc:oracle")) {
            sql = "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE TABLE_TYPE='TABLE' AND OWNER=? AND TABLE_NAME=?";
        }
        // SQL Server: sys.tables + sys.schemas + sys.extended_properties('MS_Description')
        else if (url.startsWith("jdbc:sqlserver")) {
            sql = """
                SELECT CAST(ep.value AS NVARCHAR(4000)) AS comment
                FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
                LEFT JOIN sys.extended_properties ep ON ep.major_id = t.object_id
                WHERE ep.minor_id = 0 AND ep.class=1 AND ep.name='MS_Description'
                AND s.name=? AND t.name=?
                """;
        }
        if (sql == null) {
            return result;
        }
        try (var ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                result = Optional.ofNullable(rs.getString(1)).orElse("");
            }
        }
        catch (SQLException ignore) {
        }
        return result;
    }

    /**
     * 获取表近似行数（不同数据库的统计机制不同，均为估算值）。
     * 返回字符串形式的近似行数，若无法获取则返回空串。
     */
    public static Long getTableRowCount(Connection conn, String dbName, String tableName)
    {
        Long rows = null;
        try {
            String dbType = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (dbType.contains("mysql")) {
                String sql = "SELECT TABLE_ROWS FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dbName);
                    ps.setString(2, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long v = rs.getLong(1);
                            rows = rs.wasNull() ? null : v;
                        }
                    }
                }
            }
            else if (dbType.contains("postgresql")) {
                String sql = "SELECT c.reltuples FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relkind IN ('r','p') AND n.nspname=? AND c.relname=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dbName);
                    ps.setString(2, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            double est = rs.getDouble(1);
                            rows = rs.wasNull() ? null : Math.round(est);
                        }
                    }
                }
            }
            else if (dbType.contains("oracle")) {
                String sql = "SELECT t.NUM_ROWS FROM ALL_TABLES t WHERE t.OWNER=? AND t.TABLE_NAME=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dbName);
                    ps.setString(2, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long v = rs.getLong(1);
                            rows = rs.wasNull() ? null : v;
                        }
                    }
                }
            }
            else if (dbType.contains("microsoft sql server") || dbType.contains("sql server")) {
                String sql = "SELECT SUM(p.rows) AS row_count\n" +
                    "FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id\n" +
                    "JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0,1)\n" +
                    "WHERE s.name=? AND t.name=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dbName);
                    ps.setString(2, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long v = rs.getLong(1);
                            rows = rs.wasNull() ? null : v;
                        }
                    }
                }
            }
            else if (dbType.contains("db2")) {
                String sql = "SELECT CARD FROM SYSCAT.TABLES WHERE TABSCHEMA=? AND TABNAME=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dbName);
                    ps.setString(2, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long v = rs.getLong(1);
                            rows = rs.wasNull() ? null : v;
                        }
                    }
                }
            }
            else if (dbType.contains("clickhouse")) {
                Long total = tryQueryLong(conn,
                    "SELECT total_rows FROM system.tables WHERE database=? AND name=?",
                    dbName, tableName);
                if (total != null) {
                    rows = total;
                }
                else {
                    rows = tryQueryLong(conn,
                        "SELECT sum(rows) FROM system.parts WHERE active=1 AND database=? AND table=?",
                        dbName, tableName);
                }
            }
        }
        catch (SQLException e) {
            log.warn("getTableCommentAndRowCount error", e);
        }
        return rows;
    }

    private static String getClickHouseTableComment(Connection conn, String dbName, String tableName)
    {
        // 尝试直接读取 system.tables.comment
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT comment FROM system.tables WHERE database=? AND name=?")) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String c = rs.getString(1);
                    if (c != null) {
                        return c;
                    }
                }
            }
        }
        catch (SQLException e) {
            // 可能该版本无 comment 列，忽略并走回退
            log.debug("ClickHouse read system.tables.comment failed, fallback to parse create_table_query", e);
        }
        // 回退：读取 create_table_query 并用正则解析 COMMENT '...'
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT create_table_query FROM system.tables WHERE database=? AND name=?")) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String ddl = rs.getString(1);
                    if (ddl != null) {
                        Pattern p = Pattern.compile("COMMENT\\s+'([^']*)'", Pattern.CASE_INSENSITIVE);
                        Matcher m = p.matcher(ddl);
                        if (m.find()) {
                            return m.group(1);
                        }
                    }
                }
            }
            catch (SQLException e) {
                log.debug("ClickHouse read create_table_query failed", e);
            }
        }
        catch (SQLException e) {
            log.debug("ClickHouse read create_table_query failed", e);
        }
        return null;
    }

    private static Long tryQueryLong(Connection conn, String sql, String dbName, String tableName)
    {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    return rs.wasNull() ? null : v;
                }
            }
        }
        catch (SQLException e) {
            log.debug("tryQueryLong failed: {}", sql, e);
        }
        return null;
    }

    public static QuoteChars getQuoteCharsForDb(DbType dbType)
    {
        if (dbType == null) {
            dbType = DbType.RDBMS; // default to MySQL when not specified
        }
        return switch (dbType) {
            case POSTGRESQL, ORACLE -> new QuoteChars("\\" + "\"", "\\" + "\"");
            case SQLSERVER -> new QuoteChars("[", "]");
            default -> new QuoteChars("`", "`");
        };
    }

    // 新增：按数据库类型决定引号（默认 MySQL）
    public static String quoteIfNeeded(String columnName, DbType dbType)
    {
        if (columnName == null) {
            return null;
        }
        QuoteChars q = getQuoteCharsForDb(dbType);
        // already quoted with chosen quote characters?
        if (columnName.length() >= 2 && columnName.startsWith(q.left()) && columnName.endsWith(q.right())) {
            return columnName;
        }
        if (isSqlKeyword(columnName)
            || columnName.contains(" ")
            || columnName.contains("-")
            || !(Character.isLetter(columnName.charAt(0)) || columnName.charAt(0) == '_')
        ) {
            return q.left() + columnName + q.right();
        }
        return columnName;
    }

    public static String quoteIfNeeded(String columnName)
    {

        // 默认使用 MySQL 的反引号
        return quoteIfNeeded(columnName, DbType.MYSQL);
    }

    /**
     * 判断给定名称是否为 SQL 保留关键字（大小写不敏感）。
     */
    public static boolean isSqlKeyword(String name)
    {
        if (name == null) {
            return false;
        }
        return SQL_RESERVED_KEYWORDS.contains(name.toUpperCase(Locale.ROOT));
    }
}
