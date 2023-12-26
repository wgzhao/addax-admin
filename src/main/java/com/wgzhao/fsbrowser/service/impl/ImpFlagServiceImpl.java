package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpFlag;
import com.wgzhao.fsbrowser.repository.oracle.ImpFlagRepo;
import com.wgzhao.fsbrowser.service.ImpFlagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_FLAG服务接口实现
 *
 * @author 
 */
@Service
public class ImpFlagServiceImpl implements ImpFlagService {
    
    @Autowired
    private ImpFlagRepo impFlagRepo;

    @Override
    public List<ImpFlag> findAll() {
            return impFlagRepo.findAll();
    }

}