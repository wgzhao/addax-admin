package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.Notification;
import com.wgzhao.addax.admin.model.TbImpChk;
import com.wgzhao.addax.admin.repository.NotificationRepo;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.TaskService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 监控相关接口，提供采集完成情况、特殊任务、拒绝任务、系统风险等监控数据查询
 */
@RestController
@RequestMapping("/monitor")
@AllArgsConstructor
public class MonitorController
{
    /** 任务服务 */
    private final TaskService taskService;
    /** 统计服务 */
    private final StatService statService;
    /** 消息提醒数据仓库 */
    private final NotificationRepo notificationRepo;

    /**
     * 获取最近两天采集完成情况列表
     * @return 采集完成情况列表
     */
    @RequestMapping("/accomplish")
    public ResponseEntity<List<Map<String, Object>>> getLast2DaysCompleteList()
    {
        return ResponseEntity.ok(statService.getLast2DaysCompleteList());
    }

    /**
     * 获取特殊任务提醒列表
     * @return 特殊任务列表
     */
    @GetMapping("/special-task")
    public ResponseEntity<List<EtlTable>> specialTask()
    {
        return ResponseEntity.ok(taskService.findAllSpecialTask());
    }

    /**
     * 获取拒绝执行的任务列表
     * @return 拒绝任务列表
     */
    @GetMapping("/reject-task")
    public ResponseEntity<List<EtlStatistic>> getTaskReject()
    {
        return ResponseEntity.ok(statService.findErrorTask());
    }

    /**
     * 获取系统风险检测结果
     * @return 风险检测结果
     */
    @RequestMapping("/sys-risk")
    public ResponseEntity<List<TbImpChk>> getSysRisk()
    {
        // 具体实现略
        return ResponseEntity.ok(null);
    }

    // ODS采集源库的字段变更提醒（T-1日结构与T日结构对比）
    @RequestMapping("/field-change")
    public ResponseEntity<List<Object>> odsFieldChange()
    {
        return null;
    }

    // 短信发送详情
    @RequestMapping("/sms-detail")
    public ResponseEntity<List<Notification>> smsDetail()
    {
        return ResponseEntity.ok(notificationRepo.findAll());
    }
}
