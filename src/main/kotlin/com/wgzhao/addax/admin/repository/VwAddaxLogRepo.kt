package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.VwAddaxLog
import org.springframework.data.jpa.repository.JpaRepository

interface VwAddaxLogRepo : JpaRepository<VwAddaxLog?, String?> {
    fun findTop15BySpnameIn(spNames: MutableList<String?>?): MutableList<VwAddaxLog?>?
}
