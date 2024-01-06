package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.model.oracle.TbDict;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TbDictRepo extends JpaRepository<TbDict, String> {

    TbDict findByDictCode(String code);
}
