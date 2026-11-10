package com.wgzhao.addax.admin.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.alert.model.AlertChannel;
import com.wgzhao.addax.admin.alert.model.AlertDelivery;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import com.wgzhao.addax.admin.alert.repo.AlertChannelRepo;
import com.wgzhao.addax.admin.alert.repo.AlertDeliveryRepo;
import com.wgzhao.addax.admin.alert.repo.AlertEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DB-outbox delivery worker. Rows are claimed with FOR UPDATE SKIP LOCKED and a
 * lease, then the HTTP call runs outside the claim transaction, so a slow
 * vendor does not hold database locks and a crashed node's rows are reaped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertDeliveryWorker
{
    private static final int MAX_ATTEMPTS = 5;
    private static final long[] RETRY_DELAY_SECONDS = {60, 300, 900, 3600, 21600};
    private static final String CLAIM_SQL = """
        update alert_delivery d
           set status = 'SENDING',
               attempts = d.attempts + 1,
               locked_by = ?,
               locked_until = clock_timestamp() + interval '60 seconds',
               updated_at = clock_timestamp()
         where d.id in (
             select id from alert_delivery
              where status in ('PENDING', 'FAILED')
                and next_retry_at is not null
                and next_retry_at <= clock_timestamp()
              order by next_retry_at, id
              limit ?
              for update skip locked
         )
         returning d.id
        """;
    private static final String REAP_SQL = """
        update alert_delivery
           set status = case when attempts >= 5 then 'DEAD' else 'FAILED' end,
               next_retry_at = clock_timestamp(),
               locked_by = null,
               locked_until = null,
               last_error = coalesce(last_error, 'delivery lease expired'),
               updated_at = clock_timestamp()
         where status = 'SENDING'
           and locked_until is not null
           and locked_until < clock_timestamp()
        """;

    private final JdbcTemplate jdbcTemplate;
    private final AlertDeliveryRepo deliveryRepo;
    private final AlertEventRepo eventRepo;
    private final AlertChannelRepo channelRepo;
    private final AlertChannelService channelService;
    private final AlertProviderRegistry providerRegistry;
    private final AlertMessageFactory messageFactory;
    private final AlertRateLimiter rateLimiter;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String workerId = resolveWorkerId();

    @Value("${alert.delivery.enabled:true}")
    private boolean enabled;

    @Value("${alert.delivery.batch-size:20}")
    private int batchSize;

    @Scheduled(
        initialDelayString = "${alert.delivery.initial-delay-ms:5000}",
        fixedDelayString = "${alert.delivery.interval-ms:2000}"
    )
    public void tick()
    {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            jdbcTemplate.update(REAP_SQL);
            int size = Math.min(Math.max(batchSize, 1), 100);
            List<Long> ids = jdbcTemplate.queryForList(CLAIM_SQL, Long.class, workerId, size);
            for (Long id : ids) {
                process(id);
            }
        }
        catch (Exception ex) {
            log.error("Alert delivery worker tick failed", ex);
        }
        finally {
            running.set(false);
        }
    }

    private void process(long deliveryId)
    {
        Optional<AlertDelivery> deliveryOpt = deliveryRepo.findById(deliveryId);
        if (deliveryOpt.isEmpty()) {
            return;
        }
        AlertDelivery delivery = deliveryOpt.get();
        if (delivery.getChannelId() == null) {
            dead(delivery, "通道已删除");
            return;
        }
        Optional<AlertChannel> channelOpt = channelRepo.findById(delivery.getChannelId());
        if (channelOpt.isEmpty()) {
            dead(delivery, "通道已删除");
            return;
        }
        AlertChannel channel = channelOpt.get();
        delivery.setChannelName(channel.getName());
        if (!channel.isEnabled()) {
            dead(delivery, "通道已停用，投递取消");
            return;
        }
        Optional<AlertEvent> eventOpt = eventRepo.findById(delivery.getEventId());
        if (eventOpt.isEmpty()) {
            dead(delivery, "告警事件不存在");
            return;
        }
        if (!rateLimiter.tryAcquire(channel.getId(), channel.getRateLimitPerMinute())) {
            delivery.setStatus(AlertDeliveryStatus.PENDING);
            delivery.setAttempts(Math.max(0, delivery.getAttempts() - 1));
            delivery.setNextRetryAt(rateLimiter.nextWindow());
            delivery.setLockedBy(null);
            delivery.setLockedUntil(null);
            deliveryRepo.save(delivery);
            log.debug("Delivery {} postponed by channel rate limit", deliveryId);
            return;
        }

        AlertDeliveryResult result;
        try {
            JsonNode config = channelService.decryptConfig(channel);
            AlertChannelProvider provider = providerRegistry.get(channel.getType());
            AlertMessage message = messageFactory.fromEvent(eventOpt.get());
            result = provider.send(config, message);
        }
        catch (Exception ex) {
            result = AlertDeliveryResult.failed(null, null, 0, ex.getMessage());
        }

        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        delivery.setResponseCode(result.responseCode());
        delivery.setResponseBody(result.responseBody());
        if (result.success()) {
            delivery.setStatus(AlertDeliveryStatus.SENT);
            delivery.setSentAt(Instant.now());
            delivery.setLastError(null);
            delivery.setNextRetryAt(null);
            deliveryRepo.save(delivery);
            log.info("Alert delivery {} sent via {} in {}ms", deliveryId, channel.getName(), result.latencyMs());
            return;
        }

        String error = result.error() == null ? "发送失败" : result.error();
        if (error.length() > 1000) {
            error = error.substring(0, 1000);
        }
        delivery.setLastError(error);
        if (delivery.getAttempts() >= MAX_ATTEMPTS) {
            delivery.setStatus(AlertDeliveryStatus.DEAD);
            delivery.setNextRetryAt(null);
            log.error("Alert delivery {} dead after {} attempts: {}", deliveryId, delivery.getAttempts(), error);
        }
        else {
            int delayIndex = Math.min(delivery.getAttempts() - 1, RETRY_DELAY_SECONDS.length - 1);
            delivery.setStatus(AlertDeliveryStatus.FAILED);
            delivery.setNextRetryAt(Instant.now().plusSeconds(RETRY_DELAY_SECONDS[Math.max(delayIndex, 0)]));
            log.warn("Alert delivery {} attempt {} failed, retry scheduled: {}",
                deliveryId, delivery.getAttempts(), error);
        }
        deliveryRepo.save(delivery);
    }

    private void dead(AlertDelivery delivery, String reason)
    {
        delivery.setStatus(AlertDeliveryStatus.DEAD);
        delivery.setLastError(reason);
        delivery.setNextRetryAt(null);
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        deliveryRepo.save(delivery);
    }

    private static String resolveWorkerId()
    {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        }
        catch (Exception ex) {
            return "addax-admin-" + ProcessHandle.current().pid();
        }
    }
}
