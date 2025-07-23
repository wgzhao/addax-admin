package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.ImpSpCom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * HADOOP_SP的运行脚本（作为主表的附属表）
 *
 * @author 
 */
public interface ImpSpComRepo extends JpaRepository<ImpSpCom, String> {

    List<ImpSpCom> findAllBySpId(String spId);
}