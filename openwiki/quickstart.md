---
type: guide
title: 快速开始
description: 用最短路径说明 Addax Admin 的定位、运行时组成、目录结构、Docker/本地启动命令、默认入口与深入阅读路线。
tags: [quickstart, setup, docker, local-development, architecture]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-cc9ac771b9086a58897b0e1c
    resource: repo://backend/pom.xml
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-97ae9adf7f31e5151e176dc2
    resource: repo://backend/src/main/resources/application.properties
  - id: openwiki-source-b79fbbd921df689b4bbdc82f
    resource: repo://docker-compose.yml
  - id: openwiki-source-d0086bc10a96e3d7e78bdb91
    resource: repo://DOCKER.md
  - id: openwiki-source-bb1ebe868e35e9e500714501
    resource: repo://Dockerfile
  - id: openwiki-source-04b8358eb735678eb6d2a093
    resource: repo://frontend/.env.development
  - id: openwiki-source-6b72ccb83227836142347d82
    resource: repo://frontend/nginx.conf
  - id: openwiki-source-1047363cf615000e4c9bb694
    resource: repo://frontend/package.json
  - id: openwiki-source-b5a647f291d0527867261a77
    resource: repo://frontend/src/layouts/default/NavDrawer.vue
  - id: openwiki-source-d70a29676ece6708fba2de92
    resource: repo://frontend/src/service/table-service.ts
  - id: openwiki-source-802e2cebcb071446cbffdea1
    resource: repo://frontend/src/views/sys-settings.vue
  - id: openwiki-source-378e3cf05ab0d05d335c68d5
    resource: repo://frontend/vite.config.ts
  - id: openwiki-source-5b54a58d1b51cd490b0e7162
    resource: repo://package.json
  - id: openwiki-source-2355f81d7cf522f8dbdaabd4
    resource: repo://pom.xml
  - id: openwiki-source-23775c3de52f3ab95a13cb8b
    resource: repo://README.md
  - id: openwiki-source-49f5868b9bc0b9372e54c787
    resource: repo://scripts/data.sql
  - id: openwiki-source-56ca36fad3cacc52c6c8f19a
    resource: repo://scripts/schema.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 快速开始

Addax Admin 是 Addax ETL 引擎的**管理与调度控制台**，不是数据搬运引擎本身。它负责保存采集定义、生成 Addax job JSON、维护数据库队列与 Redis 仲裁，并在真正执行时调用本机的 `addax.sh`；数据实际由 Addax 写入目标端（`README.md#L8-L10`、`DOCKER.md#L87-L93`）。

项目是 Mono repo：

- 后端：Spring Boot 3.5.6 + Java 21（`pom.xml#L21-L27`、`backend/pom.xml#L24-L41`）；
- 前端：Vue 3 + Vite + Vuetify 单页应用（`frontend/package.json#L22-L61`）；
- 持久化：PostgreSQL 保存配置、队列、执行快照和统计；
- 仲裁：Redis 负责节点选举、worker 心跳、任务锁与并发 permit；
- 执行：本机 Addax 子进程，平台读取其日志并回写数据库。

## 运行时组成

| 组件 | 作用 | 默认位置 |
| --- | --- | --- |
| 前端 SPA | 采集表、源、目标端、监控、审计界面 | Vite `:3030` 或容器 Nginx `:80` |
| 后端 API/调度器 | `/api/v1`、定时触发、队列、job 编译 | `:50601` |
| PostgreSQL | 配置、`etl_job_queue`、`etl_execution`、审计 | `:5432` |
| Redis | master 选举、心跳、任务锁、并发控制 | `:6379` |
| Addax | 实际执行 ETL 的本地程序 | `${ADDAX_HOME}/bin/addax.sh` |

后端默认连接串与端口写在 `application.properties`，全部可以用环境变量覆盖：数据库默认 `localhost:5432/addax_admin`，Redis 默认 `localhost:6379/0`（`backend/src/main/resources/application.properties#L15-L17`、`#L32-L35`）。

## 五分钟启动：Docker Compose

最短路径是直接用 compose 构建并启动：

```bash
git clone https://github.com/wgzhao/addax-admin.git
cd addax-admin
docker compose up -d --build
docker compose ps
```

