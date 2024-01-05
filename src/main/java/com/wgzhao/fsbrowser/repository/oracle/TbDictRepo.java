package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.TbDict;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TbDictRepo extends JpaRepository<TbDict, String> {

    TbDict findByDictCode(String code);
}
