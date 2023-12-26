package com.wgzhao.fsbrowser.repository.pg;

import com.wgzhao.fsbrowser.model.pg.TbImpChkSpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ImpChkSpRepo extends JpaRepository<TbImpChkSpEntity, String> {
    @Query(value = "select t.logdate, t.procName, t.checkValue,t.updtDate, " +
            " case when t.procName='edw.com_date' and t.checkValue<>0 then 1 else 0 end as remark " +
            "from TbImpChkSpEntity t " +
            "where t.checkItem='2' and t.logdate>=?1")
    List<TbImpChkSpEntity> findValidChkSp(String l5td);
}
