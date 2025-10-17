package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.SysDict
import org.springframework.data.jpa.repository.JpaRepository

interface SysDictRepo

    : JpaRepository<SysDict?, Int?> {
    fun findByCode(code: Int): SysDict?
}
