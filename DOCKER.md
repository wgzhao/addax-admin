# Addax Admin Docker 部署指南

本项目支持通过 Docker 和 Docker Compose 进行快速部署，包含前端、后端、数据库和缓存服务。

## 📋 前提条件

确保你的系统已安装以下软件：

- Docker 20.10 或更高版本
- Docker Compose 2.0 或更高版本

检查版本：

```bash
docker --version
docker compose version
```

## 🚀 快速启动

两种方式任选其一，快速部署请使用方式一（拉取预构建镜像，无需本地构建）。

### 方式一：拉取预构建镜像（推荐）

适用于快速体验与生产部署，不需要源码和构建工具链。

```bash
# 1. 创建部署目录
mkdir addax-admin && cd addax-admin
mkdir -p scripts

# 2. 下载部署文件（docker-compose.yml 和数据库初始化脚本）
wget https://raw.githubusercontent.com/wgzhao/addax-admin/master/docker-compose.yml
wget -P scripts/ https://raw.githubusercontent.com/wgzhao/addax-admin/master/scripts/schema.sql
wget -P scripts/ https://raw.githubusercontent.com/wgzhao/addax-admin/master/scripts/data.sql

# 3. 拉取镜像并启动（默认拉取 Docker Hub 上的 wgzhao/addax-admin:latest）
docker compose -f docker-compose.yml pull
docker compose -f docker-compose.yml up -d

# 4. 查看服务状态
docker compose -f docker-compose.yml ps
```

