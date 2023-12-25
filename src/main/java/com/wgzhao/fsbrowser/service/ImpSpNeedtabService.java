package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.ImpSpNeedtab;

import java.util.List;
import java.util.Map;

/**
 * TB_IMP_SP_NEEDTAB 服务接口
 *
 * @author 
 */
public interface ImpSpNeedtabService {

      public List<ImpSpNeedtab> findAll();

    List<Map> findSceneByTableName(String tableName);
}