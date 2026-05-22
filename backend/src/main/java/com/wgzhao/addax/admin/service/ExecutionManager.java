package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;


/**
 * ExecutionManager: manage running processes on this node and coordinate cross-node kill via Redis pub/sub.
 */
@Component
@Slf4j
@AllArgsConstructor
public class ExecutionManager
    implements MessageListener
{
    private static final String KILL_CHANNEL = "etl:kill";
    // in-memory registry of collecting table(s) on this instance
    private final ConcurrentHashMap<Long, ProcessHolder> running = new ConcurrentHashMap<>();
    // Tracks tasks explicitly killed by user so worker can avoid rescheduling them as normal failures.
    private final ConcurrentHashMap<Long, Instant> killRequested = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisMessageListenerContainer listenerContainer;

    @PostConstruct
    public void subscribe()
    {
        try {
            listenerContainer.addMessageListener(this, new ChannelTopic(KILL_CHANNEL));
            log.info("Subscribed to redis kill channel {}", KILL_CHANNEL);
        }
        catch (Exception e) {
            log.warn("Failed to subscribe to kill channel", e);
        }
    }

    public void register(long tid, Process process, long pid, String instanceId)
    {
        ProcessHolder h = new ProcessHolder(process, pid, instanceId, Instant.now());
        running.put(tid, h);
        log.info("Registered collecting table {} pid={} on instance={}", tid, pid, instanceId);
    }

    public void unregister(long tid)
    {
        ProcessHolder removed = running.remove(tid);
        if (removed != null) {
            log.info("Unregistered collecting table {} pid={} instance={}", tid, removed.pid(), removed.instanceId());
        }
    }

    /**
     * Lookup current instance id for a running table on this node.
     */
    public Optional<String> findInstanceId(long tid)
    {
        ProcessHolder holder = running.get(tid);
        if (holder == null || holder.instanceId() == null || holder.instanceId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(holder.instanceId());
    }

    /**
     * Kill local process if present. Returns true if kill attempted (process existed), false if not found locally.
     */
    public boolean killLocal(long tid)
    {
        ProcessHolder holder = running.get(tid);
        if (holder == null) {
            return false;
        }
        try {
            killRequested.put(tid, Instant.now());
            Process p = holder.process();
            log.warn("Killing local collecting table {} , pid={} requested", tid, holder.pid());

            ProcessHandle root = p.toHandle();
            List<ProcessHandle> descendants = new ArrayList<>(root.descendants().toList());
            descendants.sort(Comparator.comparingLong(this::depth).reversed());

            int gracefulStopped = 0;
            for (ProcessHandle handle : descendants) {
                if (tryStopHandle(handle, false)) {
                    gracefulStopped++;
                }
            }
            if (tryStopHandle(root, false)) {
                gracefulStopped++;
            }

            int forcedStopped = 0;
            for (ProcessHandle handle : descendants) {
                if (tryStopHandle(handle, true)) {
                    forcedStopped++;
                }
            }
            if (tryStopHandle(root, true)) {
                forcedStopped++;
            }

            String marker = "-DjobName=" + tid;
            int markerStopped = 0;
            List<ProcessHandle> all = ProcessHandle.allProcesses().toList();
            for (ProcessHandle handle : all) {
                if (!handle.isAlive()) {
                    continue;
                }
                if (handle.pid() == root.pid()) {
                    continue;
                }
                String cmdLine = handle.info().commandLine().orElse("");
                if (cmdLine.contains(marker) && tryStopHandle(handle, true)) {
                    markerStopped++;
                }
            }

            log.warn("Kill requested tid={} rootPid={} descendants={} gracefulStopped={} forcedStopped={} markerStopped={}",
                tid, root.pid(), descendants.size(), gracefulStopped, forcedStopped, markerStopped);
            return true;
        }
        catch (Exception e) {
            log.error("Failed to kill local collecting table {} ", tid, e);
            return false;
        }
    }

    /**
     * Returns true once when a kill request has been recorded for this tid.
     */
    public boolean consumeKillRequested(long tid)
    {
        return killRequested.remove(tid) != null;
    }

    private long depth(ProcessHandle handle)
    {
        long d = 0;
        Optional<ProcessHandle> p = handle.parent();
        while (p.isPresent()) {
            d++;
            p = p.get().parent();
        }
        return d;
    }

    private boolean tryStopHandle(ProcessHandle handle, boolean force)
    {
        if (handle == null || !handle.isAlive()) {
            return false;
        }
        try {
            if (force) {
                handle.destroyForcibly();
            }
            else {
                handle.destroy();
            }
            waitExit(handle, 1200);
            return true;
        }
        catch (Exception e) {
            log.debug("Failed to stop pid={} force={}", handle.pid(), force, e);
            return false;
        }
    }

    private void waitExit(ProcessHandle handle, long millis)
    {
        try {
            handle.onExit().get(millis, TimeUnit.MILLISECONDS);
        }
        catch (Exception ignored) {
        }
    }

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern)
    {
        try {
            String body = new String(message.getBody());
            // expected payload: jobId as number or JSON {jobId:123}
            long jobId = -1;
            try {
                if (body.trim().startsWith("{")) {
                    var node = objectMapper.readTree(body);
                    if (node.has("jobId")) {
                        jobId = node.get("jobId").asLong(-1);
                    }
                }
                else {
                    jobId = Long.parseLong(body.trim());
                }
            }
            catch (Exception e) {
                log.warn("Invalid kill message payload: {}", body);
            }
            if (jobId > 0) {
                boolean killed = killLocal(jobId);
                if (killed) {
                    log.info("Handled kill for job {} locally via pubsub", jobId);
                }
            }
        }
        catch (Exception e) {
            log.error("Failed to handle kill message", e);
        }
    }

    public record ProcessHolder(Process process, long pid, String instanceId, Instant startAt) {}
}
