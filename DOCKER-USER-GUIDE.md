# Docker 用户快速部署指南

本指南专为使用预构建镜像的用户提供，无需编译即可快速部署 Addax Admin。

## 🚀 快速开始（5 分钟部署）

### 1. 准备部署目录

```bash
# 创建项目目录
mkdir addax-admin && cd addax-admin

# 创建必要的子目录
mkdir -p scripts drivers job
```

### 2. 下载必要文件

```bash
# 下载 docker-compose 配置文件
wget https://raw.githubusercontent.com/wgzhao/addax-admin/master/docker-compose.yml

# 下载环境变量配置示例
wget https://raw.githubusercontent.com/wgzhao/addax-admin/master/.env.example

# 下载数据库初始化脚本
wget -P scripts/ https://raw.githubusercontent.com/wgzhao/addax-admin/master/scripts/schema.sql
wget -P scripts/ https://raw.githubusercontent.com/wgzhao/addax-admin/master/scripts/data.sql
```

### 3. 配置环境变量

```bash
# 复制配置文件
cp .env.example .env

# 编辑配置文件（至少修改数据库密码）
vim .env
```

**最小化配置示例**：
```bash
# 修改数据库密码（生产环境必须修改！）
POSTGRES_PASSWORD=your_strong_password_here
DB_PASSWORD=your_strong_password_here
```

### 4. 启动服务

```bash
# 启动所有服务
docker-compose -f docker-compose.yml up -d

# 查看服务状态
docker-compose -f docker-compose.yml ps

# 查看日志
docker-compose -f docker-compose.yml logs -f
```

### 5. 访问应用

http://localhost:50080

## 📁 最终目录结构

```
addax-admin/
├── docker-compose.prod.yml    # Docker Compose 配置文件
├── .env                        # 环境变量配置（自己创建）
├── scripts/                    # 数据库初始化脚本
│   ├── schema.sql
│   └── data.sql
├── drivers/                    # JDBC 驱动目录（可选）
│   └── your-jdbc-driver.jar
└── job/                        # 任务配置目录（自动创建）
```

## 🔧 高级配置

### 1. 添加自定义 JDBC 驱动

如果你需要连接特定数据库，将 JDBC 驱动放入 `drivers` 目录：

```bash
# 例如：添加 MySQL 驱动
cd drivers
wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

# 重启后端服务以加载驱动
docker-compose -f docker-compose.yml restart backend
```

**常用驱动下载**：
- MySQL: `wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar`
- Oracle: 访问 https://www.oracle.com/database/technologies/jdbc-ucp-downloads.html
- SQL Server: `wget https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.4.2.jre11/mssql-jdbc-12.4.2.jre11.jar`
- ClickHouse: `wget https://repo1.maven.org/maven2/com/clickhouse/clickhouse-jdbc/0.5.0/clickhouse-jdbc-0.5.0-all.jar`

### 2. 使用外部数据库

如果你已经有 PostgreSQL 数据库，编辑 `.env` 文件：

```bash
# 连接到外部数据库
DB_HOST=your-db-host.example.com
DB_PORT=5432
DB_NAME=addax_admin
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

然后在 `docker-compose.yml` 中注释掉或删除 postgres 服务。

### 3. 使用外部 Redis

编辑 `.env` 文件：

```bash
REDIS_HOST=your-redis-host.example.com
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
REDIS_DB=0
```

### 4. 修改端口映射

如果 80 端口已被占用，修改 `.env` 文件：

```bash
# 使用其他端口
FRONTEND_PORT=8080
BACKEND_PORT=8601
```

然后访问 http://localhost:8080

### 5. 完全自定义配置（高级）

如果环境变量不能满足需求，可以使用配置文件：

```bash
# 创建配置目录
mkdir -p config

# 创建自定义配置文件
cat > config/application.properties << 'EOF'
spring.profiles.active=prod
server.servlet.context-path=/api/v1
server.port=50601

# 你的完整配置...
EOF
```

编辑 `docker-compose.prod.yml`，取消配置文件挂载的注释：

```yaml
volumes:
  - ./config/application.properties:/app/config/application.properties:ro
```

## 📊 环境变量完整说明

### Docker 镜像配置
| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DOCKER_REGISTRY` | `wgzhao` | Docker 仓库地址 |
| `VERSION` | `latest` | 镜像版本标签 |

### 数据库配置
| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` | `postgres` | 数据库主机地址 |
| `DB_PORT` | `5432` | 数据库端口 |
| `DB_NAME` | `addax_admin` | 数据库名称 |
| `DB_USERNAME` | `addax_admin` | 数据库用户名 |
| `DB_PASSWORD` | `addax_admin@123` | 数据库密码 ⚠️生产环境必改 |

### Redis 配置
| 变量 | 默认值 | 说明 |
|------|--------|------|
| `REDIS_HOST` | `redis` | Redis 主机地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | (空) | Redis 密码 |
| `REDIS_DB` | `0` | Redis 数据库编号 |

### 应用配置
| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_PORT` | `50601` | 后端服务端口 |
| `WEIGHT` | `1.0` | 节点并发权重 (0.0-1.0) |
| `WECOM_ROBOT_KEY` | (空) | 企业微信机器人 Key |

