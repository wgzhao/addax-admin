package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.model.oracle.TbImpSpNeedtab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TbImpSpNeedtabRepo extends JpaRepository<TbImpSpNeedtab, String> {
    @Query(value = """
        select  distinct decode(kind,'ALL','SP基础表','DS','数据服务源表','NEEDS','SP前置依赖','其他类型') AS kind,
        used from TbImpSpNeedtab
        where lower(tableName)=:tablename or lower(tableName) like 'ods' || :sysId || '.%'
        """, nativeQuery = false)
    List<TbImpSpNeedtab> findByTableName(String tablename, String sysId);

    List<TbImpSpNeedtab> findDistinctByTableNameIgnoreCase(String tbl);
}
