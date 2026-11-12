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
- `user` — read/write only inside granted sources; shared reference data is read-only.
- `guest` — global read-only observer. Guest is an authenticated account, not anonymous access.
  A future public dashboard is a separate anonymous surface, not this role.

Roles are not linearly ordered: a guest can read the metadata of every source but cannot change
anything; a user can change a granted source but cannot see unrelated sources. This matches the
Viewer/Operator split used by mainstream schedulers (Airflow roles plus DAG-level permissions,
DolphinScheduler project/data-source authorization, Kubernetes Role plus RoleBinding).

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

`VIEW` allows metadata reads (source, tables, execution history, statistics). `OPERATE` adds writes,
execution control, and operational reads. The two levels exist for a concrete migration reason:
today's `user` accounts can read everything and write nothing. Migrating them to `VIEW` on every
source preserves that behaviour exactly; granting `OPERATE` would silently escalate them, and
granting nothing would hide existing data. `VIEW` also expresses a real requirement: a colleague may
need to see one source without being able to change it.

The four authorization predicates are:

```
canReadSource(sid)      = admin || guest || grant(sid) exists
canOperateSource(sid)   = admin || grant(sid).level == OPERATE
canViewOperational(sid) = admin || grant(sid).level == OPERATE
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
- List endpoints filter in SQL, not after pagination. A request that names an ungranted source is
  rejected with 403 rather than an empty page.
- Batch operations validate every id and reject the whole batch if any id is outside scope.
- Table updates validate both the current `sid` and the requested `sid`, so a table cannot be moved
  into or out of a source the caller does not control.
- Execution, statistic, log and queue access resolves `tid` to the owning source.
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
- `POST /log/job-report` is currently unauthenticated and can forge statistics; it needs a shared
  secret or a network restriction.

## Migration

- `normalizeAuthority` accepts `guest`; unknown authorities fail closed to guest. If multiple
  authorities exist, `admin > user > guest` wins.
- `scripts/schema.sql` and the startup `SchemaInitializer` create `user_source_grant` and
  `users.auth_version` idempotently.
- Existing `user` accounts receive `VIEW` on every current source in one migration, preserving their
  current read-all/write-none behaviour. Admins promote individual sources to `OPERATE` afterwards.
  The migration is audited with a system actor as `granted_by`.

## Consequences

- A `user` with no grants sees an empty workspace and an explanatory state; admins see the grant
  count in account management.
- Source-scoped reads include dashboard and monitoring data. Those queries are the largest surface to
  adapt; until they are scoped, a user must not be shown cross-source aggregates.
- The UI hides admin pages and write controls based on the role and grants returned by
  `/users/me`. This is presentation only; the server re-checks every request.
- The model deliberately stops at source granularity. Database- or table-level grants, user groups
  and custom roles can be added later by extending the grant table with a scope or by introducing a
  role-permission table; neither is needed today.
- Anonymous dashboard access is not part of this decision.
