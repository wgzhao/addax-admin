---
type: frontend
title: 采集表管理界面
description: 采集表列表与详情页各 Tab、字段映射编辑、目标契约检查、批量新增向导、补采对话框及其与后端 API 的对应关系。
tags: [frontend, table-management, column-mapping, batch-wizard, fillback, target-contract]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-0fec0bd6dd38c4984661c80d
    resource: repo://frontend/src/components/table/AddaxJob.vue
  - id: openwiki-source-3f7296a0fc45f11363bd2002
    resource: repo://frontend/src/components/table/batch/BatchReviewStep.vue
  - id: openwiki-source-9b048aa571ecf89ef58d28b5
    resource: repo://frontend/src/components/table/batch/BatchTableStep.vue
  - id: openwiki-source-ba5df7ebd2bffe375ebbae64
    resource: repo://frontend/src/components/table/BatchAdd.vue
  - id: openwiki-source-00c84f519966b1628c95ca0a
    resource: repo://frontend/src/components/table/ExecutionHistory.vue
  - id: openwiki-source-16fd4a82adfaf1d8d9c4cf71
    resource: repo://frontend/src/components/table/FieldsCompare.vue
  - id: openwiki-source-027880894ad026680c8e7690
    resource: repo://frontend/src/components/table/FillbackDialog.vue
  - id: openwiki-source-6ba86788a18f53cdec159754
    resource: repo://frontend/src/components/table/TableDetail.vue
  - id: openwiki-source-d70a29676ece6708fba2de92
    resource: repo://frontend/src/service/table-service.ts
  - id: openwiki-source-a95b0f6836c813fac092c8b4
    resource: repo://frontend/src/views/table/detail.vue
  - id: openwiki-source-cf93ab3be0236723515ecb9e
    resource: repo://frontend/src/views/table/index.vue
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 采集表管理界面

采集表管理是控制台的核心工作面，由两部分组成：

- `/table` 列表页：筛选、批量操作、单表动作与 CSV 导出；
- `/table/detail/:tid` 详情页：配置查看/编辑，以及日志、作业 JSON、字段映射、统计、执行快照和变更历史七个 Tab。

批量新增使用独立路由 `/table/batch-add`，补采和批量修改则以列表页对话框形式出现。所有请求都经由 `frontend/src/service/table-service.ts`，领域语义分别见 [采集定义与生命周期](../domain/collection-definition-lifecycle.md) 和 [目标契约与字段映射](../domain/target-contract-and-mapping.md)。

## 列表页

### 筛选与 URL 状态

列表页支持四类条件：

| 条件 | 说明 |
| --- | --- |
| 关键字 | 表名、编码、分区字段 |
| 状态多选 | `N/W/R/Y/E/U/X`，触发器最多展示 3 个已选状态 |
| 采集源 | 下拉选择，停用源带“（已停用）”后缀 |
| 管理范围 | 当前定义 / 暂停中 / 已退役 / 全部 |

管理范围直接对应后端的 `CURRENT`、`DORMANT`、`RETIRED`、`ALL`（`frontend/src/views/table/index.vue#L565-L575`）。筛选条件、页码、页大小都会写入 URL query（`q`、`status`、`source`、`scope`、`page`、`size`），页面初始化时再从 URL 恢复（`frontend/src/views/table/index.vue#L663-L697`）。因此从详情页返回或直接分享链接都能落到同一批数据。

列表使用 `v-data-table-server` 服务端分页，`@update:options="loadItems"` 触发 `GET /tables`，把 `statuses`、`sourceId`、`scope`、排序字段一起提交（`frontend/src/views/table/index.vue#L829-L853`）。

### 批量操作

只有选中行且当前范围不是“已退役”时才显示批量操作栏（`frontend/src/views/table/index.vue#L147-L180`）：

- **批量采集**：调用批量执行接口，任务进入任务中心；
- **补采数据**：打开 `FillbackDialog`；
- **批量修改**：打开 `BatchUpdate`；
- **批量删除**：逐表调用退役接口。

批量退役是前端 `Promise.all` 逐表调用 `DELETE /tables/{id}`，不是单个原子事务；任一张失败会提示错误并刷新列表，已经成功的行保持退役（`frontend/src/views/table/index.vue#L977-L992`）。

