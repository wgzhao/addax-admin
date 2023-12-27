package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.TbImpChk;
import com.wgzhao.fsbrowser.repository.oracle.TbImpChkRepo;
import com.wgzhao.fsbrowser.service.ImpChkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_CHK服务接口实现
 *
 * @author 
 */
@Service
public class ImpChkServiceImpl implements ImpChkService {
    
    @Autowired
    private TbImpChkRepo impChkRepo;

    @Override
    public List<TbImpChk> findAll() {
            return impChkRepo.findAll();
    }

}