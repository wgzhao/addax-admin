package com.wgzhao.addax.admin.dto;

import lombok.Data;

@Data
public class AddaxReportDto {
    private String jobName;
    private long startTimeStamp;
    private long endTimeStamp;
    private long totalCosts;
    private long totalBytes;
    private long byteSpeedPerSecond;
    private long recordSpeedPerSecond;
    private long totalReadRecords;
    private long totalErrorRecords;

    @Override
    public String toString() {
        return "AddaxReportDto{" +
                "jobName='" + jobName + '\'' +
                ", startTimeStamp=" + startTimeStamp +
                ", endTimeStamp=" + endTimeStamp +
                ", totalCoasts=" + totalCosts +
                ", totalBytes=" + totalBytes +
                ", byteSpeedPerSecond=" + byteSpeedPerSecond +
                ", recordSpeedPerSecond=" + recordSpeedPerSecond +
                ", totalReadRecords=" + totalReadRecords +
                ", totalErrorRecords=" + totalErrorRecords +
                '}';
    }
}
