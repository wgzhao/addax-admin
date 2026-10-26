---
type: integration
title: Addax 任务契约与目标适配器
description: Addax job JSON 的模板来源、reader/writer 片段生成、插件配置合并、Hive 与 RDBMS writer 方言、类型映射、目标适配器分派以及 Addax 安装目录、作业文件和日志契约。
tags: [addax, job-json, target-adapter, hdfs, rdbms, writer-plugin, ddl]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-131075c0b1a6fd22773f5f72
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java
  - id: openwiki-source-666cda7a33d670fcf5f1e9f2
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/RdbmsTargetAdapter.java
  - id: openwiki-source-10c4c4c99c8b3da608d3f1f5
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceImpl.java
  - id: openwiki-source-9a1773cc4388f1722dacb00d
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImpl.java
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-650482944d35601234caaec8
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java
  - id: openwiki-source-34e028b85db6fe1cb4d4a9ac
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/target/RdbmsWriterPlugins.java
  - id: openwiki-source-8e602ccc6d546ced91e432f5
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetAdapter.java
  - id: openwiki-source-c1f27e033d83feafe7508084
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetAdapterRegistry.java
  - id: openwiki-source-8ee43c3b0b88a703d0d2bdd7
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetTypeResolver.java
  - id: openwiki-source-6aaaf02024b7684672696d20
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/utils/RdbmsDdlUtil.java
  - id: openwiki-source-49f5868b9bc0b9372e54c787
    resource: repo://scripts/data.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# Addax 任务契约与目标适配器

平台不直接实现数据传输，而是把一张采集表编译成 Addax job JSON，再以子进程方式调用本机 Addax。这个编译过程由三部分组成：主模板、reader 片段、writer 片段。writer 片段由目标端适配器生成，因为 HDFS 与 RDBMS 的插件名、参数和 DDL 规则完全不同。

执行时序本身见 [采集执行链路](../architecture/execution-pipeline.md)；本页只描述 job JSON 的契约。

## 模板来源与选择

### 字典中的模板

模板保存在 `sys_item`：

| 字典 | key | 内容 | 来源 |
| --- | --- | --- | --- |
| 5000 | `R2H` | 关系型源到 HDFS 的主 job 模板 | `scripts/data.sql` |
| 5000 | `R2R` | 关系型源到关系型目标的主 job 模板 | 启动时内置默认值写入 |
| 5001 | `rR` | 通用 RDBMS reader 片段 | `scripts/data.sql` |
| 5001 | `wH` | HDFS writer 片段 | `scripts/data.sql` |
| 5001 | `wR` | 通用 RDBMS writer 片段 | 启动时内置默认值写入 |

（`scripts/data.sql#L100-L142`、`backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java#L250-L280`）

`DictService.ensureRdbmsTemplates` 在启动时幂等补齐 R2R 与 wR。读取时 R2R、wR 还有代码内置回退，字典缺失也能生成可用 job；R2H、rR、wH 没有内置回退，缺失会导致任务生成失败（`DictService.java#L196-L245`）。

### 主模板按目标族选择

`JobContentService.resolveJobTemplate` 调用 `TargetTypeResolver.resolveAdapterType(tid)`：解析结果为 HDFS 时用 R2H，否则用 R2R（`backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java#L144-L152`）。这与适配器注册中心使用同一套路由，避免“writer 用了 RDBMS 模板、适配器却按 HDFS 处理”。

路由优先级（`backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetTypeResolver.java#L39-L89`）：

1. `etl_target.writer_template_key = wH` → HDFS；`wR` → RDBMS；
2. 否则看 `target_type` 是否命中已知 HDFS/RDBMS 方言；
3. 其他非空 `target_type` 原样返回，由注册中心显式拒绝；
4. 都没有时回退默认类型 `HDFS`。

### 编译流程

`JobContentService.updateJob`（`backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java#L115-L160`）：

1. 生成 reader 片段（`fillRdbmsReaderJob`）；
2. 通过 `TargetService.buildWriterJob` 取得目标端 writer 片段；
3. 用 `StringSubstitutor` 把 `${reader}`、`${writer}` 替换进主模板；
4. 合并用户插件配置；
5. 写入 `etl_job`，每张表一行。

