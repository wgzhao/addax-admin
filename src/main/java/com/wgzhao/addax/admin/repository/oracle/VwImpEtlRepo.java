package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.model.oracle.VwImpEtl;
import com.wgzhao.addax.admin.repository.BaseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VwImpEtlRepo extends BaseRepository<VwImpEtl, String> {
    List<VwImpEtl> findByFlagAndFilterColumnContaining(String etlFlag, String etlFilter, Sort by);

    List<VwImpEtl> findByFilterColumnContaining(String etlFilter, Sort by);

    List<VwImpEtl> findByFlag(String etlFlag, Sort by);
}
