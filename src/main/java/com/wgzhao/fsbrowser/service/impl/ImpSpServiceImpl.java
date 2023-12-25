package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpSp;
import com.wgzhao.fsbrowser.repository.ImpSpRepo;
import com.wgzhao.fsbrowser.service.ImpSpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * HADOOP_SP的配置主表服务接口实现
 *
 * @author 
 */
@Service
public class ImpSpServiceImpl implements ImpSpService {
    
    @Autowired
    private ImpSpRepo impSpRepo;

    @Override
    public List<ImpSp> findAll() {
            return impSpRepo.findAll();
    }

    @Override
    public List<Map<String, Object>> findLineage(String spId) {
        return null;
//        return impSpRepo.findLineage(spId);
    }

    @Override
    public List<Map<String, String>> findRequires(String spId) {
        return impSpRepo.findRequires(spId);
    }

    @Override
    public Map<String, String> findThrough(String spId) {
        return impSpRepo.findAllNeeds(spId);
    }

}