package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.RiskLog;
import com.wgzhao.addax.admin.repository.RiskLogRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class RiskLogService {

    private final RiskLogRepository repository;

    @Transactional
    public void recordRisk(String source, String level, String message, Long referenceId) {
        String text = message == null ? "" : message;
        boolean seen = referenceId == null
            ? repository.existsByMessageAndTidIsNull(text)
            : repository.existsByMessageAndTid(text, referenceId);
        if (seen) {
            log.debug("Skip duplicated risk log for tid {}: {}", referenceId, text);
            return;
        }
        RiskLog r = new RiskLog();
        r.setRiskLevel(level == null ? "WARN" : level);
        r.setSource(source == null ? "unknown" : source);
        r.setMessage(text);
        r.setTid(referenceId);
        repository.save(r);
    }

    public List<RiskLog> getRecentRisks(int limit) {
        Pageable p = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "createdAt"));
        // 一周前的日期
        LocalDateTime beforeDays = LocalDateTime.now().plusDays(-7);
        return repository.findAllByCreatedAtAfterOrderByCreatedAtDesc(beforeDays, p);
    }
}
