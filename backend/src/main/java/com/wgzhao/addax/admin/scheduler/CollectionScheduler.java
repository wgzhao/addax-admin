package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.service.TaskSchedulerService;
import com.wgzhao.addax.admin.service.TaskService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Component
@Slf4j
@AllArgsConstructor
public class CollectionScheduler
{
    private final TaskSchedulerService taskSchedulerService;
    private final EtlSourceRepo etlSourceRepo;
    private final TaskService taskService;
    private final MasterElectionService electionService;

    @PostConstruct
    public void init()
    {
        electionService.onBecameMaster(() -> {
            log.info("Became master — registering all source cron tasks");
            rescheduleAllTasks();
        });
        electionService.onLostMaster(() -> {
            log.info("Lost master — cancelling all source cron tasks");
            cancelAllScheduledTasks();
        });
        // Guard against the election tick firing before this callback was registered
        if (electionService.isMaster()) {
            rescheduleAllTasks();
        }
    }

    public void rescheduleAllTasks()
    {
        try {
            List<EtlSource> sources = etlSourceRepo.findByEnabled(true);
            for (EtlSource source : sources) {
                scheduleOrUpdateTask(source);
            }
        }
        catch (Exception e) {
            log.error("Error in rescheduleAllTasks", e);
        }
    }

    public void scheduleOrUpdateTask(EtlSource source)
    {
        String taskId = "source-" + source.getCode();
        if (source.isEnabled() && source.getStartAt() != null) {
            log.info("Scheduling task for source {} at {}", source.getCode(), source.getStartAt());
            String cronExpression = convertLocalTimeToCron(source.getStartAt());
            taskSchedulerService.cancelTask(taskId);
            // Enqueue is idempotent: DB unique constraint prevents duplicate pending/running entries
            Runnable task = () -> {
                try {
                    taskService.executeTasksForSource(source.getId());
                }
                catch (Exception e) {
                    log.error("Error executing scheduled collection for source {}", source.getCode(), e);
                }
            };
            taskSchedulerService.scheduleTask(taskId, task, cronExpression);
        }
        else {
            taskSchedulerService.cancelTask(taskId);
        }
    }

    public void cancelAllScheduledTasks()
    {
        try {
            taskSchedulerService.cancelAll();
        }
        catch (Exception e) {
            log.error("Error cancelling all scheduled tasks", e);
        }
    }

    public void cancelTask(String code)
    {
        String taskId = "source-" + code;
        taskSchedulerService.cancelTask(taskId);
    }

    private String convertLocalTimeToCron(LocalTime time)
    {
        return String.format("0 %d %d * * ?", time.getMinute(), time.getHour());
    }
}
