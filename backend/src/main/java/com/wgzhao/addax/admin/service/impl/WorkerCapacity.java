package com.wgzhao.addax.admin.service.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This worker's in-memory capacity counters.
 *
 * <p>Reservation and release live together so the invariant "a count is released only if the same
 * run took it" is expressed by a token instead of two booleans in the execution method. The earlier
 * negative-counter bug came from early exits that reached the decrement without an increment; a
 * token cannot be released twice because the caller has one token per reserved run.</p>
 *
 * <p>The raw counter objects are exposed for the heartbeat, which publishes them every 15 seconds.
 * Per-source counters are only taken when the source has a concurrency limit, mirroring the
 * dispatch gate.</p>
 */
final class WorkerCapacity
{
    private final AtomicInteger running = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, AtomicInteger> sourceRunning = new ConcurrentHashMap<>();

    AtomicInteger runningCounter()
    {
        return running;
    }

    ConcurrentHashMap<Integer, AtomicInteger> sourceRunningCounters()
    {
        return sourceRunning;
    }

    int running()
    {
        return running.get();
    }

    /** Take one global slot. The caller must pass the token to {@link #release(Token)}. */
    Token reserve()
    {
        running.incrementAndGet();
        return new Token();
    }

    /** Take one per-source slot as well, for a source that declares a concurrency limit. */
    void countSource(Token token, int sid)
    {
        sourceRunning.computeIfAbsent(sid, k -> new AtomicInteger(0)).incrementAndGet();
        token.sourceSid = sid;
    }

    /**
     * Release everything the token took and return the remaining global count.
     */
    int release(Token token)
    {
        int afterGlobal = running.decrementAndGet();
        if (token.sourceSid != null) {
            sourceRunning.computeIfPresent(token.sourceSid, (k, v) -> {
                v.decrementAndGet();
                return v.get() <= 0 ? null : v;
            });
        }
        return afterGlobal;
    }

    static final class Token
    {
        private Integer sourceSid;
    }
}
