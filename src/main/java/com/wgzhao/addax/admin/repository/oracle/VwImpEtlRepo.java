package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.dto.VwImpEtlListDto;
import com.wgzhao.addax.admin.model.oracle.VwImpEtl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VwImpEtlRepo extends JpaRepository<VwImpEtl, String> {
    List<VwImpEtl> findByFlagAndFilterColumnContaining(String etlFlag, String etlFilter, Sort by);
    
    List<VwImpEtl> findByFlag(String etlFlag, Sort by);

    Page<VwImpEtl> findByFilterColumnContaining(String q, Pageable pageable);

    Page<VwImpEtlListDto> findAllProjectedBy(Pageable page);

    Page<VwImpEtlListDto> findAllProjectedByFilterColumnContaining(String upperCase, Pageable pageable);

    List<VwImpEtl> findAllByFilterColumnContaining(String q);
}
