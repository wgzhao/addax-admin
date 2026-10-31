# 故障排查

先确定问题属于哪一层：容器与进程、数据库、Redis、Addax 引擎、目标端契约、调度队列或前端代理。日志通常能直接定位层级。

## 容器与部署

### 拉取镜像失败 / registry-1.docker.io 超时

国内网络常见问题。按优先级尝试：

1. 为 Docker 配置镜像加速器并重启 Docker daemon。
2. 改用 Quay 备用源：

   ```bash
   echo "DOCKER_REGISTRY=quay.io/wgzhao" >> .env
   docker compose pull
   docker compose up -d
   ```

3. 使用源码构建：

   ```bash
   git clone https://github.com/wgzhao/addax-admin.git
   cd addax-admin
   docker compose up -d --build
   ```

### 报错 `failed to read dockerfile: open Dockerfile: no such file or directory`

只下载了 `docker-compose.yml`，没有下载 `Dockerfile`，镜像拉取失败后 Compose 回退到本地构建。先解决镜像拉取问题，或改用源码构建。

### 服务启动失败

```bash
docker compose logs app
docker compose logs postgres
docker compose ps
```

检查端口占用：

```bash
lsof -i :50080
```

清理并重启：

```bash
docker compose down
docker compose up -d
```

### 健康检查失败（容器反复重启）

```bash
docker inspect addax-admin | grep -A 10 Health
docker compose logs app | tail -100
```

重点检查数据库和 Redis 是否已就绪，以及 `DB_*`、`REDIS_*` 环境变量是否正确。

## 数据库

### 应用无法连接数据库

1. 等待 PostgreSQL 完全启动：

   ```bash
   docker compose logs postgres | grep "ready to accept connections"
   ```

2. 手动测试连接：

   ```bash
   docker compose exec postgres psql -U addax_admin -d addax_admin -c "SELECT 1;"
   ```

3. 检查 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`。
4. 确认 `scripts/schema.sql` 和 `scripts/data.sql` 已执行。

### 数据库权限不足

升级迁移需要 `ALTER TABLE` 和 `CREATE OR REPLACE VIEW` 权限。如果日志中出现迁移失败，检查数据库用户权限，修复后重启。

## Redis

### Redis 连接失败

检查：

- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DB`
- 容器网络是否互通
- Redis 是否已启动

```bash
docker compose exec redis redis-cli ping
```

应返回 `PONG`。

### 集群没有 Master / 任务不分配

检查所有节点的 Redis 配置是否一致。Master 通过 Redis 选举产生；如果节点使用了不同的 Redis 数据库或地址，会各自看到不同的集群状态。

## Addax 引擎

### 提交任务失败，找不到 `bin/addax.sh`

说明系统配置中的“Addax 程序目录”不正确，或 Addax 没有挂载进容器。

- 确认目录中包含 `bin/addax.sh`。
- Docker 部署时确认挂载路径与配置一致，例如 `/opt/app/addax`。
- 修改配置后重新发起任务。

### 任务启动但没有日志

检查：

- Addax 程序目录下的 `log/` 是否存在且可写。
- 后端 `LOG_DIR` 是否可写。
- 任务是否真正进入运行状态，而不是仍在排队。

### Addax 执行失败

在采集详情中查看“采集日志”和“采集任务配置”，确认：

- Reader/Writer 插件配置是否正确
- 源端和目标端连接是否可用
- 目标表结构是否满足契约
- 过滤条件是否符合源端 SQL 语法

## 目标端与字段映射

### `EXTERNAL` 模式提示目标表不存在

外部管理模式要求目标表在创建采集定义前已经存在。先在目标端建表，或改用 `MANAGED`。

### 类型不兼容或必填字段未映射

按“字段比较”中的阻断项处理：

- 补充目标字段
- 调整字段映射
- 修正目标字段类型
- 重新预览并提交

### 预览后目标结构发生变化

平台会在提交前重新读取目标元数据。如果目标结构在预览后变化，该表会被阻断并要求重新预览。重新执行预览即可，其他表不受影响。

### 目标表重复数据

检查：

- 是否关闭了“按源端主键在目标表建主键”
- `writeMode` 是否为 `overwrite` 或 `update`
- 是否配置了 `preSql` 先清理当日数据
- 目标表是否有主键或唯一键

关系型数据库目标没有主键/唯一键时会记录风险日志。

### 补数被跳过

没有日期维度的表不支持补数：

- HDFS 非分区表
- 未配置业务日期列的关系型目标表

补多个日期会互相覆盖，因此提交时会被跳过并记录风险日志。

## 调度与队列

### 到了采集时间没有触发

检查：

- 采集源是否启用
- 采集时间格式是否为 `HH:mm` 或 `HH:mm:ss`
- 采集日历是否允许当天采集
- 表级 `start_at` 是否覆盖了源级时间
- 表是否处于停采（`X`）或已退役状态
- 当前节点是否为 Master

### 任务一直排队

检查：

- 节点并发是否已满
- 源级最大并发是否已满
- 队列长度是否达到上限
- 节点心跳是否正常
- 是否存在长时间运行的任务占用 slot

### 任务卡在运行中

执行期间 Worker 每 20 秒续租。节点崩溃后，租约过期才会重新分配。可以通过日志确认：

- 租约续期是否失败
- Addax 子进程是否仍在运行
- 节点是否在线

不要手工修改 `running` 状态，除非确认旧进程已经停止；否则可能导致重复写入。

### `__max__` 回退为全量

如果取不到最大值或字段类型不兼容，系统会回退为 `1=1` 并记录风险日志。检查目标端上一业务日期分区、字段类型和字段名。

## 认证与前端

### JWT 认证错误

检查：

- `JWT_SECRET` 是否在升级或迁移后被改变
- 令牌是否过期
- 多节点是否使用相同的 `JWT_SECRET`

修改 `JWT_SECRET` 后所有旧令牌失效，需要重新登录。

### 前端 API 跨域或代理异常

开发模式检查：

- `VITE_API_BASE_URL`
- `VITE_API_HOST`
- `vite.config.ts` 的 proxy 配置

生产模式检查 Nginx 是否把 `/api` 反向代理到后端 `50601`。

### 端口冲突

默认端口：

- 前端：`3030`（开发）、`50080`（Docker）
- 后端：`50601`

确认端口未被其他进程占用。

## 日志位置

- 后端日志：`LOG_DIR`，默认 `./logs`，Docker 中为 `/app/logs`
- 后端日志配置：`backend/src/main/resources/logback-spring.xml`
- Addax 日志：`<Addax 程序目录>/log/`
- 容器日志：`docker compose logs -f app`

## 相关文档

- [执行监控与日志](01-monitoring.md)
- [常见问题](03-faq.md)
- [系统配置](../install/03-config.md)
- [升级与迁移](../install/04-upgrade.md)
