package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpTblHdp;
import com.wgzhao.fsbrowser.repository.ImpTblHdpRepo;
import com.wgzhao.fsbrowser.service.ImpTblHdpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_TBL_HDP服务接口实现
 *
 * @author 
 */
@Service
public class ImpTblHdpServiceImpl implements ImpTblHdpService {
    
    @Autowired
    private ImpTblHdpRepo impTblHdpRepo;

    @Override
    public List<ImpTblHdp> findAll() {
            return impTblHdpRepo.findAll();
    }

}