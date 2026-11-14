package com.wgzhao.addax.admin.dto;

import java.time.LocalDate;

/**
 * 手动触发对账请求：对指定采集表在指定业务日期的采集结果做一次对账。
 */
public record ReconcileRunRequest(Long tid, LocalDate bizDate)
{
}
