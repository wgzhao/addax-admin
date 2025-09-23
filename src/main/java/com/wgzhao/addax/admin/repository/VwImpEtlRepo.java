package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.dto.VwImpEtlListDto;
import com.wgzhao.addax.admin.model.VwImpEtl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VwImpEtlRepo extends JpaRepository<VwImpEtl, String> {
    Page<VwImpEtl> findByFlagAndFilterColumnContaining(String etlFlag, String etlFilter, Pageable pageable);
    
    List<VwImpEtl> findByFlag(String etlFlag, Sort by);

    Page<VwImpEtl> findByFilterColumnContaining(String q, Pageable pageable);

    Page<VwImpEtlListDto> findAllProjectedBy(Pageable page);

    Page<VwImpEtlListDto> findAllProjectedByFilterColumnContaining(String upperCase, Pageable pageable);

    List<VwImpEtl> findAllByFilterColumnContaining(String q);
}
