package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.pg.TbImpChkSpEntity;
import com.wgzhao.fsbrowser.repository.oracle.ViewPseudoRepo;
import com.wgzhao.fsbrowser.repository.pg.ImpChkSpRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.wgzhao.fsbrowser.utils.TradeDateUtils.calcTradeDate;
/**
 * SP 监控
 */
@RestController
@CrossOrigin
@RequestMapping("/sp")
public class SpMonitorController {

    @Autowired
    private ViewPseudoRepo  viewPseudoRepo;

    @Autowired
    private ImpChkSpRepo impChkSpRepo;

    // SP 整体执行情况
    @GetMapping("/totalExec")
    public List<Map<String, Object>> spTotalExec() {
        return viewPseudoRepo.findSpExecInfo();
    }

    // SP 计算的有效性检测结果
    @GetMapping("/validChkSp")
    public List<TbImpChkSpEntity> getValidChkSp() {
        return impChkSpRepo.findValidChkSp(calcTradeDate(5, "yyyyMMdd"));
    }

    // SP计算的记录数检测结果
    @GetMapping("/validSpCnt")
    public List<Map<String, Object>> getValidSpCnt() {
        return impChkSpRepo.findValidSpCnt(calcTradeDate(5, "yyyyMMdd"));
    }

    // 特殊任务：报错、重跑
    @GetMapping("/errorTasks")
    public List<Map<String, Object>> getErrorTasks() {
        return viewPseudoRepo.findErrorTasks();
    }
}
