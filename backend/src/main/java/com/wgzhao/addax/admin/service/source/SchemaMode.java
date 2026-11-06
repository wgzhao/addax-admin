package com.wgzhao.addax.admin.service.source;

/**
 * The two existing RDBMS metadata paths differ subtly (catalog/schema selection and primary-key
 * handling), so the adapter keeps them separate instead of pretending one shape fits both.
 */
public enum SchemaMode
{
    /** Read-only preview used by the collection plan wizard. */
    PREVIEW,
    /** Column discovery used when a definition is created or refreshed. */
    COLUMN_REFRESH
}
