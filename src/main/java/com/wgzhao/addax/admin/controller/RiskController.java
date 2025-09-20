package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbImpChk;
import com.wgzhao.addax.admin.model.Notification;
import com.wgzhao.addax.admin.repository.NotificationRepo;
import com.wgzhao.addax.admin.utils.CacheUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 风险点接口
 */
@Api(value = "风险点接口")
@RestController
@RequestMapping("/risk")
public class RiskController
{

    @Autowired
    private NotificationRepo notificationRepo;

    @Resource
    private CacheUtil cacheUtil;

    // 系统风险检测结果
    @RequestMapping("/sysRisk")
    public ApiResponse<List<TbImpChk>> sysRisk()
    {
        return null;
    }

    // ODS采集源库的字段变更提醒（T-1日结构与T日结构对比）
    @RequestMapping("/odsFieldChange")
    public ApiResponse<List<Object>> odsFieldChange()
    {
        return null;
    }

    // 短信发送详情
    @RequestMapping("/smsDetail")
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
