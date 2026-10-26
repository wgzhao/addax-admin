---
type: architecture
title: 采集执行链路
description: 从 Worker 接收队列任务到 Addax 进程退出、快照与统计落库、失败重试和用户取消的完整执行时序与失败语义。
tags: [execution, queue, addax, snapshot, retry, cancellation]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-b93ec68ea14da4d409f9d27a
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/SecurityConfiguration.java
  - id: openwiki-source-9cc70e02acfa740508044688
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/LogController.java
  - id: openwiki-source-e4dfbcff7a63eabc250d04aa
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/TaskController.java
  - id: openwiki-source-cac423a04b2adc57c463096b
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/dto/AddaxReportDto.java
  - id: openwiki-source-a6aba7cf003bf9fc4074e119
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/repository/EtlTableRepo.java
  - id: openwiki-source-55a3c4182262f33d158776d1
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/AddaxLogService.java
  - id: openwiki-source-cbe0612165577672a134d133
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/AlertService.java
  - id: openwiki-source-058bdcb2c3aa67bf05fcaf8c
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlExecutionService.java
  - id: openwiki-source-a850b14847118325bacc355d
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlJobQueueService.java
  - id: openwiki-source-81f3367a093b712e36616d28
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlJourService.java
  - id: openwiki-source-f07858a5fa52c6d7d897c621
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/ExecutionManager.java
  - id: openwiki-source-666cda7a33d670fcf5f1e9f2
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/RdbmsTargetAdapter.java
  - id: openwiki-source-9a1773cc4388f1722dacb00d
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImpl.java
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-8e53bcc120d6cbf603fa935b
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java
  - id: openwiki-source-b90e22511fd9c7caa5e4d3cb
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/UserNotificationService.java
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 采集执行链路

本页描述一个采集任务从队列被分派到执行结束的完整路径。任务的产生与分派见 [调度与分布式队列](scheduling-and-queue.md)；Addax job JSON 的构造见 [Addax 任务契约与目标适配器](../integrations/addax-job-contract.md)；执行历史的查询与告警见 [可观测性、审计与告警](../operations/observability-and-alerts.md)。

## 入口：Worker 收到任务分派

Master 通过 Redis pub/sub 频道 `addax:task:assign:{instanceId}` 把已认领的 `EtlJobQueue` 行推送给指定 Worker。Worker 的 `TaskQueueManagerV2Impl` 实现 `MessageListener`，反序列化消息后提交到本地 `etl-worker` 线程池执行（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L316-L331`）。分派本身不占用 Worker 的并发额度，额度在真正开始执行时记账。

Worker 在执行前做两次防重复保护（`TaskQueueManagerV2Impl.java#L869-L884`）：

- 用 `renewLease` 确认该队列行仍由本实例持有；若在分派与接收之间已被回收、改派，则直接跳过，避免重复执行。
- 检查 Redis 中是否存在该表的 kill signal；存在则把队列行标记为 `cancelled`，不再启动 Addax。

只有通过这两步后，才递增本地全局/源级运行计数，并进入执行逻辑。无论后续成功、失败还是抛异常，`finally` 都会释放计数、注销进程并触发下一次分派（`TaskQueueManagerV2Impl.java#L951-L977`）。

## 执行准备：快照、契约与目标准备

### 不可变执行快照

每次尝试都会写入一条 `etl_execution` 记录，`EtlExecutionService.start` 负责构造不可变快照（`backend/src/main/java/com/wgzhao/addax/admin/service/EtlExecutionService.java#L42-L74`）：

- `snapshotVersion = 2`，`bizDate`、`attemptNo`、`jobQueueId`、源/目标编码与名称。
- `configSnapshot` 包含当时采集定义的关键字段、`targetOptions`、解析后的字段映射（源名、目标名、序号、类型、注释、是否仍活跃），以及 job digest 和可选的目标契约报告（`EtlExecutionService.java#L94-L155`）。
- 快照写入后不再随采集定义修改而变化，历史执行据此解释当时的配置，而不是回读当前 `etl_table`。设计背景见 [ADR 0001](../../docs/adr/0001-retire-collection-definitions-with-execution-snapshots.md)。

