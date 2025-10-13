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
import org.slf4j.LoggerFactory

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
    private val log = LoggerFactory.getLogger(MonitorController::class.java)

    @GetMapping("/accomplish")
    fun last2DaysCompleteList(): ResponseEntity<List<Map<String, Any>>> =
        ResponseEntity.ok(statService.last2DaysCompleteList)

    /**
     * 获取特殊任务提醒列表
     * @return 特殊任务列表
     */
    @GetMapping("/special-task")
    fun specialTask(): ResponseEntity<List<EtlTable>> =
        ResponseEntity.ok(taskService.findAllSpecialTask())

    @get:GetMapping("/reject-task")
    val taskReject: ResponseEntity<List<EtlStatistic>>
        /**
         * 获取拒绝执行的任务列表
         * @return 拒绝任务列表
         */
        get() = ResponseEntity.ok(statService.findErrorTask())

    @get:RequestMapping("/sys-risk")
    val sysRisk: ResponseEntity<List<TbImpChk>>
        /**
         * 获取系统风险检测结果
         * @return 风险检测结果
         */
        get() = ResponseEntity.ok(emptyList())

    // ODS采集源库的字段变更提醒（T-1日结构与T日结构对比）
    @RequestMapping("/field-change")
    fun odsFieldChange(): ResponseEntity<List<Any>> =
        ResponseEntity.ok(emptyList())

    // 短信发送详情
    @RequestMapping("/sms-detail")
    fun smsDetail(): ResponseEntity<List<Notification>> =
        ResponseEntity.ok(notificationRepo.findAll())
}
