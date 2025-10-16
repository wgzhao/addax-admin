package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.model.EtlStatistic
import com.wgzhao.addax.admin.model.EtlTable
import com.wgzhao.addax.admin.model.Notification
import com.wgzhao.addax.admin.model.TbImpChk
import com.wgzhao.addax.admin.repository.NotificationRepo
import com.wgzhao.addax.admin.service.StatService
import com.wgzhao.addax.admin.service.TaskService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 监控相关接口，提供采集完成情况、特殊任务、拒绝任务、系统风险等监控数据查询
 */
@RestController
@RequestMapping("/monitor")
class MonitorController(
    private val taskService: TaskService,
    private val statService: StatService,
    private val notificationRepo: NotificationRepo
) {

    /**
     * 获取采集完成情况列表（最近两天）
     * @return 采集完成情况列表
     */
    @GetMapping("/accomplish")
    fun last2DaysCompleteList(): ResponseEntity<List<Map<String, Any>>> =
        ResponseEntity.ok(statService.last2DaysCompleteList())

    /**
     * 获取特殊任务提醒列表
     * @return 特殊任务列表
     */
    @GetMapping("/special-task")
    fun specialTask(): ResponseEntity<List<EtlTable?>?>? =
        ResponseEntity.ok(taskService.findAllSpecialTask())

    /**
     * 获取拒绝执行的任务列表
     * @return 拒绝任务列表
     */
    @GetMapping("/reject-task")
    fun taskReject(): ResponseEntity<List<EtlStatistic>> =
        ResponseEntity.ok(statService.findErrorTask()?.filterNotNull())

    /**
     * 获取系统风险检测结果
     * @return 风险检测结果
     */
    @GetMapping("/sys-risk")
    fun sysRisk(): ResponseEntity<List<TbImpChk>> =
        ResponseEntity.ok(emptyList())

    /**
     * ODS采集源库的字段变更提醒（T-1日结构与T日结构对比）
     * @return 字段变更提醒列表
     */
    @GetMapping("/field-change")
    fun odsFieldChange(): ResponseEntity<List<Any>> =
        ResponseEntity.ok(emptyList())

    /**
     * 短信发送详情
     * @return 短信发送详情列表
     */
    @GetMapping("/sms-detail")
    fun smsDetail(): ResponseEntity<List<Notification>> =
        ResponseEntity.ok(notificationRepo.findAll().filterNotNull())
}
