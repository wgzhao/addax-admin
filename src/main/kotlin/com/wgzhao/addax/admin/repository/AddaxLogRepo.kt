package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.dto.AddaxLogDto
import com.wgzhao.addax.admin.model.AddaxLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface AddaxLogRepo : JpaRepository<AddaxLog?, Long?> {
    fun findFirstByTidOrderByRunDateDesc(tid: Long?): AddaxLog?

    fun findTop5ByTidOrderByRunDateDesc(tid: Long?): List<AddaxLog?>?

    fun findTop5ByTidAndRunDateGreaterThanOrderByIdDesc(tid: Long?, runDate: LocalDate?): List<AddaxLog?>?

    fun findByTidAndRunDate(tid: Long?, runDate: LocalDate?): AddaxLog?

    fun findTop5ByTidOrderByRunAtDesc(tid: String?): List<AddaxLogDto>?
}
