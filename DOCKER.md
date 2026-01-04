# Addax Admin Docker 部署指南

本项目支持通过 Docker 和 Docker Compose 进行快速部署，包含前端、后端、数据库和缓存服务。

## 📋 前提条件

确保你的系统已安装以下软件：

- Docker 20.10 或更高版本
- Docker Compose 2.0 或更高版本

检查版本：
```bash
docker --version
docker-compose --version
```

## 🚀 快速启动

### 1. 克隆项目（如果还未克隆）

```bash
git clone <repository-url>
cd addax-admin
```

### 2. 构建并启动所有服务

```bash
docker-compose up -d
```

这个命令会：
- 自动构建前端和后端 Docker 镜像
- 拉取 PostgreSQL 和 Redis 官方镜像
- 启动所有服务并进行数据库初始化
- 在后台运行所有容器

### 3. 查看服务状态

```bash
docker-compose ps
```

应该看到 4 个服务正在运行：
- `addax-frontend` - 前端服务 (端口 80)
- `addax-backend` - 后端服务 (端口 50601)
- `addax-postgres` - PostgreSQL 数据库 (端口 5432)
- `addax-redis` - Redis 缓存 (端口 6379)

### 4. 访问应用

打开浏览器访问：
- **前端应用**: http://localhost
- **后端 API**: http://localhost:50601/api/v1

## 📦 服务架构

```
┌─────────────┐
│   Browser   │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│  Frontend   │────▶│   Backend   │
│   (Nginx)   │     │  (Spring)   │
│   Port 80   │     │  Port 50601 │
└─────────────┘     └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    ▼             ▼
              ┌──────────┐  ┌─────────┐
              │PostgreSQL│  │  Redis  │
              │Port 5432 │  │Port 6379│
              └──────────┘  └─────────┘
```

## 🔧 常用命令

### 启动服务
```bash
# 启动所有服务（后台运行）
docker-compose up -d

# 启动所有服务（前台运行，可查看日志）
docker-compose up

# 启动特定服务
docker-compose up -d backend
```

### 停止服务
```bash
# 停止所有服务
docker-compose stop

# 停止特定服务
docker-compose stop backend

# 停止并删除容器
docker-compose down

# 停止并删除容器、网络、卷（清空数据）
docker-compose down -v
```

### 查看日志
```bash
# 查看所有服务日志
docker-compose logs

# 查看特定服务日志
docker-compose logs backend
docker-compose logs frontend

# 实时查看日志
docker-compose logs -f backend

# 查看最近 100 行日志
docker-compose logs --tail=100 backend
```

### 重启服务
```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart backend
```

### 重新构建镜像
```bash
# 重新构建所有镜像
docker-compose build

# 重新构建特定服务
docker-compose build backend

# 强制重新构建（不使用缓存）
docker-compose build --no-cache

# 重新构建并启动
docker-compose up -d --build
```

### 进入容器
```bash
# 进入后端容器
docker-compose exec backend sh

# 进入数据库容器
docker-compose exec postgres psql -U addax_admin -d addax_admin

# 进入 Redis 容器
docker-compose exec redis redis-cli
```

## 🔍 健康检查

所有服务都配置了健康检查：

```bash
# 检查服务健康状态
docker-compose ps

# 查看详细健康信息
docker inspect addax-backend | grep -A 10 Health
```

## 🗄️ 数据持久化

项目使用 Docker 卷进行数据持久化：

- `addax-postgres-data`: PostgreSQL 数据库数据
- `addax-redis-data`: Redis 缓存数据
- `addax-backend-logs`: 后端日志文件

查看卷信息：
```bash
docker volume ls | grep addax
docker volume inspect addax-postgres-data
```

## ⚙️ 环境变量配置

可以通过修改 `docker-compose.yml` 中的环境变量来自定义配置：

### 数据库配置
```yaml
environment:
  POSTGRES_DB: addax_admin          # 数据库名
  POSTGRES_USER: addax_admin        # 数据库用户
  POSTGRES_PASSWORD: addax_admin@123 # 数据库密码
```

