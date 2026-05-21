package com.wgzhao.addax.admin.redis;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Master election via Redis NX lock.
 *
 * Every node tries to acquire/renew "addax:master:lock" every RENEW_INTERVAL_SECONDS.
 * The node holding the lock is master. TTL is 30s; if the master crashes, another
 * node wins within 30s (the next RENEW_INTERVAL_SECONDS tick).
 *
 * Callbacks (onBecameMaster / onLostMaster) are invoked synchronously on the
 * election thread — keep them non-blocking.
 */
@Service
@Slf4j
public class MasterElectionService
{
    public static final String MASTER_LOCK_KEY = "addax:master:lock";
    private static final int LOCK_TTL_SECONDS = 30;
    private static final int RENEW_INTERVAL_SECONDS = 10;

    // SET key value XX EX ttl — renew only if we already own the lock (value matches)
    private static final DefaultRedisScript<String> RENEW_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  redis.call('expire', KEYS[1], ARGV[2]); return 'OK' " +
        "else return nil end",
        String.class
    );

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    private volatile boolean master = false;
    private final ScheduledExecutorService electionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "master-election");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> electionFuture;

    private final List<Runnable> onBecameMasterCallbacks = new CopyOnWriteArrayList<>();
    private final List<Runnable> onLostMasterCallbacks = new CopyOnWriteArrayList<>();

    public MasterElectionService(StringRedisTemplate redisTemplate)
    {
        this.redisTemplate = redisTemplate;
        this.instanceId = resolveInstanceId();
    }

    @PostConstruct
    public void start()
    {
        electionFuture = electionScheduler.scheduleWithFixedDelay(
            this::tryElect,
            0, RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS
        );
        log.info("Master election started, instanceId={}", instanceId);
    }

    @PreDestroy
    public void stop()
    {
        if (electionFuture != null) {
            electionFuture.cancel(false);
        }
        electionScheduler.shutdownNow();
        // Release lock if we are master so a new master can be elected faster
        if (master) {
            releaseMasterLock();
        }
    }

    public boolean isMaster()
    {
        return master;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    /** Returns the instanceId of the current master, or null if no master is elected. */
    public String getMasterInstanceId()
    {
        try {
            return redisTemplate.opsForValue().get(MASTER_LOCK_KEY);
        }
        catch (Exception e) {
            log.warn("Failed to read master lock key", e);
            return null;
        }
    }

    /** Register a callback invoked when this node becomes master. */
    public void onBecameMaster(Runnable callback)
    {
        onBecameMasterCallbacks.add(callback);
    }

    /** Register a callback invoked when this node loses the master role. */
    public void onLostMaster(Runnable callback)
    {
        onLostMasterCallbacks.add(callback);
    }

    // ---- internal ----

    private void tryElect()
    {
        try {
            if (master) {
                renewOrLose();
            }
            else {
                tryAcquire();
            }
        }
        catch (Exception e) {
            log.error("Master election tick failed", e);
            if (master) {
                // Assume we lost master on Redis error to prevent split-brain
                transitionToWorker();
            }
        }
    }

    private void tryAcquire()
    {
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(MASTER_LOCK_KEY, instanceId, Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (Boolean.TRUE.equals(acquired)) {
            transitionToMaster();
        }
    }

    private void renewOrLose()
    {
        try {
            String result = redisTemplate.execute(
                RENEW_SCRIPT,
                Collections.singletonList(MASTER_LOCK_KEY),
                instanceId,
                String.valueOf(LOCK_TTL_SECONDS)
            );
            if (!"OK".equals(result)) {
                log.warn("Master lock renewal failed (lock stolen or expired), stepping down");
                transitionToWorker();
            }
        }
        catch (Exception e) {
            log.error("Redis error during master renewal, stepping down", e);
            transitionToWorker();
        }
    }

    private void releaseMasterLock()
    {
        try {
            // Only delete if we still own it
            DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class
            );
            redisTemplate.execute(releaseScript, Collections.singletonList(MASTER_LOCK_KEY), instanceId);
        }
        catch (Exception e) {
            log.warn("Failed to release master lock on shutdown", e);
        }
    }

    private void transitionToMaster()
    {
        master = true;
        log.info("*** This node became MASTER: instanceId={} ***", instanceId);
        for (Runnable cb : onBecameMasterCallbacks) {
            try {
                cb.run();
            }
            catch (Exception e) {
                log.error("onBecameMaster callback failed", e);
            }
        }
    }

    private void transitionToWorker()
    {
        master = false;
        log.info("*** This node stepped down to WORKER: instanceId={} ***", instanceId);
        for (Runnable cb : onLostMasterCallbacks) {
            try {
                cb.run();
            }
            catch (Exception e) {
                log.error("onLostMaster callback failed", e);
            }
        }
    }

    private static String resolveInstanceId()
    {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return host + "-" + pid;
        }
        catch (Exception e) {
            return java.util.UUID.randomUUID().toString();
        }
    }
}
