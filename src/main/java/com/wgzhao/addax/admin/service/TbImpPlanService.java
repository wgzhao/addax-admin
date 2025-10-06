package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.TbImpPlanRepo;
import com.wgzhao.addax.admin.model.TbImpPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 计划任务服务类，负责计划任务的相关业务操作
 */
@Service
public class TbImpPlanService {
    @Autowired
    private TbImpPlanRepo tbImpPlanRepo;

    /**
     * 查询所有计划任务
     * @return 计划任务列表
     */
    public List<TbImpPlan> findAll() {
        // 查询所有计划任务记录
        return tbImpPlanRepo.findAll();
    }
}
