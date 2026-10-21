# Retire collection definitions while preserving execution snapshots

Collection definitions change and may be retired, but the statistics, history, and logs of completed runs must remain attributable to the configuration that existed when they ran. We therefore keep `etl_table` as a stable, retire-only identity, create an immutable snapshot for each execution attempt, and link execution facts to that snapshot instead of resolving their meaning from current source, target, or table configuration.

## Considered Options

- Cascading deletes or `ON DELETE SET NULL` preserve referential integrity at the cost of destroying or orphaning history.
- Soft deletion alone prevents broken references but still mislabels old runs after configuration changes.
- Full configuration versioning would support arbitrary historical replay, but the product only needs to audit and troubleshoot actual executions.

## Consequences

The delete action becomes retirement. Current scheduling queries exclude retired definitions, while historical reports and the retired-table history view include facts produced before retirement. Table-level opt-out (`X`) and a disabled source remain non-retired definitions: they are visible and editable in table management but do not participate in automatic scheduling. Restoring clears only the retirement state; it does not enable the table or source. Raw Addax logs have their own 180-day retention; execution snapshots, statistics, and journeys are long-lived.
