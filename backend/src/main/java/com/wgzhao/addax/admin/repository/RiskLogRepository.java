package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.RiskLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskLogRepository extends JpaRepository<RiskLog, Long> {

    List<RiskLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<RiskLog> findBySourceOrderByCreatedAtDesc(String source, Pageable pageable);
}
