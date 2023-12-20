package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpDs2Tbls;
import com.wgzhao.fsbrowser.repository.ImpDs2TblsRepo;
import com.wgzhao.fsbrowser.service.ImpDs2TblsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_DS2_TBLS服务接口实现
 *
 * @author 
 */
@Service
public class ImpDs2TblsServiceImpl implements ImpDs2TblsService {
    
    @Autowired
    private ImpDs2TblsRepo impDs2TblsRepo;

    @Override
    public List<ImpDs2Tbls> findAll() {
            return impDs2TblsRepo.findAll();
    }

}