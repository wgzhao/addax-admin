package com.wgzhao.addax.admin.common;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum DbType
{
    MYSQL("mysql", "jdbc:mysql"),
    POSTGRESQL("postgresql", "jdbc:postgresql"),
    ORACLE("oracle", "jdbc:oracle"),
    SQLSERVER("sqlserver", "jdbc:sqlserver"),
    HIVE("hive", "jdbc:hive2", "jdbc:hive"),
    DB2("db2", "jdbc:db2"),
    SQLITE("sqlite", "jdbc:sqlite"),
    SYBASE("sybase", "jdbc:sybase"),
    CLICKHOUSE("clickhouse", "jdbc:clickhouse", "jdbc/chk"),
    // fallback/default
    RDBMS("rdbms");

    /**
     * -- GETTER --
     * 返回枚举对应的实际字符串值，例如 MYSQL -> "mysql"
     */
    @Getter private final String value;
    private final String[] jdbcPrefixes;

    DbType(String value, String... jdbcPrefixes)
    {
        this.value = value;
        this.jdbcPrefixes = jdbcPrefixes == null ? new String[0] : jdbcPrefixes;
    }

    /**
     * 根据 value 查找对应枚举（不区分大小写）
     */
    public static Optional<DbType> fromValue(String v)
    {
        if (v == null) {
            return Optional.empty();
        }
        String normalized = v.trim();
        for (DbType t : values()) {
            if (t.value.equalsIgnoreCase(normalized) || t.name().equalsIgnoreCase(normalized)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /**
     * 返回一个有序的 Map，将每个 jdbcPrefix 映射到对应的 value（比如 "jdbc:mysql" -> "mysql"）
     */
    public static Map<String, String> jdbcKindMap()
    {
        Map<String, String> m = new LinkedHashMap<>();
        for (DbType t : values()) {
            for (String p : t.jdbcPrefixes) {
                if (p != null && !p.isEmpty()) {
                    m.put(p, t.value);
                }
            }
        }
        return m;
    }
}
