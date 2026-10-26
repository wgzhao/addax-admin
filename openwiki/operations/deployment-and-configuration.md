---
type: operations
title: 部署与配置
description: Docker 镜像与 compose 拓扑、Nginx 代理、裸机 service.sh/systemd 布局、数据库初始化与升级、环境变量与运行时字典配置、Addax 安装要求以及多节点部署注意事项。
tags: [deployment, docker, nginx, configuration, environment-variables, systemd, multi-node]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-5f5b95b3d6a215fa02ceb945
    resource: repo://.env.example
  - id: openwiki-source-7fe2845d92660e12fa0cf9ac
    resource: repo://backend/config/env.template.sh
  - id: openwiki-source-46782dd16f13e8dc07fe49c7
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/SchemaInitializer.java
  - id: openwiki-source-e14f726a9f0612c7c32a31bd
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/redis/MasterElectionService.java
  - id: openwiki-source-131075c0b1a6fd22773f5f72
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-97ae9adf7f31e5151e176dc2
    resource: repo://backend/src/main/resources/application.properties
  - id: openwiki-source-0838e4a720f37f91c4b8f7a2
    resource: repo://backend/src/main/resources/logback-spring.xml
  - id: openwiki-source-828d24ee9ccc8738afe410fb
    resource: repo://deploy.sh
  - id: openwiki-source-b79fbbd921df689b4bbdc82f
    resource: repo://docker-compose.yml
  - id: openwiki-source-d0086bc10a96e3d7e78bdb91
    resource: repo://DOCKER.md
  - id: openwiki-source-bb1ebe868e35e9e500714501
    resource: repo://Dockerfile
  - id: openwiki-source-6b72ccb83227836142347d82
    resource: repo://frontend/nginx.conf
  - id: openwiki-source-d70a29676ece6708fba2de92
    resource: repo://frontend/src/service/table-service.ts
  - id: openwiki-source-a73a96d488a35ac3cab51e68
    resource: repo://scripts/addax-admin.service
  - id: openwiki-source-49f5868b9bc0b9372e54c787
    resource: repo://scripts/data.sql
  - id: openwiki-source-a5ef1962cd825ded2bd8f225
    resource: repo://scripts/service.sh
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 部署与配置

系统有三种部署形态：Docker Compose 一体化镜像、裸机 `service.sh`/systemd、以及多节点共享同一 PostgreSQL/Redis 的集群部署。无论哪种形态，采集真正执行时都要求**运行采集的节点本机安装 Addax**，因为平台以子进程方式调用 `bin/addax.sh` 并读取本机日志目录。

## Docker 部署

### 镜像构建

`Dockerfile` 是三阶段构建（`Dockerfile#L6-L58`）：

1. `maven:3.9-eclipse-temurin-21` 构建后端 jar；
2. `node:22-alpine` 安装前端依赖并执行生产构建；
3. `eclipse-temurin:21-jre-alpine` 作为运行镜像，放入 `app.jar`、前端 `dist` 和 Nginx 配置。

运行镜像安装 Nginx 与 busybox-extras，暴露 `50601`（后端）与 `80`（前端），健康检查同时探测后端端口和 Nginx 首页（`Dockerfile#L64-L71`）。

容器入口在同一 shell 中启动 Java 与 Nginx（`Dockerfile#L75-L85`）：

```sh
java -XX:MaxRAMPercentage=75.0 ... -cp app.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher &
nginx -g 'daemon off;'
```

后端以 `PropertiesLauncher` 运行，`-Dloader.path=/app/drivers` 允许挂载自定义 JDBC 驱动。

### Compose 拓扑

`docker-compose.yml` 定义三个服务：

| 服务 | 镜像 | 作用 |
| --- | --- | --- |
| `postgres` | `postgres:15-alpine` | 持久化数据库，时区 Asia/Shanghai |
| `redis` | `redis:7-alpine` | 主节点选举、worker 心跳、并发 permit |
| `app` | `${DOCKER_REGISTRY}/addax-admin:${VERSION}` | 前后端合一 |

关键行为：

- `app` 依赖 PostgreSQL/Redis 健康后才启动；
- PostgreSQL 首次初始化时执行挂载到 `/docker-entrypoint-initdb.d/` 的 `schema.sql` 与 `data.sql`；
- 卷 `addax-postgres-data`、`addax-redis-data`、`addax-backend-logs` 持久化数据与日志；
- `./backend/drivers` 和 `./backend/job` 以 bind mount 方式挂入容器；
- 只发布 `FRONTEND_PORT:80`（默认 50080），后端 50601 默认不暴露到宿主机；
- 健康检查为 `nc -z localhost 50601` + `wget http://localhost/`。

