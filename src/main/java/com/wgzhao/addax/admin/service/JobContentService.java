package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlJob;
import com.wgzhao.addax.admin.repository.EtlJobRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JobContentService
{
    @Autowired
    private EtlJobRepo jobRepo;

    public String getJobContent(long tid) {
        return jobRepo.findById(tid).map(EtlJob::getJob).orElse(null);
    }
}
