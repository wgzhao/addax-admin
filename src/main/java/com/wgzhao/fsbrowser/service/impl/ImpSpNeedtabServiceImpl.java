package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpSpNeedtab;
import com.wgzhao.fsbrowser.repository.ImpSpNeedtabRepo;
import com.wgzhao.fsbrowser.service.ImpSpNeedtabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_SP_NEEDTAB服务接口实现
 *
 * @author 
 */
@Service
public class ImpSpNeedtabServiceImpl implements ImpSpNeedtabService {
    
    @Autowired
    private ImpSpNeedtabRepo impSpNeedtabRepo;

    @Override
    public List<ImpSpNeedtab> findAll() {
            return impSpNeedtabRepo.findAll();
    }

}