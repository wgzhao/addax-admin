package com.wgzhao.addax.admin.service.source;

/**
 * Where a target type is going to be used. The RDBMS source adapter returns the source type verbatim
 * for {@link #ETL_COLUMN} (the historical ColumnService behavior) and a rendered DDL type for
 * {@link #PLANNED_DDL} (the historical plan-preview behavior).
 */
public enum TypePurpose
{
    ETL_COLUMN,
    PLANNED_DDL
}
