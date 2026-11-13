#!/bin/sh
set -eu

FAKE_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PROPS=${2:-}
JOB_NAME=$(printf '%s' "$PROPS" | sed -n 's/.*-DjobName=\([^ ]*\).*/\1/p')
LOG_NAME=$(printf '%s' "$PROPS" | sed -n 's/.*-Dlog.file.name=\([^ ]*\).*/\1/p')

mkdir -p "$FAKE_HOME/log"

# Failure scenarios for the failure-facts E2E. The mode file is keyed by table id so parallel
# specs sharing this fixture cannot steer each other's runs.
TID=$(printf '%s' "$JOB_NAME" | cut -d: -f1)
MODE_FILE="$FAKE_HOME/fail-mode.${TID:-none}"
FAIL_MODE=""
if [ -f "$MODE_FILE" ]; then
  FAIL_MODE=$(cat "$MODE_FILE")
fi

case "$FAIL_MODE" in
  table_not_found)
    cat >"$FAKE_HOME/log/$LOG_NAME" <<'EOF'
2026-11-13 09:18:19.708 [        main] INFO  Engine - Addax started
2026-11-13 09:18:20.104 [    reader-0] ERROR CommonRdbmsReader$Task - 读取源表失败
com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException: Table 'demo.missing_table' doesn't exist
	at com.mysql.jdbc.Util.handleNewInstance(Util.java:425)
EOF
    exit 2
    ;;
  permission_denied)
    cat >"$FAKE_HOME/log/$LOG_NAME" <<'EOF'
2026-11-13 09:20:01.002 [        main] INFO  Engine - Addax started
2026-11-13 09:20:01.880 [    writer-0] ERROR HdfsWriter$Job - 写入失败
org.apache.hadoop.security.AccessControlException: Permission denied: user=hive, access=WRITE, inode="/warehouse/ods/oms/oms_order":hdfs:hdfs:drwxr-xr-x
EOF
    exit 2
    ;;
  oom)
    cat >"$FAKE_HOME/log/$LOG_NAME" <<'EOF'
2026-11-13 09:22:44.120 [        main] INFO  Engine - Addax started
2026-11-13 09:22:59.903 [      job-0] ERROR JobContainer - 内存溢出
java.lang.OutOfMemoryError: Java heap space
EOF
    exit 137
    ;;
esac

printf 'fake addax execution for %s\n' "$JOB_NAME" >"$FAKE_HOME/log/$LOG_NAME"

if [ -n "${ADDAX_E2E_REPORT_URL:-}" ] && [ -n "$JOB_NAME" ]; then
  NOW=$(date +%s)
  curl --silent --show-error --fail \
    -H 'Content-Type: application/json' \
    -d "{\"jobName\":\"$JOB_NAME\",\"startTimeStamp\":$((NOW - 4)),\"endTimeStamp\":$NOW,\"totalCosts\":4,\"totalBytes\":4096,\"byteSpeedPerSecond\":1024,\"recordSpeedPerSecond\":32,\"totalReadRecords\":128,\"totalErrorRecords\":0}" \
    "$ADDAX_E2E_REPORT_URL" >/dev/null
fi
