package com.wgzhao.addax.admin.alert.dto;

import java.time.Instant;

public record AlertChannelTestResult(
    boolean ok,
    String message,
    Integer responseCode,
    String responseBody,
    long latencyMs,
    Instant testedAt
)
{
}
