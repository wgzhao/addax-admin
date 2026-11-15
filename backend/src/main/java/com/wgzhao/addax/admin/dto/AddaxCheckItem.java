package com.wgzhao.addax.admin.dto;

/**
 * 单条 Addax 目录校验结果。
 *
 * @param key     检查项标识，前端据此渲染图标与分组
 * @param label   检查项名称
 * @param status  结果状态：ok / fail / skip
 * @param message 结果描述，失败时给出具体原因
 * @param hint    修复提示，仅在失败时非空
 */
public record AddaxCheckItem(
    String key,
    String label,
    String status,
    String message,
    String hint
)
{
}
