package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.SysItem
import com.wgzhao.addax.admin.model.SysItemPK
import org.springframework.data.jpa.repository.JpaRepository

interface SysItemRepo : JpaRepository<SysItem, SysItemPK> {

    fun findFirstByDictCodeAndItemKeyOrderByItemKeyDesc(dictCode: Int, curDate: String): SysItem?
    fun findByDictCode(dictCode: Int): kotlin.collections.List<com.wgzhao.addax.admin.model.SysItem>
}
