package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.VwImpSpRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VwImpSpService {

    @Autowired
    private VwImpSpRepo vwImpSpRepo;
}
