package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EtlTargetRepo
    extends JpaRepository<EtlTarget, Long>
{
    Optional<EtlTarget> findByCode(String code);

    List<EtlTarget> findByEnabledTrueOrderByIsDefaultDescIdAsc();

    List<EtlTarget> findAllByOrderByIsDefaultDescIdAsc();

    @Modifying
    @Query("update EtlTarget t set t.isDefault = false where t.id <> :id and t.isDefault = true")
    void clearDefaultExcept(@Param("id") Long id);
}
