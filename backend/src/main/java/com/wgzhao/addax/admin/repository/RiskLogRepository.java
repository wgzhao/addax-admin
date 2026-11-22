package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.RiskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskLogRepository
    extends JpaRepository<RiskLog, Long>
{
    /**
     * Used to de-duplicate repeated risks for the same table; a schema refresh can otherwise add the
     * same warning on every run and bury the interesting entries.
     */
    boolean existsByMessageAndTid(String message, Long tid);

    boolean existsByMessageAndTidIsNull(String message);
}
