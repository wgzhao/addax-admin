package com.wgzhao.addax.admin.dto;

import java.util.List;

/**
 * 目标端健康检查的检查清单：key 与顺序由后端给出，前端据此先列出全部检查项，再逐项回填结果。
 * 清单与执行共用同一份定义，避免两边各自维护出现漂移。
 *
 * @param family 目标端族（HDFS / RDBMS）
 * @param items  按执行顺序排列的检查项
 */
public record TargetHealthCheckPlan(String family, List<Item> items)
{
    public record Item(String key, String label)
    {
    }
}
