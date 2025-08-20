package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbImpChkEtl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface TbImpChkEtlRepo extends JpaRepository<TbImpChkEtl, String> {
    @Query(value = """
            with t_cnt as
            	(select tblname,
            			max(case when logdate=:ltd and kind='new_cnt' then cnt else -1 end) cnt_ltd,
            			max(case when logdate=:td and kind='new_cnt' then cnt else -1 end) cnt_td,
            			sum(case when logdate=:td and kind in('new-old','old-new') then cnt else 0 end) cnt_err
            		from tb_imp_chk_etl
            		where logdate in(:ltd, :td)
            		group by tblname)
            select t.* from (
            	select *,
            		case when cnt_err > 0 then 1
            			when 1.00*cnt_ltd*cnt_td = 0 and cnt_ltd+cnt_td > 0 then 2
            			when 1.00*cnt_ltd*cnt_td > 0 and cnt_ltd+cnt_td > 300 and abs(cnt_td-cnt_ltd)>cnt_ltd*0.5 then 3
            		else 0 end err_kind
            	from t_cnt
            ) t
            where err_kind>0
            order by err_kind, tblname
            """, nativeQuery = true)
    List<Map<String, Object>> findAbnormalRecord(@Param("ltd") String ltd, @Param("td") String td);
}
