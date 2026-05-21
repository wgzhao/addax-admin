package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.TaskService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

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
    private final TaskService taskService;
    private final MasterElectionService electionService;

    private volatile ScheduledFuture<?> scheduledFuture;

    @PostConstruct
    public void init()
    {
        electionService.onBecameMaster(() -> {
            log.info("Became master — registering schema refresh cron");
            scheduleInternal();
        });
        electionService.onLostMaster(() -> {
            log.info("Lost master — cancelling schema refresh cron");
            cancelInternal();
        });
        // Guard against election tick firing before this @PostConstruct runs
        if (electionService.isMaster()) {
            scheduleInternal();
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
        }
        catch (Exception e) {
            log.error("Schema refresh failed", e);
        }
    }

    @Override
    public void destroy()
    {
        cancelInternal();
    }
}
