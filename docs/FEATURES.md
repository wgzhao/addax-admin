# Addax Admin 功能详解

本文件对 README 中列出的关键功能点逐一展开，包含实现原理（基于源码分析）、关键配置项、UI/使用指引与运维建议，供开发与运维人员参考。

目录

- 快速批量新增采集表（Batch Add）
- 表结构演化（Schema Evolution）
- 动态表名采集（按日/月分表）
- 增量采集与智能过滤（Incremental Filtering）
- 多节点并发与权重（Sharing-nothing 架构）
- 运行监控、回滚与运维建议

---

1. 快速批量新增采集表（Batch Add）

概述

- 功能说明：在管理界面通过批量模板或导入（UI 操作）快速新增大量采集表配置，并能一并在目标存储（如 Hive）中创建对应的目标表与分区。
- 前端实现：相关组件见 frontend/src/components/table/BatchAdd.vue、BatchUpdate.vue、AddaxJob.vue；前端在 UI 层负责批量录入、模板预览与校验并调用后端 API。
- 后端落地：后端通过 TableService / TargetServiceWithHiveImpl 负责将元信息持久化到 DB（EtlTable、EtlColumn 等表），并调用 Hive 相关逻辑在目标库创建表与分区。

实现要点

- 前端会将批量配置打包为一组请求（JSON），后端 JobContentService / TableService 解析并入库。
- 创建目标表步骤通常为：验证目标表名 -> 生成 DDL（TargetServiceWithHiveImpl 有实现 Hive DDL 的方法）-> 执行建表（通过 JDBC 或 Hive 客户端）-> 记录建表结果与异常到 SchemaChangeLog / AddaxLog。
- 若需要为每张表创建分区，后端会在建表后执行 ALTER TABLE ADD PARTITION / MSCK REPAIR（视目标存储而定）。

UI/使用指引

- 批量操作通常在“表管理”页面通过“批量新增”对话框完成（BatchAdd.vue）。
- 可选择：手工模板、多行粘贴或 CSV 导入，界面会进行字段校验并显示预览。
- 提交后可在“任务/表状态”中查看建表进度与结果日志。

运维建议

- 在生产批量建表前，在测试环境先执行一小批，确认生成的 Hive DDL 与权限设置正确。
- 建表前请确保 Hive 用户/连接有足够权限，且目标库分区策略与分区字段一致。
- 对大量建表操作，建议分批次提交并观察集群负载，避免短时间内对 Hive 元数据库或 NameNode 造成冲击。

---

1. 表结构演化（Schema Evolution）

概述

- 功能说明：自动探测源表结构变化（字段类型变更、字段新增、字段删除等），并能同步更新目标表结构；对于分区表保留历史数据兼容性策略，尽量减少中断。
- 相关代码：ColumnService、TableService、SchemaChangeLogService、TargetServiceWithHiveImpl（包含对 Hive 的 DDL 操作实现）。

实现要点

- 探测机制：系统周期性或在触发源更新时读取源端表元信息（通过 DbUtil / Jdbc 驱动），比对已登记的 EtlColumn 信息，生成变更集合。
- 变更类型：字段新增、字段删除、字段类型变更、字段顺序变更（一般不影响存储但影响映射）。
- 同步策略：
  - 新增字段：执行 ALTER TABLE ADD COLUMN（若 Hive 不支持某些类型或存在兼容性要求，会生成兼容列并记录警告）。
  - 删除字段：默认不物理删除历史列，可能在元数据中标记为 inactive，或对分区进行安全裁剪（需人工确认）。
  - 类型变更：根据兼容性采取转换或新增列 + 填充逻辑；对不兼容变更，记录风险并需要人工干预。
- 历史数据兼容：对于有分区的表，系统会针对新增字段/类型变更优先在新分区生效，对旧分区保留旧列数据；也可以在策略中触发逐分区回填/转换（此类操作通常是异步任务，需谨慎）。

配置与参数

- 表结构刷新保护：Constants.SCHEMA_REFRESH_LOCK_KEY 用于在刷新期间阻断新增/提交采集任务，避免变更期间数据不一致。
- schema 刷新控制参数（在 application.properties/系统常量中可以调整续租/超时时间等）。

UI/使用指引

- 前端在“表详情”或“字段管理”界面显示探测到的变更建议，用户可选择自动应用或手动确认。
- 系统会在变更历史中记录每次 schema 更新（SchemaChangeLog），便于审计与回滚。

运维建议

- 在启用自动演化时，对重要业务表先开启“审阅模式”而非直接生效，确保变更安全。
- 大规模类型变更或需要数据回填的场景，建议走离线迁移任务并通知下游应用窗口期。
- 定期备份表元信息与 schema（元数据导出），以便回滚。

---

1. 动态表名采集（按日/月分表）

概述

- 功能说明：支持按时间模板解析动态表名（例如 my_table_{yyyyMMdd}、ods_{yyyyMM}），自动为时间范围内的表生成采集任务。
- 相关模块：TaskService / JobContentService 与前端 AddaxJob.vue 的任务配置 UI 支持时间模板与调度配置。

实现要点

- 配置方式：在创建任务时提供表名模板与时间范围（start/end 或最近 N 天），后端会展开模板生成具体表名并尝试探测表存在性。
- 展开与调度：对于周期性任务，调度器（SchedulingConfig + CollectionScheduler）会在每个周期根据模板解析出当期表名并执行采集。

使用示例

- 在 UI 中选择“动态表名”类型，填写模板 my_table_{yyyyMMdd}，设置日期区间/周期。系统会列出即将创建的任务并允许批量确认。

运维建议

- 对大量历史表展开时，建议分批跑任务以避免短时间对源库造成压力。
- 若模板包含跨月/跨年，请注意时间格式与时区一致性。

