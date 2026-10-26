---
type: workflow
title: 采集定义创建流程
description: 从前端五步批量向导到 schema-plans 预览、逐表复核、MANAGED/EXTERNAL 提交重校验、失败隔离与重试的完整创建流程。
tags: [workflow, collection-creation, batch-wizard, managed, external, preview, commit]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-378e112858def65c9f326dda
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/TableController.java
  - id: openwiki-source-22b7ce71857d8eaa846c7b73
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/CollectionPlanCommitService.java
  - id: openwiki-source-bda9ff5b7330c86cf3ae0774
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/CollectionPlanPreviewService.java
  - id: openwiki-source-23c15be856854fed465d8e31
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/CollectionSchemaPreviewService.java
  - id: openwiki-source-636d456d0413329b8f962e64
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/ExternalCollectionService.java
  - id: openwiki-source-8e53bcc120d6cbf603fa935b
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java
  - id: openwiki-source-4ba650d840d04aed588da736
    resource: repo://frontend/src/components/table/batch/BatchConfigStep.vue
  - id: openwiki-source-3f7296a0fc45f11363bd2002
    resource: repo://frontend/src/components/table/batch/BatchReviewStep.vue
  - id: openwiki-source-ba5df7ebd2bffe375ebbae64
    resource: repo://frontend/src/components/table/BatchAdd.vue
  - id: openwiki-source-b056a859f2d9a3485233b5bb
    resource: repo://frontend/src/utils/collection-plan.ts
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 采集定义创建流程

创建采集定义不是一次表单提交，而是一个**先预览、再确认、最后逐表提交并重新校验**的流程。这样设计的原因是：源字段、目标表结构和目标端是否存在都可能在用户确认期间变化；只有把“计划”和“落库”分开，才能让阻断项在写入前暴露，而不是留下半创建的采集表。

前端入口是 `/table/batch-add`，后端核心是三个接口：

| 接口 | 作用 |
| --- | --- |
| `POST /tables/schema-preview` | 单表 EXTERNAL 预览（底层能力，批量向导不直接使用） |
| `POST /tables/schema-plans/preview` | 批量计划预览，按表返回 MANAGED/EXTERNAL 计划 |
| `POST /tables/schema-plans/commit` | 确认后逐表创建，返回每张表的结果 |

（`backend/src/main/java/com/wgzhao/addax/admin/controller/TableController.java#L327-L373`）

## 五步向导

`BatchAdd.vue` 使用 stepper 组织五步：

1. 选择采集源；
2. 选择采集表；
3. 配置目标策略；
4. 预览与校验；
5. 创建结果。

（`frontend/src/components/table/BatchAdd.vue#L28-L100`）

顶部常驻上下文条显示当前源、源库、已选表数和目标库，避免在长表单里丢失选择状态。

### 第 1、2 步：源与表

- 源系统只加载启用中的采集源（`sourceService.listActiveSources()`）；
- 选择源后自动加载该源的数据库列表，默认目标库为 `ods{源 code 小写}`；
- 第 2 步只加载当前源库中**尚未纳入采集**的表；切换源或库会清空已选表，避免隐藏的旧选择继续计数。

（`frontend/src/components/table/BatchAdd.vue#L350-L455`）

### 第 3 步：目标策略

目标结构管理只有两个选项：

- `MANAGED`：平台创建并维护目标表；
- `EXTERNAL`：目标表已存在，只做校验。

目标表名模板支持 `${table}`（源表名）与 `${db}`（源库名），默认 `${table}`。生成预览前必须满足：

- 目标端、目标库已选择；
- 每张表都能解析出非空目标表名；
- 解析后的目标表名不重复（大小写不敏感）。

（`frontend/src/components/table/BatchAdd.vue#L314-L338`）

HDFS 目标额外配置分区绑定（最多一个 `BIZ_DATE`）、分区创建方式和存储/压缩格式；RDBMS 目标配置快照业务日期列和是否按源主键建主键。分区类型选择会禁用第二个业务日期分区，因为目标契约不允许（`frontend/src/components/table/batch/BatchConfigStep.vue#L29-L180`）。

## 预览阶段

### 请求与逐表隔离

`POST /tables/schema-plans/preview` 接收一个数组，每项包含源、目标、结构模式和 `target_options`。`CollectionPlanPreviewService.preview` 对每项单独调用 `previewOne`：

- 单张表抛异常不会让整个批次失败；
- 该表返回 `status=ERROR`、`action=BLOCKED`、`finding=PLAN_PREVIEW_FAILED`，其余表继续预览。

（`backend/src/main/java/com/wgzhao/addax/admin/service/CollectionPlanPreviewService.java#L47-L79`）

