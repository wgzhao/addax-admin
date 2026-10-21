package com.wgzhao.addax.admin.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditMaintenanceService
{
    private final AuditRepository auditRepository;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${audit.retention-days:365}")
    private int retentionDays;

    @Scheduled(cron = "${audit.retention-cron:0 30 2 * * *}")
    @Transactional
    public void purgeExpiredEvents()
    {
        if (retentionDays < 30) {
            log.warn("Audit retention is {} days, refusing to purge below 30 days", retentionDays);
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        jdbcTemplate.queryForObject(
            "select set_config('addax.audit_purge', 'on', true)",
            String.class
        );
        long deleted = auditRepository.purgeBefore(cutoff, 5000);
        if (deleted > 0) {
            auditRecorder.success("AUDIT_RETENTION_PURGED", "DATA_ACCESS", "WARNING",
                "AUDIT_EVENT", null, null, null, null,
                Map.of("cutoff", cutoff.toString(), "deleted", deleted, "retentionDays", retentionDays));
            log.info("Purged {} audit event(s) older than {}", deleted, cutoff);
        }
    }
}
