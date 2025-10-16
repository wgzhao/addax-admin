package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.dto.AddaxLogDto
import com.wgzhao.addax.admin.model.AddaxLog
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface AddaxLogRepo : JpaRepository<AddaxLog?, Long?> {
    fun findFirstByTidOrderByRunDateDesc(tid: Long?): AddaxLog?

    fun findTop5ByTidOrderByRunDateDesc(tid: Long?): List<AddaxLog?>?

    fun findTop5ByTidOrderByRunAtDesc(tid: Long?): List<AddaxLogDto>?
}
