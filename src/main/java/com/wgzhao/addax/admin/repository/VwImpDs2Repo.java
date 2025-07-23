package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.VwImpDs2;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VwImpDs2Repo extends JpaRepository<VwImpDs2, String> {
    VwImpDs2 findByDsId(String id);
}
