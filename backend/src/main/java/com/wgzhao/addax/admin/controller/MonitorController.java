package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.Notification;
import com.wgzhao.addax.admin.model.RiskLog;
import com.wgzhao.addax.admin.model.SchemaChangeLog;
import com.wgzhao.addax.admin.repository.NotificationRepo;
import com.wgzhao.addax.admin.service.RiskLogService;
import com.wgzhao.addax.admin.service.SchemaChangeLogService;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.TaskService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
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

    private final TaskService taskService;
    private final StatService statService;
    private final NotificationRepo notificationRepo;
    private final SchemaChangeLogService schemaChangeLogService;
    private final RiskLogService riskLogService;

    /**
     * 获取最近两天采集完成情况列表
     *
     * @return 采集完成情况列表
     */
    @RequestMapping("/accomplish")
    public ResponseEntity<List<Map<String, Object>>> getLast2DaysCompleteList()
    {
        return ResponseEntity.ok(statService.getLast2DaysCompleteList());
    }

    /**
     * 获取特殊任务提醒列表
     *
     * @return 特殊任务列表
     */
    @GetMapping("/special-task")
    public ResponseEntity<List<EtlTable>> specialTask()
    {
        return ResponseEntity.ok(taskService.findAllSpecialTask());
    }

    /**
     * 获取拒绝执行的任务列表
     *
     * @return 拒绝任务列表
     */
    @GetMapping("/reject-task")
    public ResponseEntity<List<EtlStatistic>> getTaskReject()
    {
        return ResponseEntity.ok(statService.findErrorTask());
    }

    /**
     * 获取系统风险检测结果
     *
     * @return 风险检测结果
     */
    @RequestMapping("/sys-risk")
    public ResponseEntity<List<RiskLog>> getSysRisk()
    {
        // 具体实现略
        return ResponseEntity.ok(riskLogService.getRecentRisks(10));
    }

    /**
     * 查询字段变更
     *
     * @param page 页码（查询参数），默认 0
     * @param pageSize 每页记录数（查询参数），默认 10，-1 表示不分页
     * @return 分页的字段变更记录
     */
    @RequestMapping("/field-change")
    public ResponseEntity<PageResponse<SchemaChangeLog>> odsFieldChange(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    )
    {
        if (page < 0) {
            page = 0;
        }
        if (pageSize == -1) {
            pageSize = Integer.MAX_VALUE;
        }
        Page<SchemaChangeLog> result = schemaChangeLogService.getFieldChanges(page, pageSize);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    // 短信发送详情
    @RequestMapping("/sms-detail")
    public ResponseEntity<List<Notification>> smsDetail()
    {
        return ResponseEntity.ok(notificationRepo.findAll());
    }

    // 最近 15 个采集日内，表数据量无变化的表信息
    @RequestMapping("/no-table-change")
    public ResponseEntity<List<Map<String, Object>>> noTableChange()
    {
        return ResponseEntity.ok(statService.getNoTableRowsChangeList(15));
    }
}
