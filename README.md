# Addax Admin

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-green.svg)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.x-4FC08D.svg)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-7.x-646CFF.svg)](https://vitejs.dev/)

Addax Admin 是一个现代化的 ETL 管理解决方案的单仓(monorepo)，包含后端服务与前端界面：
- `backend/`：Spring Boot 3 + Java 21 的后端 API 与调度服务
- `frontend/`：Vue 3 + Vite + Vuetify 的 Web 管理界面

## 📋 项目概述

整个解决方案由三个项目组成：
- **[Addax](https://github.com/wgzhao/addax)** - ETL 核心程序
- **Addax Admin (本仓库)** - 后端服务 + 前端界面
- （历史上前端为独立仓库，现在已合并到本仓库的 `frontend/` 目录）

## ✨ 主要特性

- 🚀 现代化架构：Spring Boot 3.5.6 + Vue 3
- 🔐 安全认证：JWT + Spring Security
- 💾 多数据库支持：PostgreSQL（推荐）、Oracle、SQL Server 等
- 📊 完整 REST API：内置 OpenAPI/Swagger 文档
- 🔧 灵活配置：多环境配置、动态参数
- 📈 监控与管理：ETL 作业状态监控与日志
- 🖥️ 友好 UI：基于 Vuetify 的响应式管理界面

## 🛠 技术栈

- 后端
  - Spring Boot 3.5.6、Spring Security、Spring Data JPA、Hibernate 6.6.11
  - PostgreSQL 驱动、Lombok、Hutool、Apache Commons
- 前端
  - Vue 3、TypeScript、Vite、Vuetify 3、Pinia、Vue Router、Axios、Chart.js

## 📦 目录结构

```
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

## 🚀 快速开始（开发环境）

### 1) 后端 Backend

前置要求：Java 21、Maven 3.8+、PostgreSQL 15+（推荐）

- 初始化数据库（PostgreSQL）
```bash
# 在 PostgreSQL 中创建数据库并导入初始化脚本
psql -U postgres -d your_database -f backend/src/main/resources/schema.sql
psql -U postgres -d your_database -f backend/src/main/resources/data.sql
```

- 配置应用（开发环境默认使用 `application.properties` 中的 dev 配置）
  - 默认端口：`50601`
  - 上下文路径：`/api/v1`
  - 也可通过环境变量覆盖：`DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`

- 启动后端
```bash
cd backend
mvn clean package
java -jar target/addax-admin-1.0.0-SNAPSHOT.jar
# 服务地址：http://localhost:50601/api/v1
# OpenAPI 文档：http://localhost:50601/api/v1/swagger-ui/index.html
```

### 2) 前端 Frontend

前置要求：Node.js ≥ 18（建议 18/20/22 LTS），npm 或 yarn

- 安装依赖
```bash
cd frontend
npm install
# 或
yarn install
```

- 配置本地代理（创建 `frontend/.env.local`）
```bash
# 代理到后端 API（与后端默认配置一致）
VITE_API_BASE_URL=/api/v1
VITE_API_HOST=http://localhost:50601
```

- 启动前端（开发模式）
```bash
npm run dev
# 或
yarn dev
# 访问：http://localhost:3030
```

## 🏭 生产构建与部署

- 构建后端
```bash
cd backend
mvn clean package
# 产物：backend/target/addax-admin-1.0.0-SNAPSHOT.jar
```

- 构建前端
```bash
cd frontend
npm run build
# 产物：frontend/dist
```

- 部署建议
  - 后端：以独立服务方式部署（Java 21 运行时或容器）
  - 前端：将 `frontend/dist` 交由 Nginx/静态文件服务器托管
  - 通过 Nginx/网关将 `/api/`（或 `/api/v1`）反向代理到后端

## 🔧 配置说明

- 多环境配置
  - 后端：`backend/src/main/resources/application.properties`（可扩展 `-dev`/`-prod`）
  - 前端：`.env.*` 文件（`VITE_API_BASE_URL`、`VITE_API_HOST`）
- 日志与安全
  - 后端默认日志目录为 `./logs`（可通过 `LOG_DIR` 修改）
  - 认证使用 JWT，过期时间与密钥在后端配置中设置

## 📚 文档与 API

- OpenAPI/Swagger UI：`http://localhost:50601/api/v1/swagger-ui/index.html`
- 前端项目文档：见 `frontend/README.md`

## 📝 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可协议。

## 🙏 致谢

- 感谢 [IntelliJ IDEA](https://jetbrains.com) 提供开发工具支持
- 感谢所有参与贡献的开发者
