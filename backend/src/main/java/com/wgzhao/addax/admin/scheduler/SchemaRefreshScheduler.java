package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.service.SystemFlagService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.DictService;
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
 * 每天在切日时间后 10 分钟触发一次，进行表结构刷新。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SchemaRefreshScheduler {
    private final TaskScheduler taskScheduler;
    private final DictService dictService;
    private final SystemFlagService systemFlagService;
    private final TableService tableService;

    private ScheduledFuture<?> scheduledFuture;

    public void schedule() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            log.info("Schema refresh task is already scheduled.");
            return;
        }
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        String cron = toCron(switchTime);
        log.info("Scheduling schema refresh at {} (cron: {})", switchTime, cron);
        scheduledFuture = taskScheduler.schedule(this::runRefresh, new CronTrigger(cron));
    }

    public void cancel() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            log.info("Cancelled scheduled schema refresh task.");
        }
    }

    private String toCron(LocalTime time) {
        // cron format: second minute hour day-of-month month day-of-week
        return String.format("0 %d %d * * *", time.getMinute(), time.getHour());
    }

    public void runRefresh() {
        log.info("Schema refresh triggered");
        boolean acquired = systemFlagService.beginRefresh("scheduler");
        if (!acquired) {
            log.info("Another instance is refreshing schema, skip this run");
            return;
        }
        try {
            tableService.refreshAllTableResources();
            log.info("Schema refresh finished successfully");
        }
        catch (Exception e) {
            log.error("Schema refresh failed", e);
        }
        finally {
            systemFlagService.endRefresh("scheduler");
        }
    }
}
