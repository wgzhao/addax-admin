package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TaskService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.util.concurrent.ScheduledFuture;

/**
 * 每天在切日时间触发一次，进行表结构刷新。仅 master 节点注册定时任务；failover 后新 master 重新注册。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SchemaRefreshScheduler
    implements DisposableBean
{
    private final TaskScheduler taskScheduler;
    private final DictService dictService;
    private final SystemConfigService configService;
    private final TaskService taskService;
    private final MasterElectionService electionService;
    private final StringRedisTemplate stringRedisTemplate;

    private volatile ScheduledFuture<?> scheduledFuture;
    // Guards the one-shot 5-minute retry after a failed refresh: never retry-storm the refresh
    // (each attempt stops/restarts the queue monitor cluster-wide).
    private volatile boolean retryScheduled;

    @PostConstruct
    public void init()
    {
        electionService.onBecameMaster(() -> {
            log.info("Became master — registering schema refresh cron");
            scheduleInternal();
            maybeCatchUpRefresh();
        });
        electionService.onLostMaster(() -> {
            log.info("Lost master — cancelling schema refresh cron");
            cancelInternal();
        });
        // Guard against election tick firing before this @PostConstruct runs
        if (electionService.isMaster()) {
            scheduleInternal();
            maybeCatchUpRefresh();
        }
    }

    private synchronized void scheduleInternal()
    {
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        String cron = toCron(switchTime);
        log.info("Scheduling schema refresh at {} (cron: {})", switchTime, cron);
        cancelInternal();
        scheduledFuture = taskScheduler.schedule(this::runRefresh, new CronTrigger(cron));
    }

    /**
     * Public API for controllers/services: reschedule after SWITCH_TIME is changed.
     * No-op on worker nodes.
     */
    public synchronized void reschedule()
    {
        if (!electionService.isMaster()) {
            log.debug("reschedule() ignored on non-master node");
            return;
        }
        log.info("Rescheduling schema refresh trigger due to switch time update");
        scheduleInternal();
    }

    private void cancelInternal()
    {
        ScheduledFuture<?> future = this.scheduledFuture;
        if (future != null) {
            future.cancel(false);
            this.scheduledFuture = null;
        }
    }

    private String toCron(LocalTime time)
    {
        return String.format("0 %d %d * * *", time.getMinute(), time.getHour());
    }

    public void runRefresh()
    {
        log.info("Schema refresh triggered");
        try {
            taskService.updateParams();
            log.info("Schema refresh finished successfully");
            retryScheduled = false;
        }
        catch (Exception e) {
            log.error("Schema refresh failed", e);
            scheduleRetry();
        }
    }

    /**
     * One-shot retry 5 minutes after a failed refresh. A single retry only: repeated attempts would
     * stop/restart the queue monitor repeatedly. updateParams itself takes the cluster refresh lock,
     * so a concurrent trigger (daily cron, another retry) is skipped rather than duplicated.
     */
    private void scheduleRetry()
    {
        if (retryScheduled) {
            return;
        }
        retryScheduled = true;
        taskScheduler.schedule(() -> {
            if (electionService.isMaster()) {
                log.warn("Retrying schema refresh after earlier failure");
                runRefresh();
            }
        }, Instant.now().plusSeconds(300));
    }

    /**
     * If the business switch time has already passed for today but no successful refresh is recorded
     * (master down across the switch, cron never fired, refresh failed without retry), run one
     * immediately — otherwise the whole business date silently stays uncollected until the next day.
     */
    private void maybeCatchUpRefresh()
    {
        try {
            LocalTime switchTime = dictService.getSwitchTimeAsTime();
            if (LocalTime.now().isBefore(switchTime)) {
                log.debug("Catch-up refresh not needed: switch time {} not reached yet", switchTime);
                return;
            }
            // A successful refresh writes the marker under the business date it activated, which is
            // the calendar date of the switch day (config load inside updateParams flips it).
            String doneKey = Constants.SCHEMA_REFRESH_DONE_KEY_PREFIX + configService.getBizDate();
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(doneKey))) {
                log.info("Schema refresh already completed ({}), skipping catch-up", doneKey);
                return;
            }
            log.warn("No successful schema refresh recorded for today ({}), scheduling catch-up", doneKey);
            taskScheduler.schedule(() -> {
                if (electionService.isMaster()) {
                    runRefresh();
                }
            }, Instant.now().plusSeconds(5));
        }
        catch (Exception e) {
            log.warn("Schema refresh catch-up check failed", e);
        }
    }

    @Override
    public void destroy()
    {
        cancelInternal();
    }
}
