package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.model.oracle.TbImpEtl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TbImpEtlRepo extends JpaRepository<TbImpEtl, String> {

    @Query("SELECT t.souTablename FROM TbImpEtl t WHERE t.souSysid = :sysId AND t.souOwner = :souOwner")
    List<String> findTables(String sysId, String souOwner);
}
