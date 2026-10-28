package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.utils.ProcessGuard;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    // In-memory registry of collecting tables on this instance. Keyed by tid, then execution id:
    // a normal run and a fillback of the same table can overlap, and a single tid -> holder map
    // silently dropped whichever registered first, so a kill could miss a live process.
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, ProcessHolder>> running = new ConcurrentHashMap<>();
    // Tracks tasks explicitly killed by user so worker can avoid rescheduling them as normal failures.
    private final ConcurrentHashMap<Long, Instant> killRequested = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisMessageListenerContainer listenerContainer;
    // Kill requests are executed off the shared RedisMessageListenerContainer dispatch thread:
    // killing a process tree can take tens of seconds (up to 1.2s per descendant, twice), which
    // would otherwise stall delivery of task-assignment messages on the same container.
    private final ExecutorService killExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "etl-killer");
        t.setDaemon(true);
        return t;
    });

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

    public void register(long tid, long executionId, Process process, long pid, String instanceId)
    {
        ProcessHolder h = new ProcessHolder(process, pid, instanceId, Instant.now());
        running.computeIfAbsent(tid, k -> new ConcurrentHashMap<>()).put(executionId, h);
        log.info("Registered collecting table {} execution={} pid={} on instance={}", tid, executionId, pid, instanceId);
    }

    public void unregister(long tid, long executionId)
    {
        ConcurrentHashMap<Long, ProcessHolder> holders = running.get(tid);
        if (holders == null) {
            return;
        }
        ProcessHolder removed = holders.remove(executionId);
        if (removed != null) {
            log.info("Unregistered collecting table {} execution={} pid={} instance={}",
                tid, executionId, removed.pid(), removed.instanceId());
        }
        if (holders.isEmpty()) {
            running.remove(tid, holders);
        }
    }

    /**
     * Lookup current instance id for a running table on this node.
     */
    public Optional<String> findInstanceId(long tid)
    {
        ConcurrentHashMap<Long, ProcessHolder> holders = running.get(tid);
        if (holders == null) {
            return Optional.empty();
        }
        return holders.values().stream()
            .map(ProcessHolder::instanceId)
            .filter(id -> id != null && !id.isBlank())
            .findFirst();
    }

    /**
     * Kill the registered process tree of a task, if present. Returns true if kill attempted
     * (process existed), false if not found locally.
     *
     * Scope note: every run registered under the tid is killed. A normal run and a fillback for the
     * same table can overlap, so the registry keeps one holder per execution id. The previous
     * machine-wide scan over "-DjobName={tid}" command lines was removed — it force-killed legitimate
     * concurrent copies of the same table and could stall the caller for 1.2s per unrelated process.
     */
    public boolean killLocal(long tid)
    {
        ConcurrentHashMap<Long, ProcessHolder> holders = running.get(tid);
        if (holders == null || holders.isEmpty()) {
            return false;
        }
        killRequested.put(tid, Instant.now());
        int killed = 0;
        for (Map.Entry<Long, ProcessHolder> entry : List.copyOf(holders.entrySet())) {
            ProcessHolder holder = entry.getValue();
            log.warn("Killing local collecting table {} execution={} pid={} requested",
                tid, entry.getKey(), holder.pid());
            try {
                ProcessGuard.destroyTree(holder.process());
                holders.remove(entry.getKey(), holder);
                killed++;
            }
            catch (Exception e) {
                log.error("Failed to kill local collecting table {} execution={}", tid, entry.getKey(), e);
            }
        }
        if (holders.isEmpty()) {
            running.remove(tid, holders);
        }
        return killed > 0;
    }

    /**
     * Returns true once when a kill request has been recorded for this tid.
     *
     * @param notBefore kill requests recorded before this instant are stale — they belong to a
     *     previous execution of the same tid (the executor failed to consume them) and must not
     *     misclassify the current run's genuine failure as a user cancel. Pass the queue row's
     *     claimedAt of the current run.
     */
    public boolean consumeKillRequested(long tid, Instant notBefore)
    {
        Instant killedAt = killRequested.get(tid);
        if (killedAt == null) {
            return false;
        }
        if (killedAt.isBefore(notBefore)) {
            killRequested.remove(tid, killedAt);
            return false;
        }
        killRequested.remove(tid);
        return true;
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
                final long targetId = jobId;
                killExecutor.submit(() -> {
                    boolean killed = killLocal(targetId);
                    if (killed) {
                        log.info("Handled kill for job {} locally via pubsub", targetId);
                    }
                });
            }
        }
        catch (Exception e) {
            log.error("Failed to handle kill message", e);
        }
    }

    @PreDestroy
    public void shutdown()
    {
        // A graceful stop must not leave addax processes behind: a restarted admin would otherwise
        // re-dispatch the same queue row while the old process is still writing to the target.
        for (ConcurrentHashMap<Long, ProcessHolder> holders : running.values()) {
            for (ProcessHolder holder : holders.values()) {
                try {
                    ProcessGuard.destroyTree(holder.process());
                }
                catch (Exception e) {
                    log.warn("Failed to terminate pid={} on shutdown", holder.pid(), e);
                }
            }
        }
        running.clear();
        killExecutor.shutdownNow();
    }

    public record ProcessHolder(Process process, long pid, String instanceId, Instant startAt) {}
}
