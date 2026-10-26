---
type: architecture
title: 系统架构总览
description: Addax Admin 的运行时组件、单节点与多节点拓扑、端到端数据流、后端模块边界以及可扩展的接缝。
tags: [architecture, topology, spring-boot, vue, postgresql, redis, addax]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-cc9ac771b9086a58897b0e1c
    resource: repo://backend/pom.xml
  - id: openwiki-source-983c518510d9c040b87d7f40
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/EtlTableView.java
  - id: openwiki-source-46782dd16f13e8dc07fe49c7
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/SchemaInitializer.java
  - id: openwiki-source-e14f726a9f0612c7c32a31bd
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/redis/MasterElectionService.java
  - id: openwiki-source-98c1e6e4abcca885022cf52e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/scheduler/CollectionScheduler.java
  - id: openwiki-source-058bdcb2c3aa67bf05fcaf8c
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlExecutionService.java
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-c1f27e033d83feafe7508084
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetAdapterRegistry.java
  - id: openwiki-source-8ee43c3b0b88a703d0d2bdd7
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetTypeResolver.java
  - id: openwiki-source-97ae9adf7f31e5151e176dc2
    resource: repo://backend/src/main/resources/application.properties
  - id: openwiki-source-b79fbbd921df689b4bbdc82f
    resource: repo://docker-compose.yml
  - id: openwiki-source-d0086bc10a96e3d7e78bdb91
    resource: repo://DOCKER.md
  - id: openwiki-source-bb1ebe868e35e9e500714501
    resource: repo://Dockerfile
  - id: openwiki-source-cfe1f839549028a5fd269a65
    resource: repo://docs/adr/0001-retire-collection-definitions-with-execution-snapshots.md
  - id: openwiki-source-0d3ca05b1b0af6511c80c681
    resource: repo://docs/security-audit.md
  - id: openwiki-source-6b72ccb83227836142347d82
    resource: repo://frontend/nginx.conf
  - id: openwiki-source-1897bd25f4a1d6688509fe20
    resource: repo://frontend/src/layouts/default/build-nav.ts
  - id: openwiki-source-77c413f182fc2eead5535edb
    resource: repo://frontend/src/router/index.ts
  - id: openwiki-source-5e730aa183f97f99de9c8e6c
    resource: repo://frontend/src/utils/requests.ts
  - id: openwiki-source-378e3cf05ab0d05d335c68d5
    resource: repo://frontend/vite.config.ts
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 系统架构总览

Addax Admin 是 Addax ETL 引擎的管理与调度控制台。它本身不移动数据：负责保存采集定义、生成 Addax job、安排执行时机、控制并发，并把每次执行的事实保留下来。真正的数据采集由本机安装的 Addax 以子进程方式完成。

## 运行时组件

| 组件 | 职责 | 主要实现 |
| --- | --- | --- |
| 前端 SPA | 采集源/目标/采集表/任务/日志/审计/仪表盘界面 | Vue 3 + Vite + Vuetify + Pinia（`frontend/`） |
| 后端 API | 配置管理、调度、队列、目标契约、审计、统计 | Spring Boot 3.5.6 / Java 21（`backend/`） |
| PostgreSQL | 唯一持久化事实源：配置、队列、执行快照、统计、流水、日志、审计 | `scripts/schema.sql` |
| Redis | 运行时仲裁：Master 选举、Worker 心跳、分布式锁、pub/sub | `backend/src/main/java/com/wgzhao/addax/admin/redis/` |
| Addax | 实际执行采集的外部程序，以 `addax.sh` 子进程调用 | 需安装在后端同一文件系统内 |

后端启动类为 `AdminApplication`，启用 `@EnableAsync`、`@EnableScheduling`，并在 JVM 启动最早阶段打开 `com.sun.security.enableAIAcaIssuers`，让访问只下发叶子证书的柜台交易日历服务器时能自动补齐中间 CA（`backend/src/main/java/com/wgzhao/addax/admin/AdminApplication.java#L9-L20`）。

