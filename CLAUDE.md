# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 通用偏好

- 用中文回复，代码注释用英文，注释写 why 不写 how
- 简洁直接，不要多余总结和解释
- 直接写代码，不需要每次确认后再生成
- 从第一性原理解构问题，警惕 XY 问题，解決根本问题不要 workaround
- 架构设计时参考 ddia-principles 和 software-design-philosophy 规则

## 技术栈

- **后端**: Java 21, Spring Boot 3.5.6, Maven, Spring Data JPA + Hibernate, PostgreSQL, Redis, Spring Security + JWT
- **前端**: TypeScript, Vue 3 + Composition API, Vite 8, Vuetify 4, Pinia, Vue Router (auto-routes), Chart.js
- **工具**: Yarn 1.x (monorepo workspaces), Husky + commitlint, Prettier

## 项目架构

### 数据流核心链路

```
前端操作 → TableController → TableService → refreshTableResources()
                                              ├── columnService.updateTableColumnsV2()  ← DB 字段探测
                                              ├── targetService.createOrUpdateHiveTable() ← Hive 建表
                                              └── jobContentService.updateJob()           ← Addax JSON 生成
调度器触发 → TaskService.executeTasksForSource() → queueManager.addTaskToQueue()
队列消费 → TaskQueueManagerV2 → Redis 锁仲裁 → Addax CLI 执行 → 状态回写
```

### 后端分层

| 层 | 目录 | 职责 |
|---|---|---|
| Controller | `controller/` | REST 端点，15 个 Controller 对应业务模块 |
| Service | `service/` | 业务逻辑接口与实现 |
| Repository | `repository/` | Spring Data JPA 接口，17 个 Repo |
| Model | `model/` | JPA 实体，19 个实体类 |
| Redis | `redis/` | MasterElectionService, RedisLockService, WorkerHeartbeatService |
| Scheduler | `scheduler/` | CollectionScheduler, SchemaRefreshScheduler, TableOverrideScheduler |
| Config | `config/` | SecurityConfiguration, JwtFilter, RedisConfig 等 |
| DTO | `dto/` | 请求/响应对象 |
| Utils | `utils/` | CommandExecutor, DbUtil, QueryUtil |

### 前端分层

| 层 | 目录 | 职责 |
|---|---|---|
| Views | `views/` | 17 个页面视图（自动路由） |
| Components | `components/` | 功能组件：`table/`, `task/`, `logs/`, `dashboard/`, `source/` |
| Stores | `stores/` | 8 个 Pinia store（auth, notifier, theme, task-center 等）|
| Services | `service/` | 16 个 API 调用封装（axios） |
| Router | `router/` | 基于文件自动路由 + 手动路由扩展 |
| Layouts | `layouts/` | DefaultLayout + LoginLayout |
| Types | `types/` | TypeScript 接口定义（与后端 Model 对应） |
| Plugins | `plugins/` | Vuetify 主题注册、Pinia 安装 |

### 核心后端服务说明

- **TableService** — 采集表生命周期管理（CRUD、批量创建、资源刷新、状态转换）。表状态流转：N(新建) → R(采集中) → Y(成功)/E(失败)
- **TaskQueueManagerV2** — Master/Worker 分配模式的任务队列管理器。Master(通过 Redis NX 选举)独占从 DB 队列分配任务，通过 Redis pub/sub 推送给指定 worker。每个节点同时是 worker，通过心跳上报可用 slot
- **ColumnService** — 从源 DB 探测字段结构，与现有字段做 diff，自动推进变更到目标表
- **JobContentService** — Addax 任务 JSON 模板的编排与版本管理，每天自动更新采集任务配置文件
- **TargetServiceWithHiveImpl** — Hive 目标表管理（建表、分区、字段演化、动态表名替换）
- **CollectionScheduler** — 按 etl_source.start_at 注册 cron 任务，触发时调用 TaskService.executeTasksForSource()
- **TableOverrideScheduler** — 每分钟扫描有独立 start_at 的表，命中时间窗口则入队
- **SchemaRefreshScheduler** — 每天切日时间触发一次表结构刷新（仅 master 执行）
- **MasterElectionService** — 通过 Redis NX 锁控制多节点主备，30s TTL，10s 续约周期

### 数据库核心表

| 表 | 用途 |
|---|---|
| `etl_source` | 采集源配置（JDBC 连接、并发限制、调度时间） |
| `etl_table` | 采集表定义（源库/表、目标库/表、分区、过滤、状态） |
| `etl_column` | 采集字段映射（源类型→目标类型、长度、精度） |
| `etl_job` | Addax 任务模板 JSON |
| `etl_job_queue` | 任务队列持久化（状态、重试、所属节点） |
| `etl_jour` | 采集日记（审计追踪） |
| `etl_statistic` | 采集统计（字节/行速度、耗时） |
| `etl_target` | 目标端配置（Hive/其他） |
| `addax_log` | Addax 执行日志 |
| `sys_dict` / `sys_item` | 系统字典表 |

### 调度与并发控制

```
DB 持久化队列 (etl_job_queue)              ← 任务记录
       ↓
Redis 仲裁层:                              ← 实时控制
  ├── job lock (per-task 互斥)
  ├── global/source permit (全局/源级并发上限)
  ├── master election (主节点选举)
  └── heartbeat with slot reporting (Worker 心跳与槽位上报)
       ↓
Addax CLI 执行                             ← 调用 addax.sh
       ↓
状态回写: etl_table + etl_job_queue + etl_jour
```

系统利用 **sharing-nothing 架构**：每个节点独立运行，通过 Redis 协调。Master 负责调度和入队，Worker 消费任务。

## 常用命令

### 构建

```bash
# 前端构建
bun build:frontend
# yarn build:frontend

# 后端构建 (Maven + Spring Boot)
bun build:backend
# cd backend && mvn clean package

# 全量构建
bun build:all
```

### 本地开发

```bash
# 后端启动
cd backend && mvn spring-boot:run

# 前端开发服务器 (需先启动后端)
VITE_API_HOST=http://localhost:50601 bun run dev
# 访问 http://localhost:3030

# 本地测试全流程
# 1. bun build:backend
# 2. /opt/app/addax-admin/service.sh restart (重启本地后端)
# 3. VITE_API_HOST=http://localhost:50601 bun run dev
# 4. 访问 http://localhost:3030
```

### 其他

```bash
# 类型检查
bun run type-check

# 代码格式化
bun run format

# 全部构建并发布
yarn release
```

## 提交规范

遵循 Conventional Commits，由 commitlint + Husky 强制校验。

```
格式: type(scope): subject
type: feat|fix|refactor|perf|docs|test|chore|ci
scope: fe|be|api|db|infra|repo|deps
subject: 英文祈使句，首字母小写，无句号

Body 可选（核心变更时必写: Why / What / Impact / Test）
Footer: Refs #123, Closes #123, BREAKING CHANGE: ...
```

**提交并创建 PR 流程**: 创建 `feat/` 或 `fix/` 分支 → 按规范 commit → `gh pr create --base master` (英文 PR body)

## 禁止

- 随意修改 `.env` 文件或 `application.properties`，使用环境变量临时覆盖配置
- 随意移除功能标志而不搜索所有调用点
- 提交代码前不运行测试
