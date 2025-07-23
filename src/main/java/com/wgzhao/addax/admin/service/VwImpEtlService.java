package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.VwImpEtl;
import com.wgzhao.addax.admin.repository.VwImpEtlRepo;
import com.wgzhao.addax.admin.utils.QueryUtil;
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
    public Page<VwImpEtl> getOdsInfo(int page, int pageSize, String q, String sortField, String sortOrder) {


        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        if (q != null && !q.isEmpty()) {
            System.out.println("search " + q.toUpperCase());
            return vwImpEtlRepo.findByFilterColumnContaining(q.toUpperCase(), pageable);
        } else {
            return vwImpEtlRepo.findAll(pageable);
        }
    }

    public Page<VwImpEtl> getOdsByFlag(int page, int pageSize, String q, String flag, String sortField, String sortOrder) {
        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        return vwImpEtlRepo.findByFlagAndFilterColumnContaining(flag, q.toUpperCase(), pageable);
    }

    public VwImpEtl findOneODSInfo(String tid) {
        return vwImpEtlRepo.findById(tid).orElse(null);
    }
}