后端统一挂在 `/api/v1` 上下文路径下，默认端口 50601（`backend/src/main/resources/application.properties#L1-L3`）。前端开发服务器默认 3030，通过 Vite 代理把 `VITE_API_BASE_URL` 转发到 `VITE_API_HOST`（`frontend/vite.config.ts`）。

生产镜像把前端静态文件和 Nginx 与后端打进同一个容器：Nginx 监听 80，把 `/api/v1/` 反向代理到 `localhost:50601`（`frontend/nginx.conf`、`Dockerfile`）。Docker Compose 默认把容器 80 映射到宿主机 50080，后端端口仅在容器网络内可见（`docker-compose.yml`）。

## 部署拓扑

### 单节点

一个应用进程同时承担 Master 和 Worker 两种角色：

```text
Browser → Nginx/Vite → Spring Boot (/api/v1)
                           ├── PostgreSQL
                           ├── Redis
                           └── addax.sh → 源数据库 / 目标 HDFS 或关系库
```

### 多节点

所有节点共享同一个 PostgreSQL 和 Redis，每个节点都可能是 Master，也都是 Worker：

```text
                    ┌──────────── Redis ────────────┐
                    │ master lock / heartbeats /    │
                    │ locks / pub-sub channels      │
                    └───────┬───────────┬───────────┘
                            │           │
              ┌─────────────┴──┐   ┌────┴─────────────┐
              │ Node A (master)│   │ Node B (worker)  │
              │ schedules +    │   │ executes assigned│
              │ dispatch       │   │ jobs             │
              └───────┬────────┘   └───────┬──────────┘
                      └──────── PostgreSQL ─┘
```

- 只有 Master 注册源级/覆盖/切日定时器并执行分派；
- 任务的可恢复状态在 `etl_job_queue`，Redis 只保存实时角色、租约与分派消息；
- Master 失效后，Redis 锁过期，其他节点在 30 秒内重新选举；
- 节点权重 `node.concurrency.weight` 影响分派比例与单节点并发上限。

调度与队列细节见 [调度与分布式队列](scheduling-and-queue.md)。

## 端到端数据流

1. **配置**：用户在 UI 创建采集源、目标端和采集定义；后端写入 `etl_source`、`etl_target`、`etl_table`，并把 Hive/RDBMS 专属属性存进 `etl_table.target_options`。字段关系另存 `etl_column` 与 `etl_column_mapping`。
2. **校验与模板**：字段发现、目标契约检查、DDL 计划与字段映射在创建/刷新时完成；`etl_job` 保存生成的 Addax job JSON 模板。
3. **调度**：源 cron、表级覆盖或手动提交把“表 + 业务日期”写入 `etl_job_queue`。
4. **分派**：Master 选 Worker，Worker 领取任务。
5. **执行**：写入不可变 `etl_execution` 快照，准备目标分区，调用 `addax.sh`，采集日志、流水与统计。
6. **事实**：`etl_statistic`、`etl_jour`、`addax_log`、`etl_execution` 和 `audit_event` 分别承载运行指标、操作流水、原始日志、执行快照和安全审计。

执行链路见 [采集执行链路](execution-pipeline.md)，持久化结构见 [数据模型与持久化](../backend/persistence-model.md)。

## 状态边界

系统刻意区分三类数据：

| 类别 | 表 | 可变性 |
| --- | --- | --- |
| 当前配置 | `etl_table`、`etl_column`、`etl_column_mapping`、`etl_job` | 可修改；采集定义只能退役，不能级联删除历史 |
| 执行事实 | `etl_execution`、`etl_statistic`、`etl_jour` | 执行开始时冻结，历史不随配置变化 |
| 安全审计 | `audit_event` | 只追加，数据库触发器拒绝常规 UPDATE/DELETE |

这套边界来自两个 ADR：

