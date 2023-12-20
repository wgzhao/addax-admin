package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.ImpSp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * HADOOP_SP的配置主表
 *
 * @author 
 */
public interface ImpSpRepo extends JpaRepository<ImpSp, String> {

}