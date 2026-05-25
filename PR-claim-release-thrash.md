# PR: Reduce claim/release thrash in master dispatch

**Branch:** `feat/reduce-claim-release-thrash`  
**Commit:** `d74110d`  
**Validation:** `mvn -q -DskipTests -pl backend clean package`

## Motivation / Background

In the master-worker dispatch flow, the master previously claimed a pending job first and only then checked whether the selected worker still had enough global/source concurrency capacity.

When a job did not fit the current worker ledger, the master would immediately release it back to the queue. This created unnecessary claim/release churn and caused the same jobs to be repeatedly picked and dropped across dispatch cycles.

## What Changed

### `backend/src/main/java/com/wgzhao/addax/admin/service/EtlJobQueueService.java`

Added two new queue operations:

- `peekPendingJobs(int limit)`
  - Read-only view of eligible pending jobs.
  - Does not change queue state.

- `assignSpecificJobToWorker(long jobId, String targetInstanceId, int leaseSeconds)`
  - Atomically claims a specific pending job.
  - Keeps the actual state transition transactional and targeted.

### `backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java`

Reworked master dispatch to use a two-step flow:

1. Peek pending jobs without changing queue state.
2. Evaluate whether each job is feasible for the selected worker using the master-side ledger.
3. Only then atomically claim the specific job.

This preserves the existing:

- master-side authoritative concurrency ledger
- SWRR worker selection
- heartbeat reconciliation

while removing the main source of claim/release thrash.

## Design / Implementation Notes

- The master still owns concurrency decisions.
- Heartbeat data remains a reconciliation input, not the sole source of truth.
- The dispatch loop now filters jobs before claiming them.
- If Redis pub/sub delivery fails after claim, the job is still released as a safety fallback.

## Validation

Successfully built the backend with:

```bash
mvn -q -DskipTests -pl backend clean package
```

## Risks / Caveats / Follow-ups

- This change reduces queue thrash, but it does not completely eliminate all release paths:
  - Redis publish failure still falls back to `releaseClaim()`.
- The peek limit is currently bounded by `PENDING_JOB_PEEK_LIMIT` in `TaskQueueManagerV2Impl`.
  - If the queue becomes very large or worker selection patterns change, this limit may need tuning.
- This branch intentionally stays focused on reducing dispatch churn and does not change unrelated scheduling behavior.
