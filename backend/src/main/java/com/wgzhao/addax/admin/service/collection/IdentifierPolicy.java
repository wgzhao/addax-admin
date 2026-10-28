package com.wgzhao.addax.admin.service.collection;

import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import static com.wgzhao.addax.admin.common.Constants.HDFS_TARGET_TYPES;

/**
 * How a dialect compares database and table identifiers.
 *
 * <p>Source and target names must be keyed with the same rule the database itself uses. Guessing with
 * {@code toLowerCase()} hides distinct tables on a case-sensitive source and still misses database
 * names whose case differs. The policy is deliberately resolved from the live connection; when the
 * probe fails we fall back to exact matching, which can only offer a table again, never hide one.</p>
 */
@Slf4j
public enum IdentifierPolicy
{
    EXACT,
    INSENSITIVE;

    public String key(String value)
    {
        if (value == null) {
            return "";
        }
        return this == INSENSITIVE ? value.toLowerCase(Locale.ROOT) : value;
    }

    public static IdentifierPolicy forSource(Connection connection, String dbName)
    {
        if (connection == null) {
            return EXACT;
        }
        try
        {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (product.contains("mysql") || product.contains("mariadb"))
            {
                return mysqlPolicy(connection);
            }
            if (product.contains("microsoft sql server") || product.contains("sql server"))
            {
                return sqlServerPolicy(connection, dbName);
            }
            if (product.contains("hive") || product.contains("sqlite"))
            {
                return INSENSITIVE;
            }
        }
        catch (SQLException e)
        {
            log.debug("Failed to resolve identifier policy, fallback to exact: {}", e.getMessage());
        }
        return EXACT;
    }

    public static IdentifierPolicy forTarget(EtlTarget target)
    {
        if (target == null)
        {
            return EXACT;
        }
        String type = target.getTargetType() == null
            ? "" : target.getTargetType().trim().toUpperCase(Locale.ROOT);
        String writer = target.getWriterTemplateKey() == null
            ? "" : target.getWriterTemplateKey().trim().toUpperCase(Locale.ROOT);
        if ("WH".equals(writer))
        {
            return INSENSITIVE;
        }
        if ("WR".equals(writer))
        {
            return EXACT;
        }
        // The routing fallback is HDFS when no type is configured. Hive stores database and table
        // names in lower case even when the DDL used mixed case.
        if (type.isEmpty() || HDFS_TARGET_TYPES.contains(type))
        {
            return INSENSITIVE;
        }
        return EXACT;
    }

    private static IdentifierPolicy mysqlPolicy(Connection connection) throws SQLException
    {
        try (Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("SELECT @@lower_case_table_names"))
        {
            if (rs.next())
            {
                return rs.getInt(1) == 0 ? EXACT : INSENSITIVE;
            }
        }
        return EXACT;
    }

    private static IdentifierPolicy sqlServerPolicy(Connection connection, String dbName) throws SQLException
    {
        DbUtil.useDatabase(connection, dbName);
        try (Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("SELECT CASE WHEN 'a' = 'A' THEN 1 ELSE 0 END"))
        {
            if (rs.next())
            {
                return rs.getInt(1) == 1 ? INSENSITIVE : EXACT;
            }
        }
        return EXACT;
    }
}
