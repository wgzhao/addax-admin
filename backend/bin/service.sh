#!/bin/sh
### BEGIN INIT INFO
# Provides:          addax-admin
# Required-Start:    $remote_fs $syslog
# Required-Stop:     $remote_fs $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Addax Admin Service
# Description:       Controls the Addax Admin Spring Boot service
### END INIT INFO

export APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="addax-admin"
LIB_DIR="$APP_HOME/lib"
DRIVERS_DIR="$APP_HOME/drivers"
CONFIG_DIR="$APP_HOME/config/"
JAR_MAIN="$(ls $LIB_DIR/addax-admin-*.jar | head -n 1)"
ENV_FILE="$CONFIG_DIR/env.sh"
PID_FILE="$APP_HOME/logs/${APP_NAME}.pid"
LOG_FILE="$APP_HOME/logs/${APP_NAME}.log"
SPRING_CONFIG_LOCATION=file:$CONFIG_DIR
# How long to wait (seconds) for graceful shutdown before killing
STOP_TIMEOUT=30
KILL_WAIT=5

JAVA_HOME=/usr/lib/jvm/temurin-21-jdk/
PATH=$JAVA_HOME/bin:$PATH

[ -f "$ENV_FILE" ] && . "$ENV_FILE"

[ -d ${APP_HOME}/job ] || mkdir ${APP_HOME}/job

JAVA_OPTS="-Djava.security.properties=${LIB_DIR}/java.security -XX:+UnlockExperimentalVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+UseZGC -XX:+UseDynamicNumberOfGCThreads "
JAVA_OPTS="$JAVA_OPTS -Dapp.home=${APP_HOME} -Dlogging.file.path=${APP_HOME}/logs -Dloader.path=${DRIVERS_DIR} -Dloader.main=com.wgzhao.addax.admin.AdminApplication"

# Function to get server port
get_server_port() {
    # 1. Check JAVA_OPTS for -Dserver.port=...
    local port_from_opts=$(echo "$JAVA_OPTS" | grep -o 'server.port=[0-9]*' | cut -d'=' -f2)
    if [ -n "$port_from_opts" ]; then
        echo "$port_from_opts"
        return
    fi

    # 2. Check for SERVER_PORT environment variable
    if [ -n "$SERVER_PORT" ]; then
        echo "$SERVER_PORT"
        return
    fi

    # 3. Parse application.properties
    local app_properties="${CONFIG_DIR}/application.properties"
    if [ -f "$app_properties" ]; then
        local port_from_file=$(grep -E '^\s*server\.port\s*=' "$app_properties" | cut -d'=' -f2 | tr -d '[:space:]')
        if [ -n "$port_from_file" ]; then
            echo "$port_from_file"
            return
        fi
    fi

    # 4. Default port
    echo "50601"
}

start() {
    echo "Starting $APP_NAME..."
    if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        echo "$APP_NAME is already running."
        return 0
    fi
    [ -f "$ENV_FILE" ] && . "$ENV_FILE"

    local port=$(get_server_port)

    nohup java $JAVA_OPTS -cp ${JAR_MAIN}  org.springframework.boot.loader.launch.PropertiesLauncher > /dev/null  2>&1 &
    echo $! > "$PID_FILE"
    # check status
    echo -n "Checking port $port..."
    for i in $(seq 1 30); do
        echo -n "."
        sleep 1
        nc -z localhost $port > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            echo " Done"
            return 0
        fi
    done
    echo " Failed"
    stop
}

stop() {
    echo "Stopping $APP_NAME..."
    if [ ! -f "$PID_FILE" ]; then
        echo "$APP_NAME is not running (no PID file)."
        return 0
    fi

    pid=$(cat "$PID_FILE" 2>/dev/null)
    if [ -z "$pid" ]; then
        echo "Empty PID file, removing."
        rm -f "$PID_FILE"
        return 0
    fi

    # Ensure PID is numeric
    case "$pid" in
        ''|*[!0-9]*)
            echo "Invalid PID '$pid' in $PID_FILE, removing."
            rm -f "$PID_FILE"
            return 0
            ;;
    esac

    # If process already gone
    if ! kill -0 "$pid" 2>/dev/null; then
        echo "Process $pid not running, removing PID file."
        rm -f "$PID_FILE"
        return 0
    fi

    echo "Sending TERM to $pid..."
    kill "$pid" 2>/dev/null

    # Wait for graceful shutdown
    echo -n "Waiting up to $STOP_TIMEOUT seconds for process to exit"
    for i in $(seq 1 $STOP_TIMEOUT); do
        echo -n "."
        sleep 1
        if ! kill -0 "$pid" 2>/dev/null; then
            echo " Done"
            rm -f "$PID_FILE"
            echo "$APP_NAME stopped."
            return 0
        fi
    done
    echo " Timeout"

    # If still running, escalate
    if kill -0 "$pid" 2>/dev/null; then
        echo "Process still alive after $STOP_TIMEOUT seconds, sending KILL..."
        kill -9 "$pid" 2>/dev/null

        # Wait a little after KILL
        echo -n "Waiting $KILL_WAIT seconds after KILL"
        for i in $(seq 1 $KILL_WAIT); do
            echo -n "."
            sleep 1
            if ! kill -0 "$pid" 2>/dev/null; then
                echo " Done"
                rm -f "$PID_FILE"
                echo "$APP_NAME force-stopped."
                return 0
            fi
        done
    fi

    # Final check
    if kill -0 "$pid" 2>/dev/null; then
        echo ""
        echo "Failed to stop process $pid."
        return 1
    else
        echo ""
        rm -f "$PID_FILE"
        echo "$APP_NAME stopped."
        return 0
    fi
}

status() {
    if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        echo "$APP_NAME is running."
    else
        echo "$APP_NAME is not running."
    fi
}

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        start
        ;;
    status)
        status
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
exit 0
