package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpEtlTbls;
import com.wgzhao.fsbrowser.repository.oracle.ImpEtlTblsRepo;
import com.wgzhao.fsbrowser.service.ImpEtlTblsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HIVE的表结构信息服务接口实现
 *
 * @author 
 */
@Service
public class ImpEtlTblsServiceImpl implements ImpEtlTblsService {
    
    @Autowired
    private ImpEtlTblsRepo impEtlTblsRepo;

    @Override
    public List<ImpEtlTbls> findAll() {
            return impEtlTblsRepo.findAll();
    }

}