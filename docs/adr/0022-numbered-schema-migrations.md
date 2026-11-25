# Record schema changes as a numbered migration chain

The schema used to live in two places: `scripts/schema.sql`, a hand-maintained snapshot for fresh
installs, and a Java upgrade pipeline that re-ran every idempotent `ALTER`/`CREATE` on each start. The
snapshot and the pipeline could disagree, the pipeline's ordering constraints were implicit, and a
column change meant editing a large Java class. The previous refactor split the DDL into per-table
units, but the source of truth was still split and nothing recorded what a database had actually run.

## Decision

- The schema has one source of truth: a numbered chain under `backend/src/main/resources/db/migration`.
  - `V0001__baseline.sql` — the schema snapshot for a fresh database.
  - `V0002__seed_reference_data.sql` — the default accounts and reference dictionary rows.
  - Later files `V0003__….sql`, `V0004__….sql`, … are appended in globally increasing order. The
    number is global, not per table; a description carries the grouping.
- Not every change can be SQL. Steps that need runtime logic (conditional view drops before a type
  widening, JSON rewrites of `etl_target.connect_config`, policy probes, the view fingerprint) stay
  Java steps. Both kinds share one version sequence and one ordered chain. The historical upgrade
  logic is the single Java step `V0003` (`LegacySchemaUpgrade`) and must not change after release.
- A `schema_migration` table records `version`, `description`, `checksum`, `applied_at`,
  `execution_ms` and `baseline`. Applied steps are not executed again; their checksums are verified on
  every start, so a released migration is immutable and editing one aborts startup instead of
  silently diverging from the databases that ran it. A version present in the history but missing
  from the build also aborts startup.
- A fresh database replays the whole chain. A populated database without history is adopted: the
  baseline snapshot and the seed are recorded as `baseline` without being re-executed, and every
  later step (starting with the legacy upgrade) runs. Both paths must end with the same schema.
- The chain runs in one transaction behind a PostgreSQL advisory transaction lock, with one history
  row per step; a failure rolls the whole run back and the next start retries. The bundled snapshot
  is a pg_dump that clears `search_path` and sets session options, so the runner sets
  `search_path = public` before each step and runs `reset all` afterwards.
- Migrations must stay backward compatible for one release so a rolling cluster upgrade can mix
  versions.

## Consequences

- Existing deployments need no manual action: the first start of this build adopts them.
- The `scripts/schema.sql` / `scripts/data.sql` manual-install path is gone. The jar and the host
  bundle ship the chain (`deploy/sql/V*.sql`); applying the files in version order by hand is still
  possible, and the application will record them as applied on its next start only through its own
  chain — manual psql execution does not write history, so the next start sees a populated database
  and adopts it.
- A manually damaged column is no longer healed by the idempotent re-run on every start; that
  behavior belongs to the migration that introduced it. Restoring state is a migration operation.
- New schema changes are new numbered files. Editing an applied file fails startup by design. The
  checksum of a Java step is a constant in its class; changing the logic requires a new step.
- `schema.sql` and `data.sql` no longer exist; documentation and the host bundle point at the chain.
