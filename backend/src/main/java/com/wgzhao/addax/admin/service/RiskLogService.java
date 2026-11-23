package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.RiskLog;
import com.wgzhao.addax.admin.repository.RiskLogRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
