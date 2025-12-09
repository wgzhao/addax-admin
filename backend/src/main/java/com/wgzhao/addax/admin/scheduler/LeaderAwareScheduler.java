package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.service.CollectionSchedulingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LeaderAwareScheduler {

    private final CollectionSchedulingService collectionSchedulingService;
    private final SchemaRefreshScheduler schemaRefreshScheduler;

    public LeaderAwareScheduler(CollectionSchedulingService collectionSchedulingService, SchemaRefreshScheduler schemaRefreshScheduler) {
        this.collectionSchedulingService = collectionSchedulingService;
        this.schemaRefreshScheduler = schemaRefreshScheduler;
    }

    @EventListener(OnGrantedEvent.class)
    public void onGranted() {
        log.info("Leadership granted. Scheduling all collection tasks.");
        collectionSchedulingService.rescheduleAllTasks();
        schemaRefreshScheduler.schedule();
    }

    @EventListener(OnRevokedEvent.class)
    public void onRevoked() {
        log.info("Leadership revoked. Cancelling all collection tasks.");
        collectionSchedulingService.cancelAllTasks();
        schemaRefreshScheduler.cancel();
    }
}

