package com.wgzhao.addax.admin.common;

/**
 * 采集任务状态
 *
 * @author wgzhao
 */
public enum TaskStatus
{
    COLLECTING("R", "采集中"),
    COLLECTED("Y", "采集完成"),
    COLLECT_FAIL("F", "采集失败"),
    WAITING_COLLECT("W", "等待采集"),
    EXCLUDE_COLLECT("X", "不采集");
    TaskStatus(String status, String mark)
    {
    }
}
