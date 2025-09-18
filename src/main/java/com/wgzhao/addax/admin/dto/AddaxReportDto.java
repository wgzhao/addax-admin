package com.wgzhao.addax.admin.dto;

import lombok.Data;

@Data
public class AddaxReportDto {
    private String jobName;
    private int startTimeStamp;
    private int endTimeStamp;
    private int totalCosts;
    private int byteSpeedPerSecond;
    private int recordSpeedPerSecond;
    private int totalReadRecords;
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
