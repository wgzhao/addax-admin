package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SourceService
{
    @Autowired
    private EtlSourceRepo etlSourceRepo;

    public Integer getValidSources() {
        return etlSourceRepo.countByEnabled(true);
    }
}
