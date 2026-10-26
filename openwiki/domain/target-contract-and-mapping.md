---
type: domain
title: 目标契约与字段映射
description: MANAGED/EXTERNAL 目标结构所有权、显式 source→target 字段映射、契约校验项、指纹、阻断/告警语义与 target_options。
tags: [target-contract, column-mapping, schema-ownership, external-target, fingerprint]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-b72b0273e646b91a44a20032
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/common/SchemaManagementMode.java
  - id: openwiki-source-b97b5a7ae5dc3f0f30312a3e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/common/TargetOptions.java
  - id: openwiki-source-3c22957a0d6874fd62feaa5e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/dto/ColumnMappingInput.java
  - id: openwiki-source-b7c042bd5e5654b8a1888e11
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/dto/TargetContractReport.java
  - id: openwiki-source-22b7ce71857d8eaa846c7b73
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/CollectionPlanCommitService.java
  - id: openwiki-source-bda9ff5b7330c86cf3ae0774
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/CollectionPlanPreviewService.java
  - id: openwiki-source-c24cb3299e33853df394eda2
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/ColumnMappingService.java
  - id: openwiki-source-058bdcb2c3aa67bf05fcaf8c
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlExecutionService.java
  - id: openwiki-source-636d456d0413329b8f962e64
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/ExternalCollectionService.java
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-650482944d35601234caaec8
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java
  - id: openwiki-source-8bb1b4dd4cf1ecdfe92e906e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TargetContractService.java
  - id: openwiki-source-4e667650a3c1d4d232d9d9ce
    resource: repo://docs/adr/0002-external-target-contract-and-column-mapping.md
  - id: openwiki-source-56ca36fad3cacc52c6c8f19a
    resource: repo://scripts/schema.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 目标契约与字段映射

目标表结构可能由平台维护，也可能由 DBA 或外部治理流程维护；源字段名也未必能直接作为目标字段名。系统用两个显式概念解决这两类不确定性：**目标表结构模式**（谁负责 DDL）和**字段映射**（source → target 的稳定契约）。设计动机见 [ADR 0002](../../docs/adr/0002-external-target-contract-and-column-mapping.md)。

## 目标结构所有权

`SchemaManagementMode` 只有两个值（`backend/src/main/java/com/wgzhao/addax/admin/common/SchemaManagementMode.java`）：

| 模式 | 目标表要求 | 平台行为 |
| --- | --- | --- |
| `MANAGED`（默认） | 可以不存在，也可以已存在 | 创建或演进目标表、写入字段映射、初始化采集任务 |
| `EXTERNAL` | 创建采集定义前必须已存在 | 只读取目标元数据并校验契约，不创建/修改表、字段或约束 |

`MANAGED` 的 DDL 行为见 [元数据刷新与表结构演化](schema-evolution-and-metadata.md)。`EXTERNAL` 的刷新、执行和提交都只做检查。

## 字段映射契约

### 数据结构与解析

`etl_column_mapping` 每行是 `(tid, source_column_name, target_column_name, ordinal_no)`：

- 同一 tid 内 source 和 target 都唯一（数据库用 `lower()` 唯一索引保证大小写不敏感）；
- `ordinal_no` 决定顺序，Hive 文件列顺序与之一致；
- 没有显式映射时，`ColumnMappingService.resolve` 会按源字段生成 identity 映射，保证老数据可用；
- `reconcile` 为新出现的源字段追加同名映射，不覆盖已有映射；
- `replace` 校验重复 source、重复 target，并拒绝空映射。

实现见 `backend/src/main/java/com/wgzhao/addax/admin/service/ColumnMappingService.java#L23-L170`。

### 映射的消费方

同一份映射被以下位置共同使用，避免字段改名后链路不一致：

