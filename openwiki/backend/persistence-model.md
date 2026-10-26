---
type: backend
title: 数据模型与持久化
description: 核心表分组、约束与索引、视图与触发器、执行事实和审计的不可变性、保留策略以及数据库初始化与升级方式。
tags: [database, postgresql, schema, constraints, retention, migration]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-dad452b8a576ad7c8abdd943
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/audit/AuditMaintenanceService.java
  - id: openwiki-source-983c518510d9c040b87d7f40
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/EtlTableView.java
  - id: openwiki-source-46782dd16f13e8dc07fe49c7
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/SchemaInitializer.java
  - id: openwiki-source-9cc70e02acfa740508044688
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/LogController.java
  - id: openwiki-source-55a3c4182262f33d158776d1
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/AddaxLogService.java
  - id: openwiki-source-058bdcb2c3aa67bf05fcaf8c
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlExecutionService.java
  - id: openwiki-source-8ee43c3b0b88a703d0d2bdd7
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetTypeResolver.java
  - id: openwiki-source-49f5868b9bc0b9372e54c787
    resource: repo://scripts/data.sql
  - id: openwiki-source-56ca36fad3cacc52c6c8f19a
    resource: repo://scripts/schema.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 数据模型与持久化

PostgreSQL 是唯一持久化事实源。`scripts/schema.sql` 是手工维护的新装库结构，`scripts/data.sql` 提供字典、默认账号和默认 HDFS 目标端；已有数据库由启动时的 `SchemaInitializer` 幂等升级。表结构细节以这两个脚本和实体类为准。

## 表分组

| 分组 | 表 | 生命周期 |
| --- | --- | --- |
| 配置域 | `etl_source`、`etl_target`、`etl_table`、`etl_column`、`etl_column_mapping`、`etl_job` | 可修改；采集定义只退役 |
| 运行时队列 | `etl_job_queue`、`system_flag` | 短期；切日清理历史 |
| 执行事实 | `etl_execution`、`etl_statistic`、`etl_jour`、`addax_log` | 执行开始时冻结；原始日志有保留期 |
| 变更与风险 | `etl_table_change_log`、`schema_change_log`、`risk_log` | 只追加 |
| 字典与通知 | `sys_dict`、`sys_item`、`notification`、`user_notification` | 可维护 |
| 账号 | `users`、`authorities` | 可维护 |
| 安全审计 | `audit_event` | 数据库级只追加 |

## 配置域

### 采集源 `etl_source`

保存 JDBC 连接、调度时间、日历和并发上限（`scripts/schema.sql#L446-L463`）：

- `code` 唯一标识采集源，调度任务 ID 为 `source-{code}`；
- `start_at` 是源级每日触发时间，表级 `start_at` 为空时继承；
- `calendar` 指向字典中的工作日/交易日历名称；
- `max_concurrency` 是集群级源并发上限；
- `enabled=false` 表示源停用，其下未退役的采集定义仍可查看和编辑，但不参与自动调度。

### 目标端 `etl_target`

保存目标端类型、连接配置和 writer 模板键（`scripts/schema.sql#L1012-L1021`）：

- `target_type` 用于展示与兜底路由（HDFS、MySQL、PostgreSQL 等）；
- `writer_template_key` 是主路由键，`wH` → HDFS，`wR` → RDBMS；
- `connect_config` 是文本 JSON；`is_default` 标记默认目标端；
- `code` 有唯一索引 `uk_etl_target_code`。

### 采集定义 `etl_table`

系统的中心表（`scripts/schema.sql#L741-L770`）：

- 源/目标定位：`source_db`、`source_table`、`target_db`、`target_table`、`sid`、`target_id`；
- 目标策略：`schema_management_mode`（MANAGED/EXTERNAL）、`target_schema_fingerprint`、`target_options`（按目标端族分组的 jsonb）；
- 采集行为：`filter`、`kind`、`retry_cnt`、`max_runtime`、`split_pk`、`auto_pk`、`write_mode`、`start_at`；
- 插件扩展：`reader_plugin_config`、`writer_plugin_config`；
- 生命周期：`status`（默认 `U`）、`start_time`、`end_time`、`duration`、`retired_at`、`retired_by`。

`status` 只有单字符，语义由字典 1060 与 [采集定义与生命周期](../domain/collection-definition-lifecycle.md) 定义。`retired_at` 非空即退役；系统没有级联删除采集定义的路径。

### 字段与映射

