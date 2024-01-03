package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.repository.oracle.VwImpSpRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VwImpSpService {

    @Autowired
    private VwImpSpRepo vwImpSpRepo;
}
