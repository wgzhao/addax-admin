package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.ImpEtl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * TB_IMP_ETL
 *
 * @author 
 */
public interface ImpEtlRepo extends JpaRepository<ImpEtl, String> {

}