package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.dto.DbSourceDto;
import com.wgzhao.addax.admin.model.EtlSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EtlSourceRepo
        extends JpaRepository<EtlSource, Integer> {

    @Query(value= """
            select new com.wgzhao.addax.admin.dto.DbSourceDto(dbIdEtl as sysid,
            dbIdEtl||'_'||dbName as name,
            dbConstr as url,
            dbUserEtl as username,
            dbPassEtl as password
            )
            from  EtlSource t
            where t.dbIdEtl is not null
            """)
    List<DbSourceDto> findEtlSource();

    Integer countByEnabled(boolean b);
}
