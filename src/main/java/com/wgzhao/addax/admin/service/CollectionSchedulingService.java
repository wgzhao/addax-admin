package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Service
public class CollectionSchedulingService {

    @Autowired
    private TaskSchedulerService taskSchedulerService;

    @Autowired
    private EtlSourceRepo etlSourceRepo;

    @Autowired
    private TaskService taskService;

    @Autowired
    private SystemConfigService systemConfigService;

    @PostConstruct
    public void initializeSchedules() {
        rescheduleAllTasks();
    }

    public void rescheduleAllTasks() {
        // Schedule collection tasks for each source
        List<EtlSource> sources = etlSourceRepo.findByEnabled(true);
        for (EtlSource source : sources) {
            scheduleOrUpdateTask(source);
        }
        // Schedule the daily parameter update task
        scheduleDailyParamUpdate();
    }

    private void scheduleDailyParamUpdate() {
        LocalTime time = systemConfigService.getSwitchTimeAsTime();
        String cronExpression = convertLocalTimeToCron(time);
        Runnable task = taskService::updateParams;
        taskSchedulerService.scheduleTask("dailyParamUpdate", task, cronExpression);
    }

    public void scheduleOrUpdateTask(EtlSource source) {
        String taskId = "source-" + source.getId();
        if (source.isEnabled() && source.getStartAt() != null) {
            String cronExpression = convertLocalTimeToCron(source.getStartAt());
            Runnable task = () -> taskService.executeTasksForSource(source.getId());
            taskSchedulerService.scheduleTask(taskId, task, cronExpression);
        } else {
            taskSchedulerService.cancelTask(taskId);
        }
    }

    public void cancelTask(int sourceId) {
        String taskId = "source-" + sourceId;
        taskSchedulerService.cancelTask(taskId);
    }

    private String convertLocalTimeToCron(LocalTime time) {
        return String.format("0 %d %d * * ?", time.getMinute(), time.getHour());
    }
}
