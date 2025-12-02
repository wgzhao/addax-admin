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
public class SchemaRefreshScheduler implements DisposableBean {
    private final TaskScheduler taskScheduler;
    private final DictService dictService;
    private final SystemFlagService systemFlagService;
    private final TableService tableService;

    private ScheduledFuture<?> scheduledFuture;

    @PostConstruct
    public void schedule() {
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        LocalTime trigger = switchTime.plusMinutes(10);
        String cron = toCron(trigger);
        log.info("Scheduling schema refresh at {} (cron: {})", trigger, cron);
        scheduledFuture = taskScheduler.schedule(this::runRefresh, new CronTrigger(cron));
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

    @Override
    public void destroy() throws Exception {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }
}
