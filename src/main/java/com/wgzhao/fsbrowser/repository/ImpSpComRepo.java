package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.ImpSpCom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * HADOOP_SP的运行脚本（作为主表的附属表）
 *
 * @author 
 */
public interface ImpSpComRepo extends JpaRepository<ImpSpCom, String> {

}