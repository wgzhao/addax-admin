package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.ImpEtl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * TB_IMP_ETL
 *
 * @author 
 */
public interface ImpEtlRepo extends JpaRepository<ImpEtl, String> {

}