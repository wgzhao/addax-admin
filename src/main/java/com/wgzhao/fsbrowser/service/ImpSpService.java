package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.ImpSp;

import java.util.List;

/**
 * HADOOP_SP的配置主表 服务接口
 *
 * @author 
 */
public interface ImpSpService {

      public List<ImpSp> findAll();
}