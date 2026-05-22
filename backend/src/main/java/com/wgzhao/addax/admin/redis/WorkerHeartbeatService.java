package com.wgzhao.addax.admin.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker heartbeat service.
 *
 * Each node (including the master) publishes a heartbeat to Redis every 15 seconds:
 *   SET addax:worker:{instanceId} {json} EX 45
 *
 * The master reads all alive workers via SCAN "addax:worker:*" when dispatching.
 *
 * Workers self-report:
 *   - availableSlots  = concurrentLimit - currentRunning
 *   - sourceRunning   = Map<sid, runningCount> for per-source concurrency tracking
 *   - weight          = node concurrency weight [0.0, 1.0]
 */
@Service
@Slf4j
public class WorkerHeartbeatService
{
    public static final String WORKER_KEY_PREFIX = "addax:worker:";
    private static final int HEARTBEAT_TTL_SECONDS = 45;
    private static final int STALE_THRESHOLD_SECONDS = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MasterElectionService electionService;

    // References to runtime counters — set by TaskQueueManagerV2Impl after init
    private volatile int concurrentLimit = 1;
    private volatile double weight = 1.0;
    private volatile AtomicInteger runningTaskCount;
    private volatile ConcurrentHashMap<Integer, AtomicInteger> sourceRunningTaskCount;

    public WorkerHeartbeatService(StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper,
                                  MasterElectionService electionService)
    {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.electionService = electionService;
    }

    /**
     * Called by TaskQueueManagerV2Impl after its own @PostConstruct to inject live counters.
     */
    public void bind(int concurrentLimit,
                     double weight,
                     AtomicInteger runningTaskCount,
                     ConcurrentHashMap<Integer, AtomicInteger> sourceRunningTaskCount)
    {
        this.concurrentLimit = concurrentLimit;
        this.weight = weight;
        this.runningTaskCount = runningTaskCount;
        this.sourceRunningTaskCount = sourceRunningTaskCount;
    }

    /** Publish current heartbeat to Redis. Called every 15s by TaskQueueManagerV2Impl scheduler. */
    public void publishHeartbeat()
    {
        try {
            WorkerInfo info = buildInfo();
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(
                WORKER_KEY_PREFIX + electionService.getInstanceId(),
                json,
                Duration.ofSeconds(HEARTBEAT_TTL_SECONDS)
            );
            log.debug("Heartbeat published: instanceId={} slots={} running={}",
                electionService.getInstanceId(), info.availableSlots(), info.running());
        }
        catch (Exception e) {
            log.warn("Failed to publish worker heartbeat", e);
        }
    }

    /** Remove own heartbeat from Redis (on shutdown). */
    public void removeHeartbeat()
    {
        try {
            redisTemplate.delete(WORKER_KEY_PREFIX + electionService.getInstanceId());
        }
        catch (IllegalStateException e) {
            // During Spring shutdown, Redis connection factory may be stopped before this bean's
            // @PreDestroy is called. Redis keys with TTL will auto-expire, so it's safe to skip.
            log.debug("Redis connection factory stopped during shutdown, skipping heartbeat removal", e);
        }
        catch (Exception e) {
            log.warn("Failed to remove own heartbeat key", e);
        }
    }

    /**
     * Scan all alive worker heartbeats. Called by master during dispatch.
     * Uses SCAN instead of KEYS to avoid blocking Redis on large keyspaces.
     * Workers whose heartbeat is older than STALE_THRESHOLD_SECONDS are excluded.
     */
    public List<WorkerInfo> getAliveWorkers()
    {
        List<WorkerInfo> result = new ArrayList<>();
        Instant cutoff = Instant.now().minusSeconds(STALE_THRESHOLD_SECONDS);
        ScanOptions options = ScanOptions.scanOptions().match(WORKER_KEY_PREFIX + "*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    String json = redisTemplate.opsForValue().get(key);
                    if (json == null) continue;
                    WorkerInfo info = objectMapper.readValue(json, WorkerInfo.class);
                    if (info.lastSeen() != null && info.lastSeen().isAfter(cutoff)) {
                        result.add(info);
                    }
                }
                catch (Exception e) {
                    log.debug("Failed to parse worker heartbeat for key={}", key);
                }
            }
        }
        catch (Exception e) {
            log.warn("Failed to scan worker heartbeats", e);
        }
        return result;
    }

    private WorkerInfo buildInfo()
    {
        int running = runningTaskCount == null ? 0 : runningTaskCount.get();
        int slots = Math.max(0, concurrentLimit - running);

        ConcurrentHashMap<Integer, AtomicInteger> srcMap = sourceRunningTaskCount;
        Map<Integer, Integer> sourceRunning = new java.util.HashMap<>();
        if (srcMap != null) {
            srcMap.forEach((sid, counter) -> {
                int cnt = counter.get();
                if (cnt > 0) sourceRunning.put(sid, cnt);
            });
        }

        return new WorkerInfo(
            electionService.getInstanceId(),
            resolveHost(),
            slots,
            running,
            concurrentLimit,
            weight,
            sourceRunning,
            Instant.now()
        );
    }

    private static String resolveHost()
    {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Immutable snapshot of a worker's state at heartbeat time.
     */
    public record WorkerInfo(
        String instanceId,
        String host,
        int availableSlots,
        int running,
        int concurrentLimit,
        double weight,
        Map<Integer, Integer> sourceRunning,
        Instant lastSeen
    ) {}
}
