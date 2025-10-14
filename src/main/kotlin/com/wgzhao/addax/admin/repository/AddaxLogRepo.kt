package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.dto.AddaxLogDto
import com.wgzhao.addax.admin.model.AddaxLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface AddaxLogRepo : JpaRepository<AddaxLog?, Long?> {
    fun findFirstByTidOrderByRunDateDesc(tid: Long?): AddaxLog?

    fun findTop5ByTidOrderByRunDateDesc(tid: Long?): MutableList<AddaxLog?>?

    fun findTop5ByTidAndRunDateGreaterThanOrderByIdDesc(tid: Long?, runDate: LocalDate?): MutableList<AddaxLog?>?

    fun findByTidAndRunDate(tid: Long?, runDate: LocalDate?): AddaxLog?

    fun findTop5ByTidOrderByRunAtDesc(tid: String?): List<AddaxLogDto>?
}
