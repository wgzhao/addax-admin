# Alerting channels, routing and a transactional delivery outbox

The product only alerted through a single WeCom robot configured by
`alert.wechat.url` / `WECOM_ROBOT_KEY`. Changing the destination required an
environment edit and a restart, there was no test send or delivery log, only
collection failure/recovery produced alerts, and a source-wide failure produced
one webhook call per table. Enterprise deployments need configurable channels,
rule-based routing, delivery evidence and a bounded retry policy without a
synchronous webhook standing between a collection and its next attempt.

This ADR records the design for phase 1 of the alerting subsystem and the
decisions that shape later phases.

## Event and delivery are separate facts

An alert is recorded before any attempt to deliver it:

```
COLLECT_FAILURE / COLLECT_RECOVERY / SECURITY / SYSTEM
        |
        v
   alert_event (one row per condition, partial unique index on open keys)
        |
        v
   alert_rule matching (event type + scope)
        |
        v
   alert_delivery (one row per event x channel, the transactional outbox)
        |
        v
   AlertDeliveryWorker (SELECT ... FOR UPDATE SKIP LOCKED + lease)
        |
        +--> WeCom / DingTalk / Feishu / Email (SMTP) / Webhook
```

`alert_event` and `alert_delivery` are written in the same transaction as the
publisher call. Delivery happens asynchronously, so a broken vendor endpoint
cannot block a scheduler or a collection. Every channel has an independent
status, attempt count and response, so one failing channel does not mask a
successful one.

The partial unique index `(event_key) WHERE status IN ('FIRING','SILENCED')`
guarantees one open event per condition. A repeated failure while the event is
open increments `fire_count` and `last_seen` instead of creating another delivery
row. The dispatcher also takes a PostgreSQL advisory transaction lock on the
event key, so two nodes cannot both observe "no open event" and race into
duplicate deliveries.

## The database is the only configuration source

`alert.wechat.url`, `alert.wechat.key` and `WECOM_ROBOT_KEY` are removed. There
is no startup import of an environment robot key and no silent fallback. An
upgrade with alerting enabled but no configured channel keeps recording events
and writes a `SKIPPED_NO_CHANNEL` delivery; the configuration page shows
"尚未配置可用的告警通道，告警已暂停".

This is deliberate. A compatibility path that reads an environment variable only
when the database is empty would make "why did this alert go there?" depend on
the deployment history. Alert destinations are configuration, and configuration
has one owner: the database and its audited API.

## Routing semantics

A rule matches on event type and scope:

| Rule scope | Matches |
| --- | --- |
| `ALL` | every event of the type |
| `SOURCE` | events whose payload `sourceDb` equals the rule value |
| `TABLE` | events whose collection id equals the rule value, or whose `sourceDb.sourceTable` / `sourceTable` matches |

`SECURITY`, `SYSTEM`, `SCHEDULE` and `DAILY` events carry no source context, so
only `ALL`-scope rules can match them; the API rejects other scopes for those
types. Recovery events do not consult rules at all — their audience is fixed by
the failure's deliveries.

Matching rules select their channel lists; the effective severity is the most
severe level among matching rules. **When no enabled rule matches, the event is
sent to every enabled channel.** Rules are a narrowing mechanism, not a second
enable switch. Without the default route, a new installation would have to
discover and create a rule before its first channel did anything, and an
operator would see a configured channel that silently receives nothing.

`DAILY` is the single exception: when no enabled rule of that type exists, the
event is still recorded but every delivery is `SKIPPED_NO_CHANNEL` with the
reason "信息类事件未订阅". A once-a-day informational message must not be
broadcast to every channel by the default route. Failure types keep the default
route, because a missed failure is worse than an unexpected recipient.

Recovery events go only to the channels that the original failure targeted
(`PENDING`, `SENDING`, `SENT`, `FAILED`, `DEAD` deliveries), never to a newly
added channel. A recovery message must not introduce an audience that never saw
the failure.

`alert_rule.channel_ids` is JSONB in phase 1. Deleting a channel removes its id
from every rule and every escalation list. A future phase may split this into an
`alert_rule_channel` table if per-channel rule settings appear.

## Scheduler and daily-summary events

