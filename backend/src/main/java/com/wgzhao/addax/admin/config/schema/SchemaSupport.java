package com.wgzhao.addax.admin.config.schema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Lookups the migration units share.
 *
 * <p>They deliberately run on the migration's own connection: the DDL executed around them is
 * uncommitted, so a separate pooled connection (JdbcTemplate) would not see it.</p>
 */
public final class SchemaSupport
{
    private SchemaSupport()
    {
    }

    /**
     * Existence check on the migration connection. {@code relation} and {@code name} are constants
     * from the units, not input.
     */
    public static boolean exists(Connection connection, String relation, String name, String column)
        throws SQLException
    {
        String sql = "select count(*) from information_schema." + relation
            + " where table_schema = 'public' and table_name = ?"
            + (column == null ? "" : " and column_name = ?");
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, name);
            if (column != null)
            {
                ps.setString(2, column);
            }
            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Whether a varchar column is still narrower than the definition this build expects. A missing
     * column or an unbounded type ({@code text}) needs no widening.
     */
    public static boolean needsWidening(Connection connection, String table, String column, int minLength)
        throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement("""
                select coalesce(character_maximum_length, 0)
                  from information_schema.columns
                 where table_schema = 'public' and table_name = ? and column_name = ?
                """))
        {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery())
            {
                if (!rs.next())
                {
                    return false;
                }
                long length = rs.getLong(1);
                return length > 0 && length < minLength;
            }
        }
    }
}
