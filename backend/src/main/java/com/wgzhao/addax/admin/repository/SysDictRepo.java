package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.SysDict;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysDictRepo
    extends JpaRepository<SysDict, Integer>
{

    SysDict findByCode(int code);
}
