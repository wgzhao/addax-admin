package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VwEtlTableWithSourceRepo
    extends JpaRepository<VwEtlTableWithSource, Long>
{

    Page<VwEtlTableWithSource> findByEnabledIsTrueAndStatusAndFilterColumnContaining(String status, String filterContent, Pageable pageable);

    Page<VwEtlTableWithSource> findByEnabledIsTrueAndFilterColumnContaining(String filterContent, Pageable pageable);

    Page<VwEtlTableWithSource> findByEnabledIsTrue(Pageable pageable);

    // 支持按数据源ID分页查询（sid = source id）
    Page<VwEtlTableWithSource> findBySidAndEnabledIsTrueAndStatusAndFilterColumnContaining(int sid, String status, String filterContent, Pageable pageable);

    Page<VwEtlTableWithSource> findBySidAndEnabledIsTrueAndFilterColumnContaining(int sid, String filterContent, Pageable pageable);

    Page<VwEtlTableWithSource> findBySidAndEnabledIsTrue(int sid, Pageable pageable);

    List<VwEtlTableWithSource> findBySidAndSourceDb(int sid, String db);

    List<VwEtlTableWithSource> findByEnabledTrueAndStatusNot(String x);

    List<VwEtlTableWithSource> findBySidAndEnabledTrueAndStatusNot(int sid, String status);
}