启动后访问 <http://localhost:50080>，默认账号 `admin / admin123`（`README.md#L164-L176`）。

Compose 定义三个服务：

- `postgres`：PostgreSQL 15，首次启动时执行 `scripts/schema.sql` 与 `scripts/data.sql`；
- `redis`：Redis 7；
- `app`：前后端合一的镜像，只把容器的 `80` 端口映射到宿主机 `50080`；后端 `50601` 默认不对外暴露。

（`docker-compose.yml#L1-L90`）

应用镜像本身包含后端 jar、前端静态产物和 Nginx；Nginx 把 `/api/v1/` 反向代理到容器内的 `localhost:50601`（`Dockerfile#L45-L61`、`frontend/nginx.conf#L29-L40`）。

### 只拉镜像、不构建

如果只需要部署预构建版本，可以按 `README.md#L132-L176` 的方式下载 `docker-compose.yml` 和两个 SQL 脚本，然后：

```bash
docker compose pull
docker compose up -d
```

注意：该方式不会下载根目录 `Dockerfile`。如果镜像拉取失败，不要直接执行 `up -d`，因为 compose 会回退到本地构建并报找不到 Dockerfile；应先解决镜像源或改用源码构建（`DOCKER.md#L45-L75`）。

### Docker 运行采集前必须补的两件事

1. **Addax 必须在同一个容器文件系统里。** 平台以子进程方式调用 `${ADDAX_HOME}/bin/addax.sh` 并读取 `${ADDAX_HOME}/log/`，不能把 Addax 单独放在另一个容器里。未安装 Addax 时应用可以启动，但提交采集任务会失败（`DOCKER.md#L87-L126`）。
2. **镜像启动命令没有设置 `-Dapp.home`。** 执行器用 `System.getProperty("app.home")` 拼出 `${app.home}/job/yyyy-MM-dd/` 作业目录（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L1118-L1127`），而 `Dockerfile` 的 `CMD` 只传了 `loader.path`、`loader.main` 等参数（`Dockerfile#L73-L85`）。在容器里执行采集前，需要通过 `JAVA_TOOL_OPTIONS=-Dapp.home=/app` 或修改 `CMD` 补上该属性。

这两个问题不会阻止首页和配置页面打开，但会在第一次真正执行采集时暴露。

## 本地开发

### 前置条件

- Java 21、Maven 3.8+
- Node.js + Yarn 1.22（`package.json#L38`）
- PostgreSQL 15+、Redis

### 初始化数据库

仓库的初始化脚本在 `scripts/`，不是 `backend/src/main/resources/`：

```bash
createdb addax_admin
psql -d addax_admin -f scripts/schema.sql
psql -d addax_admin -f scripts/data.sql
```

种子数据创建两个账号：`admin/admin123`（管理员）和 `user/user123`（普通用户）（`scripts/data.sql#L1-L9`）。

### 启动后端

从仓库根目录：

```bash
yarn dev:backend
```

它等价于 `cd backend && mvn spring-boot:run`（`package.json#L9-L13`）。后端上下文路径是 `/api/v1`，端口 `50601`，默认 `spring.profiles.active=dev`（`backend/src/main/resources/application.properties#L1-L3`）。

如果本机数据库账号不是默认的 `addax_admin`，用环境变量覆盖即可，不要改 `application.properties`：

```bash
DB_USERNAME=postgres DB_PASSWORD=... yarn dev:backend
```

### 启动前端

在仓库根目录安装依赖并启动：

```bash
yarn install
VITE_API_HOST=http://localhost:50601 yarn dev
```

Vite 开发服务器监听 `3030`，并把 `VITE_API_BASE_URL`（默认 `/api/v1`）代理到 `VITE_API_HOST`（`frontend/vite.config.ts#L89-L97`、`frontend/.env.development#L1-L2`）。

访问 <http://localhost:3030>。如果本地存在 `frontend/.env.local`，它的优先级高于 `.env.development`；用命令行显式设置 `VITE_API_HOST` 最稳妥。

### README 的本地开发段落已过时

`README.md#L190-L213` 仍写着 `./start-dev.sh` 和 `backend/src/main/resources/schema.sql` / `data.sql`。当前仓库没有 `start-dev.sh`，SQL 脚本实际位于 `scripts/schema.sql` 与 `scripts/data.sql`。按本页命令执行。

