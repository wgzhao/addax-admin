package com.wgzhao.addax.admin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = "queue.impl", havingValue = "redis")
@Import(RedisAutoConfiguration.class)
public class RedisConfig {
}
