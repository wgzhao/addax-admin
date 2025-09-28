package com.wgzhao.addax.admin.common;

public enum JourKind
{
    COLLECT("ETL", "表采集"),
    ADDAX_JOB("JOB", "生成采集模板"),
    SCHEMA("UPDATE_TABLE", "表结构同步"),
    PARTITION("PARTITION", "创建表分区"),
    UPDATE_TABLE("CREATE_TABLE", "创建目标表"),
    UPDATE_COLUMN("UPDATE_COLUMN", "表字段更新" ),
    CREATE_COLUMN("CREATE_COLUMN", "首次同步表结构" );

    JourKind(String tbl, String comment)
    {

    }

}
