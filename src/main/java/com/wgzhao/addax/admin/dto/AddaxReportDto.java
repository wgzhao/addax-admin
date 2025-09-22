package com.wgzhao.addax.admin.dto;

import lombok.Data;

@Data
public class AddaxReportDto {
    private String jobName;
    private long startTimeStamp;
    private long endTimeStamp;
    private int totalCosts;
    private int byteSpeedPerSecond;
    private int recordSpeedPerSecond;
    private long totalReadRecords;
    private int totalErrorRecords;

    @Override
    public String toString() {
        return "AddaxReportDto{" +
                "jobName='" + jobName + '\'' +
                ", startTimeStamp=" + startTimeStamp +
                ", endTimeStamp=" + endTimeStamp +
                ", totalCoasts=" + totalCosts +
                ", byteSpeedPerSecond=" + byteSpeedPerSecond +
                ", recordSpeedPerSecond=" + recordSpeedPerSecond +
                ", totalReadRecords=" + totalReadRecords +
                ", totalErrorRecords=" + totalErrorRecords +
                '}';
    }
}