### 端口映射
| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FRONTEND_PORT` | `80` | 前端服务宿主机端口 |
| `BACKEND_PORT` | `50601` | 后端服务宿主机端口 |

## 🔄 常用操作

### 查看服务状态
```bash
docker-compose -f docker-compose.yml ps
```

### 查看日志
```bash
# 查看所有服务日志
docker-compose -f docker-compose.yml logs -f

# 查看特定服务日志
docker-compose -f docker-compose.yml logs -f backend
docker-compose -f docker-compose.yml logs -f frontend
```

### 重启服务
```bash
# 重启所有服务
docker-compose -f docker-compose.yml restart

# 重启特定服务
docker-compose -f docker-compose.yml restart backend
```

### 停止服务
```bash
# 停止所有服务
docker-compose -f docker-compose.yml stop

# 停止并删除容器（数据卷保留）
docker-compose -f docker-compose.yml down

# 停止并删除容器和数据卷（⚠️会清空所有数据）
docker-compose -f docker-compose.yml down -v
```

### 更新到最新版本
```bash
# 拉取最新镜像
docker-compose -f docker-compose.yml pull

# 重新启动服务
docker-compose -f docker-compose.yml up -d

# 清理旧镜像
docker image prune
```

### 备份数据
```bash
# 备份数据库
docker-compose -f docker-compose.yml exec postgres pg_dump -U addax_admin addax_admin > backup_$(date +%Y%m%d).sql

# 备份任务配置
tar czf job_backup_$(date +%Y%m%d).tar.gz job/
```

### 恢复数据
```bash
# 恢复数据库
docker-compose -f docker-compose.yml exec -T postgres psql -U addax_admin addax_admin < backup_20260104.sql

# 恢复任务配置
tar xzf job_backup_20260104.tar.gz
```

## 🔐 生产环境安全建议

1. **修改默认密码**
   ```bash
   # 在 .env 文件中设置强密码
   POSTGRES_PASSWORD=<strong-password>
   DB_PASSWORD=<strong-password>
   ```

2. **限制端口暴露**
   
   编辑 `docker-compose.yml`，移除不需要外部访问的端口映射：
   ```yaml
   # 注释掉或删除以下端口映射
   # ports:
   #   - "5432:5432"  # PostgreSQL
   #   - "6379:6379"  # Redis
   ```

3. **使用反向代理**
   
   生产环境建议使用 Nginx 或 Traefik 作为反向代理，配置 HTTPS：
   ```nginx
   server {
       listen 443 ssl http2;
       server_name your-domain.com;
       
       ssl_certificate /path/to/cert.pem;
       ssl_certificate_key /path/to/key.pem;
       
       location / {
           proxy_pass http://localhost:80;
       }
   }
   ```

4. **配置防火墙**
   ```bash
   # 仅开放必要端口
   ufw allow 80/tcp
   ufw allow 443/tcp
   ufw enable
   ```

5. **定期备份**
   
   设置 cron 定时备份：
   ```bash
   # 编辑 crontab
   crontab -e
   
   # 每天凌晨 2 点备份
   0 2 * * * cd /path/to/addax-admin && docker-compose -f docker-compose.yml exec -T postgres pg_dump -U addax_admin addax_admin > /backup/addax_$(date +\%Y\%m\%d).sql
   ```

## 🐛 故障排查

### 服务无法启动

1. **检查端口占用**
   ```bash
   # Linux/macOS
   lsof -i :80
   lsof -i :50601
   
   # 或使用 netstat
   netstat -tuln | grep -E '80|50601'
   ```

2. **查看详细日志**
   ```bash
   docker-compose -f docker-compose.yml logs backend
   ```

3. **检查数据库连接**
   ```bash
   docker-compose -f docker-compose.yml exec postgres psql -U addax_admin -d addax_admin -c "SELECT 1;"
   ```

### 镜像拉取失败

```bash
# 检查网络连接
curl -I https://hub.docker.com

# 手动拉取镜像
docker pull wgzhao/addax-admin-backend:latest
docker pull wgzhao/addax-admin-frontend:latest

# 中国大陆用户可配置镜像加速器
# 编辑 /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com"
  ]
}

# 重启 Docker
sudo systemctl restart docker
```

### 前端无法访问后端

1. **检查后端健康状态**
   ```bash
   curl http://localhost:50601/api/v1/
   ```

2. **检查容器网络**
   ```bash
   docker network inspect addax-network
   ```

3. **进入前端容器检查 Nginx 配置**
   ```bash
   docker-compose -f docker-compose.yml exec frontend cat /etc/nginx/conf.d/default.conf
   ```

## 📞 获取帮助

- **项目仓库**: https://github.com/wgzhao/addax-admin
- **提交 Issue**: https://github.com/wgzhao/addax-admin/issues
- **查看文档**: [DOCKER.md](DOCKER.md) | [DOCKER-PUBLISH.md](DOCKER-PUBLISH.md)

---

**享受 Docker 一键部署的便利！** 🐳
