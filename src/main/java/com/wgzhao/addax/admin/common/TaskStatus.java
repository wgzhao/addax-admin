package com.wgzhao.addax.admin.common;

/**
 * 采集任务状态
 *
 * @author wgzhao
 */
public class TaskStatus {
    // 采集任务状态
    public static final String COLLECTING = "R"; // 采集中
    public static final String COLLECTED = "Y"; // 采集完成
    public static final String COLLECT_FAIL = "F"; // 采集失败
    public static final String WAITING_COLLECT = "W"; // 等待采集
    public static final String EXCLUDE_COLLECT = "X"; // 不采集

    private TaskStatus() {}
}
