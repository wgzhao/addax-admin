package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.TbAddaxSta
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface AddaxStaRepo : JpaRepository<TbAddaxSta, Long> {
    /**
     * 查询所有 totalErr 不等于指定值的采集统计记录
     */
    fun findByTotalErrNot(i: Int): List<TbAddaxSta>

    /**
     * 查询指定时间范围内的数据采集量（单位：GB，保留两位小数）
     */
    @Query(
        value = """
            select coalesce(round(sum(take_secs * 1.0 * byte_speed / 1024 / 1024 / 1024 ), 2), 0) as data_size
            from tb_addax_sta
            where start_ts between :btime and :etime
            """, nativeQuery = true
    )
    fun findLastEtlData(btime: Long, etime: Long): Double

    /**
     * 查询指定时间范围内的数据采集量按月累计情况
     * 返回每月累计采集量（单位：GB，保留两位小数）
     */
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
            """, nativeQuery = true
    )
    fun findEtlDataByMonth(btime: Date, etime: Date): List<Map<String, Any>>
}
