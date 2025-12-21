package com.wgzhao.addax.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulingConfig
    implements SchedulingConfigurer
{

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar)
    {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4); // 至少 2 以上，给 heartbeat 留一个
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}
