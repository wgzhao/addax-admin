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
