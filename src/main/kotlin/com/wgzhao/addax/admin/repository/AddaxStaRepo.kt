package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.TbAddaxSta
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface AddaxStaRepo : JpaRepository<TbAddaxSta?, Long?> {
    fun findByTotalErrNot(i: Int): MutableList<TbAddaxSta?>?

    @Query(
        value = """
            select coalesce(round(sum(take_secs * 1.0 * byte_speed / 1024 / 1024 / 1024 ), 2), 0) as data_size
            from tb_addax_sta
            where start_ts between :btime and :etime
            
            """.trimIndent(), nativeQuery = true
    )
    fun findLastEtlData(btime: Long, etime: Long): Double

    // 指定时间范围的数据采集量按月累计情况
    @Query(
        value = """
                SELECT
                    to_char(updt_date, 'YYYY-MM') as month,
                    round(SUM(SUM(take_secs * 1.0 * byte_speed / 1024 / 1024 / 1024 )) OVER (ORDER BY to_char(updt_date, 'YYYY-MM')),2) AS num
                FROM tb_addax_sta
                where updt_date > :btime and updt_date < :etime
                GROUP BY
                    to_char(updt_date, 'YYYY-MM')
                ORDER BY month
            
            """.trimIndent(), nativeQuery = true
    )
    fun findEtlDataByMonth(btime: Date?, etime: Date?): MutableList<MutableMap<String?, Any?>?>?
}
