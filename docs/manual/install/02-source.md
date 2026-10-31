# 源码构建与本地开发

适用于二次开发、调试或需要定制构建的场景。生产部署优先使用 [Docker 部署](01-docker.md)。

## 环境要求

- Java 21
- Maven 3.8+
- Node.js 20.19+ 或 22.12+，推荐 22 LTS
- Yarn 1.22+
- PostgreSQL 15+
- Redis 6+
- Addax 采集引擎（运行时必需，见 [Docker 部署 · 安装 Addax](01-docker.md#安装-addax采集引擎必需)）

## 初始化数据库

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

## 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认配置连接 `localhost:5432/addax_admin` 和 `localhost:6379`。如果本地环境不同，用环境变量覆盖：

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

## 启动前端

```bash
cd frontend
yarn install
VITE_API_HOST=http://localhost:50601 yarn dev
```

前端开发服务器默认地址：<http://localhost:3030>

Vite 会把 `/api` 请求代理到 `VITE_API_HOST`。如果后端地址不同，修改该环境变量即可。

## 生产构建

### 后端

```bash
cd backend
mvn clean package
java -jar target/addax-admin-<version>.jar
```

也可以使用仓库自带的 `scripts/service.sh` 和 `scripts/addax-admin.service` 以 systemd 方式托管。该布局默认要求：

```text
/opt/app/addax-admin/
├── lib/          # addax-admin-<version>.jar
├── config/       # application.properties、env.sh
├── drivers/      # JDBC 驱动
├── job/          # 采集任务文件
└── logs/
```

### 前端

```bash
cd frontend
yarn build
```

构建产物位于 `frontend/dist/`，可以交给 Nginx 或其他静态服务器托管，并把 `/api` 反向代理到后端。

### 一体化构建

仓库根目录也提供组合脚本：

```bash
yarn build:frontend
yarn build:backend
```

## 开发约定

- 前端使用 Vue 3、Vite、TypeScript、Vuetify，页面位于 `frontend/src/views/`。
- 后端使用 Spring Boot 3、Spring Data JPA、PostgreSQL 和 Redis。
- 提交前至少完成相关构建和测试；前端可执行 `yarn type-check`、`yarn lint`、`yarn test`，交互改动建议补充或运行 `yarn e2e`。
- 不要直接修改 `.env` 或 `application.properties` 来临时验证配置，优先使用环境变量覆盖。

## 相关文档

- [Docker 部署](01-docker.md)
- [系统配置](03-config.md)
- [升级与迁移](04-upgrade.md)
