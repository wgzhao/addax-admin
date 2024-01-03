package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.VwImpDs2;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VwImpDs2Repo extends JpaRepository<VwImpDs2, String> {
    VwImpDs2 findByDsId(String id);
}
