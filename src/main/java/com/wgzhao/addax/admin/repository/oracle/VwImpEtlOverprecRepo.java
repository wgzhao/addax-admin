package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.model.oracle.VwImpEtlOverprec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

public interface VwImpEtlOverprecRepo extends JpaRepository<VwImpEtlOverprec, String> {

    List<VwImpEtlOverprec> findAllByOrderByDbStartAsc();

    @Query(value = """
            SELECT SYSNAME, round(OVER_PREC,2)*100 AS OVER_PREC,
            CASE
            	WHEN OVER_PREC = 1
            	THEN 'bg-success'
            	WHEN OVER_PREC <= 0.4
            	THEN 'bg-danger'
            	WHEN OVER_PREC <=0.6
            	THEN 'bg-warning'
            	ELSE 'bg-info'
            END AS BG_COLOR
            FROM VW_IMP_ETL_OVERPREC
            """, nativeQuery = true)
    List<Map<String, Float>> accompListRatio();
}
