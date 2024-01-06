package com.wgzhao.addax.admin.repository.pg;

import com.wgzhao.addax.admin.model.pg.TbImpChkSpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

public interface TbImpChkSpRepo extends JpaRepository<TbImpChkSpEntity, String> {
    @Query(value = "select t.logdate, t.procName, t.checkValue,t.updtDate, " +
            " case when t.procName='edw.com_date' and t.checkValue<>0 then 1 else 0 end as remark " +
            "from TbImpChkSpEntity t " +
            "where t.checkItem='2' and t.logdate>=?1")
    List<TbImpChkSpEntity> findValidChkSp(String l5td);

    @Query(value = "select t.*,\n" +
            "    case when sou_cnt<>dest_cnt then 1 else 0 end berr\n" +
            "from (\n" +
            "    select t.logdate,t.proc_name,\n" +
            "        cast(sum(case when t.check_sou='S' then check_value else 0 end) as int) sou_cnt,\n" +
            "        cast(sum(case when t.check_sou='D' then check_value else 0 end) as int) dest_cnt\n" +
            "    from tb_imp_chk_sp t\n" +
            "    inner join (select logdate,proc_name,check_item,check_sou,max(updt_date) as td from tb_imp_chk_sp where check_item='1' group by logdate,proc_name,check_item,check_sou\n" +
            "        ) a on a.logdate=t.logdate and a.proc_name=t.proc_name and a.check_item=t.check_item and a.check_sou=t.check_sou and a.td=t.updt_date\n" +
            "    where t.check_item='1' and t.logdate>=?1\n" +
            "    group by t.logdate,t.proc_name\n" +
            ") t\n" +
            "order by case when sou_cnt<>dest_cnt then 1 else 0 end desc,t.proc_name asc,t.logdate desc", nativeQuery = true)
    List<Map<String, Object>> findValidSpCnt(String l5td);
}
