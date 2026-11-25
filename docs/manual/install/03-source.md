# 源码构建与本地开发

如果不使用 Docker，可以把后端 jar 和前端静态文件分别部署到主机。本文也包含二次开发时的本地启动流程。

如果目标主机不需要构建工具链，优先使用 [主机部署（发行包）](02-host.md)；本文适用于需要从源码构建或参与二次开发的场景。

## 从源码构建部署

适用于已有 Java、PostgreSQL、Redis 和 Nginx 基础设施的环境。

### 环境要求

- Java 21（`JAVA_HOME` 必须指向 Java 21：Maven 按 `JAVA_HOME` 选择编译器，`service.sh` 也用它启动）
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

数据库为空时，后端首次启动会按版本顺序执行镜像内置的迁移链完成初始化，一般不需要手工执行 SQL。需要提前建表或检查脚本内容时，也可以在仓库根目录按版本顺序手工执行：

```bash
psql -U addax_admin -d addax_admin -f backend/src/main/resources/db/migration/V0001__baseline.sql
psql -U addax_admin -d addax_admin -f backend/src/main/resources/db/migration/V0002__seed_reference_data.sql
```

- `V0001__baseline.sql`：表结构、视图与索引
- `V0002__seed_reference_data.sql`：默认管理员和基础数据
- 更高版本按编号顺序执行；已执行版本记录在 `schema_migration`

默认账号为 `admin`，默认密码为 `admin123`。

### 2. 构建产物

```bash
# 系统默认 Java 不是 21 时，Maven 会报“无效的目标发行版: 21”，先指向 Java 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

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
# 创建服务用户（发行包的 install.sh 会自动创建；源码部署需要手工创建）
# 该账号同时执行 addax.sh 采集作业：HDFS 只允许 hive 写入时，把下面的 addax 换成 hive，
# 或者直接使用已存在的 hive 账号（跳过 useradd），并保持 chown 与 sudo -u 一致。
sudo useradd --system --shell /usr/sbin/nologin addax 2>/dev/null || true
sudo mkdir -p /opt/app/addax-admin/{bin,lib,config,drivers,job,logs}
sudo chown -R addax:addax /opt/app/addax-admin

sudo cp backend/target/addax-admin-<version>.jar /opt/app/addax-admin/lib/
sudo cp scripts/service.sh /opt/app/addax-admin/bin/
sudo cp backend/src/main/resources/application.properties /opt/app/addax-admin/config/
sudo cp backend/config/env.template.sh /opt/app/addax-admin/config/env.sh
sudo chmod +x /opt/app/addax-admin/bin/service.sh
```

`service.sh` 会从 `lib/` 中查找 `addax-admin-*.jar`，并从 `config/env.sh` 加载环境变量。

`service.sh` 必须由 `/opt/app/addax-admin` 的属主（默认 `addax`）执行，否则无法写入 `logs/` 下的 PID 和日志文件；第 5 步统一使用 `sudo -u addax`。如果希望用你自己的账号运行，把上面的 `chown` 目标改成该账号。

运行账号还是目标端权限的前提：后端以该账号启动 `addax.sh`，采集作业写 HDFS 时继承同一账号。很多 HDFS 环境只允许 `hive` 写目标目录，此时应从一开始就用 `hive` 作为服务账号（`useradd` 可跳过、`chown` 与所有 `sudo -u` 都改成 `hive`），否则到运行期才会出现权限不足。

### 4. 配置后端

编辑 `/opt/app/addax-admin/config/env.sh`：

