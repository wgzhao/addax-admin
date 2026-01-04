# 配置目录

此目录用于存放自定义的应用配置文件（可选）。

## 使用场景

如果你需要完全自定义应用配置，而不是使用环境变量，可以：

1. 复制默认的 `application.properties` 到此目录
2. 修改配置文件
3. 在 `docker-compose.yml` 中取消注释配置文件挂载行

## 示例

### 1. 创建自定义配置文件

```bash
# 在项目根目录下
mkdir -p backend/config
cat > backend/config/application.properties << 'EOF'
spring.profiles.active=prod
server.servlet.context-path=/api/v1
server.port=50601

# 数据库配置
spring.datasource.url=jdbc:postgresql://your-db-host:5432/your-database
spring.datasource.username=your-username
spring.datasource.password=your-password

# Redis 配置
spring.data.redis.host=your-redis-host
spring.data.redis.port=6379

# 其他自定义配置...
EOF
```

### 2. 启用配置文件挂载

编辑 `docker-compose.yml`，在 backend 服务的 volumes 部分取消注释：

```yaml
volumes:
  - backend_logs:/app/logs
  - ./backend/drivers:/app/drivers
  - ./backend/job:/app/job
  # 取消下面这行的注释
  - ./backend/config/application.properties:/app/config/application.properties:ro
```

### 3. 重启服务

```bash
docker-compose restart backend
```

## 注意事项

⚠️ **优先级说明**：
- 环境变量的优先级 > 配置文件
- 如果同时设置了环境变量和配置文件，环境变量会覆盖配置文件的设置

🔒 **只读挂载**：
- 使用 `:ro` 后缀表示只读挂载，防止容器内部修改配置文件

📝 **推荐方式**：
- 对于简单配置，推荐使用 `.env` 文件设置环境变量
- 对于复杂配置或批量修改，使用配置文件挂载更方便
