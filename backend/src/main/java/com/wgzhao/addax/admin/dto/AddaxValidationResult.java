package com.wgzhao.addax.admin.dto;

import java.util.List;

/**
 * Addax 目录校验的总体结果，按检查项逐个给出状态，便于前端渲染为清单。
 */
public record AddaxValidationResult(boolean success, List<AddaxCheckItem> checks)
{
}
