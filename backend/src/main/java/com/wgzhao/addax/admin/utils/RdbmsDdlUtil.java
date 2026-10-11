package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.common.DbType;

import java.util.List;

/**
 * RDBMS 目标端的类型映射与 DDL 生成工具。
 * 目标类型从源的 JDBC 类型名 + 长度/精度推导，按目标方言渲染，保证跨方言时不会直接把源类型写进 DDL。
 */
public final class RdbmsDdlUtil
{
    private RdbmsDdlUtil()
    {
    }

    /**
     * 目标列类型的规范分类。先把源类型归一到分类，再按方言渲染，避免逐方言维护交叉映射表。
     */
    private enum Kind
    {
        STRING, TEXT, INTEGER, BIGINT, SMALLINT, TINYINT, BOOLEAN,
        DECIMAL, FLOAT, DOUBLE, DATE, TIME, TIMESTAMP, TIMESTAMPTZ,
        BINARY, JSON, RAW
    }

    public static String quoteIdent(DbType dialect, String name)
    {
        if (name == null)
        {
            return null;
        }
        if (dialect == DbType.MYSQL)
        {
            return "`" + name.replace("`", "``") + "`";
        }
        if (dialect == DbType.SQLSERVER)
        {
            return "[" + name.replace("]", "]]") + "]";
        }
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /**
     * 目标表的限定名。PostgreSQL 使用连接默认 schema，不拼库名（与 writer 的 table 占位符保持一致）。
     */
    public static String tableRef(DbType dialect, String db, String table)
    {
        if (dialect == DbType.POSTGRESQL)
        {
            return quoteIdent(dialect, table);
        }
        if (db == null || db.isBlank())
        {
            return quoteIdent(dialect, table);
        }
        return quoteIdent(dialect, db) + "." + quoteIdent(dialect, table);
    }

    public static String columnDefinition(DbType dialect, String name, String sourceType,
        int length, int precision, int scale, String comment)
    {
        String def = quoteIdent(dialect, name) + " " + renderType(dialect, sourceType, length, precision, scale);
        if (dialect == DbType.MYSQL && comment != null && !comment.isBlank())
        {
            def += " COMMENT '" + normalizeComment(comment) + "'";
        }
        return def;
    }

    public static String createTableIfNotExists(DbType dialect, String tableRef, List<String> columnDefs, String tableComment)
    {
        String body = String.join(",\n", columnDefs);
        switch (dialect)
        {
            case MYSQL:
                StringBuilder sb = new StringBuilder();
                sb.append("CREATE TABLE IF NOT EXISTS ").append(tableRef).append(" (\n")
                    .append(body).append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                if (tableComment != null && !tableComment.isBlank())
                {
                    sb.append(" COMMENT='").append(normalizeComment(tableComment)).append("'");
                }
                return sb.toString();
            case SQLSERVER:
                return "IF OBJECT_ID(" + tableRef + ", 'U') IS NULL CREATE TABLE " + tableRef + " (\n" + body + "\n)";
            default:
                return "CREATE TABLE IF NOT EXISTS " + tableRef + " (\n" + body + "\n)";
        }
    }

    public static String addColumn(DbType dialect, String tableRef, String columnDef)
    {
        if (dialect == DbType.SQLSERVER)
        {
            return "ALTER TABLE " + tableRef + " ADD " + columnDef;
        }
        return "ALTER TABLE " + tableRef + " ADD COLUMN " + columnDef;
    }

    public static String modifyColumn(DbType dialect, String tableRef, String name, String sourceType,
        int length, int precision, int scale, String comment)
    {
        String def = columnDefinition(dialect, name, sourceType, length, precision, scale, comment);
        if (dialect == DbType.POSTGRESQL)
        {
            return "ALTER TABLE " + tableRef + " ALTER COLUMN " + quoteIdent(dialect, name)
                + " TYPE " + renderType(dialect, sourceType, length, precision, scale);
        }
        return "ALTER TABLE " + tableRef + " MODIFY COLUMN " + def;
    }

    /**
     * 现有列类型是否与期望一致。JDBC 元数据只给出类型名，长度差异（如 varchar(50) vs varchar(255)）不触发变更，
     * 避免对已有表做有损或高代价的类型改写。
     */
    public static boolean typeMatches(DbType dialect, String currentTypeName, String sourceType, int length, int precision, int scale)
    {
        if (currentTypeName == null || currentTypeName.isBlank())
        {
            return false;
        }
        String current = baseName(currentTypeName);
        String desired = baseName(renderType(dialect, sourceType, length, precision, scale));
        return current.equalsIgnoreCase(desired) || alias(current).equalsIgnoreCase(alias(desired));
    }

    private static String baseName(String type)
    {
        if (type == null)
        {
            return "";
        }
        String t = type.toLowerCase().trim();
        int paren = t.indexOf('(');
        if (paren > 0)
        {
            t = t.substring(0, paren).trim();
        }
        int space = t.indexOf(' ');
        if (space > 0)
        {
            t = t.substring(0, space).trim();
        }
        return t;
    }

    /**
     * 收敛常见等价类型名，减少因方言别名导致的误判改写。
     */
    private static String alias(String t)
    {
        switch (t)
        {
            case "int":
            case "int4":
            case "serial":
                return "integer";
            case "int8":
            case "serial8":
                return "bigint";
            case "int2":
                return "smallint";
            case "bool":
                return "boolean";
            case "numeric":
            case "numeric,":
            case "number":
                return "decimal";
            case "float4":
                return "real";
            case "float8":
                return "double";
            case "datetime":
                return "timestamp";
            case "bytea":
            case "blob":
            case "longblob":
                return "binary";
            case "jsonb":
                return "json";
            default:
                return t;
        }
    }

    public static String renderType(DbType dialect, String sourceType, int length, int precision, int scale)
    {
        Kind kind = kindOf(sourceType);
        switch (kind)
        {
            case STRING:
            {
                int n = length > 0 ? length : 255;
                switch (dialect)
                {
                    case POSTGRESQL:
                        return n > 10485760 ? "text" : "varchar(" + n + ")";
                    case ORACLE:
                        return n > 4000 ? "clob" : "varchar2(" + Math.max(1, Math.min(n, 4000)) + ")";
                    case SQLSERVER:
                        return n > 4000 ? "nvarchar(max)" : "nvarchar(" + n + ")";
                    default:
                        return n > 65535 ? "text" : "varchar(" + n + ")";
                }
            }
            case TEXT:
                switch (dialect)
                {
                    case POSTGRESQL:
                        return "text";
                    case ORACLE:
                        return "clob";
                    case SQLSERVER:
                        return "nvarchar(max)";
                    default:
                        return "text";
                }
            case INTEGER:
                return dialect == DbType.POSTGRESQL ? "integer" : "int";
            case BIGINT:
                return "bigint";
            case SMALLINT:
                return "smallint";
            case TINYINT:
                return dialect == DbType.POSTGRESQL ? "smallint" : "tinyint";
            case BOOLEAN:
                if (dialect == DbType.POSTGRESQL)
                {
                    return "boolean";
                }
                if (dialect == DbType.MYSQL)
                {
                    return "tinyint(1)";
                }
                return "boolean";
            case DECIMAL:
            {
                int p = precision > 0 ? precision : 10;
                int s = Math.max(scale, 0);
                if (s > p)
                {
                    s = p;
                }
                return (dialect == DbType.POSTGRESQL ? "numeric(" : "decimal(") + p + "," + s + ")";
            }
            case FLOAT:
                return dialect == DbType.POSTGRESQL ? "real" : "float";
            case DOUBLE:
                return dialect == DbType.POSTGRESQL ? "double precision" : "double";
            case DATE:
                return "date";
            case TIME:
                return "time";
            case TIMESTAMP:
                return dialect == DbType.MYSQL ? "datetime" : "timestamp";
            case TIMESTAMPTZ:
                return dialect == DbType.POSTGRESQL ? "timestamptz" : "timestamp";
            case BINARY:
                if (dialect == DbType.MYSQL)
                {
                    return "longblob";
                }
                return dialect == DbType.POSTGRESQL ? "bytea" : "blob";
            case JSON:
                return dialect == DbType.POSTGRESQL ? "jsonb" : "json";
            case RAW:
            default:
                // Unknown/source-specific type: keep the source spelling as a last resort.
                return (sourceType == null || sourceType.isBlank()) ? "text" : sourceType.trim();
        }
    }

    private static Kind kindOf(String sourceType)
    {
        if (sourceType == null)
        {
            return Kind.TEXT;
        }
        String t = sourceType.toLowerCase().trim();
        int paren = t.indexOf('(');
        if (paren > 0)
        {
            t = t.substring(0, paren).trim();
        }
        switch (t)
        {
            case "char", "varchar", "varchar2", "nvarchar", "nvarchar2", "nchar", "bpchar",
                 "character", "character varying", "string", "uuid", "uniqueidentifier":
                return Kind.STRING;
            case "text", "tinytext", "mediumtext", "longtext", "clob", "nclob", "ntext",
                 "long", "longvarchar", "long varchar":
                return Kind.TEXT;
            case "int", "integer", "int4", "mediumint", "serial", "serial4":
                return Kind.INTEGER;
            case "bigint", "int8", "bigserial", "serial8":
                return Kind.BIGINT;
            case "smallint", "int2", "smallserial", "serial2":
                return Kind.SMALLINT;
            case "tinyint", "bit":
                return Kind.TINYINT;
            case "bool", "boolean":
                return Kind.BOOLEAN;
            case "decimal", "numeric", "number", "dec", "fixed", "money", "smallmoney":
                return Kind.DECIMAL;
            case "float", "real", "float4", "binary_float":
                return Kind.FLOAT;
            case "double", "double precision", "float8", "binary_double":
                return Kind.DOUBLE;
            case "date":
                return Kind.DATE;
            case "time", "timetz", "time without time zone", "time with time zone":
                return Kind.TIME;
            case "datetime", "smalldatetime", "timestamp", "timestamp without time zone":
                return Kind.TIMESTAMP;
            case "timestamptz", "timestamp with time zone":
                return Kind.TIMESTAMPTZ;
            case "binary", "varbinary", "blob", "longblob", "mediumblob", "tinyblob", "bytea", "image", "raw":
                return Kind.BINARY;
            case "json", "jsonb":
                return Kind.JSON;
            default:
                return Kind.RAW;
        }
    }

    private static String normalizeComment(String comment)
    {
        return comment.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim().replace("'", "''");
    }
}
