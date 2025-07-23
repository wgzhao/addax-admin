package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.TbImpSp;
import com.wgzhao.addax.admin.repository.TbImpSpRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public Optional<TbImpSp> findById(String id) {
        return impSpRepo.findById(id);
    }

    public boolean exists(String id) {
        return impSpRepo.existsById(id);
    }

    public void save(TbImpSp tbImpSp) {
        impSpRepo.save(tbImpSp);
    }
}