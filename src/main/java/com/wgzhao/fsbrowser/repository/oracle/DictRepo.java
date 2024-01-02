package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.Dict;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictRepo extends JpaRepository<Dict, String> {

    Dict findByDictCode(String code);
}
