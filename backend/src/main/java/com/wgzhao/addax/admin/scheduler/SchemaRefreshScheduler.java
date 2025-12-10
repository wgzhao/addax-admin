package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.service.SystemFlagService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.LeaderElectionService;
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
public class SchemaRefreshScheduler implements DisposableBean, LeaderElectionService.LeadershipListener {
    private final TaskScheduler taskScheduler;
    private final DictService dictService;
    private final SystemFlagService systemFlagService;
    private final TableService tableService;
    private final LeaderElectionService leaderElectionService;

    private volatile ScheduledFuture<?> scheduledFuture;

    @PostConstruct
    public void init() {
        // 注册为 leader 变更监听器
        leaderElectionService.addListener(this);

        // 如果当前节点在启动时已经是 leader，则立即注册任务
        if (leaderElectionService.isLeader()) {
            log.info("Node {} is leader at startup, scheduling schema refresh", leaderElectionService.getNodeId());
            scheduleInternal();
        } else {
            log.info("Node {} is not leader at startup, skip scheduling schema refresh", leaderElectionService.getNodeId());
        }
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
        // 防御性检查：只有 leader 执行刷新逻辑
        if (!leaderElectionService.isLeader()) {
            log.info("Current node {} is not leader, skip schema refresh run", leaderElectionService.getNodeId());
            return;
        }

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
        cancelInternal();
        leaderElectionService.removeListener(this);
    }

    @Override
    public void onBecameLeader() {
        // 当本节点成为 leader 时，注册 schema refresh 任务
        log.info("Node {} became leader, scheduling schema refresh", leaderElectionService.getNodeId());
        scheduleInternal();
    }

    @Override
    public void onLostLeader() {
        // 当本节点失去 leader 身份时，取消本地 schema refresh 任务
        log.info("Node {} lost leadership, cancelling schema refresh schedule", leaderElectionService.getNodeId());
        cancelInternal();
    }
}
