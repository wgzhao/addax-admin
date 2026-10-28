package com.wgzhao.addax.admin.service.impl;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.exception.TargetContractViolationException;
import com.wgzhao.addax.admin.model.EtlExecution;
import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.redis.WorkerHeartbeatService;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.service.AlertService;
import com.wgzhao.addax.admin.service.CalendarService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.EtlJobQueueService;
import com.wgzhao.addax.admin.service.EtlExecutionService;
import com.wgzhao.addax.admin.service.EtlJourService;
import com.wgzhao.addax.admin.service.ExecutionManager;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TargetContractService;
import com.wgzhao.addax.admin.service.TargetService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import com.wgzhao.addax.admin.service.UserNotificationService;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import com.wgzhao.addax.admin.utils.ProcessGuard;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicReference;
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
    // The lease is the ownership fence: a running row may only be re-dispatched after its lease
    // expires, and the owner renews it every RENEW_INTERVAL_SECONDS. Keeping the lease short bounds
    // crash recovery; keeping it longer than the renewal interval tolerates brief DB/GC stalls.
    private static final int DEFAULT_LEASE_SECONDS = 120;
    // Provisional lease granted at claim time, before delivery is confirmed. A job whose assignment
    // message is lost (master crash between claim and publish, dead subscription, reconnect gap) is
    // recovered by the lease sweep instead of stranding. The worker extends it to DEFAULT_LEASE_SECONDS
    // at execution start; the gap between the two values is what lets releaseClaim() distinguish an
    // undelivered claim from one the worker already confirmed.
    private static final int CLAIM_LEASE_SECONDS = 60;
    private static final int RENEW_INTERVAL_SECONDS = 20;
    // Conservative fencing: if ownership cannot be confirmed for three consecutive renewals
    // (database down, network partition), kill the local addax process. This happens well before
    // the lease expires, so no successor can start while the old process is still writing.
    private static final int RENEW_FAILURE_TOLERANCE = 3;
    private static final int LEASE_SWEEP_INTERVAL_SECONDS = 10;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;
    private static final int PENDING_JOB_PEEK_LIMIT = 200;
    // Redis channel for task assignment: master → worker
    private static final String TASK_ASSIGN_CHANNEL_PREFIX = "addax:task:assign:";

    private final DictService dictService;
    private final AddaxLogService addaxLogService;
    private final AlertService alertService;
    private final TableService tableService;
    private final EtlExecutionService executionService;
    private final EtlJourService jourService;
    private final SystemConfigService configService;
    private final JobContentService jobContentService;
    private final TargetService targetService;
    private final TargetContractService targetContractService;
    private final EtlJobQueueService jobQueueService;
    private final JdbcTemplate jdbcTemplate;
    private final ExecutionManager executionManager;
    private final UserNotificationService userNotificationService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MasterElectionService electionService;
    private final WorkerHeartbeatService heartbeatService;
    private final RedisMessageListenerContainer listenerContainer;
    private final CalendarService calendarService;

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
    // Lease renewals run on their own pool: a busy dispatch pool must never delay a renew past the
    // lease TTL, which would fence a healthy run and let another worker duplicate it.
    private final ScheduledExecutorService leaseScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "etl-lease");
        t.setDaemon(true);
        return t;
    });

    private volatile Future<?> listenFuture;
    private volatile ScheduledFuture<?> pollFuture;
    private volatile ScheduledFuture<?> recoverFuture;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile ScheduledFuture<?> subscriptionRetryFuture;

    @PostConstruct
    public void init()
    {
        validateLeaseTimings();
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
        // The election thread starts before this bean is initialized (initial delay 0), so a tick
        // can win master before the callback is registered. Recovery itself is lease-based and does
        // not depend on the callback, but the in-memory dispatch bookkeeping still must be reset.
        if (electionService.isMaster()) {
            onBecameMaster();
        }

        // Heartbeat files from a previous JVM can only belong to wrappers whose child must die.
        ProcessGuard.cleanupLeaseFiles();

        running = true;
        submitScheduledTasks();

        log.info("Task queue manager started (master-worker mode). originalConcurrentLimit={} weight={} effectiveConcurrentLimit={} enqueueCapacity={} instanceId={}",
            originalConcurrentLimit, concurrencyWeight, concurrentLimit, enqueueCapacity, instanceId);
    }

    /**
     * The duplicate-prevention guarantee depends on these relations. They live in two classes
     * (the scheduler and the wrapper), so a tuning change must fail at startup instead of silently
     * opening a window where a successor can claim a row before the old process is dead.
     */
    private static void validateLeaseTimings()
    {
        int releaseGuard = DEFAULT_LEASE_SECONDS - 30;
        if (CLAIM_LEASE_SECONDS >= DEFAULT_LEASE_SECONDS || releaseGuard <= CLAIM_LEASE_SECONDS) {
            throw new IllegalStateException("Invalid lease policy: a provisional claim must be "
                + "distinguishable from a confirmed one (claim=" + CLAIM_LEASE_SECONDS
                + "s, confirmed=" + DEFAULT_LEASE_SECONDS + "s, releaseGuard=" + releaseGuard + "s)");
        }
        if (RENEW_INTERVAL_SECONDS * RENEW_FAILURE_TOLERANCE >= ProcessGuard.LEASE_FRESHNESS_SECONDS) {
            throw new IllegalStateException("Invalid lease policy: Java fence ("
                + (RENEW_INTERVAL_SECONDS * RENEW_FAILURE_TOLERANCE) + "s) must fire before the wrapper "
                + "kills a stale run (" + ProcessGuard.LEASE_FRESHNESS_SECONDS + "s)");
        }
        if (ProcessGuard.LEASE_FRESHNESS_SECONDS >= DEFAULT_LEASE_SECONDS) {
            throw new IllegalStateException("Invalid lease policy: the wrapper stale threshold ("
                + ProcessGuard.LEASE_FRESHNESS_SECONDS + "s) must be below the lease ("
                + DEFAULT_LEASE_SECONDS + "s)");
        }
        log.info("Lease policy: claim={}s confirmed={}s renew={}s fenceAfter={}s wrapperStale={}s sweep={}s",
            CLAIM_LEASE_SECONDS, DEFAULT_LEASE_SECONDS, RENEW_INTERVAL_SECONDS,
            RENEW_INTERVAL_SECONDS * RENEW_FAILURE_TOLERANCE, ProcessGuard.LEASE_FRESHNESS_SECONDS,
            LEASE_SWEEP_INTERVAL_SECONDS);
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
        recoverFuture = scheduler.scheduleWithFixedDelay(this::recoverLeases,
            LEASE_SWEEP_INTERVAL_SECONDS, LEASE_SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
        swrrCurrentWeight.clear();
        // No orphan sweep here. A row is released only when its lease expires (see recoverLeases):
        // heartbeat/startup based release cannot distinguish a dead worker from a partitioned one,
        // and releasing a live claim is exactly how the same table used to run twice.
    }

    private void onLostMaster()
    {
        log.info("Lost master role — pausing dispatch (workers continue executing current tasks)");
        swrrCurrentWeight.clear();
        workerLedgers.clear();
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
            for (EtlTable t : tasks) {
                if (enqueued >= room) { skipped++; continue; }
                try {
                    int added = jobQueueService.enqueue(t, businessDateOf(t), 100);
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

        // Ownership is never released from heartbeat absence. A heartbeat can disappear during a
        // Redis blip while the worker's addax processes keep writing; releasing that claim would
        // dispatch the same (tid, biz_date) to a second worker. Claims are recovered only when
        // their database lease expires (recoverLeases), and the lease renewer fences the old
        // process before that can happen.
        Set<String> currentWorkerIds = workers.stream()
            .map(WorkerHeartbeatService.WorkerInfo::instanceId)
            .collect(Collectors.toSet());

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
                    jobQueueService.releaseClaim(job.getId(), 2, selected.instanceId(),
                        DEFAULT_LEASE_SECONDS - 30);
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
                try { jobQueueService.releaseClaim(job.getId(), 5, selected.instanceId(),
                    DEFAULT_LEASE_SECONDS - 30); } catch (Exception ignored) {}
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
        RunLease lease = null;
        // 声明在 try 外,异常分支(notifyFinalFailure)也需要引用
        EtlTable task = null;
        // Source whose running counter was reserved at start; drives the finally decrement so a
        // deleted table or a max_concurrency change mid-run cannot leak the counter.
        Integer countedSourceSid = null;
        final long jobId = job.getId();

        try {
            // Claim confirmation: only execute while this instance still owns the row. The row may
            // have been released/reclaimed between dispatch and receipt (publish timeout, master
            // crash, lease expiry), in which case executing would duplicate a successor run.
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

            // Tie the local addax process to this row's lease. The renewer keeps the database lease
            // alive and refreshes the wrapper's heartbeat file; when ownership cannot be confirmed
            // it fences, and CommandExecutor kills the process tree before a successor can start.
            lease = new RunLease(job.getId());
            lease.start();

            taskResultDto = executeEtlTaskWithConcurrencyControl(
                task, job.getBizDate(), job.getId(), job.getAttempts(), lease);

            String fenceReason = lease.fenceReason();
            if (fenceReason != null) {
                log.warn("Job {} (tid={}) is fenced ({}); not writing table state, the row is left to lease recovery",
                    job.getId(), job.getTid(), fenceReason);
                return;
            }
            // Stop renewing before the terminal queue write: a renewal racing completeSuccess would
            // see a non-running row and fence an otherwise successful run.
            lease.finish();
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
            String fenceReason = lease == null ? null : lease.fenceReason();
            if (fenceReason != null) {
                log.warn("Job {} (tid={}) failed after its queue lease was fenced ({}); not rescheduling",
                    job.getId(), job.getTid(), fenceReason);
                return;
            }
            if (lease != null) {
                lease.finish();
            }
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
            if (lease != null) {
                lease.stop();
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

            // A fenced run no longer owns the row; its successor owns the outcome and the
            // notification. Alerting here would report a second, misleading result for one table.
            if (lease == null || lease.fenceReason() == null) {
                try { notifyJobCompletion(job, taskResultDto); } catch (Exception ignored) {}
            }

            // Trigger next dispatch cycle on master
            triggerDispatchAsync();
        }
    }

    /**
     * Ownership heartbeat for one queue row.
     *
     * <p>Renewal is what keeps the row ours. When it fails definitively (the row was released and
     * possibly reclaimed) or repeatedly (the database is unreachable), the run is fenced: it never
     * writes table state, and its addax process is destroyed before the lease can expire, so the
     * next owner of the row cannot overlap with it.</p>
     */
    private final class RunLease
    {
        private final long jobId;
        private final Path leaseFile;
        private final AtomicReference<String> fenceReason = new AtomicReference<>();
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private volatile ScheduledFuture<?> renewer;
        // Set once the process has exited and we are about to write the terminal queue state.
        // A renewal that races that write must not fence a run that just finished successfully.
        private volatile boolean done;

        RunLease(long jobId) throws IOException
        {
            this.jobId = jobId;
            this.leaseFile = ProcessGuard.newLeaseFile(jobId);
            // The wrapper must see a fresh heartbeat before it starts the child.
            ProcessGuard.touchLease(leaseFile);
        }

        void start()
        {
            renewer = leaseScheduler.scheduleAtFixedRate(this::renew,
                RENEW_INTERVAL_SECONDS, RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }

        String fenceReason()
        {
            return fenceReason.get();
        }

        Path leaseFile()
        {
            return leaseFile;
        }

        private void renew()
        {
            if (done || fenceReason.get() != null) {
                return;
            }
            try {
                if (!jobQueueService.renewLease(jobId, instanceId, DEFAULT_LEASE_SECONDS)) {
                    if (!done) {
                        fence("queue row is no longer owned by " + instanceId);
                    }
                    return;
                }
                if (done) {
                    return;
                }
                ProcessGuard.touchLease(leaseFile);
                consecutiveFailures.set(0);
            }
            catch (Exception e) {
                if (done) {
                    return;
                }
                int failures = consecutiveFailures.incrementAndGet();
                if (failures >= RENEW_FAILURE_TOLERANCE) {
                    fence("lease renewal failed " + failures + " times: " + e.getMessage());
                }
                else {
                    log.warn("Lease renewal {}/{} failed for jobId={}: {}",
                        failures, RENEW_FAILURE_TOLERANCE, jobId, e.getMessage());
                }
            }
        }

        /**
         * Final ownership check before table state is written. Successful renewal also means the
         * wrapper's heartbeat file stays fresh while the process is finishing.
         */
        boolean stillOwned()
        {
            if (fenceReason.get() != null) {
                return false;
            }
            try {
                if (!jobQueueService.renewLease(jobId, instanceId, DEFAULT_LEASE_SECONDS)) {
                    fence("queue row is no longer owned by " + instanceId);
                    return false;
                }
                ProcessGuard.touchLease(leaseFile);
                return true;
            }
            catch (Exception e) {
                fence("ownership check failed: " + e.getMessage());
                return false;
            }
        }

        /** The process finished; stop renewing while the terminal queue update is written. */
        void finish()
        {
            done = true;
            if (renewer != null) {
                renewer.cancel(false);
            }
        }

        void stop()
        {
            done = true;
            if (renewer != null) {
                renewer.cancel(false);
            }
            ProcessGuard.deleteLease(leaseFile);
        }

        private void fence(String reason)
        {
            if (fenceReason.compareAndSet(null, reason)) {
                log.warn("Fencing run of jobId={}: {}", jobId, reason);
            }
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
        return executeEtlTaskWithConcurrencyControl(task, overrideBizDate, null, 1);
    }

    private TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task, LocalDate overrideBizDate,
        Long queueId, int attemptNo)
    {
        return executeEtlTaskWithConcurrencyControl(task, overrideBizDate, queueId, attemptNo, null);
    }

    private TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task, LocalDate overrideBizDate,
        Long queueId, int attemptNo, RunLease lease)
    {
        long tid = task.getId();
        long startTime = System.currentTimeMillis();
        if (lease != null && lease.fenceReason() != null) {
            return TaskResultDto.failure("Ownership lost: " + lease.fenceReason(), 0);
        }
        try {
            tableService.setRunning(task);
            boolean result = executeEtlTaskLogic(task, overrideBizDate, queueId, attemptNo, lease);
            long duration = max((System.currentTimeMillis() - startTime) / 1000, 0);
            log.info("Task {}.{}({}) completed, elapsed={}s, result={}", task.getSourceDb(), task.getSourceTable(), tid, duration, result);
            task.setDuration(duration);
            // A run that no longer owns its queue row must not write a table state that belongs to
            // the successor run. stillOwned() also renews the lease, closing the expiry race.
            if (lease != null && !lease.stillOwned()) {
                log.warn("Task {}.{}({}) lost queue ownership before status update: {}",
                    task.getSourceDb(), task.getSourceTable(), tid, lease.fenceReason());
                return TaskResultDto.failure("Ownership lost: " + lease.fenceReason(), duration);
            }
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
            if (lease != null && !lease.stillOwned()) {
                log.warn("Task {}.{}({}) lost queue ownership during failure handling: {}",
                    task.getSourceDb(), task.getSourceTable(), tid, lease.fenceReason());
                return TaskResultDto.failure("Ownership lost: " + lease.fenceReason(), duration);
            }
            if (e instanceof TargetContractViolationException contractViolation)
            {
                task.setDuration(duration);
                tableService.setUpdateSchema(task);
                return TaskResultDto.failure(
                    "Target contract blocked: " + contractViolation.getReport().summary(), duration);
            }
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
        return executeEtlTaskLogic(task, overrideBizDate, null, 1, null);
    }

    private boolean executeEtlTaskLogic(EtlTable task, LocalDate overrideBizDate, Long queueId, int attemptNo,
        RunLease lease)
    {
        long taskId = task.getId();
        log.info("Executing task: taskId={}, destDB={}, tableName={}", taskId, task.getTargetDb(), task.getTargetTable());

        if (lease != null && lease.fenceReason() != null) {
            log.warn("Not executing tid={}: {}", taskId, lease.fenceReason());
            return false;
        }

        VwEtlTableWithSource tableView = tableService.getTableView(taskId);
        if (tableView == null) {
            log.warn("Table view not found, taskId={}", taskId);
            return false;
        }

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
        LocalDate executionBizDate = overrideBizDate != null ? overrideBizDate : systemBizDate;
        if (job == null || job.isEmpty()) {
            log.warn("Job template not generated, taskId={}", taskId);
            EtlExecution execution = executionService.start(
                tableView, null, executionBizDate, queueId, attemptNo);
            executionService.finish(execution, false, "Job template not generated");
            return false;
        }
        TargetContractReport contractReport = null;
        if (tableView.getSchemaManagementMode() == SchemaManagementMode.EXTERNAL)
        {
            contractReport = targetContractService.check(tableView);
        }
        EtlExecution execution = executionService.start(
            tableView, job, executionBizDate, queueId, attemptNo, contractReport);
        if (contractReport != null && contractReport.blocked())
        {
            executionService.finish(execution, false, contractReport.summary());
            throw new TargetContractViolationException(contractReport);
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
        try {
            boolean prepareResult = targetService.prepareBeforeRun(taskId, tableView, executionBizDate);
            if (!prepareResult) {
                executionService.finish(execution, false, "Target preparation failed");
                return false;
            }

            File tempFile;
            try {
                String curDate = DateUtil.date().toDateStr();
                // app.home falls back to the working directory so container deployments that only
                // set the working directory still get a writable job/lease home.
                String appHome = System.getProperty("app.home", System.getProperty("user.dir"));
                String jobsDir = Path.of(appHome).resolve("job").resolve(curDate) + "/";
                Files.createDirectories(Path.of(jobsDir));
                // Include bizDateStr in the file name to prevent concurrent fillback tasks
                // for the same table from overwriting each other's job files.
                tempFile = new File(jobsDir + task.getTargetDb() + "." + task.getTargetTable() + "_" + bizDateStr + ".json");
                Files.writeString(tempFile.toPath(), job);
            }
            catch (IOException e) {
                log.error("Failed to write temp job file", e);
                executionService.finish(execution, false, "Failed to write temp job file: " + e.getMessage());
                return false;
            }
            String logName = String.format("%s.%s_%d_%s.log", task.getTargetDb(), task.getTargetTable(), taskId, bizDateStr);
            String addaxScript = Path.of(dictService.getAddaxHome(), "bin", "addax.sh").toString();
            String jobName = taskId + ":" + execution.getId();
            String jvmProps = String.format("-DjobName=%s -Dlog.file.name=%s", jobName, logName);
            List<String> cmdArgs = List.of(addaxScript, "-p", jvmProps, tempFile.getAbsolutePath());
            String cmd = toCommandString(cmdArgs);
            boolean retCode = executeAddax(cmdArgs, cmd, taskId, execution.getId(), logName,
                task.getMaxRuntime() == null ? ADDAX_EXECUTE_TIME_OUT_SECONDS : task.getMaxRuntime(), lease);
            executionService.finish(execution, retCode, retCode ? null : "Addax exited with a non-zero code");
            log.debug("Task {} log written to: {}", taskId, logName);
            return retCode;
        }
        catch (RuntimeException e) {
            executionService.finish(execution, false, e.getMessage());
            throw e;
        }
    }

    private boolean executeAddax(List<String> commandArgs, String commandForLog, long tid, long executionId,
        String logName, long maxRuntimeSeconds, RunLease lease)
    {
        EtlJour etlJour = jourService.addJour(tid, executionId, JourKind.COLLECT, commandForLog);
        Process process = null;
        TaskResultDto taskResult;
        long pid = -1;
        try {
            if (lease != null && lease.fenceReason() != null) {
                log.warn("Not starting addax for tid={}: {}", tid, lease.fenceReason());
                taskResult = TaskResultDto.failure("Ownership lost: " + lease.fenceReason(), 0);
            }
            else {
                // The wrapper ties addax to this JVM and to the lease heartbeat file, so a crash
                // or a hung/fenced owner cannot leave a second writer behind.
                process = ProcessGuard.start(commandArgs, lease == null ? null : lease.leaseFile());
                try { pid = process.pid(); } catch (UnsupportedOperationException ignored) {}
                try {
                    executionManager.register(tid, executionId, process, pid, instanceId);
                }
                catch (Exception e) {
                    log.warn("Failed to register process in ExecutionManager tid={} pid={}", tid, pid, e);
                }
                taskResult = CommandExecutor.waitForProcessWithResult(process, maxRuntimeSeconds, commandForLog,
                    lease == null ? null : lease::fenceReason);
            }
        }
        catch (IOException ioe) {
            log.error("Failed to start process for command: {}", commandForLog, ioe);
            taskResult = TaskResultDto.failure(ioe.getMessage(), 0);
        }
        finally {
            if (process != null) {
                try { executionManager.unregister(tid, executionId); } catch (Exception ignored) {}
            }
        }
        Path path = Path.of(dictService.getAddaxHome() + "/log/" + logName);
        String logContent = null;
        try {
            // Decode leniently instead of Files.readString: a torn multibyte char at EOF
            // (e.g. addax killed mid-write) would otherwise discard the whole log as null.
            // NUL bytes (invalid in PG text) are stripped later in AddaxLogService.insertLog.
            logContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read Addax log: {}", path, e);
        }
        addaxLogService.insertLog(tid, executionId, logContent);
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
        leaseScheduler.shutdownNow();
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
        if (etlTable.getRetiredAt() != null) {
            return false;
        }
        if (!running) {
            return deferEnqueue(etlTable, null, null);
        }
        return jobQueueService.enqueue(etlTable, businessDateOf(etlTable), 100) > 0;
    }

    /**
     * Business date a run of this table belongs to, derived from the calendar its source is configured
     * with. The global business date cannot be used: a workday or trading-day source must be stamped with
     * a date from its own calendar, and on the morning after a holiday the two differ. Fillback is the one
     * caller that passes an explicit date, because it is collecting a date that is not the current one.
     */
    private LocalDate businessDateOf(EtlTable table)
    {
        return calendarService.businessDate(table.getSourceCalendar());
    }

    @Override
    public boolean addTaskToQueue(@NonNull EtlTable etlTable, String payload)
    {
        if (etlTable.getRetiredAt() != null) {
            return false;
        }
        if (!running) {
            return deferEnqueue(etlTable, null, payload);
        }
        return jobQueueService.enqueue(etlTable, businessDateOf(etlTable), 100, payload) > 0;
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
        if (etlTable.getRetiredAt() != null) {
            return false;
        }
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
                    : businessDateOf(d.table());
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
