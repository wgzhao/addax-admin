package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.dto.AddaxLogDto
import com.wgzhao.addax.admin.dto.AddaxReportDto
import com.wgzhao.addax.admin.dto.ApiResponse
import com.wgzhao.addax.admin.model.EtlStatistic
import com.wgzhao.addax.admin.service.AddaxLogService
import com.wgzhao.addax.admin.service.StatService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 日志相关接口，主要用于获取采集日志和作业报告
 */
@RestController
@RequestMapping("/log")
class LogController(
    private val addaxLogService: AddaxLogService,
    private val statService: StatService
) {
    private val log = LoggerFactory.getLogger(LogController::class.java)

    /**
     * 获取指定采集任务的日志列表
     * @param tid 采集任务ID
     * @return 日志列表
     */
    @GetMapping("/{tid}")
    fun getSpLog(@PathVariable tid: String?): ApiResponse<MutableList<AddaxLogDto?>?> =
        ApiResponse.success(addaxLogService.getLogEntry(tid))

    /**
     * 获取指定日志文件的内容
     * @param id 日志ID
     * @return 日志内容
     */
    @GetMapping("/{id}/content")
    fun getLogFileContent(@PathVariable id: Long): ResponseEntity<String?> =
        ResponseEntity.ok(addaxLogService.getLogContent(id))

    /**
     * 作业报告接口
     * @param dto 作业报告数据
     * @return 是否成功
     */
    @PostMapping("/job-report", consumes = ["application/json"])
    fun jobReport(@RequestBody dto: AddaxReportDto): Boolean {
        log.info("job report: {}", dto)
        val sta = EtlStatistic().apply {
            tid = dto.getJobName().toLong()
            startAt = LocalDateTime.ofEpochSecond(dto.getStartTimeStamp(), 0, ZoneOffset.ofHours(8))
            endAt = LocalDateTime.ofEpochSecond(dto.getEndTimeStamp(), 0, ZoneOffset.ofHours(8))
            takeSecs = dto.getTotalCosts()
            byteSpeed = dto.getByteSpeedPerSecond()
            recSpeed = dto.getRecordSpeedPerSecond()
            totalRecs = dto.getTotalReadRecords()
            totalErrors = dto.getTotalErrorRecords()
            runDate = startAt.toLocalDate()
            totalBytes = dto.getByteSpeedPerSecond() * dto.getTotalCosts()
        }
        return statService.saveOrUpdate(sta)
    }
}
