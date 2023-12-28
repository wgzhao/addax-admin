package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.ImpSp;
import com.wgzhao.fsbrowser.repository.oracle.ImpSpRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * HADOOP_SP的配置主表 服务接口
 *
 * @author 
 */
@Service
public class ImpSpService {

    @Autowired
    private ImpSpRepo impSpRepo;

    public List<ImpSp> findAll() {
        return impSpRepo.findAll();
    }

    public List<Map<String, Object>> findLineage(String spId) {
        return null;
//        return impSpRepo.findLineage(spId);
    }

    public List<Map<String, String>> findRequires(String spId) {
        return impSpRepo.findRequires(spId);
    }

    public Map<String, String> findThrough(String spId) {
        return impSpRepo.findAllNeeds(spId);
    }

}