#!/bin/sh
# Why: an addax run must never outlive the admin instance that owns its queue row. A
# surviving child would keep writing to the target while a restarted admin re-runs the
# same table, producing duplicate or interleaved output.
parent_pid=$1
parent_start=$2
clock_mode=$3
lease_file=$4
lease_ttl=$5
shift 5

proc_field() {
    pfield=$1
    ppid=$2
    [ -r "/proc/$ppid/stat" ] || return 1
    pline=$(cat "/proc/$ppid/stat" 2>/dev/null) || return 1
    prest=${pline##*) }
    set -- $prest
    eval "echo \${$((pfield - 2))}"
}

parent_alive() {
    if [ -z "$parent_start" ]; then
        kill -0 "$parent_pid" 2>/dev/null
        return
    fi
    pcurrent=$(proc_field 22 "$parent_pid") || return 1
    [ "$pcurrent" = "$parent_start" ]
}

now_seconds() {
    if [ "$clock_mode" = "uptime" ] && [ -r /proc/uptime ]; then
        puptime=$(cat /proc/uptime 2>/dev/null)
        echo "${puptime%%.*}"
    else
        date +%s
    fi
}

lease_fresh() {
    [ "$lease_file" = "-" ] && return 0
    [ -r "$lease_file" ] || return 1
    plast=$(cat "$lease_file" 2>/dev/null)
    case "$plast" in
        ''|*[!0-9]*) return 1 ;;
    esac
    pnow=$(now_seconds)
    [ $((pnow - plast)) -le "$lease_ttl" ]
}

kill_tree() {
    ksignal=$1
    kpid=$2
    for kchild in $(cat "/proc/$kpid/task/$kpid/children" 2>/dev/null); do
        kill_tree "$ksignal" "$kchild"
    done
    kill "-$ksignal" "$kpid" 2>/dev/null
}

if command -v setsid >/dev/null 2>&1; then
    setsid "$@" &
else
    "$@" &
fi
child=$!
pgid=$(proc_field 5 "$child") || pgid=""

kill_child() {
    if [ -n "$pgid" ] && [ "$pgid" = "$child" ]; then
        kill "-$1" "-$child" 2>/dev/null
    else
        kill_tree "$1" "$child"
    fi
}

trap 'kill_child TERM; sleep 2; kill_child KILL; exit 143' TERM INT HUP

(
    while parent_alive && lease_fresh; do
        sleep 2
    done
    kill_child TERM
    sleep 5
    kill_child KILL
) &
watchdog=$!

wait "$child"
status=$?
kill "$watchdog" 2>/dev/null
exit "$status"
