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

    @Query(
        value = """
            select new com.wgzhao.addax.admin.dto.AddaxLogDto(a.id, to_char(a.runAt, 'YYYY-MM-DD HH24:MI:SS'))
            from AddaxLog a
            where a.tid = ?1
            order by a.runAt desc
            limit 5
            
            """.trimIndent()
    )
    fun findLogEntry(tid: String?): MutableList<AddaxLogDto?>?

    @Query(value = "select log from addax_log where id = ?1", nativeQuery = true)
    fun findLogById(id: Long?): String?
}
