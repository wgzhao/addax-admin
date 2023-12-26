package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.ImpSpCom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * HADOOP_SP的运行脚本（作为主表的附属表）
 *
 * @author 
 */
public interface ImpSpComRepo extends JpaRepository<ImpSpCom, String> {

    List<ImpSpCom> findAllBySpId(String spId);
}