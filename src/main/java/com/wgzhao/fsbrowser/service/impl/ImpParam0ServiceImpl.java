package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpParam0;
import com.wgzhao.fsbrowser.repository.oracle.ImpParam0Repo;
import com.wgzhao.fsbrowser.service.ImpParam0Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 日期参数文件基表服务接口实现
 *
 * @author 
 */
@Service
public class ImpParam0ServiceImpl implements ImpParam0Service {
    
    @Autowired
    private ImpParam0Repo impParam0Repo;

    @Override
    public List<ImpParam0> findAll() {
            return impParam0Repo.findAll();
    }

}