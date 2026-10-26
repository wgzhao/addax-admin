---
type: domain
title: 采集定义与生命周期
description: 采集定义、采集源、目标端与执行事实的关系，N/W/R/Y/E/U/X 状态语义，退役/恢复、停采、源停用与历史可见性规则。
tags: [domain, lifecycle, retirement, status, collection]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-24ab985eee32c492a910e363
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/common/TableStatus.java
  - id: openwiki-source-32370565f17c2a43f503ae76
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/SourceController.java
  - id: openwiki-source-378e112858def65c9f326dda
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/TableController.java
  - id: openwiki-source-b34590bcbc9a1937e03159da
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/model/EtlSource.java
  - id: openwiki-source-a6aba7cf003bf9fc4074e119
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/repository/EtlTableRepo.java
  - id: openwiki-source-058bdcb2c3aa67bf05fcaf8c
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlExecutionService.java
  - id: openwiki-source-650482944d35601234caaec8
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java
  - id: openwiki-source-8e53bcc120d6cbf603fa935b
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java
  - id: openwiki-source-cfe1f839549028a5fd269a65
    resource: repo://docs/adr/0001-retire-collection-definitions-with-execution-snapshots.md
  - id: openwiki-source-b1490845e15f61f98ca069b8
    resource: repo://docs/testing/collection-state-matrix.md
  - id: openwiki-source-a95b0f6836c813fac092c8b4
    resource: repo://frontend/src/views/table/detail.vue
  - id: openwiki-source-cf93ab3be0236723515ecb9e
    resource: repo://frontend/src/views/table/index.vue
  - id: openwiki-source-860068e68badcc20d95b862e
    resource: repo://frontend/tests/e2e/collection-state-matrix.spec.ts
  - id: openwiki-source-49f5868b9bc0b9372e54c787
    resource: repo://scripts/data.sql
  - id: openwiki-source-56ca36fad3cacc52c6c8f19a
    resource: repo://scripts/schema.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 采集定义与生命周期

本页定义采集定义的领域对象、状态和生命周期。术语以仓库根目录 `CONTEXT.md` 为准，行为契约以 `docs/testing/collection-state-matrix.md` 为准。

## 领域对象关系

```text
etl_source 1 ─── N etl_table 1 ─── N etl_column
                     │            └── N etl_column_mapping
                     │            ├── 1 etl_job
                     │            ├── N etl_execution ── N etl_statistic / etl_jour / addax_log
                     │            └── N etl_table_change_log
etl_target 1 ─── N etl_table
```

- **采集定义**：`etl_table` 一行，描述一张表当前如何从源端采集到目标端。它是可变配置，但不是可删除对象。
- **采集源**：`etl_source`，提供 JDBC 连接、源级调度时间、日历和集群级并发上限。
- **目标端**：`etl_target`，通过 `writer_template_key` 路由到 HDFS 或 RDBMS 适配器。
- **采集执行**：`etl_execution`，一次实际启动的采集尝试，拥有独立开始/结束时间和配置快照。
- **历史事实**：`etl_statistic`、`etl_jour`、`addax_log` 等执行产物，生命周期独立于采集定义。

## 状态语义

采集表状态是单字符 `status`，定义在 `backend/src/main/java/com/wgzhao/addax/admin/common/TableStatus.java`：

| 状态 | 名称 | 含义 | 是否参与自动调度 |
| --- | --- | --- | --- |
| `N` | 未采集 | 当前业务周期尚未采集 | 是 |
| `W` | 等待采集 | 等待进入采集 | 是（历史状态，当前代码没有主动写入路径） |
| `R` | 采集中 | 正在执行 | 否（已在运行） |
| `Y` | 采集完成 | 当前业务周期已成功 | 否 |
| `E` | 采集失败 | 执行失败且重试额度已用尽 | 是（`retryCnt` 仍大于 0 时可重试） |
| `U` | 等待更新表结构 | 源/目标结构需要人工或刷新处理 | 否 |
| `X` | 停采 | 表级不采集，但定义仍有效 | 否 |

调度查询的过滤条件统一为：未退役、源启用、`status NOT IN ('Y','X','U')`、`retryCnt > 0`（`backend/src/main/java/com/wgzhao/addax/admin/repository/EtlTableRepo.java#L94-L126`）。表级覆盖调度额外要求 `status = 'N'`（`EtlTableRepo.java#L128-L145`）。

`W` 在字典 1060 和状态矩阵中保留，但当前代码只有 `TableService.setWaiting` 定义、没有调用点，因此现存数据通常是历史遗留；前端仍把它视为“当前定义”并允许调度。

## 三个独立维度

系统用三个维度描述“是否还在采集”，它们互不等价：

1. **退役**：`etl_table.retired_at` 非空。定义停止参与展示和调度，但历史事实保留。
2. **停采**：`etl_table.status = 'X'`。定义仍有效、可编辑，但不参与自动调度。
3. **源停用**：`etl_source.enabled = false`。该源下所有未退役定义进入“暂停中”，仍可查看和编辑。

由此得到四个列表范围（`backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java#L294-L325`）：

| scope | 条件 | 含义 |
| --- | --- | --- |
| `CURRENT`（默认） | `retired_at IS NULL` | 所有未退役定义，包括 `X` 和源停用表 |
| `DORMANT` | 未退役 且（源停用 或 `status='X'`） | 暂停中的采集定义 |
| `RETIRED` | `retired_at IS NOT NULL` | 已退役 |
| `ALL` | 无条件 | 全部 |

