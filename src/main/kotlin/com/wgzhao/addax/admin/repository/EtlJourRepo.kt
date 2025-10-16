package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.EtlJour
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EtlJourRepo

    : JpaRepository<EtlJour?, Long?> {
    fun deleteAllByTid(tableId: Long)


    fun findFirstByTidAndStatusIsFalse(tableId: Long): EtlJour?

    fun findFirstByTidAndStatusIsFalseOrderByIdDesc(tableId: Long): EtlJour?
}
