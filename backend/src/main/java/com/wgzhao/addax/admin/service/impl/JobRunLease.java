package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.service.EtlJobQueueService;
import com.wgzhao.addax.admin.utils.ProcessGuard;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ownership heartbeat for one queue row.
 *
 * <p>Renewal is what keeps the row ours. When it fails definitively (the row was released and
 * possibly reclaimed) or repeatedly (the database is unreachable), the run is fenced: it never
 * writes table state, and its addax process is destroyed before the lease can expire, so the
 * next owner of the row cannot overlap with it.</p>
 */
@Slf4j
final class JobRunLease
{
    /** Timing is owned by the queue manager so its startup validation and the lease agree. */
    record Settings(int leaseSeconds, int renewIntervalSeconds, int failureTolerance)
    {
    }

    private final long jobId;
    private final String ownerInstanceId;
    private final EtlJobQueueService queue;
    private final ScheduledExecutorService scheduler;
    private final Settings settings;
    private final Path leaseFile;
    private final AtomicReference<String> fenceReason = new AtomicReference<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile ScheduledFuture<?> renewer;
    // Set once the process has exited and we are about to write the terminal queue state.
    // A renewal that races that write must not fence a run that just finished successfully.
    private volatile boolean done;

    JobRunLease(long jobId, String ownerInstanceId, EtlJobQueueService queue,
        ScheduledExecutorService scheduler, Settings settings) throws IOException
    {
        this.jobId = jobId;
        this.ownerInstanceId = ownerInstanceId;
        this.queue = queue;
        this.scheduler = scheduler;
        this.settings = settings;
        this.leaseFile = ProcessGuard.newLeaseFile(jobId);
        // The wrapper must see a fresh heartbeat before it starts the child.
        ProcessGuard.touchLease(leaseFile);
    }

    void start()
    {
        renewer = scheduler.scheduleAtFixedRate(this::renew,
            settings.renewIntervalSeconds(), settings.renewIntervalSeconds(), TimeUnit.SECONDS);
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
            if (!queue.renewLease(jobId, ownerInstanceId, settings.leaseSeconds())) {
                if (!done) {
                    fence("queue row is no longer owned by " + ownerInstanceId);
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
            if (failures >= settings.failureTolerance()) {
                fence("lease renewal failed " + failures + " times: " + e.getMessage());
            }
            else {
                log.warn("Lease renewal {}/{} failed for jobId={}: {}",
                    failures, settings.failureTolerance(), jobId, e.getMessage());
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
            if (!queue.renewLease(jobId, ownerInstanceId, settings.leaseSeconds())) {
                fence("queue row is no longer owned by " + ownerInstanceId);
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