- `etl_column`：源字段元数据与目标类型，按 `tid` 归属；唯一索引 `uk_tid_column_name` 保证同一采集定义内字段名唯一。删除字段时使用占位前缀而不是物理删除，保留位置与历史映射（`backend/src/main/java/com/wgzhao/addax/admin/service/ColumnService.java` 中的 `DELETED_PLACEHOLDER_PREFIX` 逻辑）。
- `etl_column_mapping`：稳定的 `source_column_name → target_column_name` 契约，带 `ordinal_no`；`uk_column_mapping_tid_source` 和 `uk_column_mapping_tid_target` 都用 `lower()` 唯一索引，保证大小写不敏感的一对一映射；外键 `ON DELETE RESTRICT` 阻止删除仍有映射的采集定义。

### 任务模板 `etl_job`

每个采集定义最多一行：`tid` 是主键，`job` 是完整 Addax job JSON 文本（`scripts/schema.sql#L303-L306`、`pk_tb_job`）。模板在创建/刷新/修改字段映射时重新生成，执行时读取；补数时按目标日期在内存中重新生成而不覆盖该表。

## 队列与运行时

### `etl_job_queue`

见 [调度与分布式队列](../architecture/scheduling-and-queue.md)。持久化相关的关键点：

- 状态机 `pending → running → completed/failed/cancelled`，由 CHECK 约束限定；
- 部分唯一索引 `uq_etl_job_queue_active_tid_biz_date`（等价索引名 `uniq_active_etl_job`）只约束 `pending`/`running`，终态行不阻止再次入队；
- 队列行**没有**指向 `etl_table` 的外键，因此采集定义退役后队列历史仍可保留；
- 索引覆盖待处理排序（`priority, available_at, id`）、running 租约扫描和状态查询。

### `system_flag`

通用键值表，记录 `flag_key`/`flag_value` 与最近启动、完成时间。旧版每日标志逻辑已被按日历重置的 `resetEtlFlagsForCalendars` 取代，但表仍保留（`scripts/schema.sql#L1594-L1601`）。

## 执行事实

执行事实表的设计目标是“历史不随当前配置变化”，见 [ADR 0001](../../docs/adr/0001-retire-collection-definitions-with-execution-snapshots.md)。

### `etl_execution`

每次执行尝试一行，包含 `tid`、`job_queue_id`、`attempt_no`、`biz_date`、源/目标编码与名称、状态、起止时间、`snapshot_version`、`config_snapshot`（jsonb）、`job_digest` 和 `error_message`（`scripts/schema.sql#L158-L180`）。表注释明确为“不可变的采集执行尝试及其配置快照”。`config_snapshot` 在写入前已脱敏。

### `etl_statistic`

Addax 回报的运行指标：`start_at`、`end_at`、`take_secs`、`total_bytes`、`byte_speed`、`rec_speed`、`total_recs`、`total_errors`、`biz_date`，并通过 `execution_id` 关联执行尝试（`scripts/schema.sql#L599-L613`；写入逻辑 `backend/src/main/java/com/wgzhao/addax/admin/controller/LogController.java#L110-L140`）。

### `etl_jour`

操作流水，记录 `kind`（`COLLECT`、`ADDAX_JOB`、`FILLBACK` 等）、命令、开始时间、耗时、状态、错误信息；`execution_id` 关联执行尝试，非执行类流水可带 `context_snapshot`（`scripts/schema.sql#L397-L409`；`backend/src/main/java/com/wgzhao/addax/admin/service/EtlJourService.java#L26-L87`）。

### `addax_log`

原始 Addax 输出，按 `tid`/`execution_id` 关联，保留完整日志文本（`scripts/schema.sql#L59-L66`）。默认保留 180 天（`ADDAX_LOG_RETENTION_DAYS`），每日 03:15 清理；执行快照、统计和流水不随日志清理（`backend/src/main/java/com/wgzhao/addax/admin/service/AddaxLogService.java#L26-L45`、`#L72-L81`）。

## 变更、风险与字典

- `etl_table_change_log`：采集定义的字段级变更历史。由触发器 `trg_etl_table_record_change` 在 `etl_table` UPDATE 后自动写入；触发器忽略 `status`、`duration`、`retry_cnt`、`start_time`、`end_time`、`created_at`、`updated_at`、`max_runtime`、`kind` 这些运行态字段，只记录配置差异（`scripts/schema.sql#L25-L27`、`#L2101`）。`changed_by` 读取事务局部设置 `addax.changed_by`。
- `schema_change_log`：源字段类型/长度/精度/注释变化（`scripts/schema.sql#L1444-L1462`）。
- `risk_log`：风险级别、来源、消息和关联 `tid`，用于目标契约、DDL 变窄、补数跳过等场景。
- `sys_dict` / `sys_item`：系统字典。`sys_item.dict_code` 外键到 `sys_dict.code`，`ON UPDATE CASCADE ON DELETE CASCADE`。关键字典包括 1000（系统配置）、1021（交易日表）、1022（工作日历）、1060（运行状态）、2011（字段类型映射）、5000/5001（Job 模板）。
- `notification` 是旧的统一消息提醒表；`user_notification` 是站内通知，带 `username`、`status`、`ref_type`/`ref_id` 和已读时间。