The day cut (flag reset, configuration reload, per-table schema refresh, queue
cleanup) is the operation every collection depends on, and it used to fail
silently: `updateParams` caught every exception and timeout, returned void, so
the scheduler reported success, its retry branch was dead code, and the only
evidence was a log line. It now returns a `DayCutOutcome` (stage, duration,
timeout, reset count, refresh counts and per-table failures, queue-monitor state,
completion marker) and writes:

- `SCHEDULE:DAYCUT:<bizDate>` (P1, FIRING) when a stage failed or timed out. A
  later fully successful run resolves it; the retry (one attempt, five minutes)
  increments `fire_count` instead of creating a second event.
- `SCHEDULE:DAYCUT_TABLES:<bizDate>` (P2, FIRING) when the run completed but
  some tables could not be refreshed, or tables remain in `U`. Partial table
  failure counts as a successful day cut: the completion marker is written and
  there is no automatic re-run (repeated DDL is worse than a human deciding).
  These tables do not collect until someone fixes them and refreshes.
- An audit record (`DAILY_REFRESH_COMPLETED`) for every completed run, including
  the all-good case. A clean run with nothing open sends no message. A clean run
  that closes an earlier `SCHEDULE:DAYCUT` failure also publishes
  `SCHEDULE:DAYCUT_RECOVERY:<bizDate>` (P2, recorded as a RESOLVED fact) so the
  operators who saw the failure learn that the retry succeeded. That recovery is
  delivered only to the channels that received the failure delivery — never
  through rule routing, so it cannot introduce a new audience.

`U` (waiting for schema update) tables are part of the refresh set — `'X'` and
disabled sources are the only exclusions — and a successful refresh moves them
back to `N` when the business period owes them a collection. The day-cut report
carries the `U → N` count and the number still waiting.

`DAILY:BATCH:<bizDate>` (P3, recorded as a resolved point-in-time fact) is
produced once per business date, whenever the automatic batch reaches a terminal
state: no active automatic queue row for that date, no due table in
`N/W/R`, and no failed table still holding retry budget. Tables in `U` are
terminal but counted as failures. The summary distinguishes all-success,
finished-with-failures, and a day with no due tables. If the period closes
before it ever becomes terminal, the day cut reports it before resetting flags,
because the statuses that describe it are about to be overwritten. A Redis
marker per business date keeps the message unique across master failover.

## Phase 1 scope

Included:

- `alert_channel`, `alert_rule`, `alert_event`, `alert_delivery`,
  `alert_silence`.
- WeCom, DingTalk, Feishu, Email (SMTP) and generic Webhook providers.
- Channel CRUD with encrypted configuration, test send, enable-after-test.
- Rule CRUD for the four wired event types.
- DB-outbox worker with lease recovery, retry backoff
  (1m / 5m / 15m / 1h / 6h, five attempts), per-channel Redis rate limiting and
  dead-letter status.
- Delivery log with manual retry; silence windows.
- `AlertService.reportCollectionFailure`, `reportCollectionSuccess`,
  `sendSecurityAlert` and `sendToWeComRobot` keep their signatures and delegate
  to the dispatcher. Call sites are unchanged.
- Audit records for channel/rule/silence changes, test sends and manual retries.

Explicitly deferred:

- SMS provider. SMS will be API-only; vendor SDKs are not introduced.
- Aggregation windows and cooldown enforcement. The columns exist on
  `alert_rule`, but phase 1 only prevents duplicate deliveries for the same open
  event. Per-table storm aggregation is phase 2.
- Acknowledgment and escalation. `ack_required`, `escalate_after_secs` and
  `escalation_channel_ids` are persisted but not consumed.
- Missing partition, SLA breach, queue backlog, node offline and schema drift
  publishers. Scheduler liveness and queue backlog will reuse `SCHEDULE`.
- Per-user subscriptions. Routing is scope-based.

## Channel configuration and secrets

`config_ciphertext` is an AES-256-GCM envelope (`v1:iv:ct`) of the channel's
configuration JSON. The key comes from `ALERT_SECRET_KEY`. When that variable is
absent the key is derived from `JWT_SECRET` and a warning is logged, so an
upgrade does not break the feature; production deployments are expected to set
the dedicated key. The `v1` prefix leaves room for rotation.

The API never returns a stored credential. Sensitive values are masked as
`******`; on write, a field that still contains the mask keeps its stored value.
This lets an edit form change non-secret fields without the browser ever seeing
the webhook URL, signature secret, SMTP password or HMAC key. Logs never contain
the configuration.

