package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.LastEtlTaketime;
import com.wgzhao.fsbrowser.model.oracle.ViewPseudoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

/**
 * Define a pseudo repository for querying views
 */
public interface ViewPseudoRepo extends JpaRepository<ViewPseudoEntity, Long> {

    // 特殊任务提醒
    @Query(value="select spname, flag,retry_cnt,runtime," +
            "to_char(start_time,'yyyy-MM-dd HH:mm:ss') as start_time," +
            "to_char(end_time,'yyyy-MM-dd HH:mm:ss') as end_time " +
            "from vw_imp_etl " +
            "where (flag='E' or runtime>=1200 or retry_cnt<3) and bvalid=1 " +
            "order by flag asc,runtime desc", nativeQuery = true)
    List<Map> findAllSepcialTask();

    // 任务拒绝行
    @Query(value="select jobname spname, total_err kvalue,start_ts start_time, end_ts end_time " +
            "from tb_addax_sta " +
            "where total_err<>0", nativeQuery = true)
    List<Map> findTaskReject();

    // 日间实时采集任务
    @Query(value = "select LAST_TIMES, NEXT_TIMES, SPNAME, " +
            "to_char(START_TIME,'yyyy-MM-dd HH:mm:ss') as START_TIME, " +
            "to_char(END_TIME, 'yyyy-MM-dd HH:mm:ss') as END_TIME " +
            " from vw_imp_realtimes_etl", nativeQuery = true)
    List<Map> findRealtimeTask();

    @Query(value = "select tradedate as trade_date, string_agg(fid, ',' order by px)  as fids, string_agg(cast(runtime as varchar),',' order by px) as take_times\n" +
            "from (\n" +
            "select tradedate,fid,\n" +
            "       cast(extract(epoch from\n" +
            "         max(case when fval='4' then dw_clt_date end) -\n" +
            "         max(case when fval='3' then dw_clt_date end)\n" +
            "       ) as int) as runtime,\n" +
            "       row_number()over(partition by fid order by tradedate) px\n" +
            "from tb_imp_flag\n" +
            "where kind in('ETL_END','ETL_START') and tradedate>=:l5td and fval in('3','4')\n" +
            "group by tradedate, fid\n" +
            "having max(case when fval='3' then 1 else 0 end)=max(case when fval='4' then 1 else 0 end)\n" +
            ") x group by tradedate", nativeQuery = true)
    List<LastEtlTaketime> findLast5LtdTaketimes(@Param("l5td") int l5td);
}