（`docker-compose.yml#L3-L99`）

数据库初始化脚本**只在空数据目录首次启动时执行**。已有卷不会重新执行 `schema.sql`；结构升级由应用启动时的 `SchemaInitializer` 完成，见下文。

### Nginx 契约

前端 Nginx（`frontend/nginx.conf`）：

- `listen 80`，静态根目录 `/usr/share/nginx/html`；
- SPA 路由使用 `try_files $uri $uri/ /index.html`；
- 静态资源缓存 1 年并标记 immutable；
- `/api/v1/` 反向代理到 `http://localhost:50601`，保留 Host、X-Real-IP、X-Forwarded-For、X-Forwarded-Proto；
- 代理连接/发送/读取超时都是 60 秒；
- `client_max_body_size 100M`。

（`frontend/nginx.conf#L1-L45`）

60 秒读取超时对批量预览/提交是一个实际约束：前端 service 对这两类请求设置了 300/600 秒超时，但经过内置 Nginx 时仍会在 60 秒被切断。大批量操作需要调大 `proxy_read_timeout` 或绕过该代理。

### Addax 安装

Docker 镜像本身不包含 Addax。平台执行采集时调用 `<Addax 目录>/bin/addax.sh`，并读取 `<Addax 目录>/log/`，因此 Addax 必须与后端处于**同一个容器文件系统**，不能单独跑在另一个容器里（`DOCKER.md` 的“安装 Addax”一节）。

推荐做法：

1. 把 Addax 程序目录挂载到容器内 `/opt/app/addax`；
2. 登录后进入“系统管理 → 系统配置”，把“Addax 程序目录”设为容器内路径；
3. 确保所需 writer 插件位于 `/opt/app/addax/plugin/writer/`。

未安装 Addax 时应用可以启动，但提交采集任务会因找不到 `bin/addax.sh` 失败。

### Docker 部署注意事项

- **默认凭据必须修改**：compose 默认数据库密码 `addax_admin@123`，种子账号 `admin/admin123`、`user/user123`；生产必须替换。
- **JWT 密钥**：`application.properties` 带有内置默认 `jwt.secret`，生产必须通过 `JWT_SECRET` 覆盖（`backend/src/main/resources/application.properties#L9-L12`）。
- **`app.home` 未设置**：镜像启动命令没有传 `-Dapp.home`，而采集执行代码用 `System.getProperty("app.home")` 定位 job 目录（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L1122`）。走 Docker 执行采集时需要通过 `JAVA_TOOL_OPTIONS` 或修改 `CMD` 补上该属性，否则会在写作业文件阶段失败。裸机 `service.sh` 已设置该属性。
- **`spring.profiles.active=dev`** 是打包默认值，镜像未覆盖；生产建议通过环境变量改为 `prod` 并复核 `spring.jpa.show-sql` 等开发配置。
- 方式一只下载 compose 时若镜像拉取失败，compose 会尝试本地构建并因缺少 Dockerfile 报错；应先解决镜像拉取或改用源码构建。

## 裸机部署

### 目录布局

`scripts/service.sh` 假定 `APP_HOME` 是脚本父目录：

```text
/opt/app/addax-admin/
├── lib/       addax-admin-*.jar、java.security
├── drivers/   自定义 JDBC 驱动（loader.path）
├── config/    env.sh、application.properties
├── logs/      addax-admin.log、addax-admin.pid
└── job/       运行期 job 文件
```

脚本从 `config/env.sh` 加载环境变量，PID 与主日志分别写到 `logs/addax-admin.pid`、`logs/addax-admin.log`（`scripts/service.sh#L12-L26`）。

启动命令等价于（`scripts/service.sh#L29`、`#L70`）：

```sh
java -Dapp.home=$APP_HOME \
  -Dspring.config.location=$APP_HOME/config/application.properties \
  -Dlogging.file.path=$LOG_DIR \
  -Dloader.path=$APP_HOME/drivers \
  -Dloader.main=com.wgzhao.addax.admin.AdminApplication \
  -cp lib/addax-admin-*.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

`-Dapp.home` 是采集执行必需项，不能被省略。

### 启停行为

`service.sh` 支持 `start|stop|restart|status`：

- `start` 写入 PID 后最多等待 60 秒探测端口，失败则调用 `stop`。60 秒是为了覆盖 JVM/Spring 启动以及双节点同时重启时的数据库/Redis 争用（`scripts/service.sh#L72-L89`）；
- `stop` 先发 `TERM`，等待 30 秒，再 `KILL -9`（`scripts/service.sh#L124-L150`）；
- 端口解析顺序为 `JAVA_OPTS` 中的 `-Dserver.port` → `SERVER_PORT` 环境变量 → `application.properties` → 默认 50601。

