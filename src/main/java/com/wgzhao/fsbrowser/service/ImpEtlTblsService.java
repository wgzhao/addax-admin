package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.ImpEtlTbls;

import java.util.List;

/**
 * HIVE的表结构信息 服务接口
 *
 * @author 
 */
public interface ImpEtlTblsService {

      public List<ImpEtlTbls> findAll();
}