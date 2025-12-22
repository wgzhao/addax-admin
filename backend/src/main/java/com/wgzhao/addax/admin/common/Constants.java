package com.wgzhao.addax.admin.common;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 常量
 *
 * @author wgzhao
 */
public final class Constants
{
    public static final String DELETED_PLACEHOLDER_PREFIX = "__deleted__";
    public static final String SPECIAL_FILTER_PLACEHOLDER = "__max__";
    public static final long ADDAX_EXECUTE_TIME_OUT_SECONDS = 2 * 60 * 60;
    // 默认的切日时间
    public static final String DEFAULT_SWITCH_TIME = "16:30";
    public static final int HIVE_DECIMAL_MAX_PRECISION = 38;
    public static final int HIVE_DECIMAL_MAX_SCALE = 10;
    // Redis key for schema refresh lock (used to prevent submits/enqueues while schema refresh runs)
    public static final String SCHEMA_REFRESH_LOCK_KEY = "schema:refresh:lock";
    public static final String DEFAULT_PART_FORMAT = "yyyyMMdd";
    public static final DateTimeFormatter shortSdf = DateTimeFormatter.ofPattern(DEFAULT_PART_FORMAT);
    // Default SQL reserved keywords (used as fallback and baseline). Stored in upper-case.
    private static final Set<String> DEFAULT_SQL_RESERVED_KEYWORDS = Set.of(
        "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUTHORIZATION",
        "BACKUP", "BEFORE", "BETWEEN", "BREAK", "BROWSE", "BULK", "BY", "CASCADE", "CASE", "CAST", "CATALOG", "CHANGE", "CHECK", "CHECKPOINT",
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
        "SECURITYAUDIT", "SELECT", "SEMANTICKEYPHRASETABLE", "SESSION_USER", "SET", "SETUSER", "SHOW", "SHUTDOWN", "SOME", "STATISTICS", "SYSTEM",
        "SYSTEM_USER", "TABLE", "TABLESAMPLE", "TEXTSIZE", "THEN", "TO", "TOP", "TRAN", "TRANSACTION", "TRIGGER", "TRUNCATE", "TRY_CONVERT",
        "TSEQUAL", "UNION", "UNIQUE", "UNPIVOT", "UPDATE", "UPDATETEXT", "USE", "USER", "USING", "VALUES", "VARYING", "VIEW", "WAITFOR", "WHEN",
        "WHERE", "WHILE", "WITH", "WRITETEXT", "XACT_ABORT",
        // Common SQL data types — treat as reserved for column-name safety
        "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "NUMERIC", "FLOAT", "REAL", "DOUBLE",
        "BOOLEAN", "BOOL", "CHAR", "VARCHAR", "NVARCHAR", "TEXT", "TIME", "YEAR", "DATETIME", "TIMESTAMP", "TIMESTAMP_WITH_TIMEZONE",
        "BINARY", "VARBINARY", "BLOB", "CLOB", "JSON"
    );
    /**
     * The effective set of SQL reserved keywords in upper-case. May be replaced at startup by DB-loaded values.
     */
    public static volatile Set<String> SQL_RESERVED_KEYWORDS = Collections.unmodifiableSet(new LinkedHashSet<>(DEFAULT_SQL_RESERVED_KEYWORDS));

    private Constants()
    {
    }
}
