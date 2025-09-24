package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EtlSourceRepo
        extends JpaRepository<EtlSource, Integer> {

    Integer countByEnabled(boolean b);

    List<EtlSource> findAllByEnabled(boolean b);

    boolean existsByCode(String code);

    List<EtlSource> findByEnabled(boolean b);
}
