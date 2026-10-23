# Separate target schema ownership from source column discovery

External ODS tables may be created and evolved by DBA or platform-governance processes, while source systems frequently use field names that cannot be copied unchanged into the target. We therefore make schema ownership explicit: `MANAGED` targets keep the existing create-and-evolve behavior, while `EXTERNAL` targets must exist before a collection definition is saved and are only inspected. A separate column mapping records the stable `source -> target` contract; it is used by Reader aliases, Writer columns, DDL, validation, and execution snapshots instead of assuming both sides share one name.

## Consequences

External mode never creates or alters target tables or columns. It records a live target-contract check, blocks unsafe collection before Addax starts, and permits only safe extra columns with warnings. Partition creation remains an explicit runtime policy: the platform may register a known business-date partition, or the partition may be managed externally and checked before each run. Arbitrary SQL expressions are intentionally excluded from the mapping model so ODS collection remains a direct, traceable copy rather than a transformation layer.

The batch-add flow therefore separates target policy from final confirmation. One target policy is
applied to the selected tables, then a batch plan preview returns one isolated result per table:
MANAGED plans show the target schema that the platform will create or evolve, while EXTERNAL plans
show the live target contract that must already exist. Field mappings remain row-level because
renames and conflicts are specific to each source/target pair. The final action is only enabled from
this review step, and commit revalidates the plan before applying it. A failed table can be excluded
without discarding previews for the remaining batch.

RDBMS snapshot business-date columns are platform-provided ordinary columns when they are configured
but not mapped from the source. They are not target partition keys and must not be reported as
required unmapped fields. EXTERNAL targets must already contain a string-compatible column for this
value; MANAGED targets create it as part of the reviewed schema.
