package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.TbImpChk;

import java.util.List;

/**
 * TB_IMP_CHK 服务接口
 *
 * @author 
 */
public interface ImpChkService {

      public List<TbImpChk> findAll();
}