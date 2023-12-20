package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpPlan;
import com.wgzhao.fsbrowser.repository.ImpPlanRepo;
import com.wgzhao.fsbrowser.service.ImpPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_PLAN服务接口实现
 *
 * @author 
 */
@Service
public class ImpPlanServiceImpl implements ImpPlanService {
    
    @Autowired
    private ImpPlanRepo impPlanRepo;

    @Override
    public List<ImpPlan> findAll() {
            return impPlanRepo.findAll();
    }

}