package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface VwEtlTableWithSourceRepo

    : JpaRepository<VwEtlTableWithSource?, Long?> {
    fun findByStatusAndFilterColumnContaining(status: String?, filterContent: String?, pageable: Pageable?): Page<VwEtlTableWithSource?>?

    fun findByFilterColumnContaining(filterContent: String?, pageable: Pageable?): Page<VwEtlTableWithSource?>?

    fun findBySidAndSourceDb(sid: Int, db: String?): MutableList<VwEtlTableWithSource?>?

    fun findByEnabledTrueAndStatusNot(x: String?): MutableList<VwEtlTableWithSource?>?

    fun findBySidAndEnabledTrueAndStatusNot(sid: Int, status: String?): MutableList<VwEtlTableWithSource?>?
}
