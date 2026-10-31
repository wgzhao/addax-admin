# Docker 部署

本项目提供前后端合一的 Docker 镜像，推荐用 Docker Compose 同时启动 Addax Admin、PostgreSQL 和 Redis。

如果已有 Java、PostgreSQL、Redis 和 Nginx 基础设施，也可以使用 [主机模式部署](02-source.md#主机模式部署)，直接部署编译产物。

## 前提条件

- Docker 20.10 或更高版本
- Docker Compose 2.0 或更高版本
- 一台可以访问目标端数据库、HDFS/Hive 的服务器
- Addax 采集引擎（见下文“安装 Addax”）

检查版本：

```bash
docker --version
docker compose version
```

## 快速启动

两种方式任选其一。快速体验和生产部署优先使用方式一。

### 方式一：拉取预构建镜像（推荐）

适用于不需要源码和构建工具链的场景。

```bash
# 1. 创建部署目录
mkdir addax-admin && cd addax-admin
mkdir -p scripts

# 2. 下载部署文件
wget https://raw.githubusercontent.com/wgzhao/addax-admin/master/docker-compose.yml
wget -P scripts/ https://raw.githubusercontent.com/wgzhao/addax-admin/master/scripts/schema.sql
wget -P scripts/ https://raw.githubusercontent.com/wgzhao/addax-admin/master/scripts/data.sql

# 3. 可选：准备环境变量文件
wget https://raw.githubusercontent.com/wgzhao/addax-admin/master/.env.example
cp .env.example .env

# 4. 拉取镜像并启动
docker compose -f docker-compose.yml pull
docker compose -f docker-compose.yml up -d

# 5. 查看状态
docker compose -f docker-compose.yml ps
```

> 国内网络无法访问 Docker Hub 时（`registry-1.docker.io` 超时），请先配置 Docker 镜像加速器，或改用 Quay 备用源：
>
> ```bash
> echo "DOCKER_REGISTRY=quay.io/wgzhao" >> .env
> docker compose -f docker-compose.yml pull
> docker compose -f docker-compose.yml up -d
> ```

> 方式一不会下载仓库根目录的 `Dockerfile`。如果镜像拉取失败后直接执行 `up -d`，Compose 可能回退到本地构建并报 `Dockerfile: no such file or directory`。应先解决镜像拉取问题，或改用方式二。

### 方式二：克隆源码本地构建

适用于二次开发或希望构建最新代码的场景。

```bash
git clone https://github.com/wgzhao/addax-admin.git
cd addax-admin
docker compose up -d --build
```

### 查看服务状态

```bash
docker compose ps
```

正常情况下会看到 3 个服务：

- `addax-admin`：应用服务，Nginx 与 Spring Boot 合一，前端映射到宿主机 `50080`
- `addax-postgres`：PostgreSQL
- `addax-redis`：Redis

### 访问应用

- 前端：<http://localhost:50080>
- 默认账号：`admin`
- 默认密码：`admin123`

后端 API 默认只在 Compose 网络内暴露 `50601`，不对宿主机开放。

> 首次登录后请立即修改默认密码，并参见本文“安全建议”。

## 安装 Addax（采集引擎，必需）

Addax Admin 只是管理界面，本身不执行数据采集。调度时会以本地子进程方式调用 `<Addax 程序目录>/bin/addax.sh`，执行完成后读取 `<Addax 程序目录>/log/` 下的日志回写数据库。

因此 Addax 必须位于 Addax Admin 容器自身的文件系统里，不能单独运行在另一个容器中。应用镜像已经自带 JRE，Addax 推荐安装到 `/opt/app/addax`。

官方 Addax 镜像 `quay.io/wgzhao/addax` 中程序位于 `/opt/addax`，可以导出到宿主机：

```bash
# 1. 从官方镜像导出程序目录
docker create --name addax-tmp quay.io/wgzhao/addax:latest
docker cp addax-tmp:/opt/addax ./addax
docker rm addax-tmp
```

```yaml
# 2. 在 docker-compose.yml 的 app 服务下挂载
services:
  app:
    volumes:
      - backend_logs:/app/logs
      - ./backend/drivers:/app/drivers
      - ./backend/job:/app/job
      - ./addax:/opt/app/addax # 新增
```

```bash
# 3. 重启生效
docker compose up -d
```

```text
# 4. 登录后进入「系统配置」，把「Addax 程序目录」设为容器内路径
/opt/app/addax
```

> 未安装 Addax 时服务可以正常启动，但提交采集任务会因找不到 `bin/addax.sh` 而失败。

### 使用已有的 Addax 安装

如果宿主机上已经准备好 Addax，直接把该目录挂载到容器即可：

```yaml
services:
  app:
    volumes:
      - /path/to/addax:/opt/app/addax
```

挂载路径必须与系统配置中的“Addax 程序目录”一致，默认是 `/opt/app/addax`。

## 服务架构

```text
Browser
   │ 50080
   ▼
┌──────────────────────────────┐
│ app                          │
│ Nginx :80  +  Spring :50601  │
└──────────────┬───────────────┘
               │
       ┌───────┴────────┐
       ▼                ▼
  PostgreSQL        Redis
   :5432            :6379
```

- `app`：前后端合一，Nginx 反向代理同容器内的 Spring Boot
- `postgres` / `redis`：仅在 Compose 网络内互通，默认不暴露到宿主机

## 常用命令

```bash
# 启动
docker compose up -d

# 前台启动并查看日志
docker compose up

# 拉取最新镜像并重启
docker compose pull
docker compose up -d

# 重新构建并启动
docker compose up -d --build

# 停止
docker compose stop

# 停止并删除容器
docker compose down

# 停止并删除容器、网络和卷（会清空数据）
docker compose down -v

# 查看日志
docker compose logs
docker compose logs app
docker compose logs -f app
docker compose logs --tail=100 app

# 重启应用
docker compose restart app
```

## 健康检查

所有服务都配置了健康检查：

```bash
docker compose ps
docker inspect addax-admin | grep -A 10 Health
```

## 数据持久化

Docker 卷：

- `addax-postgres-data`：PostgreSQL 数据
- `addax-redis-data`：Redis 数据
- `addax-backend-logs`：后端日志

bind mount：

- `./backend/drivers` → `/app/drivers`：自定义 JDBC 驱动，容器启动时自动加载
- `./backend/job` → `/app/job`：采集任务文件目录
- `./addax` → `/opt/app/addax`：Addax 采集引擎（按需）

## 环境变量

复制 `.env.example` 为 `.env` 后按需修改：

```bash
wget https://raw.githubusercontent.com/wgzhao/addax-admin/master/.env.example
cp .env.example .env
docker compose up -d
```

### 镜像配置

| 变量              | 默认值   | 说明                                                             |
| ----------------- | -------- | ---------------------------------------------------------------- |
| `DOCKER_REGISTRY` | `wgzhao` | 镜像前缀，最终镜像为 `${DOCKER_REGISTRY}/addax-admin:${VERSION}` |
| `VERSION`         | `latest` | 镜像版本，对应 GitHub Release tag，如 `4.3.1`                    |

### 数据库配置

| 变量                | 默认值            | 说明                           |
| ------------------- | ----------------- | ------------------------------ |
| `POSTGRES_DB`       | `addax_admin`     | PostgreSQL 数据库名            |
| `POSTGRES_USER`     | `addax_admin`     | PostgreSQL 用户                |
| `POSTGRES_PASSWORD` | `addax_admin@123` | PostgreSQL 密码                |
| `POSTGRES_PORT`     | `5432`            | 数据库端口，默认不映射到宿主机 |

### 应用配置

| 变量                                                              | 默认值                      | 说明                                         |
| ----------------------------------------------------------------- | --------------------------- | -------------------------------------------- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | 与数据库配置一致            | 后端连接数据库                               |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB`       | `redis` / `6379` / 空 / `0` | 后端连接 Redis                               |
| `SERVER_PORT`                                                     | `50601`                     | 后端服务端口（容器内）                       |
| `FRONTEND_PORT`                                                   | `50080`                     | 前端映射到宿主机的端口                       |
| `LOG_DIR`                                                         | `/app/logs`                 | 后端日志目录（容器内）                       |
| `WEIGHT`                                                          | `1.0`                       | 节点并发权重，对应 `node.concurrency.weight` |
| `WECOM_ROBOT_KEY`                                                 | 空                          | 企业微信机器人 Key，多个用逗号分隔           |

更完整的运行参数说明见 [系统配置](03-config.md)。

## 安全建议

生产部署至少完成以下事项：

1. 修改默认密码：

   ```bash
   echo "POSTGRES_PASSWORD=使用强密码" >> .env
   echo "DB_PASSWORD=使用强密码" >> .env
   docker compose up -d
   ```

2. 仅暴露前端端口，保持后端 `50601` 不映射到宿主机。
3. 使用 Nginx 或反向代理配置 HTTPS。
4. 按最小权限原则准备源端、目标端数据库账号。
5. 首次登录后修改 `admin` 的默认密码。

## 更新部署

拉取镜像方式：

```bash
docker compose pull
docker compose up -d
```

源码构建方式：

```bash
git pull
docker compose up -d --build
```

升级前请先备份数据库，具体步骤和迁移说明见 [升级与迁移](04-upgrade.md)。

## 备份与恢复

### 备份数据库

```bash
docker compose exec postgres pg_dump -U addax_admin addax_admin > backup.sql
```

### 恢复数据库

```bash
# 方式一：直接导入
docker compose exec -T postgres psql -U addax_admin addax_admin < backup.sql

# 方式二：先复制进容器再导入
docker cp backup.sql addax-postgres:/tmp/backup.sql
docker compose exec postgres psql -U addax_admin addax_admin -f /tmp/backup.sql
```

## 清理

```bash
# 清理未使用的容器
docker container prune

# 清理未使用的镜像
docker image prune

# 完全重置（删除容器、网络、卷，清空数据）
docker compose down -v
```

## 资源限制（可选）

在 `docker-compose.yml` 的 `app` 服务下添加：

```yaml
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 2G
    reservations:
      memory: 512M
```

## 相关文档

- [源码构建与本地开发](02-source.md)
- [系统配置](03-config.md)
- [升级与迁移](04-upgrade.md)
- [故障排查](../operations/02-troubleshooting.md)
