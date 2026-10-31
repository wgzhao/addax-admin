# 目标端能力与表属性

目标端由 `etl_target` 的路由键决定，同一套界面里可以混用。

## 支持矩阵

| 目标端       | 模板键 | 已实现方言                    | 说明                           |
| ------------ | ------ | ----------------------------- | ------------------------------ |
| Hive / HDFS  | `wH`   | Hive                          | 按业务日期分区写入，默认目标端 |
| 关系型数据库 | `wR`   | MySQL、PostgreSQL、SQL Server | 直接写回业务库，按主键幂等写入 |

## 关系型数据库目标（阶段一）

### 写入语义

- 写入语义按主键幂等：建表时用源端主键生成 `PRIMARY KEY`。
- `overwrite` 在 MySQL 上映射为 `REPLACE INTO`。
- PostgreSQL / SQL Server 请显式配置 `update`，依赖主键或唯一键。
- 目标表没有主键/唯一键时会记录风险日志，因为重复执行会插入重复数据。

### 表结构演化

自动同步新增列与“容量变大”的类型变更，例如：

- `int → bigint`
- `varchar(20) → varchar(50)`

以下变更不会自动执行，只记录风险日志，需要人工处理：

- 类型变窄
- 类型族变更
- 主键列的类型变更

### 暂未支持的方言

Oracle、DB2、Sybase、ClickHouse、SQLite 在保存目标端或测试连接时会直接报错，不会生成引用不存在 writer 插件的采集任务。

### 已知限制

- SQL Server 列注释不落地。
- 跨方言的 `enum`/`set` 退化为字符串列。
- 源端 `DEFAULT` 仅在类型演化时重述，建表时不复制。
- 宽字符集下的宽主键（如 `utf8mb4 varchar(1000)`）可能超过 InnoDB 3072 字节索引上限。

## 目标端表属性（阶段二）

目标端专属属性不再钉死在 `etl_table` 上，而是按目标端族存放在 `etl_table.target_options`（jsonb）：

| 分组    | 属性                               | 说明                                                                                               |
| ------- | ---------------------------------- | -------------------------------------------------------------------------------------------------- |
| `hdfs`  | `partitionBindings`                | 目标分区键与运行期取值的一对一绑定；支持业务日期或固定值，可配置多个分区键                         |
| `hdfs`  | `partitionCreationMode`            | Hive 分区创建责任，`PLATFORM` 表示运行期由平台创建，`EXTERNAL` 表示由外部创建并在运行前校验        |
| `hdfs`  | `storageFormat` / `compressFormat` | Hive/HDFS 文件存储与压缩格式；为空时沿用系统设置（`HDFS_STORAGE_FORMAT` / `HDFS_COMPRESS_FORMAT`） |
| `rdbms` | `businessDateColumn`               | 平台提供的普通快照字段，为空表示不承载业务日期；不是数据库分区字段                                 |
| `rdbms` | `createPrimaryKey`                 | 是否按源端主键在目标表建主键，默认开                                                               |

表详情与批量创建表单按目标端能力动态渲染：

- Hive 目标端显示分区/存储字段。
- 关系型目标端显示快照日期列与主键开关。

## 快照式采集

配置 `businessDateColumn`（如 `logdate`）后：

- 目标表会多出一列 `varchar(8)`（SQL Server 为 `nvarchar(8)`）。
- 采集时由 reader 注入当天的业务日期字面量。
- 该列是普通快照字段，**不是分区键，也永远不参与主键**。
- 目标主键只按源端主键生成。
- 已存在的表只补加该列，不改动已有主键。
- 源表若已有同名列，直接复用，不再重复注入。
- 这类表支持补数。

跨天采集时要配合以下任一种方式：

1. 关闭“创建主键”：同一业务主键在多天会互相覆盖。
2. 用 `preSql` 先删除当日数据。

两种情况下都会记录一条风险日志提醒。

## 补数限制

没有日期维度的表不支持补数：

- HDFS 非分区表
- 未配置业务日期列的关系型目标表

补多个日期会互相覆盖，提交时会被跳过并记录风险日志。

## 主键开关

关闭 `createPrimaryKey` 后：

- 建表不再生成主键。
- 重复执行的去重交给 `writeMode` / `preSql`。
- 配置了快照日期列又关闭主键时，会记录风险日志提醒。

## 从 v4.2.0 升级

无需手工改表。服务启动会自动执行一次幂等迁移：

1. 把 `etl_table` 的 `part_kind`/`part_name`/`part_format`/`storage_format`/`compress_format` 回填进 `target_options`。
2. 重建 `vw_etl_table_with_source` 视图。
3. 删除旧的 5 列。

全新安装直接以 `scripts/schema.sql` 为准。

## 相关文档

- [目标表结构与字段映射](05-target-contract.md)
- [升级与迁移](../install/04-upgrade.md)
- [ADR 0002：外部目标契约与字段映射](../../adr/0002-external-target-contract-and-column-mapping.md)
