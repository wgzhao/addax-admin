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

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="addax-admin"
LIB_DIR="$APP_HOME/lib"
DRIVERS_DIR="$APP_HOME/drivers"
CONFIG_DIR="$APP_HOME/config"
JAR_MAIN="$(ls $LIB_DIR/addax-admin-*.jar | head -n 1)"
ENV_FILE="$CONFIG_DIR/env.sh"
PID_FILE="$APP_HOME/logs/${APP_NAME}.pid"
LOG_FILE="$APP_HOME/logs/${APP_NAME}.log"

JAVA_OPTS="-Dspring.config.location=${CONFIG_DIR}/application.properties -Dloader.path=${DRIVERS_DIR} -Dloader.main=com.wgzhao.addax.admin.AdminApplication"

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

    nohup java $JAVA_OPTS -cp ${JAR_MAIN}  org.springframework.boot.loader.launch.PropertiesLauncher > "$LOG_FILE" 2>&1 &
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
    if [ -f "$PID_FILE" ]; then
        kill $(cat "$PID_FILE") && rm -f "$PID_FILE"
        echo "$APP_NAME stopped."
    else
        echo "$APP_NAME is not running."
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
