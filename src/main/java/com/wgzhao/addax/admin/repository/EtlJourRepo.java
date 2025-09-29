package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlJour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EtlJourRepo
        extends JpaRepository<EtlJour, Long> {

    void deleteAllByTid(long tableId);
    // 可根据需要添加自定义查询方法
}