补数走 `getJobContentForDate`：使用目标日期的日期变量重新生成，不写回 `etl_job`（`JobContentService.java#L84-L110`）。正常运行读取已缓存的模板；只有补数日期与系统业务日期不同才重新生成（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L1070-L1082`）。

手工编辑 job 通过 `PUT /tables/{tid}/addax-job` 原样保存；已退役表返回 409（`JobContentService.java#L394-L407`）。这意味着手工修改会在下一次资源刷新或切日重新生成时被覆盖。

## Reader 片段

`fillRdbmsReaderJobForDate` 用字典 5001 的 `rR` 模板生成（`JobContentService.java#L225-L335`）。填充项：

| 占位符 | 来源 |
| --- | --- |
| `name` | `${dbType}reader`，由源 JDBC URL 推断方言 |
| `username` / `password` / `jdbcUrl` | 采集源连接信息 |
| `where` | 表级过滤规则；`__max__` 特殊规则见下 |
| `autoPk` / `splitPk` | 表配置 |
| `fetchSize` | 固定 `20480` |
| `${table}` | 源库.源表，按方言引用；PostgreSQL 只用表名 |
| `${column}` | 由字段映射生成的 reader 列表达式 |
| 日期变量 | `SystemConfigService.getBizDateValues()` 或补数日期变量 |

### 过滤规则与增量位点

普通过滤规则直接放入 `where`。若过滤规则以 `__max__` 开头，则视为“按目标端最大值增量”：

- 正常运行时从 `etl_statistic` 取该表最近业务日期；
- 补数时使用目标日期之前的上一个业务日，按采集源日历回退，而不是简单减一天；
- 读取目标端该列最大值后生成 `column > value`；取不到最大值时回退 `1=1` 并记录风险日志。

（`JobContentService.java#L235-L260`、`#L340-L380`）

### 字段列与业务日期

reader 的 `column` 完全由 `ColumnMappingService.resolve` 决定，而不是源表原始列顺序：

- 源字段与目标字段同名：直接输出源列；
- 不同名：`source as target`；
- 源字段已删除：`NULL as target`，保持目标列位置；
- 目标端配置了 RDBMS 业务日期列且映射中不存在该列：在最后追加 `'yyyyMMdd' as <column>`。

（`JobContentService.java#L280-L330`）

业务日期字面量放在最后，writer 也必须把它放在最后，否则 Addax 的按位置写入会错列。

动态表名（`${日期变量}`）在 reader 生成阶段替换，因此补数时用补数日期重新解析；见 [元数据刷新与表结构演化](../domain/schema-evolution-and-metadata.md)。

## 目标适配器分派

`TargetAdapter` 把目标端差异收敛为一组能力（`backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetAdapter.java#L12-L63`）：

- `createOrUpdateTable`：建表/演进；
- `inspectTable`：只读目标元数据；
- `prepareBeforeRun`：运行前准备（Hive 分区）；
- `buildWriterJob` / `buildWriterJobForDate`：生成 writer 片段；
- `getMaxValue`：增量位点；
- `hasDateDimension`：是否具备按日维度。

`TargetService` 门面把所有调用转发给 `TargetAdapterRegistry.resolve(table)`（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceImpl.java#L25-L85`）。注册中心按类型精确匹配，未知类型直接抛出 `IllegalStateException`，不会静默降级到 RDBMS（`backend/src/main/java/com/wgzhao/addax/admin/service/target/TargetAdapterRegistry.java#L30-L80`）。

### Writer 模板

writer 片段优先取 `etl_target.writer_template_key` 对应的字典项：

- HDFS：`wH`；缺失时回退全局 `getHdfsWriterTemplate()`；
- RDBMS：`wR`；缺失时回退代码内置默认 writer 模板；
- 读取失败或 key 为空时，HDFS 仍走全局 HDFS 模板，RDBMS 走 `DEFAULT_WRITER_TEMPLATE_KEY`。

（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImpl.java#L431-L445`、`backend/src/main/java/com/wgzhao/addax/admin/service/impl/RdbmsTargetAdapter.java#L371-L388`）

连接占位符来自 `etl_target.connect_config` 的 `url` / `username` / `password`，不是采集源连接（`TargetServiceWithHiveImpl.java#L447-L475`）。

## HDFS writer

`TargetServiceWithHiveImpl.buildWriterJobForDate` 用 `wH` 模板填充（`TargetServiceWithHiveImpl.java#L406-L429`）：

