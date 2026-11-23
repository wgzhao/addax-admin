package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.service.DailyBatchService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * Publishes the once-a-day "batch finished" summary. Master-only: exactly one node
 * may decide a business date is done, and the Redis marker in the service makes a
 * failover in the middle of the check harmless.
 *
 * <p>One-minute granularity is deliberate: the aggregate query is cheap, and a
 * faster tick would only race the last retry's backoff without changing the fact.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DailyBatchScheduler
{
    private static final Duration TICK = Duration.ofSeconds(60);

    private final TaskScheduler taskScheduler;
    private final MasterElectionService electionService;
    private final DailyBatchService batchService;

    private volatile ScheduledFuture<?> future;

    @PostConstruct
    public void init()
    {
        electionService.onBecameMaster(this::register);
        electionService.onLostMaster(this::cancel);
        if (electionService.isMaster()) {
            register();
        }
    }

    private synchronized void register()
    {
        if (future != null && !future.isCancelled()) {
            return;
        }
        log.info("Became master — scheduling the daily batch completion check");
        future = taskScheduler.scheduleWithFixedDelay(this::tick, TICK);
    }

    private synchronized void cancel()
    {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    private void tick()
    {
        if (!electionService.isMaster()) {
            return;
        }
        try {
            batchService.checkCurrentPeriod();
        }
        catch (Exception e) {
            log.error("Daily batch completion check failed", e);
        }
    }
}
