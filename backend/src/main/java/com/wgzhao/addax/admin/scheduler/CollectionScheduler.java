package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.redis.RedisLockService;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.service.TaskSchedulerService;
import com.wgzhao.addax.admin.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
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
    private final RedisLockService redisLockService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady()
    {
        rescheduleAllTasks();
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
            Runnable task = () -> {
                final String lockKey = "collection:source:" + source.getCode() + ":lock";
                final Duration ttl = Duration.ofSeconds(300);
                String token = null;
                try {
                    token = redisLockService.tryLock(lockKey, ttl);
                    if (token == null) {
                        log.info("Could not acquire lock for source {}, skipping this run", source.getCode());
                        return;
                    }
                    // keep existing behavior: enqueue runnable tables for this source
                    taskService.executeTasksForSource(source.getId());
                }
                catch (Exception e) {
                    log.error("Error executing scheduled collection for source {}", source.getCode(), e);
                }
                finally {
                    if (token != null) {
                        redisLockService.release(lockKey, token);
                    }
                }
            };
            taskSchedulerService.scheduleTask(taskId, task, cronExpression);
        }
        else {
            taskSchedulerService.cancelTask(taskId);
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
