package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.Dict;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictRepo extends JpaRepository<Dict, String> {

    Dict findByDictCode(String dictCode);
}
