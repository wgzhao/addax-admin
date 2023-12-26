package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpEtl;
import com.wgzhao.fsbrowser.repository.oracle.ImpEtlRepo;
import com.wgzhao.fsbrowser.service.ImpEtlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_ETL服务接口实现
 *
 * @author 
 */
@Service
public class ImpEtlServiceImpl implements ImpEtlService {
    
    @Autowired
    private ImpEtlRepo impEtlRepo;

    @Override
    public List<ImpEtl> findAll() {
            return impEtlRepo.findAll();
    }

}