```bash
# service.sh 由 sudo -u addax 或 systemd 启动时不会继承终端的 PATH，
# 必须在这里固定 Java 21，否则会退回系统默认 JDK（常见为 17）导致启动失败
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

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
- `JAVA_HOME` 必须写在 `env.sh` 里：`sudo` 和 systemd 都不会继承你终端里 `export` 的变量。
- `config/application.properties` 可以保留默认值，环境变量会覆盖其中的数据库和 Redis 配置。
- Addax 程序目录不在 `env.sh` 中配置，而是登录后进入「系统配置」设置。

### 5. 启动与停止

`service.sh` 会加载 `env.sh` 中的 `JAVA_HOME`，确认它指向 Java 21 后，以服务用户启动、查看状态和停止：

```bash
sudo -u addax /opt/app/addax-admin/bin/service.sh start
sudo -u addax /opt/app/addax-admin/bin/service.sh status
sudo -u addax /opt/app/addax-admin/bin/service.sh restart
sudo -u addax /opt/app/addax-admin/bin/service.sh stop
```

不要用普通用户直接执行 `service.sh start`：写 `logs/` 会因权限不足失败（脚本返回非零退出码）。

后端默认监听 `50601`，日志写入 `LOG_DIR` 和 `logs/`。

### 6. 使用 systemd 托管（可选）

如果不想使用 `service.sh` 的 nohup 方式，可以使用仓库里的 [systemd 模板](../../../scripts/bundle/host/addax-admin.service)。模板中的 `@APP_HOME@`、`@JAR_PATH@`、`@APP_USER@`、`@JAVA_BIN@` 需要按实际路径替换，保存为 `/etc/systemd/system/addax-admin.service`：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now addax-admin
sudo systemctl status addax-admin
```

模板通过 `sh -c '. config/env.sh && exec java ...'` 加载配置，因为 `env.sh` 使用 `export` 语句，systemd 的 `EnvironmentFile` 不解析这种写法。不要同时用 systemd 和 `service.sh` 启动同一个实例。

### 7. 部署前端

把 `frontend/dist/` 部署到 Nginx 静态目录：

```bash
sudo mkdir -p /var/www/html/addax-admin-ui
sudo rsync -a frontend/dist/ /var/www/html/addax-admin-ui/
```

Nginx 配置示例：

```nginx
server {
    listen 80;
    server_name addax-admin.example.com;

    root /var/www/html/addax-admin-ui;
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

把配置保存为 `/etc/nginx/sites-available/addax-admin`，启用后检查并重载：

```bash
sudo ln -sf /etc/nginx/sites-available/addax-admin /etc/nginx/sites-enabled/addax-admin
sudo nginx -t
sudo systemctl reload nginx
```

`server_name` 必须与访问时使用的域名一致（用 IP 访问会落到 Nginx 默认站点）。本地验证时可把示例域名写入 `/etc/hosts`：

```text
127.0.0.1 addax-admin.example.com
```

仓库中的 [`frontend/nginx.conf`](../../../frontend/nginx.conf) 是完整示例。如果前端和后端部署在不同主机，把 `proxy_pass` 改成后端主机的 `50601` 地址，并确保网络可达。

### 8. 配置 Addax 并验证

1. 打开前端地址，使用 `admin` / `admin123` 登录，并立即修改密码。
2. 进入「系统配置」，把「Addax 程序目录」设置为后端进程可访问的 Addax 安装路径，例如 `/opt/app/addax`。
3. 保证服务账号对 Addax 目录可写（`bin/addax.sh` 要可执行、`log/` 要可写）。账号与前面步骤保持一致：

   ```bash
   sudo chown -R addax:addax /opt/app/addax   # 服务账号是 hive 时改成 hive:hive
   ```

4. 配置 Addax 的上报地址。`/opt/app/addax/conf/core.json` 中的 `core.server.address` 出厂为空，必须指向后端 job-report 接口，否则采集完成上报和对账数据都会缺失：

   ```json
   "address": "http://127.0.0.1:50601/api/v1/log/job-report"
   ```

5. 回到「系统配置」点击「校验」，确认程序目录、`bin/addax.sh`、`conf/core.json`、上报地址和冒烟任务全部通过。
6. 配置采集源、目标端，按 [快速上手](../usage/01-quick-start.md) 创建第一张采集表。
7. 检查后端端口和日志：

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
   sudo -u addax /opt/app/addax-admin/bin/service.sh restart
   ```

数据库迁移会在服务启动时自动执行，具体说明见 [升级与迁移](05-upgrade.md)。

## 本地开发模式

### 初始化数据库

创建一个空的 `addax_admin` 数据库即可，后端首次启动会自动建表并写入默认数据。需要手工初始化时：

```bash
psql -U addax_admin -d addax_admin -f backend/src/main/resources/db/migration/V0001__baseline.sql
psql -U addax_admin -d addax_admin -f backend/src/main/resources/db/migration/V0002__seed_reference_data.sql
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
- [系统配置](04-config.md)
- [升级与迁移](05-upgrade.md)
- [快速上手](../usage/01-quick-start.md)
