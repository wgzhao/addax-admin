package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.Dictionary;
import com.wgzhao.fsbrowser.repository.oracle.DictionaryRepo;
import com.wgzhao.fsbrowser.service.DictionaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DictionaryServiceImpl implements DictionaryService {
    @Autowired
    private DictionaryRepo dictionaryRepo;


    @Override
    public List<Dictionary> findByEntryCode(String entryCode) {
        return dictionaryRepo.findByEntryCode(entryCode);
    }
}
