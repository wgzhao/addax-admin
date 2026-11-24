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
  - the execution started less than 24 hours ago. A captured token must not revive an old attempt
    whose real report never arrived;
  - this is the first accepted report for that execution. The claim is a conditional update of
    `etl_execution.report_received_at` (`WHERE report_received_at IS NULL`) executed in the same
    transaction as the statistics and their side effects. A rollback releases the claim, so a
    transient failure is retryable; a commit blocks replays permanently, with no TTL to outlive.
- The token and the receipt timestamp are annotated `@JsonIgnore` on the entity:
  `GET /tables/{id}/executions` is readable by every authenticated role and must not hand out report
  credentials or internal receipt state.
- A tokenless execution (created before this change) is accepted only while its queue row is still
  `running`. This is the compatibility window for an Addax process launched by a node that has not
  been upgraded yet; new attempts never take this path.
- Smoke checks use the reserved negative tids and have no execution row. The caller registers a
  one-shot token in Redis (`-tid:token`, ten-minute TTL) before launching Addax; the report endpoint
  consumes it. Reports for negative tids without a registered token are rejected. The target
  health-check smoke does not revoke its token: process exit does not prove the HTTP report has been
  handled, the check does not read the report, and the TTL prevents the token from lingering.

## Consequences

- Reports without an execution id are rejected. Every in-repo launcher passes one; an external
  caller that relied on the old `tid:executionId` form keeps working only through the queue-row
  compatibility window.
- The token is visible in the Addax process command line on the worker host. That is the same trust
  boundary as the process itself; a host-local attacker can already read the job file and the
  credentials in it.
- The startup migration adds both columns idempotently; existing executions keep a null token and a
  null receipt and are governed by the queue-row check.
- Reports are handled in one transaction. If persistence or a side effect throws, the HTTP call
  fails and the durable claim rolls back; a retry is treated as a fresh report rather than a
  duplicate. Addax itself does not retry, so the retry path is for an operator or a future reporter.
- The 24-hour window is anchored on `started_at`, not on the business date, so fillbacks and long
  delayed queues are unaffected. A table whose `max_runtime` exceeds 24 hours would reject its own
  report; that is accepted as a pathological configuration.
- `GET /log/addax/{tid}` is moved to `requireTableOperate` in the same change: raw log summaries are
  operational reads, matching the log list filter and `GET /log/addax/{id}/content` (ADR-0008).
