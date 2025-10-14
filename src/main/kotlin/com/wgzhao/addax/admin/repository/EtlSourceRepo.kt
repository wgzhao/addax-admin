package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.EtlSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EtlSourceRepo

    : JpaRepository<EtlSource?, Int?> {
    fun countByEnabled(b: Boolean): Int?

    fun findAllByEnabled(b: Boolean): MutableList<EtlSource?>?

    fun existsByCode(code: String?): Boolean
    fun findByEnabled(bool: Boolean): MutableList<EtlSource>?

}