### MANAGED 预览

`managedPreview` 读取**源端实时字段**，并通过目标适配器 `inspectTable` 读取目标现状，然后生成：

- `plannedColumns`：源字段按目标方言映射后的目标列；RDBMS 下按源主键标记 `primaryKey`，配置了业务日期列则追加一列 `varchar(8)`、`role=BUSINESS_DATE`；
- `plannedPartitions`：HDFS 的分区绑定；
- `mappings`：默认源字段到同名列的身份映射；
- `planDdl`：只读的计划 DDL（Hive `CREATE EXTERNAL TABLE`，RDBMS `CREATE TABLE IF NOT EXISTS`，MySQL 额外带 `CREATE DATABASE`）；
- `action`：目标不存在为 `CREATE_TABLE`，存在为 `UPDATE_TABLE`；
- 目标已存在时追加 `MANAGED_TARGET_EXISTS` WARNING，因此状态是 `WARNING` 而不是 `READY`。

（`CollectionPlanPreviewService.java#L101-L127`、`#L222-L258`）

MANAGED 预览不会执行任何 DDL，只描述“确认后会发生什么”。

### EXTERNAL 预览

`externalPreview` 复用 `CollectionSchemaPreviewService.preview`：

1. 读取源字段；
2. 通过目标适配器读取目标表实时元数据；
3. 目标表不存在 → `TARGET_TABLE_MISSING` ERROR，`action=BLOCKED`；
4. 源字段与目标同名字段自动建议映射，未匹配的源字段产生 `TARGET_COLUMN_MISSING` ERROR；
5. 目标未映射字段：非空、无默认值、非自增 → `TARGET_REQUIRED_COLUMN_UNMAPPED` ERROR；否则 `TARGET_EXTRA_COLUMN` WARNING；
6. 配置了 RDBMS 业务日期列时，检查目标列存在且属于字符串族；
7. 生成目标结构指纹。

（`backend/src/main/java/com/wgzhao/addax/admin/service/CollectionSchemaPreviewService.java#L52-L180`）

EXTERNAL 的 `plannedColumns` 直接来自目标表真实列，`planDdl` 为空——平台不会为外部目标生成 DDL。

### 状态与动作

`CollectionPlanPreview.status` 由 findings 决定：

- 有 ERROR → `ERROR`；
- 无 finding → `READY`；
- 只有 WARNING → `WARNING`。

`action` 取 `CREATE_TABLE`、`UPDATE_TABLE`、`VALIDATE_EXTERNAL` 或 `BLOCKED`。每次预览返回 `targetSchemaFingerprint`（EXTERNAL）或计划 DDL（MANAGED），供复核和提交使用。

## 复核阶段

第 4 步显示每张表的计划状态、字段映射进度、目标动作和摘要，可按 `READY`/`WARNING`/`ERROR` 过滤，并提供：

- **补全缺失映射**：按同名规则补齐空目标字段；
- **排除无效项**：把有阻断项的勾选行移出本次创建；
- 单表抽屉：编辑目标字段、查看计划 DDL。

（`frontend/src/components/table/batch/BatchReviewStep.vue#L20-L100`、`#L140-L240`）

提交按钮的可用性由前端校验决定。`reviewRowIssue` 定义三种阻断：

- `EMPTY_TARGET`：存在空目标字段；
- `DUPLICATE_TARGET`：多个源字段映射到同一目标字段；
- `MISSING_TARGET`：EXTERNAL 模式下目标字段不在真实目标列中。

只要任一勾选行存在这些问题，确认按钮就不可用（`frontend/src/utils/collection-plan.ts#L1-L25`）。这只影响前端提交，后端仍会独立校验。

## 提交阶段

`POST /tables/schema-plans/commit` 接收勾选行的 `table`、`mappings` 和 `targetSchemaFingerprint`。`CollectionPlanCommitService.commit` 先做批次级检查：

- 同一批次内解析到相同目标表（大小写不敏感）→ 该行直接 FAILED，不落库；
- 其余行逐表执行，单表异常被捕获为该表的 FAILED，不影响其他表。

（`backend/src/main/java/com/wgzhao/addax/admin/service/CollectionPlanCommitService.java#L36-L70`）

### MANAGED 提交

`commitManaged` 的顺序：

1. 强制 `schemaManagementMode=MANAGED`，状态置 `U`（待更新结构），`duration` 补 0；
2. 保存 `etl_table`；
3. `createTableColumns` 读取源字段并写入 `etl_column`；
4. 校验映射：每个源字段必须存在、每源只映射一次、每目标只被映射一次、且源字段必须**全部覆盖**；
5. `replace` 写入字段映射；
6. `refreshTableResources`：再次读取源字段、生成/演进目标表、重新生成 job；
7. 刷新成功 → CREATED；刷新失败 → FAILED，但**采集定义和已写入的列/映射仍然存在**。

