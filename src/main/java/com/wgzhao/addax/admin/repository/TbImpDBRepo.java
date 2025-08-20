package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbImpDb;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TbImpDBRepo extends JpaRepository<TbImpDb, String> {
    TbImpDb findByDbIdEtl(String dbName);
}
