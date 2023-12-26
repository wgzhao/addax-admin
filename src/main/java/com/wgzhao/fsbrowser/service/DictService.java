package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.Dict;

import java.util.List;

public interface DictService {
    Dict save(Dict dict);

    List<Dict> getAllDict();

    Dict findByCode(String code);

    void deleteByCode(String code);

    void delete(Dict dict);
}
