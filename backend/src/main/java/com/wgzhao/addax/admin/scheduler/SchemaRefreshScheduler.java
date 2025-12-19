package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.redis.RedisLockService;
import com.wgzhao.addax.admin.service.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.*;

/**
 * 每天在切日时间触发一次，进行表结构刷新。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SchemaRefreshScheduler implements DisposableBean {
    private final TaskScheduler taskScheduler;
    private final DictService dictService;
    private final TaskService taskService;
    private final RedisLockService redisLockService;

    private volatile ScheduledFuture<?> scheduledFuture;

    // Lock TTL and renewal interval
    private static final Duration LOCK_TTL = Duration.ofSeconds(600);
    private static final Duration LOCK_RENEW_INTERVAL = Duration.ofSeconds(60);

    // Shared renewer thread to avoid creating one per run
    private final ScheduledExecutorService renewer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "schema-refresh-lock-renewer");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        // Always schedule locally; actual execution is guarded by Redis lock so only one node will run.
        log.info("Initializing SchemaRefreshScheduler and scheduling local trigger");
        scheduleInternal();
    }

    private void scheduleInternal() {
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        String cron = toCron(switchTime);
        log.info("Scheduling schema refresh at {} (cron: {})", switchTime, cron);
        // 先取消已有任务，避免重复
        cancelInternal();
        scheduledFuture = taskScheduler.schedule(this::runRefresh, new CronTrigger(cron));
    }

    private void cancelInternal() {
        ScheduledFuture<?> future = this.scheduledFuture;
        if (future != null) {
            future.cancel(false);
            this.scheduledFuture = null;
        }
    }

    private String toCron(LocalTime time) {
        // cron format: second minute hour day-of-month month day-of-week
        return String.format("0 %d %d * * *", time.getMinute(), time.getHour());
    }

    public void runRefresh() {
        log.info("Schema refresh triggered");

        final String lockKey = Constants.SCHEMA_REFRESH_LOCK_KEY;
        String token = null;

        Future<?> renewTaskFuture = null;

        try {
            token = redisLockService.tryLock(lockKey, LOCK_TTL);
            if (token == null) {
                log.info("Could not acquire redis lock {}, another node is running refresh, skip", lockKey);
                return;
            }

            final String localToken = token;
            renewTaskFuture = renewer.scheduleAtFixedRate(() -> {
                try {
                    boolean ok = redisLockService.extend(lockKey, localToken, LOCK_TTL);
                    if (!ok) {
                        log.warn("Failed to renew redis lock {} with token {}", lockKey, localToken);
                    }
                } catch (Exception e) {
                    log.warn("Exception while renewing redis lock {}: {}", lockKey, e.getMessage());
                }
            }, LOCK_RENEW_INTERVAL.toMillis(), LOCK_RENEW_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);

            try {
                taskService.updateParams();
                log.info("Schema refresh finished successfully");
            } catch (Exception e) {
                log.error("Schema refresh failed", e);
            }
        } catch (Exception ex) {
            log.error("Error while attempting schema refresh or acquiring lock", ex);
        } finally {
            // stop renew task
            if (renewTaskFuture != null) {
                try {
                    renewTaskFuture.cancel(true);
                } catch (Exception ignored) {
                }
            }

            if (token != null) {
                boolean released = redisLockService.release(lockKey, token);
                if (!released) {
                    log.warn("Failed to release redis lock {} with token {} - will expire by TTL", lockKey, token);
                }
            }
        }
    }

    @Override
    public void destroy() {
        cancelInternal();
        try {
            renewer.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    public boolean isRefreshInProgress() {
        return  redisLockService.isLocked(Constants.SCHEMA_REFRESH_LOCK_KEY);
    }
}
