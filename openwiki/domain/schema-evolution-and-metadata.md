---
type: domain
title: 元数据刷新与表结构演化
description: 源字段发现、字段变更检测、Hive/RDBMS 目标 DDL 演进规则、动态表名解析、风险日志与刷新失败时的状态转换。
tags: [schema-evolution, metadata, ddl, hive, rdbms, risk-log]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-a9f342fd65bc1d5a23c5ad08
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/ColumnService.java
  - id: openwiki-source-666cda7a33d670fcf5f1e9f2
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/RdbmsTargetAdapter.java
  - id: openwiki-source-9a1773cc4388f1722dacb00d
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImpl.java
  - id: openwiki-source-650482944d35601234caaec8
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java
  - id: openwiki-source-6ae4d778f72d0e2b1d1fbb06
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/RiskLogService.java
  - id: openwiki-source-ab44e461e58f17c137e6558a
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/SchemaChangeLogService.java
  - id: openwiki-source-8e53bcc120d6cbf603fa935b
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java
  - id: openwiki-source-8bb1b4dd4cf1ecdfe92e906e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TargetContractService.java
  - id: openwiki-source-6aaaf02024b7684672696d20
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/utils/RdbmsDdlUtil.java
  - id: openwiki-source-23775c3de52f3ab95a13cb8b
    resource: repo://README.md
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 元数据刷新与表结构演化

采集定义不是静态快照：源表会加字段、改类型、删字段，目标表也要跟着演进。系统把“源字段发现 → 字段变更检测 → 目标 DDL 演进 → 任务模板重生成”串成一次资源刷新，并对无法安全自动执行的变更记录风险日志而不是强行修改。

## 刷新入口

资源刷新在以下场景触发：

- 创建采集定义后的初始化；
- 手动“更新表结构”（单个或批量）；
- 每日切日刷新 `TaskService.updateParams()`；
- 修改字段映射后重新生成任务。

入口统一到 `TableService.refreshTableResources`（`backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java#L66-L155`）：

1. 若源表名包含 `${...}` 占位符，先用当前业务日期变量替换，再查询源元数据；
2. `ColumnService.updateTableColumnsV2` 发现字段变化；
3. 目标结构有变化（或状态为 `U`）时：
   - `MANAGED`：调用目标适配器 `createOrUpdateTable`；
   - `EXTERNAL`：只调用 `TargetContractService.check`，不执行 DDL；
4. 重新生成 Addax job 模板；
5. 根据结果更新采集表状态。

刷新是协作式可中断的：在列更新、目标 DDL、模板生成前都会检查线程中断标志。

## 源字段发现

`ColumnService.getEtlColumnV2` 通过 JDBC `DatabaseMetaData.getColumns` 读取源表字段（`backend/src/main/java/com/wgzhao/addax/admin/service/ColumnService.java#L362-L421`）：

- 以 `COLUMN_NAME`、`TYPE_NAME`、`COLUMN_SIZE`、`DECIMAL_DIGITS`、`REMARKS` 构造 `etl_column`；
- 目标端为 HDFS 或未指定时，按字典 2011（ODS 采集类型映射）把源类型映射为 Hive 类型；`decimal` 保留 `decimal(p,s)`；
- 目标端为 RDBMS 时不做类型转换，`target_type`/`target_type_full` 直接使用源类型；
- MySQL 需要把 `source_db` 当 catalog 使用，代码显式处理了 catalog/schema 差异。

**关键失败语义**：如果连接失败、元数据查询失败或返回空字段列表，刷新会发送企业微信告警、把流水标记为失败并返回 `-1`，而不是把“空列表”解释为“所有字段都被删除”。后者会把全部目标列加上删除占位前缀，是破坏性误判（`ColumnService.java#L246-L258`、`#L353-L360`）。

## 字段变更检测

`updateTableColumnsV2` 按**字段名**而不是位置对齐（`ColumnService.java#L222-L351`）：

1. 遍历 `etl_column` 中已有的非删除占位列，按名称在源字段中查找；
2. 源字段不存在 → 把现有列名加上 `DELETED_PLACEHOLDER_PREFIX` 前缀，记录 `DELETE`，保留目标表位置与历史映射；
3. 源字段存在但类型/长度/精度/注释变化 → 记录 `TYPE_CHANGE`，更新 `etl_column` 的目标类型；
4. 源字段存在且未匹配过 → 追加到末尾，记录 `ADD`。

HDFS 目标的类型兼容判断由 `HiveType.isHiveTypeCompatible` 决定：新类型能覆盖旧类型时不算变化（例如 `int → bigint`、`string` 覆盖任意类型），避免每次刷新都产生无意义的 ALTER。RDBMS 目标则直接比较源类型与长度/精度。

最后调用 `ColumnMappingService.reconcile`：已有映射保持不动，新字段按同名生成 identity 映射，删除字段的映射保留但标记为 `sourceActive=false`。

`SchemaChangeLogService` 把三类变更写入 `schema_change_log`（`backend/src/main/java/com/wgzhao/addax/admin/service/SchemaChangeLogService.java#L16-L75`），供字段变化监控使用。

## 目标 DDL 演进

### Hive / HDFS

`TargetServiceWithHiveImpl.createOrUpdateTable`（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImpl.java#L489-L620`）：

