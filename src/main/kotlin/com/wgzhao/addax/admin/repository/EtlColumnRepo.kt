package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.EtlColumn
import com.wgzhao.addax.admin.model.EtlColumnPk
import org.springframework.data.jpa.repository.JpaRepository

interface EtlColumnRepo

    : JpaRepository<EtlColumn?, EtlColumnPk?> {
    fun findAllByTidOrderByColumnId(tid: Long): List<EtlColumn?>?

    fun deleteAllByTid(tableId: Long) //    @Modifying
}
