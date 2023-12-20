package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpEtlSoutab;
import com.wgzhao.fsbrowser.repository.ImpEtlSoutabRepo;
import com.wgzhao.fsbrowser.service.ImpEtlSoutabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_ETL_SOUTAB服务接口实现
 *
 * @author 
 */
@Service
public class ImpEtlSoutabServiceImpl implements ImpEtlSoutabService {
    
    @Autowired
    private ImpEtlSoutabRepo impEtlSoutabRepo;

    @Override
    public List<ImpEtlSoutab> findAll() {
            return impEtlSoutabRepo.findAll();
    }

}