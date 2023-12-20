package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpChkInf;
import com.wgzhao.fsbrowser.repository.ImpChkInfRepo;
import com.wgzhao.fsbrowser.service.ImpChkInfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_CHK_INF服务接口实现
 *
 * @author 
 */
@Service
public class ImpChkInfServiceImpl implements ImpChkInfService {
    
    @Autowired
    private ImpChkInfRepo impChkInfRepo;

    @Override
    public List<ImpChkInf> findAll() {
            return impChkInfRepo.findAll();
    }

}