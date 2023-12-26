package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpTblSou;
import com.wgzhao.fsbrowser.repository.oracle.ImpTblSouRepo;
import com.wgzhao.fsbrowser.service.ImpTblSouService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_TBL_SOU服务接口实现
 *
 * @author 
 */
@Service
public class ImpTblSouServiceImpl implements ImpTblSouService {
    
    @Autowired
    private ImpTblSouRepo impTblSouRepo;

    @Override
    public List<ImpTblSou> findAll() {
            return impTblSouRepo.findAll();
    }

}