- Reader 输出列与别名（`JobContentService.fillRdbmsReaderJobForDate`）；
- Writer 目标列（`RdbmsTargetAdapter.getRdbmsWriteColumns`、Hive writer 的 `column` 占位符）；
- 目标 DDL（`ColumnService.getHiveColumnsAsDDL`、`RdbmsTargetAdapter.applyColumnDiff`）；
- 执行快照（`EtlExecutionService.buildSnapshot` 写入每列的 sourceName/targetName/ordinal）；
- 目标契约校验（`TargetContractService`）；
- 错误提示与预览（缺失映射、重复映射、类型不兼容）。

源字段被删除时，映射保留但 `sourceActive=false`，Reader 会输出 `NULL as target`，契约层给出告警而不是直接阻断。

### 不开放 SQL 表达式

映射只允许列名一对一，不允许表达式。目标仍按 ODS 贴源层处理，平台内不引入额外的转换风险；需要转换时应在下游处理。

## 目标契约校验

`TargetContractService.check` 读取目标端实时元数据并生成 `TargetContractReport`（`backend/src/main/java/com/wgzhao/addax/admin/service/TargetContractService.java#L45-L189`）。校验项：

| code | 严重级别 | 条件 |
| --- | --- | --- |
| `TARGET_TABLE_MISSING` | ERROR | EXTERNAL 模式下目标表不存在 |
| `TARGET_COLUMN_MISSING` | ERROR | 映射的目标字段不存在 |
| `SOURCE_COLUMN_MISSING` | WARNING | 源字段已删除，将写 NULL |
| `TARGET_TYPE_INCOMPATIBLE` | ERROR | 目标类型无法安全承载源类型 |
| `TARGET_TYPE_WIDER` | WARNING | 类型兼容但不是精确匹配 |
| `TARGET_BUSINESS_DATE_COLUMN_MISSING` | ERROR | 配置了 RDBMS 快照日期列但目标表缺少该列 |
| `TARGET_BUSINESS_DATE_TYPE_INCOMPATIBLE` | ERROR | 快照日期列不是字符串族 |
| `TARGET_REQUIRED_COLUMN_UNMAPPED` | ERROR | 目标非空、无默认值、非自增，且没有映射 |
| `TARGET_EXTRA_COLUMN` | WARNING | 目标有未映射字段，写入时保留默认值或 NULL |
| `PARTITION_KEYS_MISMATCH` | ERROR | 配置的分区键与 Hive 目标实际分区不一致 |
| `MULTIPLE_BIZ_DATE_PARTITIONS` | ERROR | 超过一个分区键承载业务日期 |
| `TARGET_COLUMN_ORDER_MISMATCH` | ERROR | Hive 目标字段顺序与映射顺序不一致 |
| `TARGET_EXTRA_COLUMN_NOT_AT_END` | ERROR | Hive 未映射字段夹在映射字段中间 |

类型兼容规则按目标端分派：

- Hive：`HiveType.isHiveTypeCompatible`，新类型能覆盖旧类型即可；
- RDBMS：先用 `RdbmsDdlUtil.familyOf` 判断类型族一致，再用 `isWidening` 判断目标容量是否足够。

### 状态与阻断

`TargetContractReport.status`：

- `READY`：无 finding；
- `WARNING`：只有 warning；
- `ERROR`：至少一个 error，`blocked()` 返回 true。

每个 finding 都会写入 `risk_log`（按消息+tid 去重）。`ERROR` 在创建/提交/执行前阻断，`WARNING` 只提示。`TargetContractReport.summary()` 最多拼接前 5 条消息，用于 API 和日志展示。

### 指纹

`TargetContractService.fingerprint` 把目标元数据规范化为字符串后计算 SHA-256（`TargetContractService.java#L191-L207`）：

- 是否存在的标记；
- 非分区列按 ordinal 排序，记录 name/type/size/scale/nullable/default/autoIncrement；
- 分区名与类型；
- 排序后的主键和唯一键。

指纹用于：

