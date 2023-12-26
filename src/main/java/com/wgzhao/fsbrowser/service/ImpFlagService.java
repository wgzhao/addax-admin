package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.ImpFlag;

import java.util.List;

/**
 * TB_IMP_FLAG 服务接口
 *
 * @author 
 */
public interface ImpFlagService {

      public List<ImpFlag> findAll();
}