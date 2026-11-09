#!/usr/bin/env bash
#
# Reset the standalone demo database from a known-good Addax Admin database.
# The source database is never modified. If the target database exists, its
# public schema is recreated in place; otherwise it is created when the role
# has CREATEDB. The result is then enriched with deterministic demo scenarios.
#
# Required environment:
#   PGHOST, PGPORT, PGUSER, PGPASSWORD
# Optional:
#   SOURCE_DB (default: addax_admin_demo)
#   TARGET_DB (default: addax_admin_showcase)
#
set -euo pipefail

SOURCE_DB="${SOURCE_DB:-addax_admin_demo}"
TARGET_DB="${TARGET_DB:-addax_admin_showcase}"

if [[ "$SOURCE_DB" == "$TARGET_DB" ]]; then
  echo "Refusing to reset: SOURCE_DB and TARGET_DB are the same ($SOURCE_DB)" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Resetting demo database: $TARGET_DB (source: $SOURCE_DB)"

if psql -d "$TARGET_DB" -Atqc 'SELECT 1' >/dev/null 2>&1; then
  echo "Reusing existing database $TARGET_DB; recreating public schema"
  psql -v ON_ERROR_STOP=1 -d "$TARGET_DB" \
    -c 'DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;'
else
  echo "Creating database $TARGET_DB"
  if ! createdb "$TARGET_DB"; then
    echo "Cannot create $TARGET_DB: the current role lacks CREATEDB." >&2
    echo "Ask an admin to create it first, then rerun this script:" >&2
    echo "  createdb -O $PGUSER $TARGET_DB" >&2
    exit 1
  fi
fi

pg_dump --no-owner --no-privileges "$SOURCE_DB" | psql -q -v ON_ERROR_STOP=1 -d "$TARGET_DB"
psql -v ON_ERROR_STOP=1 -d "$TARGET_DB" -f "$SCRIPT_DIR/enrich.sql"

echo "Demo database ready: $TARGET_DB"
echo "Point the backend at it with DB_NAME=$TARGET_DB and restart the service."
