# Retire the source code as a naming rule; use the source id as identity

The source `code` started as the mapping key from a collection source to its Hive database:
`database = "ods" + code.toLowerCase()`. That was correct only while HDFS/Hive was the sole target
family and the mapping was hard-coded. The target endpoint and the target database are now explicit
parts of a collection route (`sid`, source namespace, source table, `target_id`, `target_db`), so one
source can write several target databases on several endpoints. The original purpose is gone.

What remained was a second identity. It is weaker than the primary key: `etl_source.code` has no
database unique constraint (unlike `etl_target`), `SourceService.create` does not check for
duplicates, and the only duplicate guard is an advisory `GET /sources/check-code` call from the form.
Treating it as an identity is actively unsafe: the scheduler used `source-<code>` as its task id, so
two sources sharing a code would cancel each other's cron registration and one would silently stop
collecting. Statistics grouped by `code`, so the same collision would merge two sources into one
series.

We therefore decouple the field from every functional path, then remove it:

## Phase 1 (this change)

- The batch wizard no longer derives `targetDb` from the source code. It suggests the target database
  most recently used by this source on the selected endpoint
  (`GET /sources/{id}/target-databases?targetId=...`), and never overwrites a user-entered value.
- The scheduler registers `source-<id>`; `cancelTask` takes the source id.
- Source-level statistics group by `sid`; display labels use `name`.
- `etl_execution` snapshots `source_id`. New rows use it; old rows fall back to the definition's
  `sid` through `vw_etl_table_with_source`. Historical `source_code` values stay untouched.
- The UI and manual use the source name as the primary label. The code field remains mandatory and
  immutable for one release so a rolling deployment still satisfies the old `NOT NULL` contract.

## Phase 2 (this change)

- The startup migration drops `etl_source.code` (after dropping the view that selected it) and
  `scripts/schema.sql` no longer creates it.
- The entity field, the source form field, the uniqueness check, `GET /sources/check-code`, the
  source list column and the source-code entries in audit snapshots and execution snapshots are gone.
- `vw_etl_table_with_source` no longer exposes the code, and the free-text search formula no longer
  matches it.
- `etl_execution.source_code` stays as a historical snapshot column; new executions leave it null
  and grouping never used it.

## Consequences

- No data migration is needed: existing target database names are already explicit route values, and
  existing Hive paths are unchanged.
- A source can be renamed without splitting its statistics, because grouping is by `sid`.
- Duplicate codes can no longer affect scheduling or statistics during Phase 1.
- Dropping the column is not compatible with a rolling upgrade: an old node still selects it, so
  every node must be stopped before the new release starts. `etl_execution.source_id` was additive
  and nullable, which is why the previous release could roll.
