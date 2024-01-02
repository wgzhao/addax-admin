package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.VwImpEtl;
import com.wgzhao.fsbrowser.repository.BaseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VwImpEtlRepo extends BaseRepository<VwImpEtl, String> {
    List<VwImpEtl> findByFlagAndFilterColumnContaining(String etlFlag, String etlFilter, Sort by);

    List<VwImpEtl> findByFilterColumnContaining(String etlFilter, Sort by);

    List<VwImpEtl> findByFlag(String etlFlag, Sort by);
}