---

1. 增量采集与智能过滤（Incremental Filtering）

概述

- 功能说明：支持在增量采集中以上一次采集结果（如前一日最大 ID、最大时间戳或任意字段值）作为本次采集的过滤条件；支持自定义字段与表达式以适应复杂场景。
- 相关位置：UI（AddaxJob.vue / AddaxResult.vue / AddSource.vue）用于设置过滤字段；后端在 JobContentService / TaskService 中把过滤信息转为查询条件并执行。

实现要点

- 过滤字段与存储：每个采集任务（EtlJob / EtlJobQueue）可以关联一个“state”或 checkpoint（例如上次最大 id），系统会在任务完成时记录最新的 checkpoint（存于 EtlJobQueue.last_processed_value 或类似表/列中）。
- 运行时行为：下一次采集时，系统读取 checkpoint，构造增量查询（WHERE id > :last_max_id 或 timestamp > :last_ts），并提交到源端执行，仅拉取新增/变更部分。
- 自定义表达式：支持用户在 UI 中提供 SQL 片段或表达式（需注意 SQL 注入与可移植性），后端会把表达式拼接到 WHERE 子句。

UI/使用指引

- 在任务配置页选择“增量采集”，指定 checkpoint 字段（例如 id、updated_at）及初始起点（若为首次采集，用户需指定初始值）
- 可以设置“归档/切分”规则（如每月归档一次），以及 checkpoint 的持久化位置和回滚策略。

运维建议

- checkpoint 应定期备份，并在任务失败/重跑时小心处理（避免漏采或重复采）
- 在并发环境下，建议配合 Redis 锁/permit 以避免重复拉取同一批数据

---

1. 多节点并发与权重（Sharing-nothing 架构）

概述

- 功能说明：Addax Admin 采用 sharing-nothing 思想，节点间无共享内存或本地队列，所有任务持久化在数据库，运行时通过 Redis 做仲裁以保证单一执行许可，并可为各节点配置权重（node.concurrency.weight）以实现容量分配。
- 相关实现：EtlJobQueue（DB 表）、EtlJobQueueService、RedisLockService、TaskQueueManagerV2Impl、ExecutionManager、CollectionScheduler

实现要点

- DB 持久化队列（etl_job_queue）：负责可靠存储、重试语义、claim 语义（claimed_by、lease_until 等字段）。数据库提供可靠审计与重入控制。
- Redis 仲裁机制：
  - per-job 独占锁：key = etl:job:{jobId}:lock（使用 SETNX + TTL + token）
  - 全局并发许可与源级 permit：集合/信号量实现（例如 key = concurrent:holders, source:holders:{sourceId}）
  - 续租机制：执行中定期续租锁与 permit，避免误回收
- 节点权重：在 application.properties 中可配置 node.concurrency.weight（或通过节点配置注入环境变量 WEIGHT），调度逻辑在获取 permit 时会参考权重以决定可占用并发槽位数。

配置要点

- application.properties 中：node.concurrency.weight=${WEIGHT:1.0}
- Redis 配置：spring.data.redis.*（host/port/password/db）
- ETL 队列索引：etl_job_queue 上的索引（status, available_at, priority 等）保证了高效的领取操作

运维建议

- 监控 lock 续租成功率、permit 获取失败率与重复执行告警指标
- 在节点扩容/缩容时观察 global permit 的消耗，避免短时并发冲击后端数据库或目标存储
- 确保 Redis 可用性（建议部署哨兵或集群）以避免仲裁单点故障

---

1. 运行监控、回滚与运维建议

监控建议

- 指标采集：任务成功率、任务耗时分布、锁续租成功率、permit 获取失败率、pending/running/failed 任务数
- 日志聚合：集中收集 backend/logs、前端异常日志与 Hive 操作日志，建议使用 ELK/EFK 或 Loki + Grafana
- 告警策略：基于任务失败率/重试次数上升、持久化队列积压/可见延迟（available_at）设置告警

回滚与变更控制

- 对自动 schema 演化类变更，提供“审阅并确认”模式以便人工批准后执行
- 在对目标表结构执行破坏性变更前，建议先在测试分区或影子表执行并验证
- 对关键表的变更操作，先导出 DDL 与元数据信息（SchemaChangeLog），并保留回滚脚本

灾难恢复

- 数据层：定期备份 PostgreSQL（schema + 数据）与 Hive 元数据
- 元数据：导出 EtlTable / EtlColumn / SchemaChangeLog 用作恢复参考
- 作业重跑：系统支持重试与重入，重跑时请注意 checkpoint 的恢复策略以避免重复写入或数据丢失

---

附：关键配置与源码参考点（便于定位）

- 后端启动类：backend/src/main/java/com/wgzhao/addax/admin/AdminApplication.java
- 调度与队列逻辑：backend/src/main/java/com/wgzhao/addax/admin/scheduler/CollectionScheduler.java
- DB 队列表：backend/src/main/resources/schema.sql（etl_job_queue）
- Redis 锁实现：backend/src/main/java/com/wgzhao/addax/admin/redis/RedisLockService.java
- 节点权重与并发：application.properties 中 node.concurrency.weight（env: WEIGHT）
- Hive 目标表操作：backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImpl.java
- 批量操作 UI：frontend/src/components/table/BatchAdd.vue
- 任务配置 UI：frontend/src/components/table/AddaxJob.vue
- 字段/列相关：backend/src/main/java/com/wgzhao/addax/admin/service/ColumnService.java
- Schema 变更日志：backend/src/main/java/com/wgzhao/addax/admin/service/SchemaChangeLogService.java
- 任务执行管理：backend/src/main/java/com/wgzhao/addax/admin/service/ExecutionManager.java
