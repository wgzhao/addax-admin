package com.wgzhao.addax.admin.dto;

public record AddaxReportDto(
        String jobName,
        long startTimeStamp,
        long endTimeStamp,
        long totalCosts,
        long totalBytes,
        long byteSpeedPerSecond,
        long recordSpeedPerSecond,
        long totalReadRecords,
        long totalErrorRecords
)
{
}
