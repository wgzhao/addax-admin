#!/bin/sh
# config/env.sh 示例
# 复制本文件为 env.sh，并根据实际环境修改变量
# 启动脚本会自动加载本文件

# 数据库连接信息
export DB_HOST="localhost"                                    # 数据库主机
export DB_PORT="5432"                                         # 数据库端口
export DB_NAME="addax_admin"                                     # 数据库名称
export DB_USERNAME="addax_admin"                               # 数据库用户名
export DB_PASSWORD="password"                           # 数据库密码
export DB_DRIVER="org.postgresql.Driver"                       # JDBC 驱动类名

export REDIS_HOST="localhost"                                  # Redis 主机
export REDIS_PORT="6379"                                       # Redis 端口
export REDIS_PASSWORD=""                                       # Redis 密码（如有）
export REDIS_DATABASE="0"                                     # Redis 数据库索引

# Hive Service 2 服务连接信息
export HIVE_METHOD="jdbc"                                   # 连接方法 jdbc 或 cmd

# 如果使用了 jdbc 方法，则下面的配置项必须填写
export HIVE_HOST="localhost"                                  # Hive 主机
export HIVE_PORT="10000"                                      # Hive 端口
export HIVE_USERNAME="hive"                                   # Hive 用户名
export HIVE_PASSWORD=""                                       # Hive 密码（如有）

export LOG_DIR="./logs"                                   # 日志目录
# 企业微信通知
export WECOM_ROBOT_KEY=""                                # 企业微信机器人 Key（如有）
# 其他可选环境变量
# export SERVER_PORT=9090                                      # 服务端口（如需覆盖 application.properties 配置）
# export SPRING_PROFILES_ACTIVE=prod                           # Spring Boot 激活的 profile

# 使用说明：
# 1. 修改上述变量为你的实际数据库和环境信息。
# 2. 启动服务时，bin/service.sh 会自动加载本文件。
# 3. 如需添加更多 Spring Boot 支持的环境变量，可在此文件中继续 export。

