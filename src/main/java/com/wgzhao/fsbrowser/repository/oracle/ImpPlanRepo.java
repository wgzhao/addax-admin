package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.ImpPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * TB_IMP_PLAN
 *
 * @author 
 */
public interface ImpPlanRepo extends JpaRepository<ImpPlan, String> {

}