package com.wgzhao.addax.admin.audit;

import com.wgzhao.addax.admin.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditAlertScheduler
{
    private static final Duration ALERT_DEDUP_TTL = Duration.ofMinutes(30);

    private final AuditRepository auditRepository;
    private final AlertService alertService;
    private final StringRedisTemplate redisTemplate;

    @Value("${audit.alerts.failed-login-threshold:5}")
    private int failedLoginThreshold;

    @Value("${audit.alerts.failed-login-window-minutes:10}")
    private int failedLoginWindowMinutes;

    @Scheduled(
        initialDelayString = "${audit.alerts.initial-delay-ms:60000}",
        fixedDelayString = "${audit.alerts.interval-ms:300000}"
    )
    public void scan()
    {
        alertFailedLoginBursts();
        alertCriticalEvents();
    }

    private void alertFailedLoginBursts()
    {
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(failedLoginWindowMinutes);
        List<AuditRepository.LoginFailureBucket> buckets =
            auditRepository.failedLoginBuckets(since, failedLoginThreshold);
        for (AuditRepository.LoginFailureBucket bucket : buckets) {
            String key = "audit:alert:login:" + bucket.actor() + ":" + bucket.clientIp();
            if (markOnce(key)) {
                alertService.sendSecurityAlert(String.format(
                    "登录失败次数异常：账号 %s，来源 IP %s，最近 %d 分钟失败 %d 次",
                    bucket.actor(), bucket.clientIp(), failedLoginWindowMinutes, bucket.count()
                ));
            }
        }
    }

    private void alertCriticalEvents()
    {
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(failedLoginWindowMinutes);
        List<AuditRepository.CriticalEventSummary> events = auditRepository.criticalEvents(since);
        if (events.isEmpty()) {
            return;
        }
        String key = "audit:alert:critical:" + System.currentTimeMillis() / 300_000L;
        if (!markOnce(key)) {
            return;
        }
        String summary = events.stream()
            .map(event -> event.action() + "=" + event.count())
            .collect(Collectors.joining(", "));
        alertService.sendSecurityAlert("检测到关键安全审计事件：" + summary);
    }

    private boolean markOnce(String key)
    {
        try {
            return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, "1", ALERT_DEDUP_TTL)
            );
        }
        catch (Exception ex) {
            return false;
        }
    }
}
