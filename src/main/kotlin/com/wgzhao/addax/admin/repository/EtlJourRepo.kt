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

    @Query(
        value = """
            select error_msg from etl_jour
            where tid = :tableId and status = false
            order by id desc
            limit 1
            
            """.trimIndent(), nativeQuery = true
    )
    fun findLastError(tableId: Long): String?

    fun findFirstByTidAndStatusIsFalse(tableId: Long): Optional<EtlJour?>? // 可根据需要添加自定义查询方法
}
