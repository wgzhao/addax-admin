package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.ImpEtl;

import java.util.List;

/**
 * TB_IMP_ETL 服务接口
 *
 * @author 
 */
public interface ImpEtlService {

      public List<ImpEtl> findAll();
}