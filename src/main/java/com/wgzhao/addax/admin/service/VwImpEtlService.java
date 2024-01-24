package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.VwImpEtlListDto;
import com.wgzhao.addax.admin.model.oracle.VwImpEtl;
import com.wgzhao.addax.admin.repository.oracle.VwImpEtlRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class VwImpEtlService {

    @Autowired
    private VwImpEtlRepo vwImpEtlRepo;

    public Page<VwImpEtl> fetchEtlInfo(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        return vwImpEtlRepo.findAll(pageable);
    }

    /**
     * ODS 采集信息
     *
     */
    public Page<VwImpEtl> getOdsInfo(int page, int pageSize, String q) {
        Pageable pageable = PageRequest.of(page, pageSize);
        if (q != null && !q.isEmpty()) {
            System.out.println("search " + q.toUpperCase());
            return vwImpEtlRepo.findByFilterColumnContaining(q.toUpperCase(), pageable);
        } else {
            return vwImpEtlRepo.findAll(pageable);
        }
    }

    public VwImpEtl findOneODSInfo(String tid) {
        return vwImpEtlRepo.findById(tid).orElse(null);
    }
}
