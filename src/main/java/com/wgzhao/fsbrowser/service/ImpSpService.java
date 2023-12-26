package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.ImpSp;

import java.util.List;
import java.util.Map;

/**
 * HADOOP_SP的配置主表 服务接口
 *
 * @author 
 */
public interface ImpSpService {

      public List<ImpSp> findAll();

    List<Map<String, Object>> findLineage(String spId);

    List<Map<String, String>> findRequires(String spId);

    Map<String, String> findThrough(String spId);
}