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
    // 表更新状态
    NEED_UPDATE_TABLE("Y", "需要更新表信息"),
    NO_NEED_UPDATE_TABLE("N", "不需要更新表信息"),
    // 目标表创建状态
    NEED_CREATE_TABLE("Y", "需要创建目标表"),
    NO_NEED_CREATE_TABLE("N", "不需要创建目标表"),
    CREATE_TABLE_SUCCESS("S", "目标表创建成功"),
    CREATE_TABLE_FAIL("F", "目标表创建失败");
    TableStatus(String status, String mark)
    {

    }
}
