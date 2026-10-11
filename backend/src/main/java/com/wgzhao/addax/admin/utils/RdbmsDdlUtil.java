package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.common.DbType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RDBMS 目标端的类型映射与 DDL 生成工具。
 * 目标类型优先使用源库原样类型（同方言），否则按目标方言从源的 JDBC 类型名 + 长度/精度推导。
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

    private static final Pattern CHAR_LEN = Pattern.compile("^(?:var)?char2?\\s*\\(\\s*(\\d+)");
    private static final Pattern DECIMAL_PS = Pattern.compile("^(?:decimal|numeric)\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)");

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

    /**
     * Build a column definition from an already-resolved type expression. Callers pass either a verbatim
     * source type (same dialect) or a rendered canonical type, so this stays dialect-agnostic.
     */
    public static String columnDefinition(DbType dialect, String name, String typeExpr, String comment)
    {
        return columnDefinition(dialect, name, typeExpr, false, comment);
    }

    public static String columnDefinition(DbType dialect, String name, String typeExpr, boolean notNull, String comment)
    {
        String def = quoteIdent(dialect, name) + " " + typeExpr;
        if (notNull)
        {
            def += " NOT NULL";
        }
        if (dialect == DbType.MYSQL && comment != null && !comment.isBlank())
        {
            def += " COMMENT '" + normalizeComment(comment) + "'";
        }
        return def;
    }

    public static String createTableIfNotExists(DbType dialect, String tableRef, List<String> columnDefs,
        List<String> primaryKeyColumns, String tableComment)
    {
        List<String> defs = new ArrayList<>(columnDefs);
        if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty())
        {
            List<String> pk = new ArrayList<>();
            for (String c : primaryKeyColumns)
            {
                pk.add(quoteIdent(dialect, c));
            }
            defs.add("PRIMARY KEY (" + String.join(", ", pk) + ")");
        }
        String body = String.join(",\n", defs);
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

    /**
     * MySQL 的 MODIFY 是整列重定义，未重述的属性会被重置为默认值，因此需一并重述 NOT NULL / DEFAULT /
     * AUTO_INCREMENT，否则会静默丢掉目标表已有的约束。其余方言仅调整类型。
     */
    public static String modifyColumn(DbType dialect, String tableRef, String name, String typeExpr, String comment,
        boolean notNull, String defaultValue, boolean autoIncrement)
    {
        if (dialect == DbType.POSTGRESQL)
        {
            return "ALTER TABLE " + tableRef + " ALTER COLUMN " + quoteIdent(dialect, name)
                + " TYPE " + typeExpr;
        }
        StringBuilder sb = new StringBuilder("ALTER TABLE ").append(tableRef)
            .append(" MODIFY COLUMN ").append(quoteIdent(dialect, name)).append(' ').append(typeExpr);
        if (notNull)
        {
            sb.append(" NOT NULL");
        }
        String def = safeDefault(defaultValue);
        if (def != null)
        {
            sb.append(" DEFAULT ").append(def);
        }
        if (autoIncrement && dialect == DbType.MYSQL)
        {
            sb.append(" AUTO_INCREMENT");
        }
        if (dialect == DbType.MYSQL && comment != null && !comment.isBlank())
        {
            sb.append(" COMMENT '").append(normalizeComment(comment)).append('\'');
        }
        return sb.toString();
    }

    /**
     * 现有列类型是否与期望一致（仅比较类型族）。族内差异（长度、枚举取值等）不算不一致，
     * 是否改写由 {@link #isWidening} 决定。
     */
    public static boolean typeMatches(String currentTypeName, String desiredTypeExpr)
    {
        if (currentTypeName == null || currentTypeName.isBlank()
            || desiredTypeExpr == null || desiredTypeExpr.isBlank())
        {
            return false;
        }
        return familyOf(currentTypeName).equals(familyOf(desiredTypeExpr));
    }

    public static String familyOf(String typeExpr)
    {
        return family(baseName(typeExpr));
    }

    /**
     * 期望类型是否比现有列"更宽"（容量变大）。只有变宽才发 MODIFY——变窄可能导致数据截断，一律不自动执行。
     * 无容量概念的类型（text/blob 等）返回 false。
     */
    public static boolean isWidening(String currentTypeName, int currentSize, int currentScale, String desiredTypeExpr)
    {
        if (currentTypeName == null || desiredTypeExpr == null)
        {
            return false;
        }
        String fam = familyOf(currentTypeName);
        if (!fam.equals(familyOf(desiredTypeExpr)))
        {
            return false;
        }
        if ("string".equals(fam))
        {
            int want = stringLength(desiredTypeExpr);
            return want > 0 && currentSize > 0 && want > currentSize;
        }
        if ("number".equals(fam))
        {
            int[] ps = decimalPrecisionScale(desiredTypeExpr);
            if (ps != null)
            {
                if (currentSize > 0 && ps[0] > currentSize)
                {
                    return true;
                }
                if (ps[1] > currentScale)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** varchar/char 的字符长度，非长度型返回 -1。 */
    public static int stringLength(String typeExpr)
    {
        if (typeExpr == null)
        {
            return -1;
        }
        Matcher m = CHAR_LEN.matcher(typeExpr.toLowerCase(Locale.ROOT).trim());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** decimal(numeric) 的 precision/scale，非该类型返回 null。 */
    public static int[] decimalPrecisionScale(String typeExpr)
    {
        if (typeExpr == null)
        {
            return null;
        }
        Matcher m = DECIMAL_PS.matcher(typeExpr.toLowerCase(Locale.ROOT).trim());
        if (!m.find())
        {
            return null;
        }
        return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    private static String baseName(String type)
    {
        if (type == null)
        {
            return "";
        }
        String t = type.toLowerCase(Locale.ROOT).trim();
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

    /**
     * 类型族：用于判断"是否需要改写列类型"。
     */
    private static String family(String base)
    {
        switch (alias(base))
        {
            case "char", "varchar", "varchar2", "nvarchar", "nvarchar2", "nchar", "string", "uuid", "uniqueidentifier",
                 "enum", "set", "text", "tinytext", "mediumtext", "longtext", "clob", "nclob", "ntext", "longvarchar":
                return "string";
            case "integer", "bigint", "smallint", "tinyint", "boolean", "decimal", "float", "real", "double", "number", "money":
                return "number";
            case "date", "time", "datetime", "timestamp", "timestamptz", "smalldatetime":
                return "temporal";
            case "binary", "varbinary", "blob", "bytea", "image":
                return "binary";
            case "json", "jsonb":
                return "json";
            default:
                return base;
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
                // ENUM/SET values are not stored in our column metadata, so degrade to a plain string column.
                if (isEnumOrSet(sourceType))
                {
                    n = Math.max(n, 255);
                }
                switch (dialect)
                {
                    case POSTGRESQL:
                        return n > 10485760 ? "text" : "varchar(" + n + ")";
                    case ORACLE:
                        return n > 4000 ? "clob" : "varchar2(" + Math.max(1, Math.min(n, 4000)) + ")";
                    case SQLSERVER:
                        return n > 4000 ? "nvarchar(max)" : "nvarchar(" + n + ")";
                    default:
                        // utf8mb4 caps a single varchar column at 16383 characters; above that use text.
                        return n > 16383 ? "text" : "varchar(" + n + ")";
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
                        // Cross-dialect CLOB/longtext sources must not shrink into 64KB text.
                        return "longtext";
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
        String t = sourceType.toLowerCase(Locale.ROOT).trim();
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
            case "enum", "set":
                return Kind.STRING;
            default:
                return Kind.RAW;
        }
    }

    private static boolean isEnumOrSet(String sourceType)
    {
        if (sourceType == null)
        {
            return false;
        }
        String t = sourceType.toLowerCase(Locale.ROOT).trim();
        return t.startsWith("enum") || t.startsWith("set");
    }

    /**
     * Only restate defaults we are confident about; an unrecognized default expression is dropped rather
     * than risking invalid DDL. Returns the SQL literal to append after DEFAULT, or null to omit.
     */
    private static String safeDefault(String v)
    {
        if (v == null)
        {
            return null;
        }
        String s = v.trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s))
        {
            return null;
        }
        if (s.matches("-?\\d+(\\.\\d+)?"))
        {
            return s;
        }
        if (s.matches("'([^']|'')*'"))
        {
            return s;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("current_timestamp") || lower.equals("now()"))
        {
            return s;
        }
        return null;
    }

    private static String normalizeComment(String comment)
    {
        return comment.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim().replace("'", "''");
    }
}
