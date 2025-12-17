package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.event.SourceUpdatedEvent;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.service.LeaderElectionService;
import com.wgzhao.addax.admin.service.TaskSchedulerService;
import com.wgzhao.addax.admin.service.TaskService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Component
@Slf4j
@AllArgsConstructor
public class CollectionScheduler implements LeaderElectionService.LeadershipListener {
    private final TaskSchedulerService taskSchedulerService;
    private final EtlSourceRepo etlSourceRepo;
    private final TaskService taskService;
    private final LeaderElectionService leaderElectionService;

    @PostConstruct
    public void registerAsLeadershipListener() {
        leaderElectionService.addListener(this);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Only leader node should register scheduled tasks
        if (leaderElectionService.isLeader()) {
            log.info("Node {} is leader at startup, scheduling collection tasks", leaderElectionService.getNodeId());
            rescheduleAllTasks();
        } else {
            log.info("Node {} is not leader at startup, will wait for leadership to schedule collection tasks", leaderElectionService.getNodeId());
        }
    }

    @Override
    public void onBecameLeader() {
        log.info("Node {} became leader, rescheduling collection tasks", leaderElectionService.getNodeId());
        taskSchedulerService.cancelAll();
        rescheduleAllTasks();
    }

    @Override
    public void onLostLeader() {
        log.info("Node {} lost leadership, cancelling all collection tasks", leaderElectionService.getNodeId());
        taskSchedulerService.cancelAll();
    }

    public void rescheduleAllTasks() {
        if (!leaderElectionService.isLeader()) {
            log.info("Current node {} is not leader, skip rescheduleAllTasks", leaderElectionService.getNodeId());
            return;
        }
        try {
            // Schedule collection tasks for each source
            List<EtlSource> sources = etlSourceRepo.findByEnabled(true);
            for (EtlSource source : sources) {
                scheduleOrUpdateTask(source);
            }
        } catch (Exception e) {
            // 记录详细异常，便于排查
            log.error("Error in rescheduleAllTasks: ", e);
        }
    }

    public void scheduleOrUpdateTask(EtlSource source) {
        if (!leaderElectionService.isLeader()) {
            log.info("Current node {} is not leader, skip scheduling task for source {}", leaderElectionService.getNodeId(), source.getCode());
            return;
        }
        String taskId = "source-" + source.getCode();
        if (source.isEnabled() && source.getStartAt() != null) {
            log.info("Scheduling task for source {} at {}", source.getCode(), source.getStartAt());
            String cronExpression = convertLocalTimeToCron(source.getStartAt());
            // cancel existing task if any
            taskSchedulerService.cancelTask(taskId);
            Runnable task = () -> taskService.executeTasksForSource(source.getId());
            taskSchedulerService.scheduleTask(taskId, task, cronExpression);
        } else {
            taskSchedulerService.cancelTask(taskId);
        }
    }

    public void cancelTask(String code) {
        String taskId = "source-" + code;
        taskSchedulerService.cancelTask(taskId);
    }

    private String convertLocalTimeToCron(LocalTime time) {
        return String.format("0 %d %d * * ?", time.getMinute(), time.getHour());
    }

    @EventListener
    public void handleSourceEvent(SourceUpdatedEvent event) {
        if (event.isScheduleChanged()) {
            etlSourceRepo.findById(event.getSourceId()).ifPresent(this::scheduleOrUpdateTask);
        }
    }
}
