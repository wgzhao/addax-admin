package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.model.oracle.VwImpEtl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VwImpEtlRepo extends JpaRepository<VwImpEtl, String> {
    List<VwImpEtl> findByFlagAndFilterColumnContaining(String etlFlag, String etlFilter, Sort by);
    
    List<VwImpEtl> findByFlag(String etlFlag, Sort by);

    Page<VwImpEtl> findByFilterColumnContaining(String q, Pageable pageable);
}