- `etl_table.target_schema_fingerprint` 保存最近一次通过校验的目标结构；
- 批量预览与最终提交之间比对，若目标结构在预览后变化则阻断该表并要求重新预览；
- 执行快照中可通过 `schemaValidation` 记录本次契约报告。

## target_options

目标端专属属性不再固定成 `etl_table` 的列，而是按目标端族存放在 `target_options` jsonb 中（`backend/src/main/java/com/wgzhao/addax/admin/common/TargetOptions.java`）：

```json
{
  "hdfs": {
    "partitionBindings": [
      { "name": "logdate", "valueType": "BIZ_DATE", "format": "yyyyMMdd" },
      { "name": "region", "valueType": "FIXED", "value": "cn" }
    ],
    "partitionCreationMode": "PLATFORM",
    "storageFormat": "orc",
    "compressFormat": "snappy"
  },
  "rdbms": {
    "businessDateColumn": "logdate",
    "createPrimaryKey": true
  }
}
```

访问器是字面量的：缺失的 section/key 返回 `null`，由适配器决定自己的默认值，避免“目标端无关的默认值”污染其它目标族。

- `hdfs.partitionBindings`：目标分区键与运行期取值的一对一绑定，最多一个 `BIZ_DATE`；`partitionCreationMode` 为 `PLATFORM`（运行期创建）或 `EXTERNAL`（运行前校验）。
- `hdfs.storageFormat` / `compressFormat`：为空时回退到全局字典 `HDFS_STORAGE_FORMAT` / `HDFS_COMPRESS_FORMAT`。
- `rdbms.businessDateColumn`：平台提供的普通快照列（`varchar(8)` / `nvarchar(8)`），不是分区键，也不参与主键。
- `rdbms.createPrimaryKey`：缺省为 true；关闭后建表不生成主键，重复执行去重交给 writeMode/preSql。

## EXTERNAL 创建与提交

`ExternalCollectionService.create` 在保存 EXTERNAL 定义前做完整校验（`backend/src/main/java/com/wgzhao/addax/admin/service/ExternalCollectionService.java#L30-L100`）：

1. 重新读取目标元数据；目标表不存在 → 409；
2. 预览存在 ERROR → 409；
3. 请求携带的指纹与当前目标指纹不一致 → 409（预览后目标结构变了）；
4. 每个源字段必须映射，且不能重复映射同一个源或目标；
5. 目标非空、无默认值、非自增的字段必须被映射，或由平台业务日期列提供；
6. 保存定义、写入源字段、替换映射，然后执行一次资源刷新；刷新失败时定义已创建但处于待处理状态。

批量提交 `CollectionPlanCommitService` 逐表隔离失败：一张表失败不会回滚其他表；同一批次解析到相同目标表会被拒绝（`backend/src/main/java/com/wgzhao/addax/admin/service/CollectionPlanCommitService.java#L30-L100`）。

## 执行期契约

EXTERNAL 定义在执行前会再次调用 `TargetContractService.check`（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L1091-L1101`）：

- 通过：继续写入快照并启动 Addax；
- 阻断：本次执行记为失败，抛出 `TargetContractViolationException`，采集表状态置为 `U`，队列按失败/重试语义处理。

因此“预览通过”不是永久通行证：目标结构在运行前被外部修改，执行会被阻断而不是写入错误数据。

## 与批量创建的关系

- `MANAGED` 预览展示平台将创建/演进的目标结构、计划 DDL、字段映射和分区计划；
- `EXTERNAL` 预览展示目标表真实结构、同名字段建议映射、未匹配源/目标字段、业务日期列检查和阻断项。

预览与最终提交的分离见 [采集定义创建流程](../workflows/collection-creation.md)。

## 相关页面

- [元数据刷新与表结构演化](schema-evolution-and-metadata.md)
- [采集定义创建流程](../workflows/collection-creation.md)
- [Addax 任务契约与目标适配器](../integrations/addax-job-contract.md)
- [采集定义与生命周期](collection-definition-lifecycle.md)
