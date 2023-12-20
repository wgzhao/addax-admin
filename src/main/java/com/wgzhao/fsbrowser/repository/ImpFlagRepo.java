package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.ImpFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * TB_IMP_FLAG
 *
 * @author 
 */
public interface ImpFlagRepo extends JpaRepository<ImpFlag, String> {

}