## 构建与验证

| 目标 | 命令 | 产物/说明 |
| --- | --- | --- |
| 后端打包 | `yarn build:backend` | `backend/target/addax-admin-*.jar` |
| 前端构建 | `yarn build:frontend` | `frontend/dist` |
| 前后端一起构建 | `yarn build` | 先前端后后端 |
| 后端测试 | `mvn test` | 当前只有少量窄回归测试 |
| 前端单元测试 | `yarn workspace addax-ui test` | Vitest |
| 前端类型检查 | `yarn type-check` | `vue-tsc` |
| E2E | `cd frontend && yarn e2e` | 需要运行中的后端、PostgreSQL 和 `psql` |

（`package.json#L9-L23`、`frontend/package.json#L6-L21`、`Dockerfile#L54-L58`）

E2E 的前置条件、fake-addax 和多套件说明见 [测试策略与 E2E](testing/testing-strategy.md)。

## 首次使用路径

登录后按这个顺序配置：

1. **采集源** `/source`：填写 JDBC 连接、采集时间和采集日历（自然日/工作日/交易日）。
2. **目标端** `/target`：选择 Hive/HDFS 或 MySQL/PostgreSQL/SQL Server，并测试连接。
3. **系统配置** `/sys-settings`：把“Addax 程序目录”设置为执行节点上的真实路径（默认 `/opt/app/addax`）（`frontend/src/views/sys-settings.vue#L80-L83`、`DOCKER.md#L121-L126`）。
4. **新增采集表** `/table/batch-add`：五步向导选择源表、配置 MANAGED/EXTERNAL 目标策略、逐表预览、提交。
5. **观察结果** `/table` 查看采集定义状态，`/monitor` 查看完成率、特殊任务、拒绝行和风险。

快捷新增入口也直接指向这三个路径（`frontend/src/layouts/default/NavDrawer.vue#L74-L76`）。

## 目录结构

```text
addax-admin/
├── backend/          Spring Boot 模块（controller/service/repository/scheduler/target/audit/redis）
├── frontend/         Vue 3 应用（views/components/service/stores/router）
├── scripts/          schema.sql、data.sql、service.sh、systemd 模板、shell E2E
├── docs/             ADR、安全审计与领域文档
├── docker-compose.yml
├── Dockerfile
└── deploy.sh         站点专用部署脚本，不是通用安装器
```

（`README.md#L30-L37`）

## 生产前必须修改

- `JWT_SECRET`：`application.properties` 带内置默认值，生产必须用环境变量覆盖（`backend/src/main/resources/application.properties#L9-L12`）。
- 数据库密码与种子账号：compose 默认 `addax_admin@123`、`admin/admin123`、`user/user123`，上线前替换（`docker-compose.yml#L7-L10`、`scripts/data.sql#L1-L9`）。
- 反向代理超时：内置 Nginx 的 `proxy_read_timeout` 是 60 秒，小于前端批量预览/提交的 300/600 秒；大批量操作前需要调大（`frontend/nginx.conf#L37-L40`）。

更多部署形态、裸机 `service.sh`、systemd 与多节点注意事项见 [部署与配置](operations/deployment-and-configuration.md)。

## 深入阅读

- [系统总览](architecture/system-overview.md)：模块边界与一次采集的完整生命周期。
- [调度与分布式队列](architecture/scheduling-and-queue.md)：master 选举、心跳、租约、并发 permit。
- [采集定义与生命周期](domain/collection-definition-lifecycle.md)：状态、停采、退役、恢复。
- [目标契约与字段映射](domain/target-contract-and-mapping.md)：MANAGED/EXTERNAL、映射与目标指纹。
- [Addax 任务契约与目标适配器](integrations/addax-job-contract.md)：job JSON、reader/writer、插件配置。
- [采集定义创建流程](workflows/collection-creation.md)：五步向导与逐表提交。
- [补数与业务日历](workflows/fillback-and-calendar.md)：切日、业务日期、补数入口。
- [测试策略与 E2E](testing/testing-strategy.md)：构建、单测、Playwright 与 shell E2E。
- [部署与配置](operations/deployment-and-configuration.md)：Docker、Nginx、service.sh、多节点。
