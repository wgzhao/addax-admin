package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.scheduler.CollectionScheduler;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Subscriber for source update notifications published to Redis. When a source's schedule is updated on one
 * node, it publishes the source id to `etl:source:updated`. All nodes subscribe and will call
 * CollectionScheduler.scheduleOrUpdateTask for the affected source so each node can re-register local timers.
 */
@Component
@AllArgsConstructor
@Slf4j
public class SourceUpdateSubscriber implements MessageListener
{
    private static final String REDIS_SOURCE_UPDATE_CHANNEL = "etl:source:updated";

    private final RedisMessageListenerContainer listenerContainer;
    private final EtlSourceRepo etlSourceRepo;
    private final CollectionScheduler collectionScheduler;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribe()
    {
        try {
            listenerContainer.addMessageListener(this, new ChannelTopic(REDIS_SOURCE_UPDATE_CHANNEL));
            log.info("Subscribed to redis source update channel {}", REDIS_SOURCE_UPDATE_CHANNEL);
        }
        catch (Exception e) {
            log.warn("Failed to subscribe to source update channel", e);
        }
    }

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern)
    {
        try {
            String body = new String(message.getBody());
            long sourceId = -1;
            try {
                sourceId = Long.parseLong(body.trim());
            }
            catch (Exception ignore) {
                // try to parse JSON maybe
                try {
                    var node = objectMapper.readTree(body);
                    if (node.has("sourceId")) {
                        sourceId = node.get("sourceId").asLong(-1);
                    }
                    else if (node.has("id")) {
                        sourceId = node.get("id").asLong(-1);
                    }
                }
                catch (Exception ex) {
                    log.warn("Invalid source update payload: {}", body);
                }
            }

            if (sourceId > 0) {
                Optional<EtlSource> sOpt = etlSourceRepo.findById((int) sourceId);
                if (sOpt.isPresent()) {
                    EtlSource source = sOpt.get();
                    log.info("Received source update for id={}, scheduling/rescheduling locally", sourceId);
                    collectionScheduler.scheduleOrUpdateTask(source);
                }
                else {
                    log.warn("Received source update for id={} but source not found in repo", sourceId);
                }
            }
        }
        catch (Exception e) {
            log.error("Failed to handle source update message", e);
        }
    }
}
