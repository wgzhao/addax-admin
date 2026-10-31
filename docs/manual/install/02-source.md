# 主机模式与本地开发

如果不使用 Docker，可以把后端 jar 和前端静态文件分别部署到主机。本文也包含二次开发时的本地启动流程。

## 主机模式部署

适用于已有 Java、PostgreSQL、Redis 和 Nginx 基础设施的环境。

### 环境要求

- Java 21
- Maven 3.8+
- Node.js 20.19+ 或 22.12+，推荐 22 LTS
- Yarn 1.22+
- PostgreSQL 15+
- Redis 6+
- Nginx
- netcat（`service.sh` 启动检查会用到 `nc`）
- Addax 采集引擎（运行时必需，见 [Docker 部署 · 安装 Addax](01-docker.md#安装-addax采集引擎必需)）

### 1. 初始化数据库

先创建数据库和用户：

```sql
CREATE USER addax_admin WITH PASSWORD 'addax_admin@123';
CREATE DATABASE addax_admin OWNER addax_admin;
```

然后在仓库根目录执行初始化脚本：

```bash
psql -U addax_admin -d addax_admin -f scripts/schema.sql
psql -U addax_admin -d addax_admin -f scripts/data.sql
```

- `scripts/schema.sql`：表结构、视图与索引
- `scripts/data.sql`：默认管理员和基础数据

默认账号为 `admin`，默认密码为 `admin123`。

### 2. 构建产物

```bash
# 后端
cd backend
mvn clean package -DskipTests
cd ..

# 前端
cd frontend
yarn install
yarn build
cd ..
```

构建结果：

- 后端：`backend/target/addax-admin-<version>.jar`
- 前端：`frontend/dist/`

### 3. 部署后端目录

`scripts/service.sh` 期望的目录布局：

```text
/opt/app/addax-admin/
├── bin/
│   └── service.sh
├── lib/
│   └── addax-admin-<version>.jar
├── config/
│   ├── application.properties
│   └── env.sh
├── drivers/                 # 自定义 JDBC 驱动
├── job/                     # 采集任务文件
└── logs/
```

创建目录并复制文件：

```bash
sudo mkdir -p /opt/app/addax-admin/{bin,lib,config,drivers,job,logs}
sudo chown -R addax:addax /opt/app/addax-admin

sudo cp backend/target/addax-admin-<version>.jar /opt/app/addax-admin/lib/
sudo cp scripts/service.sh /opt/app/addax-admin/bin/
sudo cp backend/src/main/resources/application.properties /opt/app/addax-admin/config/
sudo cp backend/config/env.template.sh /opt/app/addax-admin/config/env.sh
sudo chmod +x /opt/app/addax-admin/bin/service.sh
```

`service.sh` 会从 `lib/` 中查找 `addax-admin-*.jar`，并从 `config/env.sh` 加载环境变量。

### 4. 配置后端

编辑 `/opt/app/addax-admin/config/env.sh`：

```bash
export DB_HOST=127.0.0.1
export DB_PORT=5432
export DB_NAME=addax_admin
export DB_USERNAME=addax_admin
export DB_PASSWORD='替换为实际密码'

export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD=''
export REDIS_DB=0

export LOG_DIR=/opt/app/addax-admin/logs
export WEIGHT=1.0
export JWT_SECRET='替换为随机密钥'
```

- `JWT_SECRET` 在生产环境必须使用独立随机值；修改后所有旧令牌失效。
- `WEIGHT` 是节点并发权重，单节点通常保持 `1.0`。
- `config/application.properties` 可以保留默认值，环境变量会覆盖其中的数据库和 Redis 配置。
- Addax 程序目录不在 `env.sh` 中配置，而是登录后进入「系统配置」设置。

### 5. 启动与停止

确认当前 `java` 是 Java 21：

```bash
java -version
```

如果系统默认 Java 不是 21，先把 Java 21 放到 PATH 前面：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
```

启动、查看状态和停止：

```bash
/opt/app/addax-admin/bin/service.sh start
/opt/app/addax-admin/bin/service.sh status
/opt/app/addax-admin/bin/service.sh restart
/opt/app/addax-admin/bin/service.sh stop
```

后端默认监听 `50601`，日志写入 `LOG_DIR` 和 `logs/`。

### 6. 使用 systemd 托管（可选）

如果不想使用 `service.sh` 的 nohup 方式，可以使用 systemd。示例：

```ini
[Unit]
Description=Addax Admin
After=network.target

[Service]
Type=simple
User=addax
WorkingDirectory=/opt/app/addax-admin
EnvironmentFile=/opt/app/addax-admin/config/env.sh
ExecStart=/usr/lib/jvm/java-21-openjdk-amd64/bin/java \
  -Dapp.home=/opt/app/addax-admin \
  -Dspring.config.location=/opt/app/addax-admin/config/application.properties \
  -Dlogging.file.path=/opt/app/addax-admin/logs \
  -Dloader.path=/opt/app/addax-admin/drivers \
  -Dloader.main=com.wgzhao.addax.admin.AdminApplication \
  -cp /opt/app/addax-admin/lib/addax-admin-<version>.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

替换 `<version>` 后保存为 `/etc/systemd/system/addax-admin.service`：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now addax-admin
sudo systemctl status addax-admin
```

仓库中的 [`scripts/addax-admin.service`](../../../scripts/addax-admin.service) 是旧模板，使用前需要按实际路径和 jar 版本修改。不要同时用 systemd 和 `service.sh` 启动同一个实例。

### 7. 部署前端

把 `frontend/dist/` 部署到 Nginx 静态目录：

```bash
sudo mkdir -p /var/www/html/addax-ui
sudo rsync -a frontend/dist/ /var/www/html/addax-ui/
```

Nginx 配置示例：

```nginx
server {
    listen 80;
    server_name addax-admin.example.com;

    root /var/www/html/addax-ui;
    index index.html;

    location /api/v1/ {
        proxy_pass http://127.0.0.1:50601;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

保存后检查并重载：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

仓库中的 [`frontend/nginx.conf`](../../../frontend/nginx.conf) 是完整示例。如果前端和后端部署在不同主机，把 `proxy_pass` 改成后端主机的 `50601` 地址，并确保网络可达。

### 8. 配置 Addax 并验证

1. 打开前端地址，使用 `admin` / `admin123` 登录，并立即修改密码。
2. 进入「系统配置」，把「Addax 程序目录」设置为后端进程可访问的 Addax 安装路径，例如 `/opt/app/addax`。
3. 配置采集源、目标端，按 [快速上手](../usage/01-quick-start.md) 创建第一张采集表。
4. 检查后端端口和日志：

   ```bash
   ss -ltnp | grep 50601
   tail -f /opt/app/addax-admin/logs/addax-admin.log
   ```

### 9. 升级

1. 备份数据库。
2. 替换 `lib/` 下的 jar。
3. 重新构建并替换 `frontend/dist/`。
4. 重启后端：

   ```bash
   /opt/app/addax-admin/bin/service.sh restart
   ```

数据库迁移会在服务启动时自动执行，具体说明见 [升级与迁移](04-upgrade.md)。

## 本地开发模式

### 初始化数据库

```bash
psql -U addax_admin -d addax_admin -f scripts/schema.sql
psql -U addax_admin -d addax_admin -f scripts/data.sql
```

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认连接 `localhost:5432/addax_admin` 和 `localhost:6379`。如果本地环境不同，用环境变量覆盖：

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=addax_admin
export DB_USERNAME=addax_admin
export DB_PASSWORD=addax_admin@123
export REDIS_HOST=localhost
export REDIS_PORT=6379
mvn spring-boot:run
```

后端默认地址：<http://localhost:50601/api/v1>

### 启动前端

```bash
cd frontend
yarn install
VITE_API_HOST=http://localhost:50601 yarn dev
```

前端开发服务器默认地址：<http://localhost:3030>

Vite 会把 `/api` 请求代理到 `VITE_API_HOST`。如果后端地址不同，修改该环境变量即可。

## 开发约定

- 前端使用 Vue 3、Vite、TypeScript、Vuetify，页面位于 `frontend/src/views/`。
- 后端使用 Spring Boot 3、Spring Data JPA、PostgreSQL 和 Redis。
- 提交前至少完成相关构建和测试；前端可执行 `yarn type-check`、`yarn lint`、`yarn test`，交互改动建议补充或运行 `yarn e2e`。
- 不要直接修改 `.env` 或 `application.properties` 来临时验证配置，优先使用环境变量覆盖。

## 相关文档

- [Docker 部署](01-docker.md)
- [系统配置](03-config.md)
- [升级与迁移](04-upgrade.md)
- [快速上手](../usage/01-quick-start.md)
