package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.ImpSpCom;

import java.util.List;

/**
 * HADOOP_SP的运行脚本（作为主表的附属表） 服务接口
 *
 * @author 
 */
public interface ImpSpComService {

      public List<ImpSpCom> findAll();

    List<ImpSpCom> findBySpId(String spId);
}