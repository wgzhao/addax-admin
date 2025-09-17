package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.LastEtlTaketime;
import com.wgzhao.addax.admin.model.ViewPseudo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

/**
 * Define a pseudo repository for querying views
 */
public interface ViewPseudoRepo
        extends JpaRepository<ViewPseudo, Long>
{


    // SP 整体执行情况
    @Query(value = """
            select sp_owner, flag, count(1) cnt, min(start_time) as start_time, max(end_time) as end_time,
                   extract(epoch from (max(end_time)-min(start_time))) as runtime
            from vw_imp_sp t
            where bvalid=1 and bfreq=1
            group by sp_owner,flag
            order by 1,2
            """, nativeQuery = true)
    List<Map<String, Object>> findSpExecInfo();


}
