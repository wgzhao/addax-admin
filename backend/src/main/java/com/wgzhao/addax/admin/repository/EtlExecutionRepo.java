package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlExecution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlExecutionRepo
    extends JpaRepository<EtlExecution, Long>
{
    Page<EtlExecution> findByTidOrderByStartedAtDescIdDesc(long tid, Pageable pageable);

    List<EtlExecution> findTop15ByTidOrderByStartedAtDescIdDesc(long tid);

    Optional<EtlExecution> findTop1ByTidOrderByStartedAtDescIdDesc(long tid);
}
