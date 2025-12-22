package com.wgzhao.addax.admin.service;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class RedisConfigSubscriber
    implements MessageListener
{
    private static final String REDIS_CONFIG_CHANNEL = "system:config:reload";
    private final RedisMessageListenerContainer listenerContainer;

    @PostConstruct
    public void subscribe()
    {
        listenerContainer.addMessageListener(this, new ChannelTopic(REDIS_CONFIG_CHANNEL));
        log.info("Subscribed to redis config reload channel");
    }

    @Override
    public void onMessage(Message message, byte[] pattern)
    {
        try {
            String body = new String(message.getBody());
            log.info("Received config reload notification from redis: {}", body);
        }
        catch (Exception e) {
            log.error("Failed to handle config reload message", e);
        }
    }
}
