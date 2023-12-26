package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.ImpEtlOverprec;
import com.wgzhao.fsbrowser.model.oracle.VwImpEtlOverprecEntity;
import com.wgzhao.fsbrowser.repository.oracle.VwImpEtlOverprecRepo;
import com.wgzhao.fsbrowser.service.ImpEtlOverprecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ImpEtlOverprecImpl implements ImpEtlOverprecService {

    @Autowired
    private VwImpEtlOverprecRepo impEtlOverprecRepo;
    @Override
    public List<VwImpEtlOverprecEntity> getAllImpEtlOverprec() {
//        return impEtlOverprecRepo.findAllByOrderByDbStartAsc();
        return impEtlOverprecRepo.findAll();
    }
    public List<Map<String, Float>> accompListRatio() {
        return impEtlOverprecRepo.accompListRatio();
    }

}
