package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbImpChk;
import com.wgzhao.addax.admin.model.VwImpCheckSoutab;
import com.wgzhao.addax.admin.model.Msg;
import com.wgzhao.addax.admin.repository.MsgRepo;
import com.wgzhao.addax.admin.repository.TbImpChkRepo;
import com.wgzhao.addax.admin.repository.VwImpCheckSoutabRepo;
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

import static com.wgzhao.addax.admin.utils.TradeDateUtils.calcTradeDate;

/**
 * 风险点接口
 */
@Api(value = "风险点接口", tags = {"风险点接口"})
@RestController
@RequestMapping("/risk")
public class RiskController {

    @Autowired
    private TbImpChkRepo tbImpChkRepo;

    @Autowired
    private VwImpCheckSoutabRepo vwImpCheckSoutabRepo;

    @Autowired
    private MsgRepo msgRepo;

    @Resource
    private CacheUtil cacheUtil;

    // 系统风险检测结果
    @RequestMapping("/sysRisk")
    public ApiResponse<List<TbImpChk>> sysRisk() {
        return ApiResponse.success(tbImpChkRepo.findAll());
    }

    // ODS采集源库的字段变更提醒（T-1日结构与T日结构对比）
    @RequestMapping("/odsFieldChange")
    public ApiResponse<List<VwImpCheckSoutab>> odsFieldChange() {
        return ApiResponse.success(vwImpCheckSoutabRepo.findAll());
    }

    // 短信发送详情
    @RequestMapping("/smsDetail")
    public ApiResponse<List<Msg>> smsDetail() {
        Date day;
        String td = cacheUtil.get("param.TD");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HHmm");
        try {
            day = sdf.parse(td + " 1630");
            return ApiResponse.success(msgRepo.findDistinctBydwCltDateAfter(day));
        } catch (ParseException e) {
            return ApiResponse.error(500, "日期解析错误");
        }
    }
}
