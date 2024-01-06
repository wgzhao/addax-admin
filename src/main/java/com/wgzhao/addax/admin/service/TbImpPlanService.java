package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.oracle.TbImpPlanRepo;
import com.wgzhao.addax.admin.model.oracle.TbImpPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TbImpPlanService {
    @Autowired
    private TbImpPlanRepo tbImpPlanRepo;

    public List<TbImpPlan> findAll() {
        return tbImpPlanRepo.findAll();
    }
}
