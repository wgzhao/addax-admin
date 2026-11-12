# Authorize collection sources with roles and source-scoped grants

The application had two effective roles: `admin`, and everything else. Every `GET` was open to any
authenticated account, every write required `admin`. That is not enough once ordinary users are meant
to operate only their own collection sources. It also produced two opposite failures: a normal user
could read every source (including credentials exposed by read endpoints), while self-service POSTs
such as marking a notification read were rejected because the rule keyed on the HTTP method rather
than on what the endpoint does.

We keep three roles and add one grant table. A role answers what an account may do; a grant answers
which collection sources it may do it to. The collection source is the authorization boundary because
tables, executions, statistics, logs and queue entries all resolve to a source (`etl_table.sid` or
`tid -> etl_table.sid`). Targets, dictionaries, calendars, system settings, job templates and alert
configuration are shared platform resources and stay outside the source boundary.

## Roles

- `admin` — global read/write, account and grant management, target and platform configuration,
  audit, global queue control.
- `user` — guest's global read access, plus read/write/execute inside granted sources.
- `guest` — global read-only observer. Guest is an authenticated account, not anonymous access.
  A future public dashboard is a separate anonymous surface, not this role.

Read access is monotonic: guest can read every source's metadata but cannot change anything; user
has the same global read access and can additionally manage granted sources; admin can do
everything. The grant is a management scope, not a read filter. This matches the Viewer/Operator
split used by mainstream schedulers (Airflow roles plus DAG-level permissions, DolphinScheduler
project/data-source authorization, Kubernetes Role plus RoleBinding).

## Grant model

```sql
create table user_source_grant (
  username     varchar(50) not null references users(username) on delete cascade,
  source_id    int         not null references etl_source(id) on delete cascade,
  access_level varchar(16) not null default 'OPERATE'
               check (access_level in ('VIEW', 'OPERATE')),
  granted_by   varchar(50) not null,
  granted_at   timestamptz not null default now(),
  primary key (username, source_id)
);
```

Only an admin may grant or revoke. Grants are many-to-many between accounts and sources. Admin and
guest do not need grant rows; their scope comes from their role.

A grant is a management grant: it lets the account modify and execute that source. The table keeps
`access_level` for forward compatibility, but the write API accepts only `OPERATE`; `VIEW` rows from
the first iteration are ignored. `VIEW` is deliberately not a read grant because every authenticated
role can already read every source.

The four authorization predicates are:

```
canReadSource(sid)      = true for every authenticated role
canOperateSource(sid)   = admin || grant(sid) exists with OPERATE
canViewOperational(sid) = canOperateSource(sid)
canWriteGlobal()        = admin
```

`canViewOperational` covers source connection details, rendered job JSON and raw log content, because
those carry credentials. `guest` and `VIEW` users receive metadata without those fields.

Source creation and deletion remain admin-only. A `user` with `OPERATE` may edit the whole granted
source, including its connection configuration, and may add, edit, retire, restore and execute its
tables. Targets and platform configuration stay admin-only for writes.

## Enforcement

Authorization is enforced on the server, not in the UI.

- `SecurityConfiguration` keeps only coarse tiers: public endpoints, authenticated endpoints, and
  admin-only platform endpoints. It no longer maps HTTP methods to roles.
- A single authorization service resolves the current account, its role and its grants, and exposes
  the predicates above. Table-scoped checks load the table first and read `sid` from the entity; a
  caller-supplied `sourceId` is never trusted.
- Source, table, task-status and queue reads are global for every authenticated role, matching
  guest read access. Write endpoints resolve the table to its `sid` and require OPERATE.
- Logs are the exception: guest has no log access, and a user sees only logs from sources they
  operate. The log list is filtered in SQL so pagination stays correct.
- Batch operations validate every id and reject the whole batch if any id is outside the caller's
  OPERATE scope.
- Table updates validate both the current `sid` and the requested `sid`, so a table cannot be moved
  into or out of a source the caller does not control.
- Execution, statistic and queue access resolves `tid` to the owning source.
- Background schedulers and queue consumers run as the system, without a user scope.
- Grant changes and denied writes are audited. Existing `AuditRecorder` is reused.

## Token and revocation

The JWT is a pointer, not the authority: `JwtFilter` re-reads the account's role, enabled flag and
`auth_version` from the database. It verifies that the account still exists and is enabled, and that
the token version matches the database. `users.auth_version` is incremented on role change, disable,
delete and password change. Grant information is never embedded in the token.

The `authorities` claim is kept in the token for compatibility and diagnostics, but the filter does
not trust it. This also fixes the pre-existing hole where a disabled account could keep using
an eight-day token.

Grants are resolved from the database per request with a short-lived cache. Revocation takes effect
within the cache TTL; if immediate revocation is required, the revoke path bumps `auth_version` as
well.

## Prerequisite: stop leaking credentials through "read"

Guest read-only is meaningless while read endpoints expose secrets. Before or together with this
model:

- `VwEtlTableWithSource.pass` must not be serialized; `GET /tables` currently returns every source
  password in plaintext.
- `GET /targets` must return a sanitized view for non-admin callers. It currently returns
  `connect_config`, which contains the HiveServer2 password. The non-secret HDFS writer defaults
  (`defaultFS`, `hdfsPrefix`, storage/compression format) remain visible because the add-table flow
  needs them.
- `GET /tables/{id}/addax-job` and `GET /log/addax/{id}/content` are operational reads restricted to
  admin and `OPERATE`; both can contain credentials.
- `POST /log/job-report` is unauthenticated because Addax posts to it directly. The controller now
  rejects reports for unknown tables and for executions that do not belong to the reported table; a
  shared secret or a network restriction remains a deployment follow-up.

## Migration

- `normalizeAuthority` accepts `guest`; unknown authorities fail closed to guest. If multiple
  authorities exist, `admin > user > guest` wins.
- `scripts/schema.sql` and the startup `SchemaInitializer` create `user_source_grant` and
  `users.auth_version` idempotently.
- Existing `user` accounts already have guest read access and therefore need no grant rows. The
  initial one-time `VIEW` migration was removed; rows created by that iteration are ignored. Admins
  grant `OPERATE` per source when a user should manage it.

## Consequences

- A `user` with no grants sees every source read-only, exactly like guest; only granted sources gain
  write controls.
- Dashboard and monitoring remain global read for guest and user because user includes guest read
  access. A future feature may add optional source filters, but that is not an authorization
  boundary.
- The UI hides admin pages and write controls based on the role and grants returned by
  `/users/me`. This is presentation only; the server re-checks every request.
- The model deliberately stops at source granularity. Database- or table-level grants, user groups
  and custom roles can be added later by extending the grant table with a scope or by introducing a
  role-permission table; neither is needed today.
- Anonymous dashboard access is not part of this decision.
