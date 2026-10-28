# At most one addax process per queue row

A collection run is an external addax process started by the admin JVM. The queue row is the only
ownership record shared by the cluster, so a process that outlives its owner is invisible to the
scheduler: after a crash the row is recovered and re-dispatched while the old process may still be
writing the same table. Production has seen this produce duplicate and interleaved output. The
database row alone cannot prevent it; the process itself has to be tied to ownership.

## Decision

Ownership is a **lease**, and the lease is the only authority that may release a running row.

- Claims carry a 60s provisional lease. The worker confirms execution by extending it to 120s. Only
  the provisional lease is releasable by a master whose publish failed: a claim whose message was
  actually delivered and confirmed must keep its row. An undelivered claim is released with its
  attempt counter rolled back, because it never executed.
- The owner renews every 20s on a dedicated scheduler. If renewal fails definitively (row released
  or reclaimed) or three consecutive renewals fail (database unreachable, partition), the run is
  **fenced**: it never writes table state, and its addax process tree is destroyed.
- A running row is recovered only when `lease_until` expires (swept every 10s by the master).
  Heartbeat absence and startup orphan sweeps are not release paths: they cannot distinguish a dead
  worker from a partitioned one, and releasing a live claim is how the duplicate starts. This also
  removes the startup race where a one-shot orphan sweep was never scheduled because the election
  callback was registered after the node had already become master; the periodic sweep is race-free.
- Every addax process runs under a wrapper that kills its process group when the admin JVM
  disappears **or** when a per-run heartbeat file stops advancing. The file is refreshed only after
  a successful lease renewal. A crashed, hung, or partitioned JVM therefore stops writing before its
  lease can expire and before any successor can claim the row.

## Considered Options

- Persisting the child pid and reaping it on restart only works while the old host comes back and
  the reaper runs; a hung JVM or a host that never restarts leaves the orphan alive.
- Releasing claims on missing heartbeats is fast, but a Redis blip or network partition then hands
  a live run's claim to another worker.
- A long initial lease avoided expiry during long runs but made crash recovery take hours.

## Consequences

Crash recovery is bounded by the lease (about two minutes worst case), not by the table's runtime;
long collections stay alive through renewal. The scheduler performs one small database update per
running job every 20s. A graceful shutdown kills local addax processes instead of leaving them for
the next startup. Fencing prefers killing a run over risking a concurrent writer: a transient but
prolonged database outage now aborts the run and lets the queue retry it. Deployments that share one
`app.home` between two live admin JVMs are not supported, because startup removes stale lease
heartbeat files. When upgrading from a build without this protocol, stop the old service first and
expire any `running` rows' leases; rows left with the old 7300s lease otherwise wait for that lease
to expire.
