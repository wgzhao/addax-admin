package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlColumnMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtlColumnMappingRepo extends JpaRepository<EtlColumnMapping, Long>
{
    List<EtlColumnMapping> findAllByTidOrderByOrdinalNoAscIdAsc(Long tid);

    void deleteAllByTid(Long tid);
}