The masker treats `webhookUrl`, `url`, `secret`, `password`, `token`,
`accessToken`, `hmacSecret`, `apiKey`, `key`, `authorization` and sensitive
HTTP header values as credentials.

## Test send gate

A channel cannot be enabled until a test send against the current configuration
succeeds. Editing a configuration clears the test result. Test sends are
synchronous, are returned to the caller with status code/latency/response, and
are **not** written to `alert_delivery`; they are a configuration check, not an
alert fact. Test send results are audited.

The separate `POST /alert-events/test` endpoint publishes a real event through
the router and outbox, so it exercises rules and deliveries end to end. It
accepts an optional `sourceDb` / `sourceTable` / `tid` context so SOURCE and
TABLE scopes can be tested without waiting for a real collection failure. These
events carry a unique key and are recorded as resolved point-in-time facts.

## Email (SMTP)

Email is a channel type, not a second alerting path: an `EMAIL` channel is routed,
recorded in `alert_delivery` and retried by the same worker as every other
channel. Its configuration is per channel (`host`, `port`, `security`,
`username`, `password`, `from`, `fromName`, `to`, `cc`, `subjectPrefix`,
`timeoutMs`, `allowPrivateNetwork`), so the deployment does not need a global
`spring.mail.host` and one channel's credentials cannot leak into another.

The provider builds a `JavaMailSenderImpl` from the decrypted configuration on
every delivery. Authentication is optional: both username and password must be
present or both absent. `security` is `NONE`, `STARTTLS` or `SSL`; STARTTLS is
required (not opportunistic) when selected, and TLS certificate hostname
verification is enabled. Connection, read and write timeouts are bounded to
1-30 seconds. An internal relay with a self-signed certificate must be trusted
through the JVM truststore; there is deliberately no "skip verification" switch.

Recipients are parsed with Jakarta Mail's strict `InternetAddress` parser,
deduplicated case-insensitively and capped at 50 per field; CR/LF is rejected in
every address and in the subject prefix, so a display name cannot inject extra
headers. Messages are `multipart/alternative` (plain text and HTML) and carry
`X-Addax-Event-Key`, `X-Addax-Event-Type` and `X-Addax-Severity` headers for
mail-side filtering. The SMTP password is encrypted at rest and masked by the
same `******` rule as other credentials.

## SSRF

WeCom, DingTalk and Feishu webhook URLs are pinned to the vendor host
(`qyapi.weixin.qq.com`, `oapi.dingtalk.com`, `open.feishu.cn`) over HTTPS.
Generic webhooks may use HTTP or HTTPS but reject loopback, private, link-local,
CGNAT and reserved addresses unless the channel explicitly enables
"allowPrivateNetwork". SMTP hosts follow the same rule, because a corporate mail
relay commonly resolves to a private address. Hosts are re-resolved immediately
before each request to close the DNS-rebinding window between save and send.
Enabling private-network access is an audited channel change.

## Multi-node behaviour

The delivery worker is safe on every node: rows are claimed with
`FOR UPDATE SKIP LOCKED`, given a 60-second lease and marked `SENDING` before the
HTTP call. A crashed node's expired leases are reaped to `FAILED` (or `DEAD`
after five attempts). A delivery whose channel was deleted or disabled before it
was sent is marked `DEAD`; re-enabling the channel and using manual retry is an
explicit operator action, not an automatic resurrection. Per-channel rate limits
use a Redis fixed window; if Redis is unavailable the worker allows the send and
logs at debug level, because a vendor-side burst is better than silently dropping
alerts.

## Consequences

- A failure with no configured channel is visible as an event and as a
  `SKIPPED_NO_CHANNEL` delivery, not as a log line on a node.
- Adding a channel does not require a restart. Enabling it requires a successful
  test and is audited.
- Recovery messages cannot reach a channel that never received the failure.
- Resolved events remain in `alert_event` for audit; the delivery log keeps a
  channel-name snapshot after a channel is deleted.
- Storm aggregation and cooldown enforcement are known phase-2 gaps: many
  different tables failing at once still produce one event and one delivery per
  table. The `group_window_secs` / `cooldown_secs` columns are reserved for the
  worker that will collapse them.
