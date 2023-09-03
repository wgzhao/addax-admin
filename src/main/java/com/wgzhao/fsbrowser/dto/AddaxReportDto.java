package com.wgzhao.fsbrowser.dto;

public class AddaxReportDto {
    private String jobName;
    private int startTimeStamp;
    private int endTimeStamp;
    private int totalCosts;
    private int byteSpeedPerSecond;
    private int recordSpeedPerSecond;
    private int totalReadRecords;
    private int totalErrorRecords;

    public String getJobName() {
        return jobName;
    }

    public int getStartTimeStamp() {
        return startTimeStamp;
    }

    public int getEndTimeStamp() {
        return endTimeStamp;
    }

    public int getTotalCosts() {
        return totalCosts;
    }

    public int getByteSpeedPerSecond() {
        return byteSpeedPerSecond;
    }

    public int getRecordSpeedPerSecond() {
        return recordSpeedPerSecond;
    }

    public int getTotalReadRecords() {
        return totalReadRecords;
    }

    public int getTotalErrorRecords() {
        return totalErrorRecords;
    }

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