- 先 `create database if not exists`；
- 用 `create external table if not exists` 建表，分区键来自 `target_options.hdfs.partitionBindings`，存储格式与压缩来自表级配置或全局字典；
- 如果表是本次新建的，跳过 ALTER 比对；
- 如果表已存在，用 `DESCRIBE` 读取当前列与分区列，按映射生成 `ADD COLUMNS` 或 `CHANGE COLUMN`；
- 只在当前 Hive 类型不能覆盖期望类型时才 `CHANGE COLUMN`；
- 单条 ALTER 失败不会中断整个刷新：失败写入 `etl_jour` 和 `risk_log`，继续执行后续语句。

Hive 不会自动删除目标列；源端删除只体现在 `etl_column` 的占位与字段映射的 `sourceActive=false`，目标表保留历史列。

### RDBMS（MySQL / PostgreSQL / SQL Server）

`RdbmsTargetAdapter.createOrUpdateTable`（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/RdbmsTargetAdapter.java#L131-L276`）：

- 建表使用 `CREATE TABLE IF NOT EXISTS`（SQL Server 用 `OBJECT_ID` 判存在），主键按源端主键映射生成，可通过 `createPrimaryKey=false` 关闭；
- 表已存在时进入 `applyColumnDiff`：缺失列直接 `ADD COLUMN`；
- 已有列只在**类型族变化**或**容量变大**时 `MODIFY/ALTER COLUMN`；变窄、类型族变化但无法安全承载时不会自动执行；
- 主键列需要改类型时跳过并记录风险日志，避免破坏主键约束；
- MySQL 的 `MODIFY COLUMN` 是整列重定义，因此会重述 `NOT NULL`、可识别的 `DEFAULT`、`AUTO_INCREMENT` 和注释；无法安全重述的默认值会记录风险而不是静默丢弃；
- 新表创建后会单独执行 `COMMENT ON COLUMN`（仅 PostgreSQL）或内联注释（MySQL）。

`RdbmsDdlUtil` 负责方言渲染（`backend/src/main/java/com/wgzhao/addax/admin/utils/RdbmsDdlUtil.java`）：

- `familyOf` 把类型归为 string/number/temporal/binary/json 等族；
- `isWidening` 只在容量变大时返回 true，`text/blob` 等无容量概念的类型返回 false；
- `renderType` 按 MySQL/PostgreSQL/SQL Server 渲染；`enum`/`set` 退化为字符串，SQL Server 不落地列注释。

### EXTERNAL 模式

外部管理的目标在刷新时完全不执行 DDL。`refreshTableResources` 调用 `TargetContractService.check`，被阻断时把表状态置为 `U` 并返回失败；通过时保存目标结构指纹（`target_schema_fingerprint`）。契约规则见 [目标契约与字段映射](target-contract-and-mapping.md)。

## 动态表名

源表名可以包含 `${日期变量}` 占位符，用于日表、月表等按时间切分的表。解析分两处：

- 刷新元数据前：`TableService.refreshTableResources` 用 `SystemConfigService.getBizDateValues()` 替换占位符后再查源表（`TableService.java#L84-L95`）；
- 生成 job 时：`JobContentService.fillRdbmsReaderJobForDate` 用同一组日期变量替换 reader 的表名（`backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java#L255-L268`）。

因此占位符的日期口径必须与业务日期一致；补数时使用目标日期变量重新生成模板，而不是复用当前模板（见 [补数与业务日历](../workflows/fillback-and-calendar.md)）。

## 失败与状态转换

| 失败点 | 采集表状态 | 副作用 |
| --- | --- | --- |
| 源连接/元数据获取失败 | `E` | 企业微信告警、`etl_jour` 失败 |
| 目标 DDL 执行失败 | `U`（待更新结构） | 流水、风险日志；不会把表标成已采集 |
| EXTERNAL 契约阻断 | `U` | 契约摘要写入流水与风险日志 |
| job 模板生成失败 | `E` | 流水失败 |
| 全部成功 | `N`（仅当业务日到期） | 休息日保留上一次采集状态 |

状态语义见 [采集定义与生命周期](collection-definition-lifecycle.md)；切日刷新如何暂停队列见 [调度与分布式队列](../architecture/scheduling-and-queue.md)。

## 风险日志

`RiskLogService.recordRisk` 按“消息 + tid”去重后写入 `risk_log`（`backend/src/main/java/com/wgzhao/addax/admin/service/RiskLogService.java#L24-L37`）。它用于记录无法自动处理的演进：

- RDBMS 主键列类型变化被跳过；
- 类型族变化导致原默认值无法重述；
- 目标表已存在但业务日期列无法自动加入主键；
- Hive ALTER 失败；
- 不支持的目标方言。

风险日志不是阻断项，采集可能继续；需要人工判断是否修复目标结构。查询入口在监控页面（见 [可观测性、审计与告警](../operations/observability-and-alerts.md)）。

## 已知边界

- 只有 MySQL、PostgreSQL、SQL Server 的 RDBMS writer 方言端到端实现；Oracle/DB2/Sybase/ClickHouse/SQLite 会在保存目标端或测试连接时被拒绝。
- SQL Server 不落地列注释；跨方言 `enum`/`set` 退化为字符串；源端 `DEFAULT` 只在类型演化时尝试重述，建表时不复制。
- 超宽字符集下的宽主键可能超过 InnoDB 3072 字节索引上限。
- Hive 分区表的列顺序与映射顺序必须一致，否则文件列会错位；契约检查会对此阻断。
