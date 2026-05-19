package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlTableChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EtlTableChangeLogRepo
    extends JpaRepository<EtlTableChangeLog, Long>
{
    Page<EtlTableChangeLog> findByTidOrderByChangedAtDescIdDesc(Long tid, Pageable pageable);

    @Query(
        value = """
            select *
            from etl_table_change_log
            where tid = :tid
              and jsonb_exists(changed_fields, :field)
            order by changed_at desc, id desc
            """,
        countQuery = """
            select count(*)
            from etl_table_change_log
            where tid = :tid
              and jsonb_exists(changed_fields, :field)
            """,
        nativeQuery = true
    )
    Page<EtlTableChangeLog> findByTidAndChangedField(@Param("tid") Long tid, @Param("field") String field, Pageable pageable);
}
