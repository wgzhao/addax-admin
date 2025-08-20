package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbImpSpNeedtab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TbImpSpNeedtabRepo extends JpaRepository<TbImpSpNeedtab, String> {
    @Query(value = """
            select distinct
              CASE
                WHEN kind = 'ALL' THEN 'SP基础表'
                WHEN kind = 'DS' THEN '数据服务源表'
                WHEN kind = 'NEEDS' THEN 'SP前置依赖'
                ELSE '其他类型'
              END AS kind,
            used from TbImpSpNeedtab
            where lower(tableName)=:tablename or lower(tableName) like 'ods' || :sysId || '.%'
        """, nativeQuery = false)
    List<TbImpSpNeedtab> findByTableName(String tablename, String sysId);

    List<TbImpSpNeedtab> findDistinctByTableNameIgnoreCase(String tbl);
}
