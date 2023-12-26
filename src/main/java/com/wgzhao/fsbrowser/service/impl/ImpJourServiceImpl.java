package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpJour;
import com.wgzhao.fsbrowser.repository.oracle.ImpJourRepo;
import com.wgzhao.fsbrowser.service.ImpJourService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_JOUR服务接口实现
 *
 * @author 
 */
@Service
public class ImpJourServiceImpl implements ImpJourService {
    
    @Autowired
    private ImpJourRepo impJourRepo;

    @Override
    public List<ImpJour> findAll() {
            return impJourRepo.findAll();
    }

}