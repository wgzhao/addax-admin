package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.VwImpSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VwImpSystemRepo extends JpaRepository<VwImpSystem, String> {


    @Query(value = """
        select sysKind,sysid,sysName,dbConstr,dbUser
        from VwImpSystem
        where ((sysKind='etl' and length(sysid)=2) or sysKind='ds')
        order by 1,2
        """)
    List<VwImpSystem> findEtlAndDs();

    @Query(value = """
        select sysKind,sysid,sysName,dbConstr,dbUser
        from VwImpSystem
        where ((sysKind='etl' and length(sysid)=2) or sysKind='ds')
            and lower(sysid||sysName||dbConstr||dbUser) like lower('%?1%')
        order by 1,2
        """)
    List<VwImpSystem> findEtlAndDs(String filter);
}
