package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.LastEtlTaketime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PesudoRepo extends JpaRepository<LastEtlTaketime, String> {


    @Query(value = "select tradedate as trade_date, string_agg(fid, ',' order by px)  as fids, string_agg(cast(runtime as varchar),',' order by px) as take_times\n" +
            "from (\n" +
            "select tradedate,fid,\n" +
            "       cast(extract(epoch from\n" +
            "         max(case when fval='4' then dw_clt_date end) -\n" +
            "         max(case when fval='3' then dw_clt_date end)\n" +
            "       ) as int) as runtime,\n" +
            "       row_number()over(partition by fid order by tradedate) px\n" +
            "from stg01.tb_imp_flag\n" +
            "where kind in('ETL_END','ETL_START') and tradedate>=:l5td and fval in('3','4')\n" +
            "group by tradedate, fid\n" +
            "having max(case when fval='3' then 1 else 0 end)=max(case when fval='4' then 1 else 0 end)\n" +
            ") x group by tradedate", nativeQuery = true)
    List<LastEtlTaketime> findLast5LtdTaketimes(@Param("l5td") int l5td);
}
