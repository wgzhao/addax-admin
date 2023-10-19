package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.ImpDB;
import com.wgzhao.fsbrowser.repository.ImpDBRepo;
import com.wgzhao.fsbrowser.service.ImpDBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImpDBServiceImpl implements ImpDBService {

    @Autowired
    private ImpDBRepo impDBRepo;
    @Override
    public List<ImpDB> getAllImpDB() {
        return impDBRepo.findAll();
    }

    @Override
    public ImpDB getImpDBById(String id) {
        Optional<ImpDB> optional = impDBRepo.findById(id);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            throw new RuntimeException("ImpDB not found for id: " + id);
        }
    }

    @Override
    public void saveImpDB(ImpDB impDB) {
        impDBRepo.save(impDB);
    }
}