### systemd 模板

`scripts/addax-admin.service` 是 systemd 模板：

```ini
EnvironmentFile=/opt/addax-admin/config/env.sh
ExecStart=/usr/bin/java -Dspring.config.location=/opt/addax-admin/config/application.properties \
  -jar /opt/addax-admin/lib/addax-admin-0.0.1-SNAPSHOT.jar
WorkingDirectory=/opt/addax-admin
```

（`scripts/addax-admin.service#L6-L12`）

使用前必须：

- 替换安装路径与 jar 版本号（模板写死 `0.0.1-SNAPSHOT`）；
- 在 `ExecStart` 补上 `-Dapp.home=/opt/addax-admin` 与 `-Dloader.path=.../drivers`，否则采集执行和外部驱动加载会失败；
- 确认运行用户对 `job/`、`logs/` 有写权限。

### 站点部署脚本

`deploy.sh` 是站点专用脚本，不是通用安装器：它直接构建后端与前端，rsync 到 `hive@etl01`、`hive@et201` 的 `/opt/app/addax-admin/lib/`，然后远程执行 `bin/service.sh restart`，最后把前端产物同步到 `etl01:/usr/share/nginx/html/addax-ui/`（`deploy.sh#L1-L14`）。

其中 rsync 源写的是 `./target/addax-admin-*.jar`，而当前多模块构建的产物在 `backend/target/`；使用前需要按实际构建布局调整。不要把它当作多节点部署的通用流程。

## 配置体系

### application.properties

`backend/src/main/resources/application.properties` 的固定项：

| 配置 | 值 |
| --- | --- |
| `server.servlet.context-path` | `/api/v1` |
| `server.port` | `50601` |
| `spring.profiles.active` | `dev` |
| `jwt.expiration` | `691200000`（8 小时） |
| `jwt.secret` | `${JWT_SECRET:内置默认值}` |
| `spring.jpa.open-in-view` | `false` |
| Hikari 连接池 | 最大 20、最小空闲 5、连接超时 30s |
| `node.concurrency.weight` | `${WEIGHT:1.0}` |
| `logging.file.path` | `${LOG_DIR:./logs}` |
| `alert.wechat.key` | `${WECOM_ROBOT_KEY:}`，多个 key 用逗号分隔 |

（`backend/src/main/resources/application.properties#L1-L46`）

数据库与 Redis 全部通过环境变量注入：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DB`。

### 环境变量

`.env.example` 是 Docker 模式的变量清单：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DOCKER_REGISTRY` / `VERSION` | `wgzhao` / `latest` | 镜像地址与版本 |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `addax_admin` / `addax_admin` / `addax_admin@123` | PostgreSQL 初始化 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `5432` / `addax_admin` / `addax_admin` / `addax_admin@123` | 后端连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` | `redis` / `6379` / 空 / `0` | 后端连接 |
| `LOG_DIR` | `/app/logs` | 应用日志目录 |
| `WEIGHT` | `1.0` | 节点并发权重，应用会夹到 `[0,1]` |
| `WECOM_ROBOT_KEY` | 空 | 企业微信告警机器人，多个用逗号分隔 |
| `SERVER_PORT` | `50601` | 后端容器内端口 |
| `FRONTEND_PORT` | `50080` | 宿主机访问端口 |

（`.env.example#L1-L38`）

`backend/config/env.template.sh` 是裸机 `env.sh` 模板，但有两处需要手改后才能使用：

- `export REDIS_DATABASE="0"` 与后端读取的 `REDIS_DB` 名称不一致；
- `export WEIGHT = 0.5` 带空格，不是合法赋值。

（`backend/config/env.template.sh#L17-L21`）

### 运行时字典配置

一部分配置不在文件里，而在数据库字典 `sys_item` 中，修改后无需重启：

| 字典 | key | 默认值/含义 |
| --- | --- | --- |
| 1000 | `ADDAX` | Addax 程序目录，默认 `/opt/app/addax` |
| 1000 | `SWITCH_TIME` | 切日时间，默认 16:20 |
| 1000 | `HDFS_PREFIX` | HDFS 路径前缀，默认 `/ods` |
| 1000 | `HDFS_STORAGE_FORMAT` | 默认 `orc` |
| 1000 | `HDFS_COMPRESS_FORMAT` | 默认 `lz4` |
| 1000 | `CONCURRENT_LIMIT` | 全局采集并发，默认 30 |
| 1000 | `QUEUE_SIZE` | 队列容量，默认 40 |
| 5000/5001 | `R2H`/`R2R`/`rR`/`wH`/`wR` | job 模板 |