快照在目标契约检查之后、Addax 启动之前创建，因此契约阻断也会留下一条失败的执行事实。

### EXTERNAL 目标契约门禁

当采集定义的 `schemaManagementMode` 为 `EXTERNAL` 时，执行逻辑会在启动 Addax 前调用 `TargetContractService.check` 读取目标端实时元数据（`TaskQueueManagerV2Impl.java#L1091-L1101`）。若报告为 blocked，则：

1. 把执行记录标记为失败并写入契约摘要；
2. 抛出 `TargetContractViolationException`；
3. 上层把采集表状态置为 `U`（待更新结构），而不是普通失败 `E`（`TaskQueueManagerV2Impl.java#L1031-L1039`）。

契约检查不创建也不修改目标表；规则与字段映射语义见 [目标契约与字段映射](../domain/target-contract-and-mapping.md)。

### 目标准备

随后调用 `targetService.prepareBeforeRun`：

- Hive/HDFS 目标按 `target_options.hdfs.partitionBindings` 解析本次业务日期的分区值；`PLATFORM` 模式创建缺失分区，`EXTERNAL` 模式只校验分区已存在（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImpl.java#L382-L397`）。
- 关系型数据库目标没有运行前 DDL 或分区动作，直接返回 `true`（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/RdbmsTargetAdapter.java#L316-L320`）。

准备失败时执行记录以 `Target preparation failed` 结束，任务按普通失败处理。

## 调用 Addax

任务 JSON 从 `etl_job` 表读取；补数日期与系统业务日期不同时会按目标日期在内存中重新生成模板（`TaskQueueManagerV2Impl.java#L1064-L1083`，生成逻辑见 [补数与业务日历](../workflows/fillback-and-calendar.md)）。

执行器把 JSON 写到 `job/<yyyy-MM-dd>/<targetDb>.<targetTable>_<bizDate>.json`，文件名带业务日期，避免同表并发补数互相覆盖；然后拼接命令（`TaskQueueManagerV2Impl.java#L1119-L1141`）：

```text
$ADDAX_HOME/bin/addax.sh -p "-DjobName=<tid>:<executionId> -Dlog.file.name=<logName>" <jobFile>
```

`jobName` 同时携带采集表 ID 与执行 ID，Addax 的统计回报靠它把指标关联回本次尝试。执行超时取采集定义的 `maxRuntime`，未配置时使用全局默认值。进程通过 `CommandExecutor` 启动，并登记到 `ExecutionManager`，供取消操作定位进程树。

## 日志、流水与统计

### 原始日志

Addax 退出后，执行器读取 `$ADDAX_HOME/log/<logName>` 的完整内容，写入 `addax_log`（`TaskQueueManagerV2Impl.java#L1177-L1189`）。`AddaxLogService` 在持久化边界剥离 PostgreSQL 不允许的 NUL 字节（`backend/src/main/java/com/wgzhao/addax/admin/service/AddaxLogService.java#L31-L45`）。

原始日志生命周期短于执行事实：默认保留 180 天，可用 `ADDAX_LOG_RETENTION_DAYS` 覆盖，每天 03:15 清理过期日志（`AddaxLogService.java#L75-L83`）。执行快照、统计与流水不随日志清理。

### 流水（jour）

每次 Addax 调用前后都会写 `etl_jour`：类型为 `COLLECT`，记录命令、开始时间、耗时、成功/失败与错误信息，并关联 `executionId`（`backend/src/main/java/com/wgzhao/addax/admin/service/EtlJourService.java#L26-L87`；调用点 `TaskQueueManagerV2Impl.java#L1152-L1196`）。非执行类操作（如模板更新、补数提交）也使用同一张流水表，类型为 `ADDAX_JOB` 或 `FILLBACK`。

### 统计回报

Addax 通过 `POST /api/v1/log/job-report` 回报指标，该端点无需 JWT（`backend/src/main/java/com/wgzhao/addax/admin/controller/LogController.java#L110-L140`）。`jobName` 格式为 `tid:executionId`，端点解析后写入 `etl_statistic`，包括起止时间、耗时、字节/记录速度、读取记录数和错误记录数。

