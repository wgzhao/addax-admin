package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.TmpImpSpNeedtab;
import com.wgzhao.fsbrowser.repository.TmpImpSpNeedtabRepo;
import com.wgzhao.fsbrowser.service.TmpImpSpNeedtabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TMP_IMP_SP_NEEDTAB服务接口实现
 *
 * @author 
 */
@Service
public class TmpImpSpNeedtabServiceImpl implements TmpImpSpNeedtabService {
    
    @Autowired
    private TmpImpSpNeedtabRepo tmpImpSpNeedtabRepo;

    @Override
    public List<TmpImpSpNeedtab> findAll() {
            return tmpImpSpNeedtabRepo.findAll();
    }

}