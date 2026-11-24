# Serialize explicit response DTOs and redact audit free text

`GET /sources` and `GET /tables` returned JPA entities (and a view entity) directly. The table view
joins `etl_source`, so every table row carried the source URL, account and connect config; the
source endpoint returned the account and structured config to every authenticated role, although
ADR-0008 defines those as an operational read. The password was already suppressed, but the rest of
the connection block was not, and any future entity column would silently become part of the public
API.

The audit writer sanitized `before_data`/`after_data` only. `metadata` and the free-text columns
(`resource_name`, `error_message`) were written verbatim, and callers do put user-supplied URLs and
account strings there.

## Decision

- `/sources` and `/tables` return explicit DTOs (`SourceView`, `TableView`) instead of entities.
- The source URL is metadata: both DTOs return it to every authenticated role. The operator manual
  requires the account and password to be entered in their own fields, so the URL is an address,
  not a credential container.
- `TableView` has no field for the source account, password or connect config: table metadata is
  readable by every authenticated role and must not carry credential material.
- `SourceView` keeps the account and configuration fields (`username`, `prerequisite`, `preScript`,
  `connectConfig`) only when `canViewOperationalSource(id)` is true (`admin` or an `OPERATE` grant,
  ADR-0008). Other callers get them as null. `pass` is the existing sentinel for operators and null
  otherwise; the real password never crosses the wire.
- `AuditRepository` runs `metadata` through `AuditSanitizer.sanitize` and `resource_name` /
  `error_message` through a new text redaction path. Free text can contain
  `user:password@host` and `password=...` fragments; both are replaced with `[REDACTED]`.
- `GET /tables` pagination treats `page`/`size` as part of the route state: a page or size change
  pushes a history entry, and the route watcher compares page/size as well as filters, reloading
  the incoming slice instead of resetting to page one.

## Consequences

- Guests and users without a grant see the source URL but not the account or structured config.
  A URL that embeds credentials is therefore visible to them; the manual's entry rule (separate
  account/password fields) is what keeps the URL free of secrets.
- `/tables` responses are smaller and stable. A client that used the joined `username`/
  `connectConfig` fields must switch to `/sources/{id}` and hold an `OPERATE` grant.
- Audit rows written before this change are unchanged; new rows redact metadata and free text.
- No database migration. The DTOs are serialization-only; internal rendering (job content, health
  checks) still reads the entities.
