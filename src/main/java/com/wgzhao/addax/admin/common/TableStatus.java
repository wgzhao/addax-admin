package com.wgzhao.addax.admin.common;

import java.util.Set;

public enum TableStatus {
    /**
     * 表记录刚创建，尚未开始处理
     */
    INITIAL("I", "初始状态"),
    /**
     * 正在刷新表结构、更新目标表和生成任务配置
     */
    REFRESHING("P", "处理中"),
    /**
     * 表已就绪，可以被调度器选中并执行采集
     */
    READY("N", "待采集"),
    /**
     * 采集中
     */
    COLLECTING("R", "采集中"),
    /**
     * 本次采集周期成功
     */
    COLLECTED("Y", "采集完成"),
    /**
     * 任何步骤失败（包括刷新或采集）
     */
    FAILED("E", "采集失败"),
    /**
     * 用户手动禁用，不参与任何流程
     */
    DISABLED("X", "禁用"),
    /**
     * 检测到源表结构变更，需要人工介入或自动处理
     */
    SCHEMA_CHANGED("U", "结构变更");

    private final String code;
    private final String description;

    TableStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TableStatus fromCode(String code) {
        for (TableStatus status : TableStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown table status code: " + code);
    }
}

