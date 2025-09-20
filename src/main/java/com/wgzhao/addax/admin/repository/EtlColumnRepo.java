package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlColumnPk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EtlColumnRepo
        extends JpaRepository<EtlColumn, EtlColumnPk>
{
    List<EtlColumn> findAllByTid(long tid);

    @Query(value = """
            select string_agg('"'  || column_name || '"', ',' order by column_id) as columns
            from etl_column where tid = :tid
            """, nativeQuery = true
    )
    String getAllColumns(long tid);
}
