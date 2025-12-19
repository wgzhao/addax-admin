# Addax Admin

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-green.svg)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.x-4FC08D.svg)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-7.x-646CFF.svg)](https://vitejs.dev/)

Addax Admin 是一个现代化的 ETL 管理解决方案的 **Monorepo** 项目，包含完整的前后端解决方案：

## 🏗️ 项目结构

```ini
addax-admin/
├── backend/                 # Spring Boot 3 + Java 21 后端 API 服务
├── frontend/                # Vue 3 + Vite + Vuetify 前端管理界面
├── pom.xml                  # Maven 父项目配置
└── package.json             # NPM 工作区配置
```

## 📋 项目概述

整个解决方案由以下组件组成：

- **[Addax](https://github.com/wgzhao/addax)** - ETL 核心执行引擎
- **Addax Admin Backend** - Spring Boot 后端 API 与任务调度服务 (`backend/`)
- **Addax Admin Frontend** - Vue.js 前端管理界面 (`frontend/`)

## ✨ 主要特性

- 🚀 现代化架构：Spring Boot 3.5.6 + Vue 3
- 🔐 安全认证：JWT + Spring Security
- 💾 多数据库支持：PostgreSQL（推荐）、Oracle、SQL Server 等
- 📊 完整 REST API：内置 OpenAPI/Swagger 文档
- 🔧 灵活配置：多环境配置、动态参数
- 📈 监控与管理：ETL 作业状态监控与日志
- 🖥️ 友好 UI：基于 Vuetify 的响应式管理界面
- 🔁 多节点并发支持：数据库持久化队列 + Redis 仲裁，保证多实例部署下任务并发可控与高可用

## 🛠 技术栈

- 后端
  - Spring Boot 3.5.6、Spring Security、Spring Data JPA、Hibernate 6.6.11
  - PostgreSQL 驱动、Lombok、Hutool、Apache Commons
- 前端
  - Vue 3、TypeScript、Vite、Vuetify 3、Pinia、Vue Router、Axios、Chart.js

## 📦 目录结构

```ini
addax-admin/
├── backend/                # 后端（Spring Boot）
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── schema.sql
│   │   └── data.sql
│   └── pom.xml
├── frontend/               # 前端（Vue 3 + Vite）
│   ├── src/
│   ├── public/
│   ├── vite.config.ts
│   └── package.json
├── scripts/                # 可选脚本
└── README.md
```

## 🚀 快速开始

### 📋 开发环境要求

- **Java 21** + Maven 3.8+
- **Node.js 18+** + npm/pnpm
- **PostgreSQL 15+** （推荐）

### 🏃‍♂️ 一键启动（推荐）

```bash
# 克隆项目并进入目录
git clone https://github.com/wgzhao/addax-admin.git
cd addax-admin

# 一键启动前后端开发环境
./start-dev.sh
```

启动后访问：

- 🎨 **前端界面**: http://localhost:5173
- 🔧 **后端 API**: http://localhost:8080

### 🔧 手动启动

#### 1. 初始化数据库

```bash
# 在 PostgreSQL 中创建数据库并导入初始化脚本
psql -U postgres -d your_database -f backend/src/main/resources/schema.sql
psql -U postgres -d your_database -f backend/src/main/resources/data.sql
```

#### 2. 启动后端服务

```bash
cd backend
mvn spring-boot:run
# 或者在 IDEA 中直接运行 AdminApplication.java
```

#### 3. 启动前端服务

```bash
cd frontend
npm install
npm run dev
```

## 🔧 配置说明

- 多环境配置
  - 后端：`backend/src/main/resources/application.properties`（可扩展 `-dev`/`-prod`）
  - 前端：`.env.*` 文件（`VITE_API_BASE_URL`、`VITE_API_HOST`）
- 日志与安全
  - 后端默认日志目录为 `./logs`（可通过 `LOG_DIR` 修改）
  - 认证使用 JWT，过期时间与密钥在后端配置中设置


## 多节点并发支持

为了在多实例部署（多节点）下保证任务调度的高可用与并发可控，项目采用了“数据库持久化队列 + Redis 仲裁”的混合方案：

- 架构概览
  - 任务持久化存储仍保留在 PostgreSQL 的 `etl_job_queue` 表，负责可靠存储、审计与重试语义（pending/running/completed/failed）。
  - 每个节点为 peer-to-peer 模式都会注册本地触发器（例如定时调度或 LISTEN/NOTIFY 驱动的分发），不再做选举。真正执行前通过 Redis 做第三方仲裁以保证集群内只有一个节点拿到执行许可。

- 执行仲裁（Redis）
  - per-job 独占锁：key = `etl:job:{jobId}:lock`，使用 SET NX + TTL + token，释放使用 Lua 脚本（保证 token 匹配后删除）。
  - 全局并发许可：key = `concurrent:holders`（集合实现信号量），限制全局并发槽位。
  - 源级并发许可：key = `source:holders:{sourceId}`（集合实现），限制单个数据源的并发数。
  - schema 刷新保护：key = `schema:refresh:lock`（Constants 中配置），当存在时拒绝新增/提交采集任务，避免刷新期间不一致。
  - 续租（renewal）：执行中周期性延长锁与 permit 的 TTL（守护定时任务），TTL 与续租间隔可配置，保证长任务不会被误回收。

- 工作流（要点）
  1. 节点从 DB 领取任务（claimNext）以获得持久化语义；领取后，节点尝试获取 Redis per-job lock 与相应的 permit（全局/源级）。
  2. 若任一 Redis 授权失败：释放 DB claim（短期不可见后重试），不执行任务。若全部获授权，则进入执行，并在执行期间定期续租 Redis 授权和 DB 租约。
  3. 执行完成后：释放 Redis token/permit，更新 DB 状态（complete/fail），并触发本地调度尝试填满并发槽位。

- 优点快速说明
  - 保留 DB 的持久化和审计能力；使用 Redis 降低 DB 在高并发场景下的争用与写负载。
  - Redis 的锁+permit 使并发控制更细粒度、延迟更低且更易扩展。

- 关键配置点（代码/常量）
  - `Constants.SCHEMA_REFRESH_LOCK_KEY`（schema 刷新锁 key）
  - Redis 锁/permit TTL、续租间隔（在代码中易于配置化，建议外放至 application.properties）
  - 数据源并发限制来源：在源配置中定义 maxConcurrency，系统在分发时使用该值作为 source permit 的容量。

- 运行与测试建议
  - 在测试环境使用 Postgres + Redis（Testcontainers）做集成验证：并发领取、schema 刷新期间拒绝入队、节点崩溃后的恢复等场景。
  - 监控指标建议：锁续租成功率、permit 获取失败率、被拒绝入队次数、pending/ running 数量、任务重复执行报警。

- 备注
  - 该混合策略兼顾可靠性与性能：保留 DB 做可信存储，使用 Redis 做实时仲裁与并发控制；如需更高吞吐可考虑把部分低持久化需求的任务迁移到 Redis Streams 或消息队列（Kafka）。

（以上为实现摘要，更多参数化与运维细节见后端代码中的注释与 `Constants` 配置。）


## 🖼️ 界面截图

### 主控制台

![主控制台](screenshots/home.jpg)
*实时显示 ETL 任务状态和系统概览*

### 任务配置

![任务配置](screenshots/maintable-modify.jpg)
*ODS 表配置界面，支持表单验证和实时预览*

### 实时监控

![实时监控](screenshots/etl-monitor.jpg)
*实时任务监控面板，显示任务执行状态和性能指标*

### 字段对比

![字段对比](screenshots/maintable-fieldcompare.jpg)
*可视化对比源表和目标表字段，包括字段名、数据类型等*

## 📚 文档与 API

- OpenAPI/Swagger UI：`http://localhost:50601/api/v1/swagger-ui/index.html`
- 前端项目文档：见 `frontend/README.md`

## 📝 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可协议。

## 🙏 致谢

- 感谢 [IntelliJ IDEA](https://jetbrains.com) 提供开发工具支持
- 感谢所有参与贡献的开发者
