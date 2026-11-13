package com.wgzhao.addax.admin.dto;

import java.util.Map;

public record FailureStatsDto(long total, long distinctTables, Map<String, Long> byStage,
    Map<String, Long> byCategory)
{
}