业务日期不由回报方提供，而是由采集表所属源的日历推导（`LogController.java#L136-L139`）。这样工作日/交易日源在节假日后的数据仍会归入正确的业务周期，缺失采集报表也依赖这一列。

## 终态处理与失败语义

Addax 退出码与异常共同决定队列行和采集表状态：

| 场景 | 队列行 | 采集表 | 告警/通知 |
| --- | --- | --- | --- |
| Addax 退出码为 0 | `completed` | `Y`，`retryCnt` 重置为 3 | 若此前处于告警状态，发送恢复通知 |
| 非 0 退出或异常，且仍有重试额度 | `pending`，按退避时间重新可见 | `E`，`retryCnt` 减一 | 不告警 |
| 非 0 退出或异常，且重试耗尽 | `failed` | `E`，`retryCnt` 减一 | 发送企业微信告警 |
| 用户取消 | `cancelled` | 由执行路径写入失败状态 | 不按普通失败告警 |
| EXTERNAL 契约阻断 | 按失败/重试处理 | `U` | 契约摘要进入执行记录 |

退避从 30 秒开始、每次乘 2、上限 1800 秒（`TaskQueueManagerV2Impl.java#L980-L987`）；队列行只有在仍由本实例持有时才会被更新，避免失去所有权的执行者覆盖后续执行者的状态（`backend/src/main/java/com/wgzhao/addax/admin/service/EtlJobQueueService.java#L169-L204`）。

采集表状态更新使用带条件的 SQL，而不是整行保存：`setFinished`/`setFailed` 通过 `EtlTableRepo.markFinished` / `markFailed` 防止过期执行者把更新的 `Y` 覆盖回 `E`，也避免 `merge` 把已删除的行重新插入（`backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java#L400-L438`）。状态含义见 [采集定义与生命周期](../domain/collection-definition-lifecycle.md)。

### 手动同步执行

`POST /api/v1/tasks/{taskId}/executions?isSync=true` 不走队列，直接在请求线程调用 `executeEtlTaskWithConcurrencyControl`，并按“一次尝试即最终尝试”接入告警状态机（`backend/src/main/java/com/wgzhao/addax/admin/controller/TaskController.java#L181-L212`）。异步提交则写入队列，由 Master 分派。两条路径都受 EXTERNAL 契约预检约束（`backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java#L276-L330`）。

## 取消与跨节点终止

取消入口是 `POST /api/v1/tasks/{tid}/kill`（`TaskController.java#L319-L340`）。`ExecutionManager` 维护本节点正在运行的进程树，并提供跨节点协调（`backend/src/main/java/com/wgzhao/addax/admin/service/ExecutionManager.java`）：

- 本节点有该 tid 的进程时，按“先子后父”的顺序发送优雅终止，再强制终止，并记录 `killRequested` 时间；
- 本节点没有时，通过 Redis 频道 `etl:kill` 广播，由持有进程的节点执行终止；
- Worker 在任务结束后用 `consumeKillRequested(tid, claimedAt)` 判断本次失败是否由用户取消造成，避免把上一轮执行遗留的 kill 标记误判为本次取消（`TaskQueueManagerV2Impl.java#L917-L949`）。

启动前检查 kill signal、运行中消费 kill 标记、队列行写入 `cancelled` 三者共同保证“用户点了取消就不应再启动或重试”。

## 用户通知

当手动提交的任务在队列 payload 中带有 `submitter` 时，执行结束会为该用户创建一条 `user_notification`，内容包含目标表、成功/失败和耗时（`TaskQueueManagerV2Impl.java#L1389-L1410`）。通知中心的前端消费方式见 [采集表管理界面](../frontend/table-management.md)。

## 相关页面

- [调度与分布式队列](scheduling-and-queue.md)
- [Addax 任务契约与目标适配器](../integrations/addax-job-contract.md)
- [目标契约与字段映射](../domain/target-contract-and-mapping.md)
- [采集定义与生命周期](../domain/collection-definition-lifecycle.md)
- [可观测性、审计与告警](../operations/observability-and-alerts.md)
- [数据模型与持久化](../backend/persistence-model.md)
