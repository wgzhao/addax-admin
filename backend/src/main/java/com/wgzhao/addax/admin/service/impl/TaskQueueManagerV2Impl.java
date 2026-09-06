package com.wgzhao.addax.admin.service.impl;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.redis.WorkerHeartbeatService;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.service.AlertService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.EtlJobQueueService;
import com.wgzhao.addax.admin.service.EtlJourService;
import com.wgzhao.addax.admin.service.ExecutionManager;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TargetService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import com.wgzhao.addax.admin.service.UserNotificationService;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


import static com.wgzhao.addax.admin.common.Constants.ADDAX_EXECUTE_TIME_OUT_SECONDS;
import static com.wgzhao.addax.admin.common.Constants.DEFAULT_PART_FORMAT;
import static com.wgzhao.addax.admin.common.Constants.shortSdf;
import static java.lang.Math.max;

/**
 * 采集任务队列管理器 - Master/Worker 分配模式
 *
 * Master（通过 Redis NX 选举）独占从 DB 队列分配任务，通过 Redis pub/sub 推送给指定 worker。
 * Worker 订阅 "addax:task:assign:{instanceId}" 频道，收到任务后执行。
 * 每个节点（含 master）都同时是 worker，通过心跳上报可用 slot 给 master。
 */
@Component
@Primary
@Slf4j
@RequiredArgsConstructor
public class TaskQueueManagerV2Impl
    implements TaskQueueManager, MessageListener
{
    // Backoff strategy
    private static final int BACKOFF_MIN_SECONDS = 30;
    private static final int BACKOFF_MAX_SECONDS = 1800;
    private static final int BACKOFF_FACTOR = 2;
    // Polling interval & lease
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 3;
    private static final int DEFAULT_LEASE_SECONDS = 7300;
    // Provisional lease granted at claim time, before delivery is confirmed. A job whose assignment
    // message is lost (master crash between claim and publish, dead subscription, reconnect gap) is
    // recovered by the 30s lease sweep within a few minutes instead of stranding for the full 7300s.
    // The worker extends it to DEFAULT_LEASE_SECONDS at execution start (claim confirmation).
    private static final int CLAIM_LEASE_SECONDS = 180;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;
    // A worker missing from heartbeats for this long is considered dead. Must exceed the heartbeat
    // key TTL (45s) so a transient Redis blip/GC pause never releases claims of a live executor.
    private static final int WORKER_RECOVERY_GRACE_SECONDS = 60;
    private static final int PENDING_JOB_PEEK_LIMIT = 200;
    // Redis channel for task assignment: master → worker
    private static final String TASK_ASSIGN_CHANNEL_PREFIX = "addax:task:assign:";

    private final DictService dictService;
    private final AddaxLogService addaxLogService;
    private final AlertService alertService;
    private final TableService tableService;
    private final EtlJourService jourService;
    private final SystemConfigService configService;
    private final JobContentService jobContentService;
    private final TargetService targetService;
    private final EtlJobQueueService jobQueueService;
    private final JdbcTemplate jdbcTemplate;
    private final ExecutionManager executionManager;
    private final UserNotificationService userNotificationService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MasterElectionService electionService;
    private final WorkerHeartbeatService heartbeatService;
    private final RedisMessageListenerContainer listenerContainer;

    // Local concurrency counters (worker-side)
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, AtomicInteger> sourceRunningTaskCount = new ConcurrentHashMap<>();

    private final ExecutorService workerPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "etl-worker");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3, r -> {
        Thread t = new Thread(r, "etl-scheduler");
        t.setDaemon(true);
        return t;
    });

    // Coalescing flag to prevent flooding dispatch on concurrent task completions
    private final AtomicBoolean dispatchScheduled = new AtomicBoolean(false);
    // Enqueues rejected while the local monitor is stopped (schema refresh) are buffered and flushed
    // when the monitor restarts. Rejecting outright silently lost scheduled fires whose 2-minute
    // misfire window could elapse during a long refresh.
    private final Deque<DeferredEnqueue> deferredEnqueues = new ConcurrentLinkedDeque<>();
    // Serializes every masterDispatch invocation: the 3s poll and async dispatches triggered by job
    // completions / DB notifications run on the same scheduler pool. Capacity check-then-reserve on
    // the per-worker ledger is only safe within a single dispatch thread.
    private final ReentrantLock dispatchLock = new ReentrantLock();

    private int concurrentLimit;
    private int enqueueCapacity;
    private volatile boolean running = false;
    private String instanceId;
    private double concurrencyWeight = 1.0;

    // SWRR state: master-only, tracks accumulated weight per worker between dispatch cycles
    private final ConcurrentHashMap<String, Double> swrrCurrentWeight = new ConcurrentHashMap<>();
    // Master-side worker capacity ledger between heartbeat snapshots
    private final ConcurrentHashMap<String, WorkerLedger> workerLedgers = new ConcurrentHashMap<>();
    // Tracks alive workers seen in the previous dispatch cycle for dead-worker detection.
    // Read/written from the dispatch thread (serialized by dispatchLock) but also cleared by
    // election callbacks on other threads, so it must tolerate concurrent mutation.
    private final Set<String> knownWorkerIds = ConcurrentHashMap.newKeySet();
    // First dispatch cycle at which a currently-missing worker was observed gone (dead-worker grace)
    private final ConcurrentHashMap<String, Instant> workerGoneSince = new ConcurrentHashMap<>();

    private volatile Future<?> listenFuture;
    private volatile ScheduledFuture<?> pollFuture;
    private volatile ScheduledFuture<?> recoverFuture;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile ScheduledFuture<?> orphanRecoverFuture;
    private volatile ScheduledFuture<?> subscriptionRetryFuture;

    @PostConstruct
    public void init()
    {
        configService.loadConfig();
        int originalConcurrentLimit = configService.getConcurrentLimit();
        this.concurrencyWeight = configService.getNodeConcurrencyWeight();
        this.concurrentLimit = Math.max(1, (int) Math.floor(originalConcurrentLimit * this.concurrencyWeight));
        this.enqueueCapacity = configService.getQueueSize();
        this.instanceId = electionService.getInstanceId();

        // Bind live counters into heartbeat service so it can report accurate state
        heartbeatService.bind(this.concurrentLimit, this.concurrencyWeight, runningTaskCount, sourceRunningTaskCount);

        // Subscribe to our personal task-assignment channel (worker side)
        if (!subscribeToAssignmentChannel()) {
            // A node that never registers its listener still heartbeats normally and would keep
            // receiving DB claims it never executes (see 0-receiver check in masterDispatch),
            // stranding jobs for the whole provisional lease — retry until the listener is added.
            log.error("Failed to subscribe to task assignment channel, retrying every 30s");
            subscriptionRetryFuture = scheduler.scheduleWithFixedDelay(() -> {
                if (!running) return;
                if (subscribeToAssignmentChannel() && subscriptionRetryFuture != null) {
                    subscriptionRetryFuture.cancel(false);
                }
            }, 30, 30, TimeUnit.SECONDS);
        }

        // Register master election callbacks
        electionService.onBecameMaster(this::onBecameMaster);
        electionService.onLostMaster(this::onLostMaster);

        running = true;
        submitScheduledTasks();

        log.info("Task queue manager started (master-worker mode). originalConcurrentLimit={} weight={} effectiveConcurrentLimit={} enqueueCapacity={} instanceId={}",
            originalConcurrentLimit, concurrencyWeight, concurrentLimit, enqueueCapacity, instanceId);
    }

    private boolean subscribeToAssignmentChannel()
    {
        try {
            listenerContainer.addMessageListener(this, new ChannelTopic(TASK_ASSIGN_CHANNEL_PREFIX + instanceId));
            log.info("Subscribed to task assignment channel: {}", TASK_ASSIGN_CHANNEL_PREFIX + instanceId);
            return true;
        }
        catch (Exception e) {
            log.warn("Failed to subscribe to task assignment channel", e);
            return false;
        }
    }

    private void submitScheduledTasks()
    {
        listenFuture = scheduler.submit(this::listenLoop);
        pollFuture = scheduler.scheduleWithFixedDelay(this::pollAndDispatch, 1, DEFAULT_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        recoverFuture = scheduler.scheduleWithFixedDelay(this::recoverLeases, 30, 30, TimeUnit.SECONDS);
        heartbeatFuture = scheduler.scheduleWithFixedDelay(heartbeatService::publishHeartbeat, 2, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelScheduledTasks()
    {
        if (listenFuture != null) listenFuture.cancel(true);
        if (pollFuture != null) pollFuture.cancel(false);
        if (recoverFuture != null) recoverFuture.cancel(false);
        if (heartbeatFuture != null) heartbeatFuture.cancel(false);
        if (subscriptionRetryFuture != null) subscriptionRetryFuture.cancel(false);
    }

    // ---- Master election callbacks ----

    private void onBecameMaster()
    {
        log.info("Became master — starting dispatch loop");
        workerLedgers.clear();
        knownWorkerIds.clear();
        workerGoneSince.clear();
        // Wait 2× heartbeat interval for workers to re-register, then release orphaned tasks
        if (orphanRecoverFuture != null) orphanRecoverFuture.cancel(false);
        orphanRecoverFuture = scheduler.schedule(this::recoverOrphanedJobs,
            2L * HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void onLostMaster()
    {
        log.info("Lost master role — pausing dispatch (workers continue executing current tasks)");
        swrrCurrentWeight.clear();
        workerLedgers.clear();
        knownWorkerIds.clear();
        workerGoneSince.clear();
        if (orphanRecoverFuture != null) {
            orphanRecoverFuture.cancel(false);
            orphanRecoverFuture = null;
        }
    }

    /**
     * One-shot task scheduled 2× HEARTBEAT_INTERVAL_SECONDS after becoming master.
     * Releases tasks claimed by instances that did not re-register as alive workers.
     */
    private void recoverOrphanedJobs()
    {
        if (!electionService.isMaster()) return;
        try {
            Set<String> aliveIds = heartbeatService.getAliveWorkers().stream()
                .map(WorkerHeartbeatService.WorkerInfo::instanceId)
                .collect(Collectors.toSet());
            int recovered = jobQueueService.releaseOrphanedJobs(aliveIds);
            if (recovered > 0) {
                log.info("Orphan recovery: released {} tasks claimed by disappeared workers (alive={})", recovered, aliveIds);
                triggerDispatchAsync();
            }
            else {
                log.debug("Orphan recovery: no orphaned tasks found (alive={})", aliveIds);
            }
        }
        catch (Exception e) {
            log.error("Orphan recovery failed", e);
        }
    }

    // ---- Worker: receive task assignment via Redis pub/sub ----

    /**
     * Called when master publishes a task-assignment message to this node's channel.
     */
    @Override
    public void onMessage(@NonNull Message message, byte[] pattern)
    {
        try {
            String body = new String(message.getBody());
            EtlJobQueue job = objectMapper.readValue(body, EtlJobQueue.class);
            log.info("Received task assignment: jobId={} tid={}", job.getId(), job.getTid());
            // Capacity accounting happens inside executeClaimedJob: incrementing here (before any DB
            // lookup and pool submit) leaked counts whenever this handler threw, permanently
            // shrinking the node's advertised capacity until restart.
            workerPool.submit(() -> executeClaimedJob(job));
        }
        catch (Exception e) {
            log.error("Failed to handle task assignment message", e);
        }
    }

    // ---- Master: scan alive workers, assign jobs ----

    /**
     * scanAndEnqueueEtlTasks: persists runnable tables into DB queue (DB-side, idempotent).
     * Only one node needs to do this, but it's safe if all do (unique constraint prevents duplicates).
     */
    @Override
    public void scanAndEnqueueEtlTasks()
    {
        try {
            long pending = jobQueueService.countPending();
            if (pending >= enqueueCapacity) {
                log.warn("Queue capacity reached: {}/{}, skipping enqueue", pending, enqueueCapacity);
                return;
            }
            List<EtlTable> tasks = tableService.getRunnableTasks();
            if (tasks.isEmpty()) return;
            int room = (int) Math.max(0, enqueueCapacity - pending);
            int enqueued = 0, skipped = 0;
            LocalDate bizDate = LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
            for (EtlTable t : tasks) {
                if (enqueued >= room) { skipped++; continue; }
                try {
                    int added = jobQueueService.enqueue(t, bizDate, 100);
                    if (added > 0) enqueued++; else skipped++;
                }
                catch (Exception ex) {
                    skipped++;
                    log.debug("Enqueue skipped tid={}, reason={}", t.getId(), ex.getMessage());
                }
            }
            log.info("Enqueue complete: {} added, {} skipped, {} pending in queue", enqueued, skipped, jobQueueService.countPending());
        }
        catch (Exception e) {
            log.error("scanAndEnqueueEtlTasks failed", e);
            alertService.sendToWeComRobot("扫描采集任务失败: " + e.getMessage());
        }
    }

    private void listenLoop()
    {
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
            java.sql.Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            stmt.execute("LISTEN etl_jobs");
            log.info("LISTEN on channel etl_jobs started");
            org.postgresql.PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
            while (running) {
                try {
                    stmt.execute("SELECT 1");
                    org.postgresql.PGNotification[] notes = pgConn.getNotifications();
                    if (notes != null && notes.length > 0) {
                        log.debug("Received {} DB notifications", notes.length);
                        for (org.postgresql.PGNotification ignored : notes) {
                            triggerDispatchAsync();
                        }
                    }
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                catch (SQLException se) {
                    log.warn("LISTEN loop SQL error, will retry", se);
                    TimeUnit.SECONDS.sleep(2);
                }
            }
        }
        catch (Exception e) {
            log.error("LISTEN listener exited with error", e);
        }
        log.info("LISTEN listener stopped");
    }

    private void pollAndDispatch()
    {
        try {
            if (!running) return;
            if (electionService.isMaster()) {
                masterDispatch();
            }
        }
        catch (Throwable t) {
            // Never let the fixed-delay poll die silently: an unchecked error here would otherwise
            // stop all scheduled dispatch for the process lifetime (scheduleWithFixedDelay drops the
            // task on the first thrown exception).
            log.error("Poll dispatch cycle failed, will retry next cycle", t);
        }
    }

    private void masterDispatch()
    {
        dispatchLock.lock();
        try {
            masterDispatchLocked();
        }
        finally {
            dispatchLock.unlock();
        }
    }

    /**
     * Master-only: read alive workers, assign pending jobs from DB queue via Redis pub/sub.
     * Callers must hold dispatchLock — capacity bookkeeping and SWRR state are not thread-safe.
     *
     * Uses Smooth Weighted Round Robin (SWRR) to distribute tasks proportionally to worker weights.
     * Source-level concurrency is checked against worker.sourceRunning from the heartbeat
     * (up to HEARTBEAT_INTERVAL_SECONDS staleness — acceptable for this use case).
     */
    private void masterDispatchLocked()
    {
        List<WorkerHeartbeatService.WorkerInfo> workers = heartbeatService.getAliveWorkers();
        if (workers.isEmpty()) {
            log.debug("No alive workers found, skipping dispatch");
            return;
        }

        // Detect workers that disappeared since last dispatch cycle and proactively recover their tasks,
        // avoiding waiting for DEFAULT_LEASE_SECONDS (7300s) expiry. A worker must stay absent for
        // WORKER_RECOVERY_GRACE_SECONDS before its claims are released: a heartbeat key can expire on a
        // transient Redis blip while the worker (and its addax processes) are still running, and releasing
        // early would dispatch the same (tid, biz_date) to a second worker.
        Set<String> currentWorkerIds = workers.stream()
            .map(WorkerHeartbeatService.WorkerInfo::instanceId)
            .collect(Collectors.toSet());
        Instant now = Instant.now();
        if (!knownWorkerIds.isEmpty()) {
            for (String id : new HashSet<>(knownWorkerIds)) {
                if (currentWorkerIds.contains(id)) {
                    workerGoneSince.remove(id);
                    continue;
                }
                Instant goneAt = workerGoneSince.computeIfAbsent(id, k -> now);
                if (goneAt.plusSeconds(WORKER_RECOVERY_GRACE_SECONDS).isAfter(now)) {
                    continue;
                }
                workerGoneSince.remove(id);
                log.warn("Worker {} absent for {}s — recovering its claimed tasks", id, WORKER_RECOVERY_GRACE_SECONDS);
                try {
                    int recovered = jobQueueService.releaseClaimedByInstance(id);
                    if (recovered > 0) {
                        log.info("Released {} tasks from dead worker {}", recovered, id);
                    }
                }
                catch (Exception e) {
                    log.error("Failed to release tasks from dead worker {}", id, e);
                }
            }
        }
        knownWorkerIds.clear();
        knownWorkerIds.addAll(currentWorkerIds);

        // Clean up SWRR state for workers that are no longer alive
        swrrCurrentWeight.keySet().retainAll(currentWorkerIds);
        // Evict capacity ledgers of dead workers too: instanceId embeds the host pid, so every
        // worker restart creates a new id and dead ledgers (with their reservation deque) would
        // otherwise leak on a long-lived master for the process lifetime.
        workerLedgers.keySet().retainAll(currentWorkerIds);

        // Reconcile the master's capacity ledger with the latest heartbeat snapshot.
        // The ledger is mutated on every successful assignment, so dispatch cycles do not
        // depend on heartbeat freshness alone.
        List<WorkerSlot> slots = workers.stream()
            .map(w -> {
                WorkerLedger ledger = workerLedgers.compute(w.instanceId(), (id, existing) -> {
                    if (existing == null) {
                        return new WorkerLedger(w);
                    }
                    existing.reconcile(w);
                    return existing;
                });
                return new WorkerSlot(ledger);
            })
            .collect(Collectors.toCollection(java.util.ArrayList::new));
        List<EtlJobQueue> pendingJobs = jobQueueService.peekPendingJobs(PENDING_JOB_PEEK_LIMIT);
        if (pendingJobs.isEmpty()) {
            return;
        }

        // Cluster-wide per-source running totals (heartbeat + in-cycle reservations across all
        // workers). Source maxConcurrency is a cluster-level guarantee: without this sum it is
        // silently multiplied by the number of workers.
        Map<Integer, Integer> clusterSourceRunning = new HashMap<>();
        for (WorkerSlot slot : slots) {
            slot.ledger.sourceRunningSnapshot().forEach((sid, count) -> clusterSourceRunning.merge(sid, count, Integer::sum));
        }

        Set<Long> consumedJobIds = new HashSet<>();
        while (true) {
            WorkerSlot selected = selectWorkerSwrr(slots);
            if (selected == null) return; // all workers have no available slots

            Optional<JobClaim> maybe = findClaimableJobForWorker(selected, pendingJobs, consumedJobIds, clusterSourceRunning);
            if (maybe.isEmpty()) {
                selected.slots = 0;
                continue;
            }

            JobClaim claim = maybe.get();
            EtlJobQueue job = claim.job();
            Integer trackedSid = claim.trackedSid();

            // Publish job to worker's channel
            try {
                String assignPayload = objectMapper.writeValueAsString(job);
                String channel = TASK_ASSIGN_CHANNEL_PREFIX + selected.instanceId();
                int runningBefore = selected.running();
                int sourceRunningBefore = trackedSid != null ? selected.sourceRunning(trackedSid) : -1;
                Long receivers = stringRedisTemplate.convertAndSend(channel, assignPayload);
                if (receivers == null || receivers == 0) {
                    // No subscriber heard us: the worker heartbeats but is not listening (boot-time
                    // subscription failure or reconnect gap). Its claim is released and the short
                    // provisional lease lets the 30s sweep recover it even if this release races.
                    log.error("No listener on channel {} for job {} (worker deaf) — releasing claim", channel, job.getId());
                    jobQueueService.releaseClaim(job.getId(), 2, selected.instanceId());
                    selected.slots = 0; // do not keep claiming jobs for a worker that cannot hear us this cycle
                    continue;
                }
                selected.reserve(trackedSid);
                if (trackedSid != null) {
                    clusterSourceRunning.merge(trackedSid, 1, Integer::sum);
                }
                log.info("Assigned job {} (tid={}) to worker {} [source sid={} sourceRunning={} globalRunning={}/{}]",
                    job.getId(), job.getTid(), selected.instanceId(),
                    trackedSid != null ? String.valueOf(trackedSid) : "-",
                    trackedSid != null ? String.valueOf(sourceRunningBefore) : "-",
                    runningBefore, selected.concurrentLimit());
                selected.slots--;
                consumedJobIds.add(job.getId());
            }
            catch (Exception e) {
                log.error("Failed to publish task assignment for jobId={} to worker={}", job.getId(), selected.instanceId(), e);
                consumedJobIds.add(job.getId());
                try { jobQueueService.releaseClaim(job.getId(), 5, selected.instanceId()); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Find the first pending job that is still feasible for the selected worker and claim it atomically.
     */
    private Optional<JobClaim> findClaimableJobForWorker(WorkerSlot selected,
                                                         List<EtlJobQueue> candidates,
                                                         Set<Long> consumedJobIds,
                                                         Map<Integer, Integer> clusterSourceRunning)
    {
        for (EtlJobQueue candidate : candidates) {
            long jobId = candidate.getId();
            if (consumedJobIds.contains(jobId)) {
                continue;
            }

            JobCheck check = assessJobForWorker(selected, candidate, clusterSourceRunning);
            if (!check.assignable()) {
                continue;
            }

            // Claim with a provisional short lease: delivery is only confirmed when the worker
            // renews at execution start (see executeClaimedJob), after which the lease is long.
            Optional<EtlJobQueue> claimed = jobQueueService.assignSpecificJobToWorker(
                jobId, selected.instanceId(), CLAIM_LEASE_SECONDS);
            if (claimed.isPresent()) {
                consumedJobIds.add(jobId);
                return Optional.of(new JobClaim(claimed.get(), check.trackedSid()));
            }

            consumedJobIds.add(jobId);
        }

        return Optional.empty();
    }

    /**
     * Check whether the current worker ledger can still accept this job without claiming it first.
     */
    private JobCheck assessJobForWorker(WorkerSlot selected, EtlJobQueue job, Map<Integer, Integer> clusterSourceRunning)
    {
        if (selected.running() >= selected.concurrentLimit()) {
            return new JobCheck(false, null);
        }

        EtlTable table = tableService.getTable(job.getTid());
        if (table == null) {
            return new JobCheck(true, null);
        }

        VwEtlTableWithSource source = tableService.getTableView(table.getId());
        if (source == null || source.getMaxConcurrency() == null || source.getMaxConcurrency() <= 0) {
            return new JobCheck(true, null);
        }

        // Cluster-wide gate: source maxConcurrency bounds total concurrent pulls from the source
        // across ALL workers, so it must be checked against the cluster sum, not only this worker.
        if (clusterSourceRunning.getOrDefault(table.getSid(), 0) >= source.getMaxConcurrency()) {
            return new JobCheck(false, null);
        }

        int effectiveLimit = Math.max(1, Math.min(
            (int) Math.floor(source.getMaxConcurrency() * selected.weight()),
            selected.concurrentLimit()
        ));
        int workerSrcRunning = selected.sourceRunning(table.getSid());
        if (workerSrcRunning >= effectiveLimit) {
            return new JobCheck(false, null);
        }

        return new JobCheck(true, table.getSid());
    }

    /**
     * Smooth Weighted Round Robin selection.
     * Each call: all workers gain configuredWeight, then the one with highest currentWeight
     * and available slots is chosen and loses totalWeight.
     *
     * @return selected WorkerSlot, or null if no worker has slots
     */
    private WorkerSlot selectWorkerSwrr(List<WorkerSlot> slots)
    {
        if (slots.isEmpty()) return null;

        double totalWeight = slots.stream().mapToDouble(WorkerSlot::weight).sum();

        // Step 1: every worker accumulates its configured weight
        for (WorkerSlot s : slots) {
            swrrCurrentWeight.merge(s.instanceId(), s.weight(), Double::sum);
        }

        // Step 2: select the worker with the highest accumulated weight that has a free slot
        WorkerSlot selected = null;
        double maxCw = Double.NEGATIVE_INFINITY;
        for (WorkerSlot s : slots) {
            if (s.slots <= 0) continue;
            double cw = swrrCurrentWeight.getOrDefault(s.instanceId(), 0.0);
            if (cw > maxCw) {
                maxCw = cw;
                selected = s;
            }
        }

        if (selected == null) return null;

        // Step 3: deduct total weight from the selected worker
        swrrCurrentWeight.merge(selected.instanceId(), -totalWeight, Double::sum);

        return selected;
    }

    private record JobClaim(EtlJobQueue job, Integer trackedSid)
    {
    }

    private record JobCheck(boolean assignable, Integer trackedSid)
    {
    }

    /** Worker capacity snapshot backed by the master's authoritative ledger. */
    private static final class WorkerSlot
    {
        final WorkerLedger ledger;
        int slots;

        WorkerSlot(WorkerLedger ledger)
        {
            this.ledger = ledger;
            this.slots = ledger.availableSlots();
        }

        String instanceId()
        {
            return ledger.instanceId();
        }

        double weight()
        {
            return ledger.weight();
        }

        int running()
        {
            return ledger.running();
        }

        int heartbeatRunning()
        {
            return ledger.heartbeatRunning();
        }

        int concurrentLimit()
        {
            return ledger.concurrentLimit();
        }

        int sourceRunning(int sid)
        {
            return ledger.sourceRunning(sid);
        }

        void reserve(Integer sid)
        {
            ledger.reserve(sid);
        }
    }

    private static final class WorkerLedger
    {
        private WorkerHeartbeatService.WorkerInfo heartbeat;
        private Instant heartbeatSeenAt;
        private int reservedRunning;
        private final Map<Integer, Integer> reservedSourceRunning = new HashMap<>();
        private final Deque<Reservation> reservations = new ArrayDeque<>();

        WorkerLedger(WorkerHeartbeatService.WorkerInfo heartbeat)
        {
            this.heartbeat = heartbeat;
            this.heartbeatSeenAt = heartbeat.lastSeen();
        }

        synchronized void reconcile(WorkerHeartbeatService.WorkerInfo info)
        {
            if (heartbeatSeenAt != null && !info.lastSeen().isAfter(heartbeatSeenAt)) {
                return;
            }
            heartbeat = info;
            heartbeatSeenAt = info.lastSeen();
            while (!reservations.isEmpty() && !reservations.peekFirst().reservedAt().isAfter(heartbeatSeenAt)) {
                Reservation reservation = reservations.removeFirst();
                reservedRunning = Math.max(0, reservedRunning - 1);
                if (reservation.sid() != null) {
                    reservedSourceRunning.computeIfPresent(reservation.sid(), (k, v) -> v <= 1 ? null : v - 1);
                }
            }
        }

        synchronized String instanceId()
        {
            return heartbeat.instanceId();
        }

        synchronized double weight()
        {
            return heartbeat.weight();
        }

        synchronized int concurrentLimit()
        {
            return heartbeat.concurrentLimit();
        }

        synchronized int heartbeatRunning()
        {
            return heartbeat.running();
        }

        synchronized int running()
        {
            return heartbeat.running() + reservedRunning;
        }

        synchronized int availableSlots()
        {
            return Math.max(0, concurrentLimit() - running());
        }

        synchronized int sourceRunning(int sid)
        {
            return heartbeat.sourceRunning().getOrDefault(sid, 0)
                + reservedSourceRunning.getOrDefault(sid, 0);
        }

        /** Cluster-level accounting: per-source total across this worker (heartbeat + reservations). */
        synchronized Map<Integer, Integer> sourceRunningSnapshot()
        {
            Map<Integer, Integer> snapshot = new HashMap<>(reservedSourceRunning);
            heartbeat.sourceRunning().forEach((sid, count) -> snapshot.merge(sid, count, Integer::sum));
            return snapshot;
        }

        synchronized void reserve(Integer sid)
        {
            Reservation reservation = new Reservation(sid, Instant.now());
            reservations.addLast(reservation);
            reservedRunning++;
            if (sid != null) {
                reservedSourceRunning.merge(sid, 1, Integer::sum);
            }
        }
    }

    private record Reservation(Integer sid, Instant reservedAt)
    {
    }

    /**
     * Deferred enqueue while the monitor is stopped. explicitBizDate is only set for fillbacks:
     * normal enqueues resolve the business date against the config at flush time, because the
     * daily switch may have happened while the enqueue was waiting.
     */
    private record DeferredEnqueue(EtlTable table, LocalDate explicitBizDate, String payload)
    {
    }

    private void triggerDispatchAsync()
    {
        if (!running || !electionService.isMaster()) return;
        if (!dispatchScheduled.compareAndSet(false, true)) return;
        try {
            scheduler.execute(() -> {
                try {
                    dispatchScheduled.set(false);
                    masterDispatch();
                }
                catch (Throwable t) {
                    log.warn("Async dispatch failed", t);
                }
            });
        }
        catch (Exception e) {
            dispatchScheduled.set(false);
            log.warn("Failed to submit async dispatch", e);
        }
    }

    // ---- Worker: execute an assigned job ----

    private void executeClaimedJob(EtlJobQueue job)
    {
        long start = System.currentTimeMillis();
        TaskResultDto taskResultDto = null;
        ScheduledFuture<?> renewer = null;
        // 声明在 try 外,异常分支(notifyFinalFailure)也需要引用
        EtlTable task = null;
        // Source whose running counter was reserved at start; drives the finally decrement so a
        // deleted table or a max_concurrency change mid-run cannot leak the counter.
        Integer countedSourceSid = null;
        final long jobId = job.getId();

        try {
            // Claim confirmation: only execute while this instance still owns the row. The row may
            // have been released/reclaimed between dispatch and receipt (publish timeout, master
            // crash, orphan recovery), in which case executing would duplicate a successor run.
            if (!jobQueueService.renewLease(job.getId(), instanceId, DEFAULT_LEASE_SECONDS)) {
                log.warn("Job {} no longer owned by instance {}, skipping execution", job.getId(), instanceId);
                return;
            }
            // Honor a kill signal written while this assignment was in flight (kill pub/sub message
            // missed during a reconnect gap, or a kill issued before this run's process registered):
            // never start an execution the user asked to cancel.
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(Constants.TASK_KILL_SIGNAL_KEY_PREFIX + job.getTid()))) {
                log.warn("Kill signal present for tid={}, cancelling job {} before start", job.getTid(), job.getId());
                jobQueueService.completeCancelled(job.getId(), "Killed by user request", instanceId);
                return;
            }

            // Reserve capacity now that this run is committed. Increments and the finally decrements
            // below are symmetric by construction: any exception between here and the finally block
            // releases what was reserved.
            runningTaskCount.incrementAndGet();
            task = tableService.getTable(job.getTid());
            if (task == null) {
                throw new IllegalStateException("Task not found tid=" + job.getTid());
            }
            VwEtlTableWithSource sourceView = tableService.getTableView(task.getId());
            if (sourceView != null && sourceView.getMaxConcurrency() != null && sourceView.getMaxConcurrency() > 0) {
                sourceRunningTaskCount.computeIfAbsent(task.getSid(), k -> new AtomicInteger(0)).incrementAndGet();
                countedSourceSid = task.getSid();
            }

            int renewInterval = Math.max(30, DEFAULT_LEASE_SECONDS / 3);
            renewer = scheduler.scheduleAtFixedRate(() -> {
                try {
                    boolean dbRenewed = jobQueueService.renewLease(job.getId(), instanceId, DEFAULT_LEASE_SECONDS);
                    if (!dbRenewed) {
                        log.warn("DB lease renewal failed jobId={}", job.getId());
                    }
                }
                catch (Exception e) {
                    log.warn("Lease renewal exception for jobId={}", jobId, e);
                }
            }, renewInterval, renewInterval, TimeUnit.SECONDS);

            taskResultDto = executeEtlTaskWithConcurrencyControl(task, job.getBizDate());
            // A kill recorded before this run's claim belongs to a previous execution of the same
            // tid and must not misclassify a genuine failure as a user cancel.
            boolean killedByUser = executionManager.consumeKillRequested(job.getTid(),
                job.getClaimedAt() != null ? job.getClaimedAt() : Instant.EPOCH);
            if (killedByUser) {
                jobQueueService.completeCancelled(job.getId(), "Killed by user request", instanceId);
                log.info("Task killed by user, marked queue job {} as cancelled", job.getId());
            }
            else if (taskResultDto.success()) {
                jobQueueService.completeSuccess(job.getId(), instanceId);
                alertService.reportCollectionSuccess(task.getId(), task.getSourceDb(), task.getSourceTable());
            }
            else {
                Duration backoff = computeBackoff(job.getAttempts());
                jobQueueService.failOrReschedule(job, "Addax non-zero exit", backoff, instanceId);
                notifyFinalFailure(job, task, "Addax 非0退出");
            }
        }
        catch (Exception e) {
            log.error("Task execution failed jobId={} tid={}", job.getId(), job.getTid(), e);
            boolean killedByUser = executionManager.consumeKillRequested(job.getTid(),
                job.getClaimedAt() != null ? job.getClaimedAt() : Instant.EPOCH);
            if (killedByUser) {
                try {
                    jobQueueService.completeCancelled(job.getId(), "Killed by user request", instanceId);
                    log.info("Task killed by user during exception path, marked queue job {} as cancelled", job.getId());
                }
                catch (Exception ignored) {
                }
            }
            else {
                Duration backoff = computeBackoff(job.getAttempts());
                try { jobQueueService.failOrReschedule(job, e.getMessage(), backoff, instanceId); } catch (Exception ignored) {}
                notifyFinalFailure(job, task, e.getMessage());
            }
        }
        finally {
            if (renewer != null) {
                try { renewer.cancel(false); } catch (Exception ignored) {}
            }

            int afterGlobal = runningTaskCount.decrementAndGet();

            // Release the source reservation taken at start (countedSourceSid), even if the table
            // or its source view disappeared mid-run — no re-fetch, no skipped decrement.
            if (countedSourceSid != null) {
                sourceRunningTaskCount.computeIfPresent(countedSourceSid, (k, v) -> {
                    v.decrementAndGet();
                    return v.get() <= 0 ? null : v;
                });
            }

            // Guarded: countPending() is a DB round-trip, only pay for it when debug is enabled
            if (log.isDebugEnabled()) {
                log.debug("jobId={} done, elapsed={}s, running={}, pending={}",
                    job.getId(), (System.currentTimeMillis() - start) / 1000, afterGlobal, jobQueueService.countPending());
            }

            try { notifyJobCompletion(job, taskResultDto); } catch (Exception ignored) {}

            // Trigger next dispatch cycle on master
            triggerDispatchAsync();
        }
    }

    private Duration computeBackoff(int attempts)
    {
        long secs = BACKOFF_MIN_SECONDS;
        for (int i = 1; i < attempts; i++) {
            secs = Math.min(BACKOFF_MAX_SECONDS, secs * BACKOFF_FACTOR);
        }
        return Duration.ofSeconds(secs);
    }

    /**
     * 最后一次尝试失败时发送告警(重试耗尽才告警)。
     * AlertService 内部会记录告警状态,后续采集成功时自动发送恢复通知。
     */
    private void notifyFinalFailure(EtlJobQueue job, EtlTable task, String error)
    {
        boolean finalAttempt = job.getAttempts() >= job.getMaxAttempts();
        if (task != null) {
            alertService.reportCollectionFailure(task.getId(), task.getSourceDb(), task.getSourceTable(),
                error, job.getAttempts(), job.getMaxAttempts(), finalAttempt);
        }
        else {
            alertService.reportCollectionFailure(job.getTid(), null, null,
                error, job.getAttempts(), job.getMaxAttempts(), finalAttempt);
        }
    }

    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task, LocalDate overrideBizDate)
    {
        long tid = task.getId();
        long startTime = System.currentTimeMillis();
        try {
            tableService.setRunning(task);
            boolean result = executeEtlTaskLogic(task, overrideBizDate);
            long duration = max((System.currentTimeMillis() - startTime) / 1000, 0);
            log.info("Task {}.{}({}) completed, elapsed={}s, result={}", task.getSourceDb(), task.getSourceTable(), tid, duration, result);
            task.setDuration(duration);
            if (result) {
                tableService.setFinished(task);
                return TaskResultDto.success("Success", duration);
            }
            else {
                tableService.setFailed(task);
                return TaskResultDto.failure("Failed: Addax non-zero exit", duration);
            }
        }
        catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("Task {}.{}({}) failed, elapsed={}s", task.getSourceDb(), task.getSourceTable(), tid, duration, e);
            task.setDuration(duration);
            tableService.setFailed(task);
            String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
            return TaskResultDto.failure("Exception: " + msg, duration);
        }
    }

    @Override
    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task)
    {
        return executeEtlTaskWithConcurrencyControl(task, null);
    }

    public boolean executeEtlTaskLogic(EtlTable task, LocalDate overrideBizDate)
    {
        long taskId = task.getId();
        log.info("Executing task: taskId={}, destDB={}, tableName={}", taskId, task.getTargetDb(), task.getTargetTable());

        // For fillback tasks, overrideBizDate contains the target fillback date
        // which differs from the system biz_date. In this case the pre-generated
        // template (built for the current biz_date) is stale and must be regenerated
        // in-memory for the target date. Normal tasks pass null and use the cached template.
        String job;
        LocalDate systemBizDate = configService.getBizDateAsDate();
        if (overrideBizDate != null && !overrideBizDate.equals(systemBizDate)) {
            log.info("Task {} using fillback date {} (system biz_date={}), regenerating template",
                taskId, overrideBizDate, systemBizDate);
            job = jobContentService.getJobContentForDate(taskId, overrideBizDate);
        } else {
            job = jobContentService.getJobContent(taskId);
        }
        if (job == null || job.isEmpty()) {
            log.warn("Job template not generated, taskId={}", taskId);
            return false;
        }
        String partFormat = task.getPartFormat();
        String bizDateStr = configService.getBizDate();
        DateTimeFormatter dtf = shortSdf;
        if (partFormat != null && !partFormat.isBlank() && !DEFAULT_PART_FORMAT.equals(partFormat)) {
            dtf = DateTimeFormatter.ofPattern(partFormat);
        }
        if (overrideBizDate != null) {
            bizDateStr = overrideBizDate.format(dtf);
        }
        VwEtlTableWithSource tableView = tableService.getTableView(taskId);
        if (tableView == null) {
            log.warn("Table view not found, taskId={}", taskId);
            return false;
        }
        boolean prepareResult = targetService.prepareBeforeRun(taskId, tableView, bizDateStr);
        if (!prepareResult) return false;

        File tempFile;
        try {
            String curDate = DateUtil.date().toDateStr();
            String jobsDir = Path.of(System.getProperty("app.home")).resolve("job").resolve(curDate) + "/";
            Files.createDirectories(Path.of(jobsDir));
            // Include bizDateStr in the file name to prevent concurrent fillback tasks
            // for the same table from overwriting each other's job files.
            tempFile = new File(jobsDir + task.getTargetDb() + "." + task.getTargetTable() + "_" + bizDateStr + ".json");
            Files.writeString(tempFile.toPath(), job);
        }
        catch (IOException e) {
            log.error("Failed to write temp job file", e);
            return false;
        }
        String logName = String.format("%s.%s_%d_%s.log", task.getTargetDb(), task.getTargetTable(), taskId, bizDateStr);
        String addaxScript = Path.of(dictService.getAddaxHome(), "bin", "addax.sh").toString();
        String jvmProps = String.format("-DjobName=%d -Dlog.file.name=%s", taskId, logName);
        List<String> cmdArgs = List.of(addaxScript, "-p", jvmProps, tempFile.getAbsolutePath());
        String cmd = toCommandString(cmdArgs);
        boolean retCode = executeAddax(cmdArgs, cmd, taskId, logName, task.getMaxRuntime() == null ? ADDAX_EXECUTE_TIME_OUT_SECONDS : task.getMaxRuntime());
        log.debug("Task {} log written to: {}", taskId, logName);
        return retCode;
    }

    private boolean executeAddax(List<String> commandArgs, String commandForLog, long tid, String logName, long maxRuntimeSeconds)
    {
        EtlJour etlJour = jourService.addJour(tid, JourKind.COLLECT, commandForLog);
        Process process;
        TaskResultDto taskResult;
        long pid = -1;
        try {
            process = CommandExecutor.startProcess(commandArgs);
            try { pid = process.pid(); } catch (UnsupportedOperationException ignored) {}
            try {
                executionManager.register(tid, process, pid, instanceId);
            }
            catch (Exception e) {
                log.warn("Failed to register process in ExecutionManager tid={} pid={}", tid, pid, e);
            }
            taskResult = CommandExecutor.waitForProcessWithResult(process, maxRuntimeSeconds, commandForLog);
        }
        catch (IOException ioe) {
            log.error("Failed to start process for command: {}", commandForLog, ioe);
            taskResult = TaskResultDto.failure(ioe.getMessage(), 0);
        }
        finally {
            try { executionManager.unregister(tid); } catch (Exception ignored) {}
        }
        Path path = Path.of(dictService.getAddaxHome() + "/log/" + logName);
        String logContent = null;
        try { logContent = Files.readString(path); } catch (IOException e) { log.error("Failed to read Addax log: {}", path, e); }
        addaxLogService.insertLog(tid, logContent);
        etlJour.setDuration(taskResult.durationSeconds());
        etlJour.setStatus(true);
        if (!taskResult.success()) {
            log.error("Addax task {} failed, exit: {}", tid, taskResult.message());
            etlJour.setStatus(false);
            etlJour.setErrorMsg(taskResult.message());
        }
        jourService.saveJour(etlJour);
        return taskResult.success();
    }

    private String toCommandString(List<String> args)
    {
        return args.stream().map(this::quoteForLog).collect(Collectors.joining(" "));
    }

    private String quoteForLog(String arg)
    {
        if (arg == null || arg.isBlank()) {
            return "''";
        }
        if (arg.matches("[A-Za-z0-9_./:=@+-]+")) {
            return arg;
        }
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    private void recoverLeases()
    {
        // Lease recovery mutates the shared queue, so only the master may run it
        if (!running || !electionService.isMaster()) return;
        try {
            int recovered = jobQueueService.recoverExpiredLeases();
            if (recovered > 0) {
                log.info("Recovered {} expired leases, triggering dispatch", recovered);
                triggerDispatchAsync();
            }
        }
        catch (Exception e) {
            log.warn("Lease recovery failed", e);
        }
    }

    @PreDestroy
    public void shutdown()
    {
        running = false;
        heartbeatService.removeHeartbeat();
        scheduler.shutdownNow();
        workerPool.shutdownNow();
        log.info("Task queue manager stopped");
    }

    // ---- TaskQueueManager interface ----

    @Override
    public void stopQueueMonitor()
    {
        running = false;
        cancelScheduledTasks();
    }

    @Override
    public void restartQueueMonitor()
    {
        stopQueueMonitor();
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        running = true;
        submitScheduledTasks();
        flushDeferredEnqueues();
    }

    @Override
    public boolean addTaskToQueue(@NonNull EtlTable etlTable)
    {
        if (!running) {
            return deferEnqueue(etlTable, null, null);
        }
        LocalDate bizDate = LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        return jobQueueService.enqueue(etlTable, bizDate, 100) > 0;
    }

    @Override
    public boolean addTaskToQueue(@NonNull EtlTable etlTable, String payload)
    {
        if (!running) {
            return deferEnqueue(etlTable, null, payload);
        }
        LocalDate bizDate = LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        return jobQueueService.enqueue(etlTable, bizDate, 100, payload) > 0;
    }

    @Override
    public boolean addTaskToQueue(long tableId)
    {
        EtlTable table = tableService.getTable(tableId);
        return table != null && addTaskToQueue(table);
    }

    @Override
    public boolean addFillbackTaskToQueue(EtlTable etlTable, LocalDate fillbackDate, String payload)
    {
        if (!running) {
            return deferEnqueue(etlTable, fillbackDate, payload);
        }
        return jobQueueService.enqueue(etlTable, fillbackDate, 100, payload) > 0;
    }

    private boolean deferEnqueue(EtlTable etlTable, LocalDate explicitBizDate, String payload)
    {
        log.info("Schema refresh in progress, deferring enqueue of task {} until monitor restart", etlTable.getId());
        deferredEnqueues.addLast(new DeferredEnqueue(etlTable, explicitBizDate, payload));
        return true;
    }

    private void flushDeferredEnqueues()
    {
        int flushed = 0;
        int failed = 0;
        DeferredEnqueue d;
        while ((d = deferredEnqueues.pollFirst()) != null) {
            try {
                LocalDate bizDate = d.explicitBizDate() != null ? d.explicitBizDate()
                    : LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (jobQueueService.enqueue(d.table(), bizDate, 100, d.payload()) > 0) {
                    flushed++;
                }
            }
            catch (Exception e) {
                failed++;
                log.warn("Failed to flush deferred enqueue tid={}", d.table().getId(), e);
            }
        }
        if (flushed > 0 || failed > 0) {
            log.info("Deferred enqueue flush complete: {} enqueued, {} failed", flushed, failed);
        }
    }

    @Override
    public boolean addTaskToQueue(long tableId, String payload)
    {
        EtlTable table = tableService.getTable(tableId);
        return table != null && addTaskToQueue(table, payload);
    }

    @Override
    public int clearQueue()
    {
        return jobQueueService.clearPending();
    }

    @Override
    public boolean isRefreshing()
    {
        return !running;
    }

    @Override
    public Map<String, Object> getQueueStatus()
    {
        Map<String, Object> status = new HashMap<>();
        status.put("pendingInDatabase", jobQueueService.countPending());
        status.put("runningTasks", runningTaskCount.get());
        status.put("isMaster", electionService.isMaster());
        status.put("masterInstanceId", electionService.getMasterInstanceId());
        return status;
    }

    @Override
    public void startQueueMonitor()
    {
        if (running) return;
        running = true;
        submitScheduledTasks();
        flushDeferredEnqueues();
    }

    @Override
    public void truncateQueueExceptRunningTasksBefore(Instant createdBefore)
    {
        jobQueueService.truncateQueueExceptRunningTasksBefore(createdBefore);
    }

    @SuppressWarnings("unchecked")
    private void notifyJobCompletion(EtlJobQueue job, TaskResultDto result)
    {
        if (result == null) return;
        String payload = job.getPayload();
        if (payload == null || payload.isBlank()) return;
        String username = null;
        try {
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            Object submitter = map.get("submitter");
            if (submitter != null) username = submitter.toString();
        }
        catch (Exception e) {
            log.debug("Failed to parse job payload for jobId={}", job.getId());
        }
        if (username == null || username.isBlank()) return;
        EtlTable table = tableService.getTable(job.getTid());
        String target = table == null ? String.valueOf(job.getTid()) : table.getTargetDb() + "." + table.getTargetTable();
        String status = result.success() ? "成功" : "失败";
        String content = String.format("表 %s 采集%s，耗时 %ss。", target, status, result.durationSeconds());
        userNotificationService.create(username, "采集任务完成", content, "COLLECT", "tid", String.valueOf(job.getTid()));
    }
}
