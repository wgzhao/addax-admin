package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlReconcile;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlReconcileRepo
    extends JpaRepository<EtlReconcile, Long>
{
    boolean existsByExecutionId(Long executionId);

    Optional<EtlReconcile> findTop1ByTidAndBizDateOrderByIdDesc(long tid, LocalDate bizDate);

    List<EtlReconcile> findTop15ByTidOrderByIdDesc(long tid);
}
