package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.oracle.TbImpSp;
import com.wgzhao.addax.admin.repository.oracle.TbImpSpRepo;
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
    private TbImpSpRepo impSpRepo;

    public List<TbImpSp> findAll() {
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