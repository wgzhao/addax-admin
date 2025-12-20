package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface VwEtlTableWithSourceRepo
        extends JpaRepository<VwEtlTableWithSource, Long>
{

    Page<VwEtlTableWithSource> findByEnabledIsTrueAndStatusAndFilterColumnContaining(String status, String filterContent, Pageable pageable);

    Page<VwEtlTableWithSource> findByFilterColumnContaining(String filterContent, Pageable pageable);

    List<VwEtlTableWithSource> findBySidAndSourceDb(int sid, String db);

    List<VwEtlTableWithSource> findByEnabledTrueAndStatusNot(String x);

    List<VwEtlTableWithSource> findBySidAndEnabledTrueAndStatusNot(int sid, String status);
}
