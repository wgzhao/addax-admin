package com.wgzhao.addax.admin.dto;

import java.util.List;

/**
 * 目标端健康检查结果。条目结构与 Addax 目录校验一致，前端可复用同一套渲染。
 *
 * @param success 没有任何 fail 条目时为 true
 * @param family  目标端族（HDFS / RDBMS）
 * @param checks  检查项，按执行顺序排列
 */
public record TargetHealthCheckResult(boolean success, String family, List<AddaxCheckItem> checks)
{
}