## 账号表

`users(username, password, enabled)` 与 `authorities(username, authority)` 是 Spring Security `JdbcUserDetailsManager` 的数据源。密码为 BCrypt 哈希；默认种子数据创建 `admin/admin123` 与 `user/user123`（`scripts/data.sql#L1-L10`）。生产部署必须修改默认密码。

## 安全审计 `audit_event`

审计表是只追加的（`scripts/schema.sql#L2214-L2236`）：

- 主键 `id`，`event_id` 唯一（UUID），`occurred_at` 默认 `clock_timestamp()`；
- 记录 actor、action、category、severity、resource、outcome、HTTP 方法/路径、客户端 IP、node_id、before/after 数据、changed_fields、错误码和 metadata；
- 触发器 `trg_audit_event_append_only` 拒绝常规 UPDATE 和 DELETE；只有事务内设置 `addax.audit_purge=on` 时才允许删除；
- 索引覆盖时间、actor、action、resource、request_id 和 outcome。

保留策略由 `AuditMaintenanceService` 执行：默认 365 天，低于 30 天拒绝清理，每次批删 5000 条（`backend/src/main/java/com/wgzhao/addax/admin/audit/AuditMaintenanceService.java#L23-L45`）。写入路径会先脱敏密码、Token、私钥等敏感字段（`backend/src/main/java/com/wgzhao/addax/admin/audit/AuditRepository.java#L25-L76`，脱敏规则在 `AuditSanitizer`）。

## 视图 `vw_etl_table_with_source`

视图把 `etl_table` 左连接 `etl_source` 和 `etl_target`，暴露采集定义本身没有的源/目标展示字段（编码、名称、日历、`max_concurrency`、`db_type`、目标类型等）。源码中唯一定义在 `EtlTableView.CREATE_SQL`，`scripts/schema.sql` 保留一份手工同步副本（`backend/src/main/java/com/wgzhao/addax/admin/config/EtlTableView.java#L20-L75`）。

`EtlTableView` 对 DDL 计算 SHA-256 指纹，并把指纹写成视图注释。`SchemaInitializer` 启动时比较注释：不一致就删除并按当前定义重建，一致则不动。这样新增视图列只需改一处代码。

## 初始化与升级

1. 全新安装：PostgreSQL 容器首次启动执行 `scripts/schema.sql` 和 `scripts/data.sql`；非容器部署手动执行同样的脚本。
2. 升级：`SchemaInitializer.migrate()` 在 `@PostConstruct` 中运行，使用单个事务和 `pg_advisory_xact_lock`，按顺序补齐列、映射表、执行表、外键与视图（`backend/src/main/java/com/wgzhao/addax/admin/config/SchemaInitializer.java#L290-L360`）。
3. 迁移失败会回滚并阻止应用启动，避免半迁移结构对外服务；每一步都幂等，可重复执行。

## 关键不变量与注意事项

- **采集定义不物理删除**：`etl_execution.tid`、`etl_jour.tid`、`etl_job.tid`、`etl_column_mapping.tid` 都指向 `etl_table`，其中执行事实和外键为 `ON DELETE RESTRICT`。删除有历史的定义会被数据库拒绝，产品行为是退役。
- **队列行与定义解耦**：`etl_job_queue` 不设 `tid` 外键，队列历史不会因定义退役而丢失。
- **执行快照只写不改**：应用层在 `EtlExecutionService.finish` 中只更新状态、结束时间和错误信息，`config_snapshot` 不再修改。
- **审计只追加**：绕过 `AuditRecorder` 直接写原始请求体，或常规 UPDATE/DELETE `audit_event`，都会被触发器拒绝。
- **`leader_election` 是遗留表**：当前 Master 选举完全由 Redis 实现，Java 代码中没有对 `leader_election` 的读写；保留表结构不影响运行。
- **`etl_table.target_id` 没有外键**：目标端删除后采集定义可能保留旧 ID；目标端路由解析会在找不到时回退默认类型，业务侧应在删除目标端前检查引用。
