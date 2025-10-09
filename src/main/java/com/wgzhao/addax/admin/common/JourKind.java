package com.wgzhao.addax.admin.common;

public class JourKind {
    // 流水类型
    public static final String COLLECT = "COLLECT"; // 表采集
    public static final String ADDAX_JOB = "ADDAX_JOB"; // 生成采集模板
    public static final String SCHEMA = "SCHEMA"; // 表结构同步
    public static final String PARTITION = "PARTITION"; // 创建表分区
    public static final String UPDATE_TABLE = "UPDATE_TABLE"; // 创建目标表
    public static final String UPDATE_COLUMN = "UPDATE_COLUMN"; // 表字段更新
    public static final String CREATE_COLUMN = "CREATE_COLUMN"; // 首次同步表结构

    private JourKind() {}
}
