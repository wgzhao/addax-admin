package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Service
@Slf4j
public class CollectionSchedulingService {

    @Autowired
    private TaskSchedulerService taskSchedulerService;

    @Autowired
    private EtlSourceRepo etlSourceRepo;

    @Autowired
    private TaskService taskService;

    @Autowired
    private SystemConfigService systemConfigService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        rescheduleAllTasks();
    }

    public void rescheduleAllTasks() {
        try {
            // Schedule collection tasks for each source
            List<EtlSource> sources = etlSourceRepo.findByEnabled(true);
            for (EtlSource source : sources) {
                scheduleOrUpdateTask(source);
            }
            // Schedule the daily parameter update task
            scheduleDailyParamUpdate();
        } catch (Exception e) {
            // 记录详细异常，便于排查
           log.error("Error in rescheduleAllTasks: " , e);
            e.printStackTrace();
        }
    }

    private void scheduleDailyParamUpdate() {
        LocalTime time = systemConfigService.getSwitchTimeAsTime();
        String cronExpression = convertLocalTimeToCron(time);
        Runnable task = taskService::updateParams;
        taskSchedulerService.scheduleTask("dailyParamUpdate", task, cronExpression);
    }

    public void scheduleOrUpdateTask(EtlSource source) {
        String taskId = "source-" + source.getCode();
        if (source.isEnabled() && source.getStartAt() != null) {
            String cronExpression = convertLocalTimeToCron(source.getStartAt());
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
}