- `fileType`、`compress` 优先取表级配置，否则回退全局字典 `HDFS_STORAGE_FORMAT` / `HDFS_COMPRESS_FORMAT`；
- `writeMode` 直接取表配置；
- `path` = 全局 `HDFS_PREFIX` + 目标库 + 目标表 + 各分区 `name=value`；
- `column` 占位符格式由模板决定：模板中写 `[ ${column} ]` 时输出 `"col1","col2"`，否则输出 HDFS writer 需要的对象数组。

HDFS 列对象使用每个源字段的 `targetTypeFull` 作为目标类型，并按映射顺序排列（`TargetServiceWithHiveImpl.java#L767-L783`、`#L806-L814`）。真正的 Hive 建表/ALTER 不发生在 Addax 内，而由适配器在资源刷新时执行。

## RDBMS writer

### 支持的方言与插件

`RdbmsWriterPlugins` 只映射三种方言（`backend/src/main/java/com/wgzhao/addax/admin/service/target/RdbmsWriterPlugins.java#L31-L75`）：

| 方言 | writer 插件 |
| --- | --- |
| MySQL | `mysqlwriter` |
| PostgreSQL | `postgresqlwriter` |
| SQL Server | `sqlserverwriter` |

其他方言在配置目标端或生成 writer 片段时返回 400。若 `ADDAX_HOME` 可见，目标端配置阶段还会检查 `${ADDAX_HOME}/plugin/writer/<plugin>` 是否存在；不可见时只记录一次运行期告警，不阻断配置（`RdbmsWriterPlugins.java#L77-L120`）。

### 写入模式

`resolveWriteMode` 把表级 `writeMode` 归一化为 Addax 可接受的值（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/RdbmsTargetAdapter.java#L408-L426`）：

- 空值 → `insert`；
- `overwrite`：MySQL → `replace`；PostgreSQL/SQL Server 没有原生 overwrite，回退 `insert` 并记录告警；
- 以 `insert` / `replace` / `update` 开头的值直接保留；
- 其他值回退 `insert`。

因此“覆盖”语义在非 MySQL 目标上依赖显式 `preSql` 或唯一键，而不是平台生成的 job 参数。

### 表名与列

writer 的 `table` 使用目标方言的限定名：PostgreSQL 只用表名，SQL Server 为 `db.[dbo].table`，MySQL 为 `db.table`（`backend/src/main/java/com/wgzhao/addax/admin/utils/RdbmsDdlUtil.java#L92-L110`）。`${column}` 展开为按方言引用的目标列，并在业务日期列由 reader 注入时把它追加到末尾（`RdbmsTargetAdapter.java#L909-L930`）。

## 类型映射

### Hive

Hive 目标列类型来自源字段元数据经字典 2011（ODS 采集类型映射）转换后的 `target_type_full`；HDFS writer 的列对象直接使用它。映射规则与刷新失败语义见 [元数据刷新与表结构演化](../domain/schema-evolution-and-metadata.md)。

### RDBMS

RDBMS 类型解析有两条路径（`RdbmsTargetAdapter.java#L622-L680`）：

1. **同方言 MySQL 源与目标**：复用源端 `information_schema.columns.COLUMN_TYPE` 原样类型、`NOT NULL` 和源主键。这样 `enum`/`set`、`unsigned`、精确长度等无法从 JDBC 元数据还原的类型不会丢失，`REPLACE`/`UPDATE` 依赖的键也能保留。
2. **跨方言或其他源**：用 `RdbmsDdlUtil.renderType` 把源 JDBC 类型归一到 string/text/integer/bigint/smallint/tinyint/boolean/decimal/float/double/date/time/timestamp/timestamptz/binary/json 等类别，再按目标方言渲染。

渲染规则的代表性差异（`RdbmsDdlUtil.java#L424-L530`）：

- 字符串超长：PostgreSQL 用 `text`，MySQL 用 `text`，SQL Server 用 `nvarchar(max)`；
- `tinyint`：PostgreSQL/SQL Server 提升为 `smallint`，避免 SQL Server 无符号范围问题；
- 布尔：PostgreSQL `boolean`、MySQL `tinyint(1)`、SQL Server `bit`；
- `enum`/`set` 退化为字符串列；
- SQL Server 不落地列注释。

类型族与扩容判断由 `familyOf` / `isWidening` 完成（`RdbmsDdlUtil.java#L224-L315`）：只有类型族一致且容量变大才自动 `MODIFY`，变窄或跨族不自动执行。

## 插件配置合并