### 行级状态与动作

状态列不仅显示单字符状态码，还按状态挂载额外操作（`frontend/src/views/table/index.vue#L280-L325`）：

- `R`（采集中）：显示中止图标，确认后调用中止接口；成功后本地把状态改为 `W`；
- `U`/`E`：显示错误图标，点击时按需拉取最近一次错误信息并弹出。

操作列的按钮随退役状态切换（`frontend/src/views/table/index.vue#L347-L416`）：

| 状态 | 可用动作 |
| --- | --- |
| 未退役 | 发起采集、更新表结构、退役、查看详情 |
| 已退役 | 恢复、查看详情 |

“更新表结构”支持单表、`need`（仅待更新）和 `all`（全部）三种模式（`frontend/src/views/table/index.vue#L914-L938`）。详情页在新标签页打开，并带上 `tab=info` 与源表名用于标题展示（`frontend/src/views/table/index.vue#L889-L911`）。

### 显示设置与导出

“显示设置”允许隐藏过滤规则、耗时、完成时间三列，选择写入 `localStorage` 的 `ds.table.hiddenColumns`（`frontend/src/views/table/index.vue#L644-L659`）。“导出本页”只导出当前页内存中的行，包含生命周期、采集源状态、退役时间等派生字段（`frontend/src/views/table/index.vue#L699-L718`）。

## 详情页

### 路由、生命周期提示与 Tab

详情页从 `:tid` 读取采集表编号，Tab 由 `?tab=` 控制并保持 URL 同步（`frontend/src/views/table/detail.vue#L102-L137`）。切换 Tab 前会调用基本信息面板暴露的 `canLeave()`，有未保存修改时要求确认。

页面顶部按优先级显示三种生命周期提示（`frontend/src/views/table/detail.vue#L15-L37`）：

1. 已退役：日常调度与编辑关闭，历史仍可查询；
2. 采集源停用：仍可查看和编辑，但不参与自动调度；
3. 状态 `X`：采集定义有效且可编辑，但停止调度。

七个 Tab 及组件：

| Tab | 组件 | 作用 |
| --- | --- | --- |
| 基本详情 | `TableDetail` | 配置查看与编辑 |
| 采集日志 | `LogFiles` | 按执行选择并查看原始日志 |
| 采集任务配置 | `AddaxJob` | 查看/编辑 Addax job JSON |
| 字段比较 | `FieldsCompare` | 字段映射编辑与目标契约检查 |
| 采集结果 | `AddaxResult` | 统计汇总、明细与 CSV 导出 |
| 执行快照 | `ExecutionHistory` | 不可变执行快照分页列表 |
| 变更历史 | `ChangeHistory` | 配置字段级变更记录 |

（`frontend/src/views/table/detail.vue#L47-L72`）

### 基本详情的查看与编辑

只读态按“执行配置”“抽取与存储”“高级配置”组织信息，插件 JSON 和备注折叠展示（`frontend/src/components/table/detail/DetailOverviewCard.vue#L1-L90`）。编辑态切换为五张卡片：基础字段、目标与调度、抽取与存储、插件配置、快照备注（`frontend/src/components/table/detail/TableDetailEditForm.vue#L1-L45`）。

编辑流程有几个关键约束（`frontend/src/components/table/TableDetail.vue#L100-L140`）：

- 进入编辑时对当前表做 JSON 深拷贝作为取消快照；
- 表对象、reader/writer 插件文本任一变化都通过签名比较判定 dirty；
- 已退役表禁止进入编辑；
- 离开路由或关闭浏览器时，dirty 状态会触发确认。

保存时先做表单校验和插件 JSON 解析，再提交 `PUT /tables/{id}`（`frontend/src/components/table/TableDetail.vue#L199-L266`）。`target_options` 不是整体覆盖，而是把当前目标端族的配置合并回已存储的 jsonb，避免切换目标端时丢失另一族的设置。若保存后的 `schemaManagementMode` 为 `EXTERNAL`，前端还会调用 `updateSingleTable` 立即刷新资源并重新校验契约。

### 字段映射与目标契约

“字段比较”把 `etl_column_mapping` 与目标表真实结构放在同一张表中：

