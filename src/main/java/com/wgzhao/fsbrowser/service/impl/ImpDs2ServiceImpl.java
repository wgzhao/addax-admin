package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpDs2;
import com.wgzhao.fsbrowser.repository.oracle.ImpDs2Repo;
import com.wgzhao.fsbrowser.service.ImpDs2Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TB_IMP_DS2服务接口实现
 *
 * @author 
 */
@Service
public class ImpDs2ServiceImpl implements ImpDs2Service {
    
    @Autowired
    private ImpDs2Repo impDs2Repo;

    @Override
    public List<ImpDs2> findAll() {
            return impDs2Repo.findAll();
    }

}