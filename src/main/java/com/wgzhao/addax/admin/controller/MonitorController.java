package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.Notification;
import com.wgzhao.addax.admin.model.TbImpChk;
import com.wgzhao.addax.admin.repository.NotificationRepo;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.TaskService;
import com.wgzhao.addax.admin.utils.CacheUtil;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
@AllArgsConstructor
public class MonitorController
{

    private final TaskService taskService;
    private final StatService statService;
    private final NotificationRepo notificationRepo;
    private final CacheUtil cacheUtil;

    // 数据源采集完成情况列表,获取最近两天的采集情况
    @RequestMapping("/accomplish")
    public ResponseEntity<List<Map<String, Object>>> getLast2DaysCompleteList()
    {
        return ResponseEntity.ok(statService.getLast2DaysCompleteList());
    }

    // 特殊任务提醒
    @GetMapping("/special-task")
    public ResponseEntity<List<EtlTable>> specialTask()
    {
        return ResponseEntity.ok(taskService.findAllSpecialTask());
    }

    // 任务拒绝行
    @GetMapping("/reject-task")
    public ResponseEntity<List<EtlStatistic>> getTaskReject()
    {
        return ResponseEntity.ok(statService.findErrorTask());
    }


    // 系统风险检测结果
    @RequestMapping("/sys-risk")
    public ApiResponse<List<TbImpChk>> sysRisk()
    {
        return null;
    }

    // ODS采集源库的字段变更提醒（T-1日结构与T日结构对比）
    @RequestMapping("/field-change")
    public ApiResponse<List<Object>> odsFieldChange()
    {
        return null;
    }

    // 短信发送详情
    @RequestMapping("/sms-detail")
    public ApiResponse<List<Notification>> smsDetail()
    {
        Date day;
        String td = cacheUtil.get("param.TD");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HHmm");
        try {
            day = sdf.parse(td + " 1630");
            return ApiResponse.success(notificationRepo.findDistinctByCreateAtAfter(day));
        }
        catch (ParseException e) {
            return ApiResponse.error(500, "日期解析错误");
        }
    }
}
