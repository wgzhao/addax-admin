package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.event.SourceUpdatedEvent;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class SourceEventListener implements ApplicationListener<SourceUpdatedEvent> {

    private final LockRegistryLeaderInitiator leaderInitiator;
    private final CollectionSchedulingService collectionSchedulingService;
    private final EtlSourceRepo etlSourceRepo;

    public SourceEventListener(LockRegistryLeaderInitiator leaderInitiator,
                               CollectionSchedulingService collectionSchedulingService,
                               EtlSourceRepo etlSourceRepo) {
        this.leaderInitiator = leaderInitiator;
        this.collectionSchedulingService = collectionSchedulingService;
        this.etlSourceRepo = etlSourceRepo;
    }

    @Override
    public void onApplicationEvent(SourceUpdatedEvent event) {
        if (Objects.requireNonNull(leaderInitiator.getContext()).isLeader()) {
            log.info("Leader node received SourceUpdatedEvent for source ID: {}", event.getSourceId());
            EtlSource etlSource = etlSourceRepo.findById(event.getSourceId()).orElse(null);
            if (etlSource == null) {
                // source deleted
                log.warn("Source with id {} not found, it may be deleted, cancel the schedule", event.getSourceId());
                collectionSchedulingService.cancelTask(String.valueOf(event.getSourceId()));
                return;
            }
            if (event.isConnectionChanged()) {
                // update all tasks under this source
                log.info("Connection changed for source {}, updating tasks", etlSource.getName());
            }
            if (event.isScheduleChanged()) {
                log.info("Schedule changed for source {}, rescheduling", etlSource.getName());
                collectionSchedulingService.scheduleOrUpdateTask(etlSource);
            }
        } else {
            log.debug("Non-leader node received SourceUpdatedEvent, ignoring.");
        }
    }
}

