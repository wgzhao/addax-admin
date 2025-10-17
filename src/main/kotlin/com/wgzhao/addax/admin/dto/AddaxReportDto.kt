package com.wgzhao.addax.admin.dto

data class AddaxReportDto(
    val jobName: String,
    val startTimeStamp: Long = 0,
    val endTimeStamp: Long = 0,
    val totalCosts: Long = 0,
    val totalBytes: Long = 0,
    val byteSpeedPerSecond: Long = 0,
    val recordSpeedPerSecond: Long = 0,
    val totalReadRecords: Long = 0,
    val totalErrorRecords: Long = 0
)
