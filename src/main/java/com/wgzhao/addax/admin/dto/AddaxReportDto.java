package com.wgzhao.addax.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Getter
@Service
@AllArgsConstructor
@NoArgsConstructor
public class AddaxReportDto {
    private String jobName;
    private int startTimeStamp;
    private int endTimeStamp;
    private int totalCosts;
    private long totalBytes;
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
                ", totalBytes=" + totalBytes +
                ", byteSpeedPerSecond=" + byteSpeedPerSecond +
                ", recordSpeedPerSecond=" + recordSpeedPerSecond +
                ", totalReadRecords=" + totalReadRecords +
                ", totalErrorRecords=" + totalErrorRecords +
                '}';
    }
}
