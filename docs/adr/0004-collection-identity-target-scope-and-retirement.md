# Scope collection identity by source route and reuse retired definitions

The add-table flow used to treat "already collected" as "the source database already has a row with
the same `source_table`", regardless of the target. That assumption breaks as soon as one source table
must be collected into a second target endpoint or a second target database. The same subtraction also
compared names with an unconditional lower-case heuristic, which is wrong for case-sensitive sources,
and it disagreed with the target-existence check shown later in the wizard: the list said "not
collected" while the preview said "target table exists".

We therefore make the collection route explicit:

```
(sid, source namespace, source table, target id, target database)
```

`etl_table` keeps one row per route for its whole lifecycle. Retirement is a state of that row, not a
deletion and not a reason to create a second row.

## Identifier case

Source and target names are compared with an `IdentifierPolicy` resolved from the live dialect, not
with unconditional lower-casing. Source names are stored verbatim because they are the physical names
used by the reader. Hive target names are case-insensitive because the metastore lower-cases them;
RDBMS target names are compared exactly, with the live target contract check remaining authoritative.

| Dialect | Policy |
| --- | --- |
| MySQL / MariaDB | `@@lower_case_table_names = 0` -> exact; `1` or `2` -> insensitive |
| SQL Server | `'a' = 'A'` under the selected database -> insensitive, otherwise exact |
| PostgreSQL / Oracle / DB2 / ClickHouse | exact |
| Hive / SQLite | insensitive |
| Unknown or probe failure | exact |

Exact is the safe failure mode: a table may be offered again and then rejected by the create check,
but a distinct table is never hidden because of a guessed case rule.

## Definition states

For a route the collectable-table endpoint returns one of:

- `AVAILABLE`: no definition exists for the route; it may be created.
- `ACTIVE`: an active definition exists; it is not offered as new.
- `RETIRED`: a definition exists but is retired; it is shown as retired and can only be restored.

Creating a definition for a route with an active row fails with a duplicate error. Creating one for a
route with a retired row fails with an instruction to restore that row. Restoring checks that no other
active definition occupies the same route or target table, clears only the retirement markers, and
keeps columns, mappings, job content, statistics, executions and logs under the original id.

## Target database

The target database is part of the route. The same source table may be collected into `ods` and `dwd`
on the same target endpoint, or into two different target endpoints. The target table name is not part
of the route: one source table has one definition per target database. A different target table for the
same route is an edit of the restored/active definition, not a second definition.

## Legacy rows

Rows with `target_id is null` are backfilled to the default target on startup, including retired rows.
Historical target names are not rewritten: changing a Hive target name can change the writer path and
orphan existing data. Comparisons use the target policy instead.

## Consequences

- The wizard selects the target endpoint and target database before loading tables; changing either
  reloads the table list.
- `SOURCE_ALREADY_COLLECTED` and `SOURCE_DEFINITION_RETIRED` are explicit preflight findings and create
  errors, distinct from `TARGET_ALREADY_EXISTS` / target-name collisions.
- Retired definitions are restored, never re-created. The retired list and the collectable list both
  point at the same row id.
- A legacy active+retired pair for one route is visible to the audit; restoring the retired row is
  rejected while the active row exists.
- A future change may add materialized source/target keys and partial unique indexes. The current
  enforcement is application-level because identifier case rules are dialect-specific.
- Dynamic source names containing `${...}` are not compared against concrete metadata. They are a
  separate identity case; adding a concrete table while a dynamic definition covers it remains a
  known limitation until the route model represents name patterns.
