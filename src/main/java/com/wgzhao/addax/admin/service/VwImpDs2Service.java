package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.oracle.VwImpDs2Repo;
import com.wgzhao.addax.admin.model.oracle.VwImpDs2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VwImpDs2Service {

    @Autowired
    private VwImpDs2Repo vwImpDs2Repo;

    public List<VwImpDs2> getAllDs() {
        return vwImpDs2Repo.findAll();
    }

    public Optional<VwImpDs2> getDsInfo(String id) {
        return vwImpDs2Repo.findById(id);
    }
}
