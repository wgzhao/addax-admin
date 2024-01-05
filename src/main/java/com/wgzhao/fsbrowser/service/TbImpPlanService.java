package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.TbImpPlan;
import com.wgzhao.fsbrowser.repository.oracle.TbImpPlanRepo;
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
