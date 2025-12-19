package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExecutionManager: manage running processes on this node and coordinate cross-node kill via Redis pub/sub.
 */
@Component
@Slf4j
@AllArgsConstructor
public class ExecutionManager implements MessageListener {
    // in-memory registry of running jobs on this instance
    private final ConcurrentHashMap<Long, ProcessHolder> running = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RedisMessageListenerContainer listenerContainer;

    private static final String KILL_CHANNEL = "etl:kill";

    public record ProcessHolder(Process process, long pid, String instanceId, Instant startAt) {}

    @PostConstruct
    public void subscribe() {
        try {
            listenerContainer.addMessageListener(this, new ChannelTopic(KILL_CHANNEL));
            log.info("Subscribed to redis kill channel {}");
        } catch (Exception e) {
            log.warn("Failed to subscribe to kill channel", e);
        }
    }

    public void register(long jobId, Process process, long pid, String instanceId) {
        ProcessHolder h = new ProcessHolder(process, pid, instanceId, Instant.now());
        running.put(jobId, h);
        log.info("Registered running job {} pid={} on instance={}", jobId, pid, instanceId);
    }

    public void unregister(long jobId) {
        ProcessHolder removed = running.remove(jobId);
        if (removed != null) {
            log.info("Unregistered running job {} pid={} instance={}", jobId, removed.pid(), removed.instanceId());
        }
    }

    public Optional<ProcessHolder> getLocal(long jobId) {
        return Optional.ofNullable(running.get(jobId));
    }

    /**
     * Kill local process if present. Returns true if kill attempted (process existed), false if not found locally.
     */
    public boolean killLocal(long jobId) {
        ProcessHolder holder = running.get(jobId);
        if (holder == null) return false;
        try {
            Process p = holder.process();
            log.warn("Killing local job {} pid={} requested", jobId, holder.pid());
            p.destroyForcibly();
            return true;
        } catch (Exception e) {
            log.error("Failed to kill local job {}", jobId, e);
            return false;
        }
    }

    @Override
    public void onMessage(@NonNull Message message, byte @Nullable [] pattern) {
        try {
            String body = new String(message.getBody());
            // expected payload: jobId as number or JSON {jobId:123}
            long jobId = -1;
            try {
                if (body.trim().startsWith("{")) {
                    var node = objectMapper.readTree(body);
                    if (node.has("jobId")) jobId = node.get("jobId").asLong(-1);
                } else {
                    jobId = Long.parseLong(body.trim());
                }
            } catch (Exception e) {
                log.warn("Invalid kill message payload: {}", body);
            }
            if (jobId > 0) {
                boolean killed = killLocal(jobId);
                if (killed) {
                    log.info("Handled kill for job {} locally via pubsub", jobId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle kill message", e);
        }
    }
}
