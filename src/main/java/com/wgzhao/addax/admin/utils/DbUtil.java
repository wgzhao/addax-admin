package com.wgzhao.addax.admin.utils;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * 数据库工具类。
 * 提供 JDBC 连接测试、数据库类型识别、表/字段注释获取等通用方法。
 */
@Slf4j
public class DbUtil
{
    /**
     * 数据库类型与标识映射表。
     * key 为数据库类型关键字，value 为类型标识。
     */
    private static final java.util.Map<String, String> KIND_MAP = new java.util.LinkedHashMap<>();
    static {
        KIND_MAP.put("jdbc:mysql", "M");
        KIND_MAP.put("jdbc:oracle", "O");
        KIND_MAP.put("jdbc:sqlserver", "S");
        KIND_MAP.put("jdbc:postgresql", "P");
        KIND_MAP.put("jdbc:db2", "D");
        KIND_MAP.put("jdbc:clickhouse", "C");
        KIND_MAP.put("jdbc/chk", "C");
    }

    // test jdbc is connected or not
    public static boolean testConnection(String url, String username, String password)
    {
        try(Connection _ = DriverManager.getConnection(url, username, password)) {
            return true;
        }
        catch (SQLException e) {
            log.error("Failed to connect database", e);
            return false;
        }
    }

    /**
     * 根据 JDBC url 查表获取数据库类型标识。
     * 优先匹配 KIND_MAP 中的 key，未命中则返回 "R"。
     * @param jdbcUrl JDBC连接字符串
     * @return 数据库类型标识（如 M、O、S、P、D、C、R）
     */
    public static String getKind(String jdbcUrl)
    {
        for (var entry : KIND_MAP.entrySet()) {
            if (jdbcUrl.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "R";
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
}
