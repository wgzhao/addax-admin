package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbImpFlag;
import com.wgzhao.addax.admin.repository.TbImpFlagRepo;
import com.wgzhao.addax.admin.repository.ViewPseudoRepo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.wgzhao.addax.admin.utils.TradeDateUtils.calcTradeDate;

/*
 * 任务组接口
 */

@Api(value="任务组接口",tags={"任务组接口"})
@RestController
@RequestMapping("/taskGroup")
public class TaskGroupController {

    @Autowired
    private VwImpTaskgroupDetailRepo vwImpTaskgroupDetailRepo;

    @Autowired
    private TbImpFlagRepo tbImpFlagRepo;

    @Autowired
    private ViewPseudoRepo viewPseudoRepo;

    // 任务组整体执行情况
    @GetMapping("/totalExec")
    public ApiResponse<List<VwImpTaskgroupDetail>> taskGroupTotalExec() {
        return ApiResponse.success(vwImpTaskgroupDetailRepo.findAll());
    }

    // 任务组标志生成时间
    @GetMapping("/flagGenTime")
    public ApiResponse<List<TbImpFlag>> taskGroupFlagGenTime() {
        int td = Integer.parseInt(calcTradeDate(1,"yyyyMMdd"));
        return ApiResponse.success(tbImpFlagRepo.findByTradedateAndKind(td, "TASK_GROUP"));
    }

    // 数据服务执行时间
    @GetMapping("/dataServiceExecTime")
    public ApiResponse<List<Map<String, Object>>> dataServiceExecTime() {
        return ApiResponse.success(viewPseudoRepo.findDataServiceExecTime());
    }

    // 数据服务执行时间超长列表
    @GetMapping("/dataServiceExecTimeout")
    public ApiResponse<List<Map<String, Object>>> dataServiceExecTimeLong() {
        return ApiResponse.success(viewPseudoRepo.findDataServiceExecTimeout());
    }

    // 按照目标系统的任务完成情况
    @GetMapping("/targetComplete")
    public ApiResponse<List<Map<String, Object>>> targetComplete() {
        return ApiResponse.success(viewPseudoRepo.findTargetComplete());
    }
}
