package com.wgzhao.addax.admin.common;

public class TableStatus
{
    // 表采集状态
    // 未采集
    public static final String NOT_COLLECT = "N";
    // 采集中
    public static final String COLLECTING = "R";
    //采集完成
    public static final String COLLECTED = "Y";
    //采集失败
    public static final String COLLECT_FAIL = "E";
    //等待采集
    public static final String WAITING_COLLECT = "W";
    //不采集
    public static final String EXCLUDE_COLLECT = "X";
    //等待同步表结构
    public static final String WAIT_SCHEMA = "U";

    private TableStatus() {}
}
