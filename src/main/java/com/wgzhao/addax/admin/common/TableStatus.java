package com.wgzhao.addax.admin.common;

public enum TableStatus
{
    // 表采集状态
    NOT_COLLECT("N", "未采集"),
    COLLECTING("R", "采集中"),
    COLLECTED("Y", "采集完成"),
    COLLECT_FAIL("F", "采集失败"),
    WAITING_COLLECT("W", "等待采集"),
    EXCLUDE_COLLECT("X", "不采集"),
    WAIT_SCHEMA("U", "等待同步表结构");
    TableStatus(String status, String mark)
    {

    }
}
