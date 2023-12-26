package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpSpNeedall;
import com.wgzhao.fsbrowser.repository.oracle.ImpSpNeedallRepo;
import com.wgzhao.fsbrowser.service.ImpSpNeedallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_SP_NEEDALL服务接口实现
 *
 * @author 
 */
@Service
public class ImpSpNeedallServiceImpl implements ImpSpNeedallService {
    
    @Autowired
    private ImpSpNeedallRepo impSpNeedallRepo;

    @Override
    public List<ImpSpNeedall> findAll() {
            return impSpNeedallRepo.findAll();
    }

}