- [ADR 0001](../../docs/adr/0001-retire-collection-definitions-with-execution-snapshots.md)：采集定义退役而非删除，每次执行保留配置快照；
- [ADR 0002](../../docs/adr/0002-external-target-contract-and-column-mapping.md)：目标结构所有权显式化，字段映射独立于源/目标同名列假设。

## 后端模块边界

`backend/src/main/java/com/wgzhao/addax/admin/` 按职责分层：

- `controller`：REST 端点与权限入口，负责把请求转成 service 调用并写审计；
- `service` 与 `service/impl`：业务编排，包括采集表、任务、目标、映射、日历、告警、统计；
- `service/target`：目标端适配器接口、注册中心与路由解析，是新增目标方言的扩展点；
- `scheduler`：源级 cron、表级覆盖、切日刷新；
- `redis`：Master 选举、心跳、分布式锁；
- `audit`：审计记录器、过滤器、写入器、脱敏与保留；
- `repository`：Spring Data JPA 与原生 SQL 查询；
- `model`：JPA 实体与视图映射；
- `config`：安全、JWT、Redis、调度线程池、启动迁移；
- `common` / `dto` / `utils`：枚举、常量、DTO 与数据库/DDL 工具。

新增目标端的接缝是 `TargetAdapter` 接口与 `TargetAdapterRegistry`：按 `etl_target.writer_template_key` 路由到 HDFS 或 RDBMS 适配器，未知类型显式报错而不是静默降级（`backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetAdapterRegistry.java`、`TargetTypeResolver.java`）。细节见 [Addax 任务契约与目标适配器](../integrations/addax-job-contract.md)。

## 前端结构

前端是纯 SPA，不使用服务端渲染：

- 路由由 `unplugin-vue-router` 从 `src/views` 文件自动生成，布局由 `vite-plugin-vue-layouts-next` 按路由 meta 选择（`frontend/vite.config.ts`、`frontend/src/router/index.ts`）；
- 导航树由路由 meta（`navGroup`、`navTitle`、`navOrder`）聚合而成，分源端、目标端、采集、数据、系统管理五组（`frontend/src/layouts/default/build-nav.ts`）；
- Pinia 保存认证、主题、任务中心、通知等状态；axios 封装统一注入 JWT、处理 401 与错误提示（`frontend/src/utils/requests.ts`）；
- 详情页、批量向导等复杂交互拆分在 `components/table/` 下。

细节见 [前端架构](../frontend/architecture.md) 与 [采集表管理界面](../frontend/table-management.md)。

## 启动与迁移

项目没有独立的迁移框架。首次安装使用 `scripts/schema.sql` 与 `scripts/data.sql`；已有数据库的升级由 `SchemaInitializer` 在 `@PostConstruct` 中执行（`backend/src/main/java/com/wgzhao/addax/admin/config/SchemaInitializer.java`）：

- 在单个事务和 PostgreSQL advisory lock 下运行，多个实例不会交叉执行；
- 每一步幂等，失败会回滚并阻止启动，避免半迁移状态对外提供服务；
- 最后按 `EtlTableView` 的 DDL 指纹重建 `vw_etl_table_with_source`，视图定义变化会在下次启动自动生效。

部署方式与配置见 [部署与配置](../operations/deployment-and-configuration.md)。

## 关键运行约束

- Addax 必须安装在后端进程可访问的同一文件系统内，默认从字典项 `ADDAX_HOME` 指向的 `bin/addax.sh` 调用；不能把 Addax 放在另一个容器里（`DOCKER.md`）。
- 源端与目标端的连接信息保存在数据库/DTO 中，连接测试是显式操作，不在采集执行时动态发现。
- 前端构建产物由 Nginx 托管；后端不提供静态资源映射（`spring.web.resources.add-mappings=false`）。
- JWT 默认有效期 8 小时，生产应通过 `JWT_SECRET` 覆盖默认密钥（`backend/src/main/resources/application.properties#L8-L10`）。
