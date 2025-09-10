package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbDict;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TbDictRepo extends JpaRepository<TbDict, Integer> {

    TbDict findByDictCode(int code);
}
