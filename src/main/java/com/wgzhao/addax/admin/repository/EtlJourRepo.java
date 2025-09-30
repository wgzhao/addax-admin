package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlJour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EtlJourRepo
        extends JpaRepository<EtlJour, Long> {

    void deleteAllByTid(long tableId);

    @Query(value = """
            select error_msg from etl_jour
            where tid = :tableId and status = false
            order by id desc
            limit 1
            """, nativeQuery = true)
    String findLastError(long tableId);
    // 可根据需要添加自定义查询方法
}
