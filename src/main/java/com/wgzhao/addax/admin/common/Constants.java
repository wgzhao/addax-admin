package com.wgzhao.addax.admin.common;

import java.util.Locale;
import java.util.Set;

/**
 * 常量
 *
 * @author wgzhao
 */
public final class Constants
{
    private Constants() {
    }

    public static final String DELETED_PLACEHOLDER_PREFIX = "__deleted__";

    public static final long ADDAX_EXECUTE_TIME_OUT_SECONDS = 2 * 60 * 60;

    public static final Set<String> SQL_RESERVED_KEYWORDS =  Set.of("ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUTHORIZATION",
            "BACKUP", "BEFORE", "BETWEEN", "BREAK", "BROWSE", "BULK", "BY", "CASCADE", "CASE", "CAST", "CATALOG", "CHECK", "CHECKPOINT",
            "CLOSE", "CLUSTERED", "COALESCE", "COLLATE", "COLUMN", "COMMIT", "COMPUTE", "CONSTRAINT", "CONTAINS", "CONTINUE", "CONVERT",
            "CREATE", "CURRENT", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR", "DATABASE", "DATE", "DAY",
            "DEALLOCATE", "DECLARE", "DEFAULT", "DELETE", "DESC", "DISK", "DISTINCT", "DISTRIBUTED", "DO", "DROP", "DUMP", "ELSE", "END",
            "ERRLVL", "ESCAPE", "EXCEPT", "EXEC", "EXECUTE", "EXISTS", "EXIT", "EXPLAIN", "EXTERNAL", "FETCH", "FILE", "FOR", "FOREIGN", "FREETEXT",
            "FREETEXTTABLE", "FROM", "FULL", "FUNCTION", "GOTO", "GRANT", "GROUP", "HAVING", "HOLDLOCK", "IDENTITY", "IDENTITYCOL",
            "IDENTITY_INSERT", "IF", "IN", "INDEX", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "KEY", "KILL", "LEFT", "LIKE",
            "LINENO", "LOAD", "MERGE", "NATIONAL", "NOCHECK", "NONCLUSTERED", "NOT", "NULL", "NULLIF", "OF", "OFF", "OFFSETS", "ON", "OPEN",
            "OPENDATASOURCE", "OPENQUERY", "OPENROWSET", "OPENXML", "OPTION", "OR", "ORDER", "OUTER", "OVER", "PERCENT", "PIVOT", "PLAN",
            "PRECISION", "PRIMARY", "PRINT", "PROC", "PROCEDURE", "PUBLIC", "RAISERROR", "RANK", "READ", "READTEXT", "RECONFIGURE", "REFERENCES",
            "REPLICATION", "RESTORE", "RESTRICT", "RETURN", "REVOKE", "RIGHT", "ROLLBACK", "ROWCOUNT", "ROWGUIDCOL", "RULE", "SAVE", "SCHEMA",
            "SECURITYAUDIT", "SELECT", "SEMANTICKEYPHRASETABLE", "SESSION_USER", "SET", "SETUSER", "SHUTDOWN", "SOME", "STATISTICS", "SYSTEM",
            "SYSTEM_USER", "TABLE", "TABLESAMPLE", "TEXTSIZE", "THEN", "TO", "TOP", "TRAN", "TRANSACTION", "TRIGGER", "TRUNCATE", "TRY_CONVERT",
            "TSEQUAL", "UNION", "UNIQUE", "UNPIVOT", "UPDATE", "UPDATETEXT", "USE", "USER", "VALUES", "VARYING", "VIEW", "WAITFOR", "WHEN",
            "WHERE", "WHILE", "WITH", "WRITETEXT", "XACT_ABORT");

    /**
     * 判断给定名称是否为 SQL 保留关键字（大小写不敏感）。
     */
    public static boolean isSqlKeyword(String name) {
        if (name == null) {
            return false;
        }
        return SQL_RESERVED_KEYWORDS.contains(name.toUpperCase(Locale.ROOT));
    }

    // 如果列名是 SQL 关键字或包含空格/中划线，则加上引号
    public static String quoteColumnIfNeeded(String columnName, String quoteChar) {

        if (isSqlKeyword(columnName) || columnName.contains(" ") || columnName.contains("-")) {
            return quoteChar + columnName + quoteChar;
        }
        return columnName;
    }

    public static String quoteColumnIfNeeded(String columnName) {

        return quoteColumnIfNeeded(columnName, "`");
    }
}
