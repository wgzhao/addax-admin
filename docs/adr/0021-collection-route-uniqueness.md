# Enforce collection route uniqueness in the database

`CollectionScopeService` rejects duplicate collection routes by reading the source's existing
definitions and comparing normalized identifiers. The check is correct, but it is a read followed by
a write: two concurrent creates (two nodes, a batch create, or two requests on one node) can both see
no duplicate and both insert. `batchCreateTable` even validates and saves inside one transaction
without an explicit lock. A route is supposed to have exactly one active definition, and a duplicate
leaves two rows fighting over the same target table.

The comparison cannot be moved into a plain unique index on the raw columns: `IdentifierPolicy`
folds identifiers per dialect (Hive targets and Hive/SQLite sources fold to lower case, MySQL follows
`lower_case_table_names`, SQL Server follows its collation, PostgreSQL, MongoDB and Elasticsearch are
exact). An index on the raw text would either miss duplicates or reject valid case-distinct routes.

## Decision

- `etl_table` stores the normalized identity in two internal columns:
  - `source_route_key` = target database key + source database key + source table key, compared
    within `(sid, target_id)`;
  - `target_slot_key` = target database key + target table key, compared within `(sid, target_id)`.
  The fragments are joined with U+001F, which cannot occur in a database or table name (PostgreSQL
  text cannot store the NUL the in-memory comparison uses).
- The keys are written by `CollectionScopeService` on create, edit, batch create and restore, using
  the same `IdentifierPolicy` resolution the service checks use. The columns are `@JsonIgnore` and
  are not part of any response DTO.
- Two partial unique indexes back the checks:
  - `uk_etl_table_active_source_route (sid, target_id, source_route_key)`
  - `uk_etl_table_active_target_slot (sid, target_id, target_slot_key)`
  Both use `where retired_at is null and <key> is not null`. A retired definition does not reserve
  its route or target table (the service allows a retired row and an active row to share them and
  requires a restore instead of a second insert), and a row whose key has not been computed yet must
  not block unrelated writes.
- Rows that predate this change are keyed by a startup backfill (`TableRouteKeyBackfillService`)
  that runs after the application is up. It cannot run from the schema migration: the normalization
  needs a live source connection to answer the policy probe, and a migration must not open network
  connections inside its transaction. The backfill computes each key through
  `CollectionScopeService`, skips a row whose key would collide with another active definition, and
  logs the conflict instead of failing the launch.
- A source connection edit invalidates the source's cached identifier policy (the cache key is the
  source id, URL and database name, so an edit that keeps the URL would otherwise reuse the old
  answer) and rewrites the source's keys on the async refresh thread. A source server that changes
  its case rule without any edit is the only remaining way the source-side key can drift, and the
  60-second cache TTL bounds that window.
- Changing a target's type or writer template changes its identifier policy, so the target update
  rewrites every key of that target inside its transaction. A conflict rolls the target change back
  with a 409 instead of leaving keys that disagree with the policy used to compare them.

## Consequences

- The service checks remain the primary, precise error path (they can say which definition owns the
  route). The indexes are the backstop that turns a lost race into a database error rather than a
  duplicate row.
- A legacy duplicate leaves the later row unkeyed and logs an error; it stays outside the index
  until an operator resolves the duplicate or edits the row. The migration does not stop the
  application for data that was already inconsistent.
- Retired rows are keyed but not indexed. Restoring a retired row recomputes its keys and is
  rejected by the service and the index if an active definition now owns the route.
- The stored keys depend on the live identifier policy. A MySQL `lower_case_table_names` change or a
  SQL Server collation change is reflected on the next source edit, not immediately; service checks
  stay authoritative in that window.
- No API change and no response field. Rolling back is redeploying the previous jar; the added
  columns and indexes are ignored by the older code.
