package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SourceService
{
    @Autowired
    private EtlSourceRepo etlSourceRepo;

    public Integer getValidSources() {
        return etlSourceRepo.countByEnabled(true);
    }

    public EtlSource getSource(Integer sid) {
        return etlSourceRepo.findById(sid).orElse(null);
    }

    public boolean checkCode(String code) {
        return etlSourceRepo.existsByCode(code);
    }

    public List<EtlSource> findAll() {
        return etlSourceRepo.findAll();
    }
    public Optional<EtlSource> findById(int id) {
        return etlSourceRepo.findById(id);
    }
    public EtlSource save(EtlSource etlSource) {
        return etlSourceRepo.save(etlSource);
    }
    public void deleteById(int id) {
        etlSourceRepo.deleteById(id);
    }
    public boolean existsById(int id) {
        return etlSourceRepo.existsById(id);
    }
    public void saveAll(List<EtlSource> sources) {
        etlSourceRepo.saveAll(sources);
    }
}
