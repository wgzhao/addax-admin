package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.ImpDB;

import java.util.List;


public interface ImpDBService {
    List<ImpDB> getAllImpDB();

    ImpDB getImpDBById(String id);

    void saveImpDB(ImpDB impDB);
}
