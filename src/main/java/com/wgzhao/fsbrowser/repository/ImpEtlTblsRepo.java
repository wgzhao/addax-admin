package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.ImpEtlTbls;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * HIVE的表结构信息
 *
 * @author 
 */
public interface ImpEtlTblsRepo extends JpaRepository<ImpEtlTbls, String> {

}