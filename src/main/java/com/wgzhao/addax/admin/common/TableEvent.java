package com.wgzhao.addax.admin.common;

public enum TableEvent {
    // 开始处理（刷新元数据、生成配置等）
    START_PROCESSING,
    // 处理成功
    PROCESS_SUCCESS,
    // 处理失败
    PROCESS_FAIL,
    // 调度器选中，开始采集
    START_COLLECT,
    // 采集成功
    COLLECT_SUCCESS,
    // 采集失败
    COLLECT_FAIL,
    // 用户禁用
    DISABLE,
    // 用户启用
    ENABLE,
    // 检测到结构变更
    DETECT_SCHEMA_CHANGE,
    // 结构变更已处理
    SCHEMA_CHANGE_PROCESSED,
    // 重试
    RETRY
}

