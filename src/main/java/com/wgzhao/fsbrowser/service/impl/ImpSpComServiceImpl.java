package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpSpCom;
import com.wgzhao.fsbrowser.repository.oracle.ImpSpComRepo;
import com.wgzhao.fsbrowser.service.ImpSpComService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HADOOP_SP的运行脚本（作为主表的附属表）服务接口实现
 *
 * @author 
 */
@Service
public class ImpSpComServiceImpl implements ImpSpComService {
    
    @Autowired
    private ImpSpComRepo impSpComRepo;

    @Override
    public List<ImpSpCom> findAll() {
            return impSpComRepo.findAll();
    }

    @Override
    public List<ImpSpCom> findBySpId(String spId) {
        return impSpComRepo.findAllBySpId(spId);
    }

}