package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VwEtlTableWithSourceRepo extends JpaRepository<VwEtlTableWithSource, Long> {
    // 可根据需要添加自定义查询方法
    Page<VwEtlTableWithSource> findByNameContainingIgnoreCase(String sourceName, Pageable pageable);
    Page<VwEtlTableWithSource> findByStatusAndNameContainingIgnoreCase(String status, String sourceName, Pageable pageable);

    Page<VwEtlTableWithSource> findByStatusAndFilterColumnContaining(String status, String filterContent, Pageable pageable);

    Page<VwEtlTableWithSource> findByFilterColumnContaining(String filterContent, Pageable pageable);

    List<VwEtlTableWithSource> findBySidAndSourceDb(int sid, String db);
}
