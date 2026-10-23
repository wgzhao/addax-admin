package com.wgzhao.addax.admin.common;

import java.util.Locale;

/**
 * Who owns target table and column DDL.
 */
public enum SchemaManagementMode
{
    MANAGED,
    EXTERNAL;

    public static SchemaManagementMode from(String value)
    {
        if (value == null || value.isBlank())
        {
            return MANAGED;
        }
        try
        {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Unsupported schemaManagementMode: " + value);
        }
    }
}