> **国内网络提示**：无法访问 Docker Hub 时（`registry-1.docker.io` 超时），请先为 Docker
> 配置镜像加速器（[官方文档](https://docs.docker.com/registry/recipes/mirror/)），
> 或改用 Quay 备用源：
> ```bash
> echo "DOCKER_REGISTRY=quay.io/wgzhao" >> .env
> docker compose -f docker-compose.yml pull
> docker compose -f docker-compose.yml up -d
> ```

> **注意**：该方式不下载根目录 `Dockerfile`，若镜像拉取失败请勿直接 `up -d`
> （compose 会尝试本地构建并报 `Dockerfile: no such file or directory`），
> 应优先解决镜像拉取问题，或改用方式二。

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

应该看到 3 个服务正在运行：

- `addax-admin` - 应用服务（前端 Nginx + 后端 Spring Boot 合一，前端端口 50080）
- `addax-postgres` - PostgreSQL 数据库
- `addax-redis` - Redis 缓存

### 访问应用

打开浏览器访问：

- **前端应用**: http://localhost:50080 （默认账号 admin / admin123）
- **后端 API**: http://localhost:50601/api/v1 （仅容器网络内使用，默认不对外暴露）

## 📦 服务架构

```
┌─────────────┐
│   Browser   │
└──────┬──────┘
       │  50080
┌──────▼──────┐     ┌──────────────┐
│    app      │────▶│   backend    │
│ (Nginx 80)  │     │  (Spring)    │
│  + backend  │     │  50601       │
└──────┬──────┘     └──────┬───────┘
       │                   │
       │             ┌─────▼─────┐   ┌──────────┐
       │             │ PostgreSQL│   │  Redis   │
       └─────────────│ Port 5432 │   │ Port 6379│
                     └───────────┘   └──────────┘
```

- `app`：前后端合一容器，Nginx 监听 80（映射到宿主机 50080），反向代理同容器内的 Spring Boot（50601）
- `postgres` / `redis`：仅在 compose 网络内互通，不暴露到宿主机

## 🔧 常用命令

### 启动服务
```bash
# 启动所有服务（后台运行）
docker compose up -d

# 启动所有服务（前台运行，可查看日志）
docker compose up

# 拉取最新镜像后重启（方式一部署时使用）
docker compose pull
docker compose up -d

# 重新构建并启动（方式二部署时使用）
docker compose up -d --build
```

### 停止服务
```bash
# 停止所有服务
docker compose stop

# 停止并删除容器
docker compose down

# 停止并删除容器、网络、卷（清空数据）
docker compose down -v
```

### 查看日志
```bash
# 查看所有服务日志
docker compose logs

# 查看特定服务日志
docker compose logs app

# 实时查看日志
docker compose logs -f app

# 查看最近 100 行日志
docker compose logs --tail=100 app
```

### 重启服务
```bash
docker compose restart app
```

## 🔍 健康检查

所有服务都配置了健康检查：

```bash
docker compose ps
docker inspect addax-admin | grep -A 10 Health
```

## 🗄️ 数据持久化

项目使用 Docker 卷进行数据持久化：

- `addax-postgres-data`: PostgreSQL 数据库数据
- `addax-redis-data`: Redis 缓存数据
- `addax-backend-logs`: 后端日志文件

另外有两个 bind mount 目录（方式二克隆源码时位于 `backend/` 下，方式一下载部署时 compose 会自动创建）：

- `./backend/drivers` -> `/app/drivers`：自定义 JDBC 驱动目录，放入后容器启动时自动加载
- `./backend/job` -> `/app/job`：采集任务文件目录

## ⚙️ 环境变量配置

复制 `.env.example` 为 `.env` 后按需修改（两种方式均可下载：

```bash
wget https://raw.githubusercontent.com/wgzhao/addax-admin/master/.env.example
cp .env.example .env
```

### 镜像配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DOCKER_REGISTRY` | `wgzhao` | 仓库用户名或地址前缀，最终镜像为 `${DOCKER_REGISTRY}/addax-admin:${VERSION}`；国内可改 `quay.io/wgzhao` |
| `VERSION` | `latest` | 镜像版本（对应 GitHub Release tag，如 `4.1.1`） |

### 数据库配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `addax_admin` / `addax_admin` / `addax_admin@123` | 容器内 PostgreSQL 初始化参数 |
| `POSTGRES_PORT` | `5432` | 数据库端口（默认不映射到宿主机） |

### 应用配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | 与数据库配置一致 | 后端连接数据库 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` | `redis` / `6379` / 空 / `0` | 后端连接 Redis |
| `SERVER_PORT` | `50601` | 后端服务端口（容器内） |
| `FRONTEND_PORT` | `50080` | 前端（Nginx）映射到宿主机的端口 |
| `LOG_DIR` | `/app/logs` | 后端日志目录（容器内） |
| `WEIGHT` | `1.0` | 节点并发权重因子 (0.0 - 1.0) |
| `WECOM_ROBOT_KEY` | 空 | 企业微信机器人 Key，用于告警（多个用逗号分隔） |

## 🔐 安全建议

**生产环境部署时，请务必：**

1. **修改默认密码**
   ```bash
   echo "POSTGRES_PASSWORD=使用强密码" >> .env
   echo "DB_PASSWORD=使用强密码" >> .env
   docker compose up -d
   ```

2. **限制端口暴露**：仅保留前端 `FRONTEND_PORT`，后端 `50601` 保持默认不映射

3. **配置 HTTPS**：使用 Nginx 或反向代理配置 SSL 证书

## 🐛 故障排查

### 拉取镜像失败 / registry-1.docker.io 超时

国内网络常见问题。按优先级尝试：

1. 为 Docker 配置镜像加速器后重启 Docker daemon
2. 改用 Quay 备用源：`echo "DOCKER_REGISTRY=quay.io/wgzhao" >> .env`
3. 使用方式二（克隆源码本地构建）

### 报错 `failed to read dockerfile: open Dockerfile: no such file or directory`

方式一（仅下载 compose 文件）下**没有 Dockerfile**，此报错表示镜像拉取失败后
compose 回退到了本地构建。请先解决镜像拉取问题（见上一条），或改用方式二。

### 服务启动失败

1. **检查日志**
   ```bash
   docker compose logs app
   docker compose logs postgres
   ```

2. **检查端口占用**
   ```bash
   lsof -i :50080
   ```

3. **清理并重启**
   ```bash
   docker compose down
   docker compose up -d
   ```

### 数据库连接失败

1. **等待数据库完全启动**
   ```bash
   docker compose logs postgres | grep "ready to accept connections"
   ```

2. **手动测试连接**
   ```bash
   docker compose exec postgres psql -U addax_admin -d addax_admin -c "SELECT 1;"
   ```

### 健康检查失败（容器反复重启）

```bash
# 查看容器健康状态与日志
docker inspect addax-admin | grep -A 10 Health
docker compose logs app | tail -100
```

## 🔄 更新部署

### 方式一（拉取镜像）更新

```bash
docker compose pull
docker compose up -d
```

### 方式二（源码构建）更新

```bash
git pull
docker compose up -d --build
```

## 🧹 清理

```bash
# 清理未使用的容器
docker container prune

# 清理未使用的镜像
docker image prune

# 完全重置（删除容器、网络、卷，清空数据）
docker compose down -v
```

## 📝 备份和恢复

### 备份数据库

```bash
# 导出数据库
docker compose exec postgres pg_dump -U addax_admin addax_admin > backup.sql
```

### 恢复数据库

```bash
# 方式 1: 直接导入
docker compose exec -T postgres psql -U addax_admin addax_admin < backup.sql

# 方式 2: 使用 docker cp
docker cp backup.sql addax-postgres:/tmp/backup.sql
docker compose exec postgres psql -U addax_admin addax_admin -f /tmp/backup.sql
```

## 📊 资源限制（可选）

```bash
# 在 docker-compose.yml 的 app 服务下添加
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 2G
    reservations:
      memory: 512M
```

## 🔗 相关链接

- [Docker 官方文档](https://docs.docker.com/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [PostgreSQL Docker 镜像](https://hub.docker.com/_/postgres)
- [Redis Docker 镜像](https://hub.docker.com/_/redis)

## 💬 获取帮助

如遇到问题，请：

1. 查看日志：`docker compose logs -f`
2. 检查服务状态：`docker compose ps`
3. 提交 Issue 到项目仓库

---

**祝你使用愉快！** 🎉