`reader_plugin_config` / `writer_plugin_config` 是用户级 JSON 对象。`mergePluginConfigIntoJob` 在模板替换完成后解析 job，把对象字段逐项写入 `job.content.reader.parameter` 或 `job.content.writer.parameter`（`JobContentService.java#L154-L208`）。

语义：

- 自定义 key 覆盖模板中的同名参数；
- 只接受 JSON 对象；非对象或 job 结构不符合预期时记录告警并返回未合并的 job；
- 合并在每次生成时执行，包括补数和资源刷新，因此插件配置不会被模板重新生成丢失。

由于合并发生在 `${reader}`/`${writer}` 替换之后，用户在插件配置里可以覆盖连接、列、writeMode 等任意参数，但覆盖列或连接会破坏与字段映射的一致性，属于高风险操作。

## Addax 安装目录、作业文件与日志

### 安装目录

`ADDAX_HOME` 来自字典 1000 的 `ADDAX` 项，默认 `/opt/app/addax`（`backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java#L168-L173`）。平台用它定位：

- 可执行脚本 `${ADDAX_HOME}/bin/addax.sh`；
- writer 插件目录 `${ADDAX_HOME}/plugin/writer/<plugin>`；
- Addax 日志目录 `${ADDAX_HOME}/log/`。

平台不启动常驻的 Addax 服务，而是每次采集启动一个进程；多节点部署时每个 worker 节点都必须安装同一份 Addax 及所需 writer 插件。

### 作业文件

执行前把 job 写到 `${app.home}/job/yyyy-MM-dd/`，文件名 `{targetDb}.{targetTable}_{bizDate}.json`（`TaskQueueManagerV2Impl.java#L1118-L1130`）。把业务日期放进文件名，避免同一张表并发补数时互相覆盖。

### 进程与日志

命令形式（`TaskQueueManagerV2Impl.java#L1134-L1140`）：

```bash
${ADDAX_HOME}/bin/addax.sh \
  -p -DjobName=<tid>:<executionId> -Dlog.file.name=<logName> \
  /path/to/job.json
```

- `jobName` 把 Addax 进程与执行快照关联；
- `logName` 形如 `{targetDb}.{targetTable}_{tid}_{bizDate}.log`；
- 进程退出码决定执行成功/失败，`maxRuntime` 超时会终止进程；
- 进程结束后读取 `${ADDAX_HOME}/log/<logName>` 并写入 `addax_log`，即使日志不完整也保留已有内容（`TaskQueueManagerV2Impl.java#L1152-L1190`）。

### 失败语义

| 失败点 | 行为 |
| --- | --- |
| 主模板/reader 模板缺失 | job 为空，执行在启动 Addax 前失败 |
| 不支持的 RDBMS 方言 | 配置或生成 writer 时返回 400 |
| writer 插件目录缺失 | `ADDAX_HOME` 可见时配置阶段 400；否则运行期告警 |
| 插件配置不是 JSON 对象 | 忽略合并并告警，job 仍可执行 |
| Addax 非零退出 | 执行快照标记失败，队列按重试语义处理 |
| job 模板未生成 | 执行快照直接失败，不启动进程 |

执行成功后的统计回报由 Addax 调用 `/log/job-report` 完成，见 [采集执行链路](../architecture/execution-pipeline.md) 与 [REST API 与安全模型](../backend/api-and-security.md)。

## 扩展新目标端

新增目标端类型需要同时提供：

1. 一个 `TargetAdapter` 实现，声明 `getType()` 并实现建表、检查、运行前准备和 writer 生成；
2. 一个 `writer_template_key` 与对应 `sys_item` 5001 模板；
3. 若为 RDBMS，还需在 `RdbmsWriterPlugins` 注册方言与 writer 插件，并在 `RdbmsDdlUtil` 补齐类型渲染与标识符引用；
4. 在 `TargetTypeResolver` 的已知类型集合中登记，确保路由不会落入显式拒绝分支。

注册中心按类型精确匹配，未注册类型会直接失败，这是有意的 fail-fast 设计，避免生成引用不存在插件的 job。

## 相关页面

- [采集执行链路](../architecture/execution-pipeline.md)
- [元数据刷新与表结构演化](../domain/schema-evolution-and-metadata.md)
- [目标契约与字段映射](../domain/target-contract-and-mapping.md)
- [部署与配置](../operations/deployment-and-configuration.md)
