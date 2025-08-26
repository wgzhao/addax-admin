package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.dto.EtlTime;
import com.wgzhao.addax.admin.repository.BaseRepository;
import com.wgzhao.addax.admin.model.oracle.TbImpFlag;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

public interface TbImpFlagRepo extends BaseRepository<TbImpFlag, String> {
    List<TbImpFlag> findByTradedateAndKind(Integer date, String taskGroup);

    List<TbImpFlag> findByTradedateAndKindOrderByDwCltDateDesc(Integer i, String taskGroup);

    //最近5天采集耗时对比

    @Query(value = """
            select tradedate as tradeDate,fid,
                   trunc((max(case when fval=4 then dw_clt_date end) -
                   	max(case when fval=3 then dw_clt_date end))*24*60*60) runtime
            from tb_imp_flag
            where kind in('ETL_END','ETL_START') and tradedate>=?1 and fval in(3,4)
            group by tradedate,fid
            having max(case when fval=3 then 1 else 0 end)=max(case when fval=4 then 1 else 0 end)
            order by tradeDate,fid
            """, nativeQuery = true)
    List<EtlTime> findLast5DaysEtlTime(Integer l5td);

}
