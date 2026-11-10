package com.wgzhao.addax.admin.alert.dto;

public record AlertSummaryDto(
    long channelCount,
    long enabledChannelCount,
    boolean paused
)
{
}