### 后端配置
```yaml
environment:
  DB_HOST: postgres                  # 数据库主机
  DB_PORT: 5432                      # 数据库端口
  REDIS_HOST: redis                  # Redis 主机
  REDIS_PORT: 6379                   # Redis 端口
```

## 🔐 安全建议

**生产环境部署时，请务必：**

1. **修改默认密码**
   ```yaml
   POSTGRES_PASSWORD: 使用强密码
   ```

2. **限制端口暴露**
   - 移除不需要外部访问的端口映射
   - 仅保留前端 80 端口

3. **使用环境变量文件**
   ```bash
   # 创建 .env 文件
   cp .env.example .env
   # 编辑 .env 文件设置敏感信息
   ```

4. **配置 HTTPS**
   - 使用 Nginx 或反向代理配置 SSL 证书
   - 建议使用 Let's Encrypt

## 🐛 故障排查

### 服务启动失败

1. **检查日志**
   ```bash
   docker-compose logs backend
   docker-compose logs postgres
   ```

2. **检查端口占用**
   ```bash
   # macOS/Linux
   lsof -i :80
   lsof -i :50601
   lsof -i :5432
   ```

3. **清理并重启**
   ```bash
   docker-compose down
   docker-compose up -d
   ```

### 数据库连接失败

1. **等待数据库完全启动**
   ```bash
   docker-compose logs postgres | grep "ready to accept connections"
   ```

2. **手动测试连接**
   ```bash
   docker-compose exec postgres psql -U addax_admin -d addax_admin -c "SELECT 1;"
   ```

### 前端无法访问后端

1. **检查后端服务状态**
   ```bash
   curl http://localhost:50601/api/v1/actuator/health
   ```

2. **检查 Nginx 配置**
   ```bash
   docker-compose exec frontend cat /etc/nginx/conf.d/default.conf
   ```

## 🔄 更新部署

### 更新代码后重新部署

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker-compose up -d --build
```

### 仅更新前端
```bash
docker-compose up -d --build frontend
```

### 仅更新后端
```bash
docker-compose up -d --build backend
```

## 📊 性能优化

### 调整资源限制

在 `docker-compose.yml` 中添加：

```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          memory: 512M
```

### 数据库性能调优

```bash
# 进入数据库容器
docker-compose exec postgres psql -U addax_admin -d addax_admin

# 查看连接数
SELECT count(*) FROM pg_stat_activity;

# 查看慢查询
SELECT * FROM pg_stat_statements ORDER BY total_time DESC LIMIT 10;
```

## 🧹 清理

### 清理未使用的资源

```bash
# 清理未使用的容器
docker container prune

# 清理未使用的镜像
docker image prune

# 清理未使用的卷
docker volume prune

# 清理所有未使用资源
docker system prune -a
```

### 完全重置

```bash
# 停止并删除所有容器和卷
docker-compose down -v

# 删除所有相关镜像
docker images | grep addax | awk '{print $3}' | xargs docker rmi -f

# 重新开始
docker-compose up -d --build
```

## 📝 备份和恢复

### 备份数据库

```bash
# 导出数据库
docker-compose exec postgres pg_dump -U addax_admin addax_admin > backup.sql

# 或使用 docker cp
docker-compose exec postgres pg_dump -U addax_admin addax_admin -f /tmp/backup.sql
docker cp addax-postgres:/tmp/backup.sql ./backup.sql
```

### 恢复数据库

```bash
# 方法 1: 直接导入
docker-compose exec -T postgres psql -U addax_admin addax_admin < backup.sql

# 方法 2: 使用 docker cp
docker cp backup.sql addax-postgres:/tmp/backup.sql
docker-compose exec postgres psql -U addax_admin addax_admin -f /tmp/backup.sql
```

## 🔗 相关链接

- [Docker 官方文档](https://docs.docker.com/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [PostgreSQL Docker 镜像](https://hub.docker.com/_/postgres)
- [Redis Docker 镜像](https://hub.docker.com/_/redis)
- [Nginx Docker 镜像](https://hub.docker.com/_/nginx)

## 💬 获取帮助

如遇到问题，请：
1. 查看日志：`docker-compose logs -f`
2. 检查服务状态：`docker-compose ps`
3. 提交 Issue 到项目仓库

---

**祝你使用愉快！** 🎉