- 每行显示顺序、源字段、目标字段、源类型、实际目标类型和映射状态；
- 目标字段是 combobox，可输入或从“检查目标表”返回的列中选择；
- 已删除的源字段显示“源字段已删除，写入 NULL”，可在编辑态移除此映射；
- 选中目标字段在契约报告中不存在时，行状态变为“目标字段缺失”。

（`frontend/src/components/table/FieldsCompare.vue#L78-L160`）

进入编辑后，只有映射签名变化才算 dirty。保存前要求每个源字段都有非空目标字段，然后按当前行序重新编号 `ordinalNo`，调用 `PUT /tables/{tid}/column-mappings`；成功后自动再执行一次目标契约检查并提示“字段映射已保存并重新生成采集任务”（`frontend/src/components/table/FieldsCompare.vue#L249-L270`）。

“检查目标表”调用 `POST /tables/{tid}/target-contract/check`，返回的 `READY`/`WARNING`/`ERROR` 映射为通过、告警、阻断三种标签，并把 finding 逐条展示（`frontend/src/components/table/FieldsCompare.vue#L206-L247`）。该检查只读取目标元数据，不执行 DDL；阻断项的语义见 [目标契约与字段映射](../domain/target-contract-and-mapping.md)。

### 作业配置、统计、快照与历史

**采集任务配置** 展示格式化并高亮的 Addax JSON，可复制；未退役时可编辑，保存前先做 `JSON.parse` 校验，再调用 `PUT` 更新 job。父组件尚未确定生命周期时（`retiredAt === undefined`）编辑按钮不出现，已退役则强制只读（`frontend/src/components/table/AddaxJob.vue#L115-L205`）。

**采集结果** 汇总最近业务日期、累计记录数、累计错误数与峰值行速，并提供明细表和 CSV 导出（`frontend/src/components/table/AddaxResult.vue#L1-L70`）。

**执行快照** 分页展示不可变的执行尝试：执行 ID/次数、业务日期、源/目标表快照、状态、开始时间和 job 摘要（`frontend/src/components/table/ExecutionHistory.vue#L1-L60`）。空态文案明确说明新执行会自动记录当时的源表、目标表、字段映射和配置摘要。

**变更历史** 按时间倒序展示 `etl_table_change_log`，可按字段名过滤；数据来自 `GET /tables/{tid}/changes`（`frontend/src/components/table/ChangeHistory.vue#L110-L126`）。

**采集日志** 左侧列出该表的日志文件，右侧显示选中日志全文，支持复制和下载（`frontend/src/components/table/LogFiles.vue#L6-L120`）。

## 批量新增向导

`/table/batch-add` 挂载 `BatchAdd`，使用五步 stepper（`frontend/src/components/table/BatchAdd.vue#L28-L100`）：

1. **选择采集源**：选源系统和源库；
2. **选择采集表**：只展示当前源库中尚未纳入采集的表；
3. **配置目标策略**：目标端、目标库、目标表名模板、`MANAGED`/`EXTERNAL`、分区与存储；
4. **预览与校验**：逐表确认结构、映射和阻断项；
5. **创建结果**：展示成功/失败并支持重试。

第 2 步的候选表由后端按“未采集”过滤，页面文案明确这一点（`frontend/src/components/table/batch/BatchTableStep.vue#L5-L8`）。

### 目标策略与表名唯一性

第 3 步默认 `MANAGED`，可切换为 `EXTERNAL`（`frontend/src/components/table/batch/BatchConfigStep.vue#L29-L45`）。目标表名可由模板按源表名生成；进入预览前必须满足：

- 目标端和目标库已选择；
- 每张选中表都能解析出目标表名；
- 解析后的目标表名不重复（大小写不敏感）。

不满足时前端直接给出“目标表名重复：…”而不发起预览（`frontend/src/components/table/BatchAdd.vue#L323-L338`）。

### 预览与提交

“生成预览并校验”把选中表连同目标端、目标库、目标表、结构模式和 `target_options` 提交到 `POST /tables/schema-plans/preview`，后端按表返回 `CollectionPlanPreview`；前端把非 `ERROR` 的表默认勾选，并进入第 4 步（`frontend/src/components/table/BatchAdd.vue#L480-L522`）。

