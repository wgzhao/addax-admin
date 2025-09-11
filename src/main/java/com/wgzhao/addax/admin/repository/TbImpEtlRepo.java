package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbImpEtl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TbImpEtlRepo extends JpaRepository<TbImpEtl, String> {

    @Query(value= """
            SELECT t.souTablename FROM TbImpEtl t WHERE t.souSysid = :sysId AND t.souOwner = :souOwner
            """)
    List<String> findTables(String sysId, String souOwner);

    @Query(value = """
            select t.tid from TbImpEtl t where t.souSysid <> 'DO' and t.bupdate = 'N' and t.bcreate = 'N'
            """)
    List<String> findValidTids();

    Page<TbImpEtl> findByFlagAndFilterColumnContaining(String etlFlag, String etlFilter, Pageable pageable);

    List<TbImpEtl> findByFlag(String etlFlag, Sort by);

    Page<TbImpEtl> findByFilterColumnContaining(String q, Pageable pageable);

    Page<TbImpEtl> findAllProjectedBy(Pageable page);

    Page<TbImpEtl> findAllProjectedByFilterColumnContaining(String upperCase, Pageable pageable);

    List<TbImpEtl> findAllByFilterColumnContaining(String q);

    @Query("SELECT t FROM TbImpEtl t WHERE t.bupdate = 'Y' OR t.bcreate = 'Y'")
    List<TbImpEtl> findByBupdateOrBcreateIsY();
}
