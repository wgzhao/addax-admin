package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.service.TaskService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class TaskConsumer implements DisposableBean {

    private final StringRedisTemplate redisTemplate;
    private final TaskService taskService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;


    public TaskConsumer(StringRedisTemplate redisTemplate, TaskService taskService) {
        this.redisTemplate = redisTemplate;
        this.taskService = taskService;
    }

    @PostConstruct
    public void start() {
        executor.submit(this::consumeTask);
    }

    public void consumeTask() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                String sourceId = redisTemplate.opsForList().rightPop("addax-task-queue", 5, TimeUnit.SECONDS);
                if (sourceId != null) {
                    log.info("Worker node picked up task for source ID: {}", sourceId);
                    try {
                        taskService.executeTasksForSource(Integer.parseInt(sourceId));
                    } catch (Exception e) {
                        log.error("Error executing task for source ID: {}", sourceId, e);
                        // Optionally, push the task back to the queue for retry
                        // redisTemplate.opsForList().leftPush("addax-task-queue", sourceId);
                    }
                }
            } catch (Exception e) {
                if (running) { // Only log errors if we are supposed to be running
                    log.error("Error in Redis task consumer loop", e);
                }
            }
        }
        log.info("Task consumer loop finished.");
    }

    @Override
    public void destroy() throws Exception {
        log.info("Shutting down task consumer...");
        running = false;
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            log.warn("Task consumer did not terminate in 10 seconds. Forcing shutdown.");
            executor.shutdownNow();
        }
        log.info("Task consumer shut down successfully.");
    }
}