第 4 步按 `READY`/`WARNING`/`ERROR` 统计和过滤，支持“补全缺失映射”“排除无效项”；点开单表抽屉可编辑目标字段、查看计划 DDL，映射调整即时生效（`frontend/src/components/table/batch/BatchReviewStep.vue#L20-L100`、`#L140-L240`）。

“确认创建”只提交勾选且映射完整的行，附带每行的映射与预览返回的目标结构指纹，调用 `POST /tables/schema-plans/commit`（`frontend/src/components/table/BatchAdd.vue#L533-L552`）。结果页按源表合并，保留此前成功的行；失败项可以只重试失败部分（`frontend/src/components/table/batch/BatchDoneStep.vue#L1-L60`）。预览与提交之间目标结构变化会被指纹校验拒绝，规则见 [目标契约与字段映射](../domain/target-contract-and-mapping.md)。

## 补采对话框

补采从列表批量操作栏打开，参数是选中的 tid 数组（`frontend/src/views/table/index.vue#L754-L770`）。对话框要求选择起止日期，实时计算“表数 × 天数”，总任务数超过 20 时禁止提交（`frontend/src/components/table/FillbackDialog.vue#L122-L165`）。

提交调用任务服务的 `fillback` 接口；结果按表展示入队日期或跳过原因。非分区表会被后端跳过，前端文案已明确提示（`frontend/src/components/table/FillbackDialog.vue#L10-L14`）。补数的业务日期语义见 [补数与业务日历](../workflows/fillback-and-calendar.md)。

## 前端 API 对应关系

| 界面能力 | Service 方法 | 后端端点 |
| --- | --- | --- |
| 列表/详情 | `fetchTableList` / `fetchTableDetail` | `GET /tables`、`GET /tables/{id}` |
| 编辑配置 | `save` | `PUT /tables/{id}` |
| 退役/恢复 | `delete` / `restore` | `DELETE /tables/{id}`、`POST /tables/{id}/restore` |
| 单表刷新 | `updateSingleTable` / `updateSchema` | `POST /tables/{id}/actions/refresh`、`POST /tables/actions/refresh` |
| 字段映射 | `fetchColumnMappings` / `saveColumnMappings` | `GET/PUT /tables/{id}/column-mappings` |
| 契约检查 | `checkTargetContract` | `POST /tables/{id}/target-contract/check` |
| 执行快照 | `fetchExecutions` | `GET /tables/{id}/executions` |
| 统计/变更 | `fetchAddaxResult` / `fetchChangeLogs` | `GET /tables/{id}/statistics`、`GET /tables/{id}/changes` |
| 作业 JSON | `fetchAddaxJob` / `updateAddaxJob` | `GET/PUT /tables/{id}/addax-job` |
| 批量预览/提交 | `previewCollectionPlans` / `commitCollectionPlans` | `POST /tables/schema-plans/preview`、`POST /tables/schema-plans/commit` |
| 外部创建 | `createExternal` | `POST /tables/external` |

（`frontend/src/service/table-service.ts#L21-L244`）

长耗时操作在 service 层显式放宽超时：映射保存 300 秒、契约检查 120 秒、批量预览 300 秒、批量提交 600 秒（`frontend/src/service/table-service.ts#L79-L146`）。

## 交互约束小结

- 退役是“只读开关”：列表不再显示采集/刷新/退役按钮，详情禁止编辑，作业 JSON 也只读；恢复后这些入口重新出现。
- 源停用与 `X` 只影响调度，不限制编辑。
- 详情页三处编辑入口（配置、映射、job）各自维护 dirty 状态与取消快照，互不共享。
- 列表批量动作是前端循环调用，逐表成败；批量新增则在后端逐表隔离失败。
- 目标契约检查在进入字段比较 Tab、保存映射后都会执行，但真正阻断执行的检查发生在运行前，见 [采集执行链路](../architecture/execution-pipeline.md)。

## 相关页面

- [采集定义与生命周期](../domain/collection-definition-lifecycle.md)
- [目标契约与字段映射](../domain/target-contract-and-mapping.md)
- [采集定义创建流程](../workflows/collection-creation.md)
- [补数与业务日历](../workflows/fillback-and-calendar.md)
- [前端架构](architecture.md)
