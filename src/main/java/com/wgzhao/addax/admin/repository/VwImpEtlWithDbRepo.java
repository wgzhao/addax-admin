package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.VwImpEtlWithDb;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VwImpEtlWithDbRepo extends JpaRepository<VwImpEtlWithDb, String>
{
    Page<VwImpEtlWithDb> findByFilterColumnContaining(String upperCase, Pageable pageable);

    Page<VwImpEtlWithDb> findByFlagAndFilterColumnContaining(String flag, String upperCase, Pageable pageable);
}
