package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpEtlOverprec;
import com.wgzhao.fsbrowser.repository.ImpEtlOverprecRepo;
import com.wgzhao.fsbrowser.service.ImpEtlOverprecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ImpEtlOverprecImpl implements ImpEtlOverprecService {

    @Autowired
    private ImpEtlOverprecRepo impEtlOverprecRepo;
    @Override
    public List<ImpEtlOverprec> getAllImpEtlOverprec() {
//        return impEtlOverprecRepo.findAllByOrderByDbStartAsc();
        return impEtlOverprecRepo.findAll();
    }
    public List<Map<String, Float>> accompListRatio() {
        return impEtlOverprecRepo.accompListRatio();
    }

}
