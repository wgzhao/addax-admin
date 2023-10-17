package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.ImpDB;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImpDBService {
    List<ImpDB> getAllImpDB();
    ImpDB getImpDBById(UUID id);

    void saveImpDB(ImpDB impDB);
}
