# Authenticate addax job reports with a per-attempt token

`POST /log/job-report` is anonymous because Addax posts to it directly, and the only thing the old
controller checked was that the report named an existing table and (optionally) an execution of that
table. A caller who knows or guesses a `tid` could therefore write statistics for any table. The
damage is broader than the row itself: `etl_statistic` is upserted by `(tid, biz_date)`, so a forged
report overwrites an already-recorded result, and the report path also triggers reconciliation, row
count drift checks and the collection-complete signal.

ADR-0008 recorded a shared secret or a network restriction as a deployment follow-up. A network
restriction fails when workers and the backend sit on different hosts, and a single shared secret
would be one global credential with no per-attempt or replay story. We use a per-attempt token
carried in the `jobName` that the backend already passes to the child JVM.

## Decision

- `etl_execution.report_token` is generated when the execution starts. The worker passes
  `jobName=tid:executionId:token` to Addax; Addax echoes the whole string in its report.
- A production report is accepted only when all of these hold:
  - an execution id is present and belongs to the reported `tid`;
  - the token matches the execution's token in constant time;
  - this is the first accepted report for that execution. The claim is a Redis
    `SET NX` key with a seven-day TTL, which blocks replaying a captured token against historical
    statistics.
- The token is annotated `@JsonIgnore` on the entity: `GET /tables/{id}/executions` is readable by
  every authenticated role and must not hand out report credentials.
- A tokenless execution (created before this change) is accepted only while its queue row is still
  `running`. This is the compatibility window for an Addax process launched by a node that has not
  been upgraded yet; new attempts never take this path.
- Smoke checks use the reserved negative tids and have no execution row. The caller registers a
  one-shot token in Redis (`-tid:token`, ten-minute TTL) before launching Addax; the report endpoint
  consumes it. Reports for negative tids without a registered token are rejected.
- A Redis failure does not reject a token-verified production report; the token still proves
  authorship, only replay protection is degraded. A Redis failure does reject a smoke report,
  because there is nothing else to verify.

## Consequences

- Reports without an execution id are rejected. Every in-repo launcher passes one; an external
  caller that relied on the old `tid:executionId` form keeps working only through the queue-row
  compatibility window.
- The token is visible in the Addax process command line on the worker host. That is the same trust
  boundary as the process itself; a host-local attacker can already read the job file and the
  credentials in it.
- The startup migration adds the column idempotently; existing executions keep a null token and are
  governed by the queue-row check.
- `GET /log/addax/{tid}` is moved to `requireTableOperate` in the same change: raw log summaries are
  operational reads, matching the log list filter and `GET /log/addax/{id}/content` (ADR-0008).