旧参数 `lifecycle=ACTIVE` 兼容映射为 `CURRENT`（`backend/src/main/java/com/wgzhao/addax/admin/controller/TableController.java#L112-L150`）。

## 退役

删除采集定义的 API 实际执行退役：`DELETE /api/v1/tables/{tableId}` 调用 `retireTable`，返回“采集表已退役，历史统计和日志已保留”（`TableController.java#L186-L197`）。

退役规则（`TableService.java#L610-L628`）：

- 若存在 `pending` 或 `running` 的队列行，返回 400，要求先停止任务；
- 设置 `retired_at` 和 `retired_by`；
- 重复退役是幂等的，不报错也不改变时间；
- 不删除任何执行事实，`etl_execution.tid` 等外键为 `ON DELETE RESTRICT`，数据库层面也阻止物理删除有历史的定义。

退役后：

- 自动调度排除该定义；
- 配置修改、字段映射修改、任务模板修改和资源刷新都返回 409（`TableController.java#L228-L245`、`#L290-L310`、`#L462-L472`；`backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java#L394-L410`）；
- 详情、执行历史、统计、流水、日志仍可查询；
- 前端详情页显示“该采集表已退役，日常调度与编辑已关闭，历史统计、流水和日志仍可查询”（`frontend/src/views/table/detail.vue#L15-L18`）。

设计动机见 [ADR 0001](../../docs/adr/0001-retire-collection-definitions-with-execution-snapshots.md)。

## 恢复

`POST /api/v1/tables/{tableId}/restore` 只清除退役状态（`TableService.java#L630-L645`）：

- 当前未退役时返回 400；
- 存在活动队列行时返回 409；
- 清除 `retired_at` 和 `retired_by`，不改变 `status`，也不启用源。

因此恢复后的表可能立即属于“暂停中”：如果它原本是 `X`，或者其源仍停用。状态矩阵明确覆盖了这些组合（`docs/testing/collection-state-matrix.md`）。

## 停采与源停用

### 停采 `X`

`X` 是表级“不采集”标记：定义有效、可编辑、可见，但不会进入自动调度。它不影响历史，也不等同于退役。前端详情页会提示“该采集表已停采，采集定义仍然有效且可编辑，但不会参与自动调度”（`frontend/src/views/table/detail.vue#L28-L36`）。

### 源停用

`etl_source.enabled = false` 时：

- 源下未退役定义仍出现在 `CURRENT` 列表，同时也属于 `DORMANT`；
- 详情页提示“该采集源下的采集表仍可查看和编辑，但不会参与自动调度”（`frontend/src/views/table/detail.vue#L19-L27`）；
- 源级 cron 和表级覆盖调度都要求源启用，因此不会触发；
- 删除源时如果仍有采集表引用，返回 400（`backend/src/main/java/com/wgzhao/addax/admin/controller/SourceController.java#L175-L194`）。

## 状态转换

正常执行路径：

```text
N --手动/调度入队--> R --成功--> Y
                       └--失败--> E --重试入队--> R ...
                       └--契约阻断--> U
刷新/切日:
N/W/E/Y --resetAllFlags（仅业务日到期的日历）--> N
U --refreshTableResources 成功--> N
```

关键实现点：

- `setRunning` 写 `R` 和开始时间；
- `setFinished` 写 `Y`、重置 `retryCnt=3`、写结束时间与耗时，并通过条件 SQL 防止过期执行者覆盖；
- `setFailed` 写 `E`、基于行当前值递减 `retryCnt`；
- `setUpdateSchema` 写 `U`，用于 EXTERNAL 契约阻断或目标结构刷新失败；
- `resetAllFlags` 只重置今天确实有采集义务的日历对应表，休息日保留上一次采集状态（`TableService.java#L570-L590`）。

执行侧的完整时序与失败语义见 [采集执行链路](../architecture/execution-pipeline.md)。

## 历史可见性与不可删除性

采集执行、统计、流水和日志都通过 `tid` 关联采集定义，而不是复制一份可变的展示字段：

- `etl_execution` 保存执行时的源/目标名称、配置快照和字段映射，历史查询不依赖当前 `etl_table`；
- `etl_statistic`、`etl_jour`、`addax_log` 通过 `execution_id` 关联到具体尝试；
- `etl_table_change_log` 由数据库触发器记录配置差异，运行态字段（状态、耗时、重试次数等）不计入变更历史。

因此退役只会让定义从“当前调度集合”中消失，不会破坏历史归因。相关表结构见 [数据模型与持久化](../backend/persistence-model.md)。

## 行为契约

`docs/testing/collection-state-matrix.md` 是采集表管理 E2E 的公开行为契约，覆盖：

- 2 种源状态 × 7 种表状态 × 2 种生命周期；
- `CURRENT` / `DORMANT` / `RETIRED` / `ALL` 可见性；
- 详情、编辑、退役、恢复；
- 旧参数兼容、源/状态筛选、重复操作与活动任务阻断。

修改生命周期语义时，应同时更新该矩阵与对应 E2E（`frontend/tests/e2e/collection-state-matrix.spec.ts`），见 [测试策略与 E2E](../testing/testing-strategy.md)。

## 相关页面

- [采集定义创建流程](../workflows/collection-creation.md)
- [目标契约与字段映射](target-contract-and-mapping.md)
- [调度与分布式队列](../architecture/scheduling-and-queue.md)
- [数据模型与持久化](../backend/persistence-model.md)
