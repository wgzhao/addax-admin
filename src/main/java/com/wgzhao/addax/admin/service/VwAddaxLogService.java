package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.VwAddaxLogRepo;
import com.wgzhao.addax.admin.model.VwAddaxLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VwAddaxLogService {

    @Autowired
    private VwAddaxLogRepo vwAddaxLogRepo;

    public List<VwAddaxLog> getAddaxResult(List<String> spNames) {
        return vwAddaxLogRepo.findTop15BySpnameIn(spNames);
    }
}
