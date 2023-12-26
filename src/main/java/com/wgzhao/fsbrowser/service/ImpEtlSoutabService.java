package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.ImpEtlSoutab;

import java.util.List;

/**
 * TB_IMP_ETL_SOUTAB 服务接口
 *
 * @author 
 */
public interface ImpEtlSoutabService {

      public List<ImpEtlSoutab> findAll();
}