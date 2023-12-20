package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.ImpPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * TB_IMP_PLAN
 *
 * @author 
 */
public interface ImpPlanRepo extends JpaRepository<ImpPlan, String> {

}