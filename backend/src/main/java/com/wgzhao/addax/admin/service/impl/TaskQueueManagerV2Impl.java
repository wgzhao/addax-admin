package com.wgzhao.addax.admin.service.impl;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;
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

    private int concurrentLimit;
    private int enqueueCapacity;
    private volatile boolean running = false;
    private String instanceId;
    private double concurrencyWeight = 1.0;

    // SWRR state: master-only, tracks accumulated weight per worker between dispatch cycles
    private final ConcurrentHashMap<String, Double> swrrCurrentWeight = new ConcurrentHashMap<>();
    // Master-side worker capacity ledger between heartbeat snapshots
    private final ConcurrentHashMap<String, WorkerLedger> workerLedgers = new ConcurrentHashMap<>();
    // Tracks alive workers seen in the previous dispatch cycle for dead-worker detection
    private final Set<String> knownWorkerIds = new HashSet<>();

    private volatile Future<?> listenFuture;
    private volatile ScheduledFuture<?> pollFuture;
    private volatile ScheduledFuture<?> recoverFuture;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile ScheduledFuture<?> orphanRecoverFuture;

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
        try {
            listenerContainer.addMessageListener(this, new ChannelTopic(TASK_ASSIGN_CHANNEL_PREFIX + instanceId));
            log.info("Subscribed to task assignment channel: {}", TASK_ASSIGN_CHANNEL_PREFIX + instanceId);
        }
        catch (Exception e) {
            log.error("Failed to subscribe to task assignment channel", e);
        }

        // Register master election callbacks
        electionService.onBecameMaster(this::onBecameMaster);
        electionService.onLostMaster(this::onLostMaster);

        running = true;
        submitScheduledTasks();

        log.info("Task queue manager started (master-worker mode). originalConcurrentLimit={} weight={} effectiveConcurrentLimit={} enqueueCapacity={} instanceId={}",
            originalConcurrentLimit, concurrencyWeight, concurrentLimit, enqueueCapacity, instanceId);
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
    }

    // ---- Master election callbacks ----

    private void onBecameMaster()
    {
        log.info("Became master — starting dispatch loop");
        workerLedgers.clear();
        knownWorkerIds.clear();
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
            runningTaskCount.incrementAndGet();
            EtlTable table = tableService.getTable(job.getTid());
            if (table != null) {
                VwEtlTableWithSource source = tableService.getTableView(table.getId());
                if (source != null && source.getMaxConcurrency() != null && source.getMaxConcurrency() > 0) {
                    sourceRunningTaskCount.computeIfAbsent(table.getSid(), k -> new AtomicInteger(0)).incrementAndGet();
                }
            }
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
        if (!running) return;
        if (electionService.isMaster()) {
            masterDispatch();
        }
    }

    /**
     * Master-only: read alive workers, assign pending jobs from DB queue via Redis pub/sub.
     *
     * Uses Smooth Weighted Round Robin (SWRR) to distribute tasks proportionally to worker weights.
     * Source-level concurrency is checked against worker.sourceRunning from the heartbeat
     * (up to HEARTBEAT_INTERVAL_SECONDS staleness — acceptable for this use case).
     */
    private void masterDispatch()
    {
        List<WorkerHeartbeatService.WorkerInfo> workers = heartbeatService.getAliveWorkers();
        if (workers.isEmpty()) {
            log.debug("No alive workers found, skipping dispatch");
            return;
        }

        // Detect workers that disappeared since last dispatch cycle and proactively recover their tasks,
        // avoiding waiting for DEFAULT_LEASE_SECONDS (7300s) expiry.
        Set<String> currentWorkerIds = workers.stream()
            .map(WorkerHeartbeatService.WorkerInfo::instanceId)
            .collect(Collectors.toSet());
        if (!knownWorkerIds.isEmpty()) {
            for (String deadId : new HashSet<>(knownWorkerIds)) {
                if (!currentWorkerIds.contains(deadId)) {
                    log.warn("Worker {} disappeared — recovering its claimed tasks immediately", deadId);
                    try {
                        int recovered = jobQueueService.releaseClaimedByInstance(deadId);
                        if (recovered > 0) {
                            log.info("Released {} tasks from dead worker {}", recovered, deadId);
                        }
                    }
                    catch (Exception e) {
                        log.error("Failed to release tasks from dead worker {}", deadId, e);
                    }
                }
            }
        }
        knownWorkerIds.clear();
        knownWorkerIds.addAll(currentWorkerIds);

        // Clean up SWRR state for workers that are no longer alive
        swrrCurrentWeight.keySet().retainAll(currentWorkerIds);

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

        Set<Long> consumedJobIds = new HashSet<>();
        while (true) {
            WorkerSlot selected = selectWorkerSwrr(slots);
            if (selected == null) return; // all workers have no available slots

            Optional<JobClaim> maybe = findClaimableJobForWorker(selected, pendingJobs, consumedJobIds);
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
                stringRedisTemplate.convertAndSend(channel, assignPayload);
                selected.reserve(trackedSid);
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
                try { jobQueueService.releaseClaim(job.getId(), 5); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Find the first pending job that is still feasible for the selected worker and claim it atomically.
     */
    private Optional<JobClaim> findClaimableJobForWorker(WorkerSlot selected,
                                                         List<EtlJobQueue> candidates,
                                                         Set<Long> consumedJobIds)
    {
        for (EtlJobQueue candidate : candidates) {
            long jobId = candidate.getId();
            if (consumedJobIds.contains(jobId)) {
                continue;
            }

            JobCheck check = assessJobForWorker(selected, candidate);
            if (!check.assignable()) {
                continue;
            }

            Optional<EtlJobQueue> claimed = jobQueueService.assignSpecificJobToWorker(
                jobId, selected.instanceId(), DEFAULT_LEASE_SECONDS);
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
    private JobCheck assessJobForWorker(WorkerSlot selected, EtlJobQueue job)
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
        final long jobId = job.getId();

        try {
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

            EtlTable task = tableService.getTable(job.getTid());
            if (task == null) {
                throw new IllegalStateException("Task not found tid=" + job.getTid());
            }
            taskResultDto = executeEtlTaskWithConcurrencyControl(task, job.getBizDate());
            boolean killedByUser = executionManager.consumeKillRequested(job.getTid());
            if (killedByUser) {
                jobQueueService.completeCancelled(job.getId(), "Killed by user request");
                log.info("Task killed by user, marked queue job {} as cancelled", job.getId());
            }
            else if (taskResultDto.success()) {
                jobQueueService.completeSuccess(job.getId());
            }
            else {
                Duration backoff = computeBackoff(job.getAttempts());
                jobQueueService.failOrReschedule(job, "Addax non-zero exit", backoff);
            }
        }
        catch (Exception e) {
            log.error("Task execution failed jobId={} tid={}", job.getId(), job.getTid(), e);
            boolean killedByUser = executionManager.consumeKillRequested(job.getTid());
            if (killedByUser) {
                try {
                    jobQueueService.completeCancelled(job.getId(), "Killed by user request");
                    log.info("Task killed by user during exception path, marked queue job {} as cancelled", job.getId());
                }
                catch (Exception ignored) {
                }
            }
            else {
                Duration backoff = computeBackoff(job.getAttempts());
                try { jobQueueService.failOrReschedule(job, e.getMessage(), backoff); } catch (Exception ignored) {}
            }
        }
        finally {
            if (renewer != null) {
                try { renewer.cancel(false); } catch (Exception ignored) {}
            }

            int afterGlobal = runningTaskCount.decrementAndGet();

            EtlTable table = tableService.getTable(job.getTid());
            if (table != null) {
                VwEtlTableWithSource source = tableService.getTableView(table.getId());
                if (source != null && source.getMaxConcurrency() != null && source.getMaxConcurrency() > 0) {
                    sourceRunningTaskCount.computeIfPresent(table.getSid(), (k, v) -> {
                        int after = v.decrementAndGet();
                        log.debug("Source {} concurrency reduced to {} (max={})", k, after, source.getMaxConcurrency());
                        return v;
                    });
                }
            }

            log.debug("jobId={} done, elapsed={}s, running={}, pending={}",
                job.getId(), (System.currentTimeMillis() - start) / 1000, afterGlobal, jobQueueService.countPending());

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
                alertService.sendToWeComRobot(String.format("采集任务 %s.%s(%d) 执行失败: Addax 非0退出", task.getSourceDb(), task.getSourceTable(), tid));
                return TaskResultDto.failure("Failed: Addax non-zero exit", duration);
            }
        }
        catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("Task {}.{}({}) failed, elapsed={}s", task.getSourceDb(), task.getSourceTable(), tid, duration, e);
            task.setDuration(duration);
            tableService.setFailed(task);
            alertService.sendToWeComRobot(String.format("采集任务 %s.%s(%d) 执行失败: %s", task.getSourceDb(), task.getSourceTable(), tid, e.getMessage()));
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
        String job = jobContentService.getJobContent(taskId);
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
            tempFile = new File(jobsDir + task.getTargetDb() + "." + task.getTargetTable() + ".json");
            Files.writeString(tempFile.toPath(), job);
        }
        catch (IOException e) {
            log.error("Failed to write temp job file", e);
            return false;
        }
        String logName = String.format("%s.%s_%d.log", task.getTargetDb(), task.getTargetTable(), taskId);
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
        if (!running) return;
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
    }

    @Override
    public boolean addTaskToQueue(@NonNull EtlTable etlTable)
    {
        if (!running) {
            log.info("Schema refresh in progress, rejecting task {} into queue", etlTable.getId());
            return false;
        }
        LocalDate bizDate = LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        return jobQueueService.enqueue(etlTable, bizDate, 100) > 0;
    }

    @Override
    public boolean addTaskToQueue(@NonNull EtlTable etlTable, String payload)
    {
        if (!running) {
            log.info("Schema refresh in progress, rejecting task {} into queue", etlTable.getId());
            return false;
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
    }

    @Override
    public void truncateQueueExceptRunningTasks()
    {
        jobQueueService.truncateQueueExceptRunningTasks();
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
