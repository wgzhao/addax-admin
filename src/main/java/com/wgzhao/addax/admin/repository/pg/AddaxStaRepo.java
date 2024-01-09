package com.wgzhao.addax.admin.repository.pg;

import com.wgzhao.addax.admin.model.pg.TbAddaxSta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface AddaxStaRepo extends JpaRepository<TbAddaxSta, Long> {
    List<TbAddaxSta> findByTotalErrNot(int i);

    @Query(value = """
            select round(sum(take_secs * 1.0 * byte_speed / 1024 / 1024 / 1024 ), 2) as data_size
            from tb_addax_sta
            where start_ts between :btime and :etime
            """, nativeQuery = true)
    double findLastEtlData(long btime, long etime);

    // 指定时间范围的数据采集量按月累计情况
    @Query(value = """
                SELECT
                    to_char(updt_date, 'YYYY-MM') as month,
                    round(SUM(SUM(take_secs * 1.0 * byte_speed / 1024 / 1024 / 1024 )) OVER (ORDER BY to_char(updt_date, 'YYYY-MM')),2) AS num
                FROM tb_addax_sta
                where updt_date > :btime and updt_date < :etime
                GROUP BY
                    to_char(updt_date, 'YYYY-MM')
                ORDER BY month
            """, nativeQuery = true)
    List<Map<String, Object>> findEtlDataByMonth(Date btime, Date etime);
}
