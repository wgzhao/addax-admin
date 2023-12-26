package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.ImpJour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * TB_IMP_JOUR
 *
 * @author 
 */
public interface ImpJourRepo extends JpaRepository<ImpJour, String> {

}