（`scripts/data.sql#L25-L33`、`backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java#L83-L205`）

HDFS 前缀、存储/压缩格式、并发、切日时间、Addax 路径都属于这类“运行期配置”。修改后新任务立即生效，但已经生成的 job 模板需要刷新资源或切日才会重新编译。

## 数据库初始化与升级

### 全新安装

1. 创建 PostgreSQL 数据库与专用用户；
2. 依次执行 `scripts/schema.sql`、`scripts/data.sql`；
3. 启动后端。

Docker 模式由官方镜像的 `docker-entrypoint-initdb.d` 自动完成；裸机需要手工 `psql -f`。

种子数据创建两个账号：`admin/admin123`（管理员）与 `user/user123`（普通用户），密码为 BCrypt（`scripts/data.sql#L1-L10`）。上线前必须修改。

### 已有数据库升级

项目没有 Flyway/Liquibase。`SchemaInitializer.migrate()` 在 `@PostConstruct` 中运行，早于 Web 服务接收请求：

- 使用单个事务和 `pg_advisory_xact_lock`，多实例同时启动时只有一个执行迁移；
- 每一步幂等，失败整体回滚并阻止应用启动；
- 视图 `vw_etl_table_with_source` 最后重建，确保引用前面步骤新增的列。

（`backend/src/main/java/com/wgzhao/addax/admin/config/SchemaInitializer.java#L17-L44`、`#L290-L320`）

因此升级流程是：备份数据库 → 替换 jar/镜像 → 重启；迁移失败时应用不会带半迁移结构对外服务。

## 多节点部署

### 必要共享

- 所有节点连接**同一个 PostgreSQL** 与**同一个 Redis**；
- 每个节点运行相同的应用版本；
- 每个节点本机都要安装 Addax 及所需 writer 插件，路径可以不同，但必须与各自字典中的 `ADDAX` 配置一致。

Master 选举、worker 心跳、队列租约都在 Redis/PostgreSQL 中完成，见 [调度与分布式队列](../architecture/scheduling-and-queue.md)。节点实例 ID 由主机名 + JVM 进程名组成，无需手工配置（`backend/src/main/java/com/wgzhao/addax/admin/redis/MasterElectionService.java#L216-L226`）。

### 节点差异

- `WEIGHT` 控制节点参与调度的权重，弱节点可以调低；
- 节点本地维护 `job/`、`logs/` 和 `${ADDAX_HOME}/log/`，这些目录不需要跨节点共享；
- 执行中的进程只能由本节点的 `ExecutionManager` 直接管理；跨节点中止依赖 Redis 的 kill 信号，因此所有节点必须能访问同一 Redis。

### 容量与清理

- **应用日志**：Logback 按日/1000MB 滚动并 gzip，但没有配置 `maxHistory` 或 `totalSizeCap`，归档不会自动删除，需要外部 logrotate 或定期清理（`backend/src/main/resources/logback-spring.xml#L15-L30`）。
- **运行期 job 文件**：写在 `${app.home}/job/yyyy-MM-dd/`，代码中没有清理任务，需要运维按保留期删除。
- **数据库日志**：`addax_log` 默认保留 180 天，每日 03:15 清理；审计事件默认保留 365 天，低于 30 天拒绝清理（见 [数据模型与持久化](../backend/persistence-model.md)）。

## 检查清单

部署上线前至少确认：

1. `JWT_SECRET` 已覆盖默认值；
2. 数据库、Redis 密码与种子账号已修改；
3. 每个执行节点都能执行 `${ADDAX_HOME}/bin/addax.sh`；
4. RDBMS 目标所需的 writer 插件位于 `${ADDAX_HOME}/plugin/writer/`；
5. `app.home` 已设置且 `job/` 可写；
6. 反向代理超时足以覆盖批量预览/提交；
7. 日志与 job 文件有清理策略；
8. 升级前已备份 PostgreSQL。

## 相关页面

- [系统总览](../architecture/system-overview.md)
- [调度与分布式队列](../architecture/scheduling-and-queue.md)
- [数据模型与持久化](../backend/persistence-model.md)
- [Addax 任务契约与目标适配器](../integrations/addax-job-contract.md)
- [可观测性、审计与告警](observability-and-alerts.md)
