package com.wgzhao.addax.admin.model;

/**
 * 统一的ETL表状态枚举
 */
public enum EtlTableStatus {
    COMPLETED(0, "今日采集完成/成功"),
    PENDING_RESOURCE(10, "等待资源调度"),
    EXECUTING(20, "正在执行"),
    SCHEDULED(30, "等待计划采集时间"),
    COLLECT_FAILED(31, "采集失败"),
    WAIT_SCHEMA_UPDATE(40, "等待表结构更新"),
    SCHEMA_UPDATE_FAILED(41, "表结构更新失败"),
    WAIT_TARGET_CREATE(50, "等待目标表创建"),
    TARGET_CREATE_FAILED(51, "目标表创建失败"),
    DISABLED(99, "禁用此表");

    private final int code;
    private final String desc;

    EtlTableStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static EtlTableStatus fromCode(Integer code) {
        if (code == null) return null;
        for (EtlTableStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
