package com.wgzhao.addax.admin.config;

import com.wgzhao.addax.admin.service.TaskQueueManager;
import com.wgzhao.addax.admin.service.impl.TaskQueueManagerV2Impl;
import com.wgzhao.addax.admin.service.impl.TaskQueueManagerRedisImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TaskQueueConfig {

    @Value("${queue.impl}")
    private String queueImplementation;

    @Bean
    @Primary
    public TaskQueueManager taskQueueManager(ApplicationContext context) {
        if ("redis".equalsIgnoreCase(queueImplementation)) {
            return context.getBean(TaskQueueManagerRedisImpl.class);
        } else {
            return context.getBean(TaskQueueManagerV2Impl.class);
        }
    }
}

