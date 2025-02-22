package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.model.oracle.TbImpDb;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TbImpDBRepo extends JpaRepository<TbImpDb, String> {
    TbImpDb findByDbIdEtl(String dbName);
}
