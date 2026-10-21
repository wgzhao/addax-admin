#!/bin/sh
set -eu

FAKE_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PROPS=${2:-}
JOB_NAME=$(printf '%s' "$PROPS" | sed -n 's/.*-DjobName=\([^ ]*\).*/\1/p')
LOG_NAME=$(printf '%s' "$PROPS" | sed -n 's/.*-Dlog.file.name=\([^ ]*\).*/\1/p')

mkdir -p "$FAKE_HOME/log"
printf 'fake addax execution for %s\n' "$JOB_NAME" >"$FAKE_HOME/log/$LOG_NAME"

if [ -n "${ADDAX_E2E_REPORT_URL:-}" ] && [ -n "$JOB_NAME" ]; then
  NOW=$(date +%s)
  curl --silent --show-error --fail \
    -H 'Content-Type: application/json' \
    -d "{\"jobName\":\"$JOB_NAME\",\"startTimeStamp\":$((NOW - 4)),\"endTimeStamp\":$NOW,\"totalCosts\":4,\"totalBytes\":4096,\"byteSpeedPerSecond\":1024,\"recordSpeedPerSecond\":32,\"totalReadRecords\":128,\"totalErrorRecords\":0}" \
    "$ADDAX_E2E_REPORT_URL" >/dev/null
fi
