package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.oracle.Dict;
import com.wgzhao.fsbrowser.repository.oracle.DictRepo;
import com.wgzhao.fsbrowser.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DictServiceImpl implements DictService {

    @Autowired
    DictRepo dictRepo;

    @Override
    public Dict save(Dict dict) {
        return dictRepo.save(dict);
    }

    @Override
    public List<Dict> getAllDict() {
        return dictRepo.findAll();
    }

    @Override
    public Dict findByCode(String code) {
        return dictRepo.findByDictCode(code);
    }

    @Override
    public void deleteByCode(String code) {
        dictRepo.deleteById(code);
    }

    @Override
    public void delete(Dict dict) {
        dictRepo.delete(dict);
    }
}
