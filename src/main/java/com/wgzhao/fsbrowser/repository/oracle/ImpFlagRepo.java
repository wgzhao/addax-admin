package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.ImpFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * TB_IMP_FLAG
 *
 * @author 
 */
public interface ImpFlagRepo extends JpaRepository<ImpFlag, String> {

}