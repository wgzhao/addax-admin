#!/bin/sh
# config/env.sh —— 后端运行环境变量
#
# 复制本文件为 config/env.sh（发行包已内置），按实际环境修改后由 bin/service.sh 自动加载。
# 只改这个文件即可，不要改 jar 内的 application.properties。

# 数据库（PostgreSQL）
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="addax_admin"
export DB_USERNAME="addax_admin"
export DB_PASSWORD="password"

# Redis
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD=""
export REDIS_DB="0"

# 节点并发权重，取值 (0.0, 1.0]，多节点部署时用不同权重分配负载
export WEIGHT="1.0"

# 日志目录跟随应用目录；service.sh 会把该值传给 -Dlogging.file.path
export LOG_DIR="${APP_HOME:-/opt/app/addax-admin}/logs"

# 生产环境必须设置为独立随机值，deploy/install.sh 首次安装会自动生成。
# export JWT_SECRET=""
# export ALERT_SECRET_KEY=""

# 其他可选覆盖
# export SERVER_PORT="50601"
# export SPRING_PROFILES_ACTIVE="prod"

# Java 运行时；发行包 install.sh 首次安装时会自动写入 JAVA_HOME 与 PATH
# export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
# export PATH="$JAVA_HOME/bin:$PATH"
