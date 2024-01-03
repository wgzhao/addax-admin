package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.pg.VwAddaxLog;
import com.wgzhao.fsbrowser.repository.pg.VwAddaxLogRepo;
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
