package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.redis.WorkerHeartbeatService.WorkerInfo;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Master-side capacity accounting for one worker.
 *
 * <p>The heartbeat reports what the worker had running 0–15 seconds ago. Assignments made by the
 * master in the current cycle are not visible there yet, so the ledger keeps them as reservations
 * and reconciles them away once a heartbeat timestamp passes their reservation time. Without this,
 * two dispatch cycles inside one heartbeat interval would hand the same free slot to two jobs.</p>
 *
 * <p>All methods are synchronized on the ledger: the master mutates it from the dispatch thread and
 * reads it from assignment bookkeeping. Keeping the reservation deque and the two counters under one
 * lock is what makes {@link #reserve} and {@link #reconcile} atomic with respect to each other.</p>
 */
final class WorkerCapacityLedger
{
    private WorkerInfo heartbeat;
    private Instant heartbeatSeenAt;
    private int reservedRunning;
    private final Map<Integer, Integer> reservedSourceRunning = new HashMap<>();
    private final Deque<Reservation> reservations = new ArrayDeque<>();

    WorkerCapacityLedger(WorkerInfo heartbeat)
    {
        this.heartbeat = heartbeat;
        this.heartbeatSeenAt = heartbeat.lastSeen();
    }

    synchronized void reconcile(WorkerInfo info)
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

/**
 * One dispatch cycle's view of a worker: the ledger plus a slot budget that shrinks as jobs are
 * assigned in this cycle.
 */
final class WorkerSlot
{
    final WorkerCapacityLedger ledger;
    int slots;

    WorkerSlot(WorkerCapacityLedger ledger)
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

record Reservation(Integer sid, Instant reservedAt)
{
}
