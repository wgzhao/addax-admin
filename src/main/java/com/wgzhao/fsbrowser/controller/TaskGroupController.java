package com.wgzhao.fsbrowser.controller;

/**
 * 任务组接口
 */

import com.wgzhao.fsbrowser.model.oracle.TbImpFlagEntity;
import com.wgzhao.fsbrowser.model.oracle.VwImpTaskgroupDetailEntity;
import com.wgzhao.fsbrowser.repository.oracle.TbImpFlagRepo;
import com.wgzhao.fsbrowser.repository.oracle.ViewPseudoRepo;
import com.wgzhao.fsbrowser.repository.oracle.VwImpTaskgroupDetailRepo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.wgzhao.fsbrowser.utils.TradeDateUtils.calcTradeDate;

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
    public List<VwImpTaskgroupDetailEntity> taskGroupTotalExec() {
        return vwImpTaskgroupDetailRepo.findAll();
    }

    // 任务组标志生成时间
    @GetMapping("/flagGenTime")
    public List<TbImpFlagEntity> taskGroupFlagGenTime() {
        int td = Integer.parseInt(calcTradeDate(1));
        return tbImpFlagRepo.findByTradedateAndKind(td, "TASK_GROUP");
    }

    // 数据服务执行时间
    @GetMapping("/dataServiceExecTime")
    public List<Map<String, Object>> dataServiceExecTime() {
        return viewPseudoRepo.findDataServiceExecTime();
    }

    // 数据服务执行时间超长列表
    @GetMapping("/dataServiceExecTimeout")
    public List<Map<String, Object>> dataServiceExecTimeLong() {
        return viewPseudoRepo.findDataServiceExecTimeout();
    }

    // 按照目标系统的任务完成情况
    @GetMapping("/targetComplete")
    public List<Map<String, Object>> targetComplete() {
        return viewPseudoRepo.findTargetComplete();
    }
}
