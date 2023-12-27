package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.VwImpEtlOverprecEntity;
import com.wgzhao.fsbrowser.repository.oracle.VwImpEtlOverprecRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ImpEtlOverprecService {

    @Autowired
    private VwImpEtlOverprecRepo impEtlOverprecRepo;

    public List<VwImpEtlOverprecEntity> getAllImpEtlOverprec() {
        return impEtlOverprecRepo.findAll();
    }

}