（`CollectionPlanCommitService.java#L79-L117`、`#L119-L153`）

因此 MANAGED 的“失败隔离”是结果级别的：失败表可能在数据库里留下一个状态为 `U` 的定义，需要人工重新刷新或处理后再用。返回结果会带上 `tableId`，便于定位。

### EXTERNAL 提交

`commitExternal` 调用 `ExternalCollectionService.create`，后者重新执行一次完整预检：

1. 目标表不存在 → 409；
2. 预览存在 ERROR → 409，消息包含前 3 条阻断项；
3. 请求携带的指纹与当前目标指纹不一致 → 409（预览后目标结构变化）；
4. 每个源字段必须映射，目标字段必须存在，源/目标都不能重复；
5. 目标非空、无默认值、非自增字段必须被映射，或由平台业务日期列提供；
6. 保存定义、写源字段、替换映射、保存指纹；
7. 执行一次资源刷新；刷新失败返回 409“定义已创建但契约校验失败”。

（`backend/src/main/java/com/wgzhao/addax/admin/service/ExternalCollectionService.java#L37-L105`）

注意第 7 步：EXTERNAL 定义在刷新前已经保存，所以刷新失败时结果虽然是 FAILED，定义仍可能以 `U` 状态存在。`CollectionPlanCommitService` 的外层 catch 会把该结果记为 `tableId=null` 的 FAILED，这一点与 MANAGED 不同，排查时需要按源表名回查。

### 审计

控制器对每个提交结果写审计：

- `COLLECTION_PLAN_CREATED`（WARNING）记录成功的表；
- `COLLECTION_PLAN_CREATE_FAILED`（WARNING）记录失败原因和源/目标表。

（`TableController.java#L341-L363`）

## 创建后的状态

MANAGED 与 EXTERNAL 最终都调用 `refreshTableResources`。成功后并不是无条件把状态改成 `N`：

- 如果当前业务日按采集源日历“应采”，状态置 `N`；
- 如果当前不是业务日，保留原状态（通常是 `U`），直到下一个业务日切日重置。

（`backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java#L459-L469`）

因此休息日创建的表可能暂时显示“待更新”，这是预期行为，不代表创建失败。

## 失败与重试

结果页把行按 `CREATED` / `FAILED` 展示：

- 成功行保留在结果中；
- “重试失败项”只把 FAILED 的源表重新勾选并回到第 4 步，成功行不会被再次提交，避免重复创建；
- 结果按源表名合并，重试后仍能看到此前的成功项。

（`frontend/src/components/table/BatchAdd.vue#L529-L575`、`frontend/src/components/table/batch/BatchDoneStep.vue#L1-L60`）

## 直接创建 EXTERNAL 的 API

`POST /tables/external` 仍保留，接收单表的 table、mappings 和 fingerprint，直接调用 `ExternalCollectionService.create` 并返回 201（`TableController.java#L365-L373`）。

但批量向导**不调用**这个接口：它在第 5 步统一调用 `schema-plans/commit`，由后端按 `schemaManagementMode` 分派到 MANAGED 或 EXTERNAL。两者共享同一个 `ExternalCollectionService`，行为一致。

## 失败语义速查

| 场景 | 结果 |
| --- | --- |
| 预览时单表目标不可达/适配器异常 | 该表 ERROR/BLOCKED，其他表继续 |
| 目标表名重复（前端） | 不发起预览 |
| 同批次目标表重复（后端） | 该行 FAILED，其他行继续 |
| MANAGED 源字段读取失败 | 该表 FAILED，定义/列可能已留下 |
| MANAGED 目标 DDL 失败 | 该表 FAILED，状态 `U` |
| EXTERNAL 目标不存在 | 预览 ERROR；提交 409 |
| EXTERNAL 预览后目标结构变化 | 提交 409，要求重新预览 |
| EXTERNAL 必填目标字段未映射 | 提交 409 |
| EXTERNAL 刷新契约失败 | 409，定义已创建但状态待处理 |
| 映射有空目标/重复目标/目标字段不存在 | 前端阻止提交；后端再次校验 |

## 相关页面

- [采集定义与生命周期](../domain/collection-definition-lifecycle.md)
- [目标契约与字段映射](../domain/target-contract-and-mapping.md)
- [元数据刷新与表结构演化](../domain/schema-evolution-and-metadata.md)
- [采集表管理界面](../frontend/table-management.md)
- [测试策略与 E2E](../testing/testing-strategy.md)
