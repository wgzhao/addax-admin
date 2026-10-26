---
type: architecture
title: 调度与分布式队列
description: 采集触发如何进入 PostgreSQL 持久化队列，以及 Redis 仲裁下的 Master/Worker 选举、心跳、加权分派、租约与故障恢复机制。
tags: [scheduling, queue, redis, master-election, concurrency, lease]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-e14f726a9f0612c7c32a31bd
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/redis/MasterElectionService.java
  - id: openwiki-source-dd863ba2f45046991646abe2
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/redis/WorkerHeartbeatService.java
  - id: openwiki-source-a6aba7cf003bf9fc4074e119
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/repository/EtlTableRepo.java
  - id: openwiki-source-98c1e6e4abcca885022cf52e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/scheduler/CollectionScheduler.java
  - id: openwiki-source-2e2b9055fdca838ebaded91a
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/scheduler/SchemaRefreshScheduler.java
  - id: openwiki-source-429caa4a71ba43e99fb89128
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/scheduler/TableOverrideScheduler.java
  - id: openwiki-source-a850b14847118325bacc355d
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlJobQueueService.java
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-9ce32dae5edb9d3bb91ad747
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/SourceService.java
  - id: openwiki-source-f8fd6cb1661202deb55c86cd
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/SourceUpdateSubscriber.java
  - id: openwiki-source-8e53bcc120d6cbf603fa935b
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java
  - id: openwiki-source-f85a5f4701b168e36912f3a1
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java
  - id: openwiki-source-56ca36fad3cacc52c6c8f19a
    resource: repo://scripts/schema.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 调度与分布式队列

系统采用“数据库持久化队列 + Redis 实时仲裁”的混合架构：任务的可恢复状态保存在 PostgreSQL 的 `etl_job_queue`，节点角色与实时容量通过 Redis 选举和心跳协调。执行阶段的详细语义见 [采集执行链路](execution-pipeline.md)。

## 触发源

所有自动触发都只在 Master 节点注册。节点成为 Master 时通过 `onBecameMaster` 回调注册本地定时器，失去 Master 时取消，因此不会出现多节点重复触发。

### 源级 cron：CollectionScheduler

每个启用的采集源按 `etl_source.start_at` 注册一个每日 cron（`backend/src/main/java/com/wgzhao/addax/admin/scheduler/CollectionScheduler.java#L60-L106`）。cron 只表达“每天这个时刻”，真正的业务日门禁在触发时执行：

1. 重新读取采集源，取得它当前的 `calendar`；
2. 用 `CalendarService.businessPeriodDate()` 判断触发所属的业务周期（切日时间之前属于前一天）；
3. `calendarService.evaluate` 拒绝非业务日，并记录拒绝原因；
4. 通过后调用 `taskService.executeTasksForSource(sourceId)`。

`executeTasksForSource` 只选择 `start_at IS NULL` 的表，即继承源调度时间的表；表级覆盖调度的表由另一个调度器负责（`backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java#L510-L514`，查询 `backend/src/main/java/com/wgzhao/addax/admin/repository/EtlTableRepo.java#L110-L126`）。

### 表级覆盖调度：TableOverrideScheduler

`start_at` 非空的表不跟随源 cron，而由每分钟一次的 tick 扫描（`backend/src/main/java/com/wgzhao/addax/admin/scheduler/TableOverrideScheduler.java#L70-L167`）：

- 扫描 `[now - 2min, now]` 的补偿窗口，容忍调度抖动或短暂重启；
- 跨午夜时把窗口拆成两段，前一段按前一个业务周期判定，避免把旧周期的分钟重复入队到新业务日期；
- 每个候选表再次通过源日历门禁后才入队；
- 只选择 `status = 'N'` 且 `retryCnt > 0` 的表（`EtlTableRepo.java#L128-L145`）。

### 切日刷新：SchemaRefreshScheduler

切日刷新在 `SWITCH_TIME` 触发，负责重置采集标志、刷新所有表结构与任务模板、清理上一周期的队列历史（`backend/src/main/java/com/wgzhao/addax/admin/scheduler/SchemaRefreshScheduler.java`）：

- 仅 Master 注册；`SWITCH_TIME` 修改后由 `POST /api/v1/settings/reschedule-switch-time-task` 重新注册；
- 刷新失败后最多安排一次 5 分钟后的重试；
- 新 Master 上任时若已过切日时间且 Redis 中没有当天的完成标记 `schema:refresh:done:{bizDate}`，会立即补跑一次，避免切日窗口内没有 Master 导致当天完全不采集。

刷新过程本身由 `TaskService.updateParams()` 执行，细节见下文“刷新期间的队列保护”。

### 手动路径

- `POST /api/v1/tasks/{taskId}/executions`：异步提交入队，或 `isSync=true` 直接执行；
- `POST /api/v1/tasks/queue/actions/reset`：清空 pending 并调用 `scanAndEnqueueEtlTasks` 重新扫描；
- `POST /api/v1/system/refresh/start`：手动触发一次切日刷新。

`scanAndEnqueueEtlTasks` 目前只在该手动重置路径被调用，不是周期性扫描入口（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L339-L369`，调用点 `backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java#L250-L258`）。

## 持久化队列

`etl_job_queue` 的一行代表一次“表 + 业务日期”的采集义务（`scripts/schema.sql#L334-L361`）：

| 字段 | 作用 |
| --- | --- |
| `tid`, `biz_date` | 任务标识；活动行受部分唯一索引约束 |
| `status` | `pending` / `running` / `completed` / `failed` / `cancelled` |
| `priority`, `available_at` | 出队排序与退避时间 |
| `attempts`, `max_attempts` | 重试计数与上限，来自采集定义的 `retryCnt` |
| `claimed_by`, `claimed_at`, `lease_until` | 所有权与租约，用于崩溃恢复 |
| `payload` | 提交人等附加信息；带 payload 的 pending 行在切日清理时受保护 |

入队通过 `ON CONFLICT (tid, biz_date) WHERE status IN ('pending','running') DO NOTHING` 去重，所以同一张表在同一业务日期最多只有一条活动任务；终态行不阻止下一次入队（`backend/src/main/java/com/wgzhao/addax/admin/service/EtlJobQueueService.java#L66-L80`）。这个部分唯一索引是并发安全的去重基础，而不是应用层先查后插。

队列容量由字典项 `QUEUE_SIZE` 控制；达到上限时扫描会跳过入队（`TaskQueueManagerV2Impl.java#L339-L369`）。

## Master 选举与心跳

### Master 选举

`MasterElectionService` 用 Redis `SET NX` 竞争 `addax:master:lock`（`backend/src/main/java/com/wgzhao/addax/admin/redis/MasterElectionService.java#L23-L31`）：

- 锁 TTL 30 秒，每 10 秒尝试续租；
- 续租使用 Lua 脚本校验 token，只有仍持有锁的节点才能延长 TTL；
- 续租失败或 Redis 异常时主动降级为 Worker，避免脑裂；
- 关闭时若仍是 Master，主动释放锁让其他节点更快接管。

`instanceId` 由主机名加 JVM 进程标识组成，因此节点重启后是新身份，旧的预占账本不会被错误复用。

### Worker 心跳

每个节点（包括 Master）每 15 秒向 `addax:worker:{instanceId}` 写入一份 JSON 心跳，TTL 45 秒（`backend/src/main/java/com/wgzhao/addax/admin/redis/WorkerHeartbeatService.java#L75-L190`）。心跳包含：

- `availableSlots` / `running` / `concurrentLimit`；
- 节点权重 `weight`；
- 按采集源 `sid` 的运行中计数；
- `lastSeen` 时间。

Master 用 `SCAN` 读取所有心跳，并排除 `lastSeen` 超过 30 秒的节点。TTL 45 秒比 30 秒的过期阈值长，是为了让一次 Redis 抖动或 GC 停顿不会立即被判定为死亡。

## 分派：SWRR 与集群级源并发

Master 的 `masterDispatch` 每 3 秒轮询一次，同时也由 PostgreSQL `LISTEN etl_jobs` 通知和任务完成事件触发（`TaskQueueManagerV2Impl.java#L371-L405`、`#L834-L853`）。所有分派调用由 `dispatchLock` 串行化，容量检查与预占只在单线程内安全。

分派步骤：

1. 读取存活 Worker，清理已消失 Worker 的 SWRR 状态和容量账本；
2. 用 Smooth Weighted Round Robin 选择目标 Worker，权重来自心跳（`TaskQueueManagerV2Impl.java#L649-L678`）；
3. 对每个候选任务检查 Worker 的全局槽位，以及采集源 `maxConcurrency` 的**集群级**总量；
4. 以 180 秒的临时租约认领队列行，再通过 `addax:task:assign:{instanceId}` 推送；
5. 推送返回 0 个订阅者时立即释放认领，避免任务卡在“已认领但无人接收”的状态。

源级并发是集群级保证：Master 把各 Worker 心跳中的 `sourceRunning` 与本周期的预占相加后再判断，而不是只看单个 Worker（`TaskQueueManagerV2Impl.java#L512-L518`、`#L608-L640`）。每个 Worker 自身的有效上限还要再乘以节点权重，并与全局并发上限取小。

节点的全局并发上限 = `CONCURRENT_LIMIT × node.concurrency.weight`，向下取整且至少为 1（`TaskQueueManagerV2Impl.java#L191-L227`；`backend/src/main/java/com/wgzhao/addax/admin/service/SystemConfigService.java#L172-L230`）。

## 租约、恢复与重试

### 租约

- 临时认领租约 180 秒，用于覆盖“分派消息丢失/订阅断连”的窗口；
- Worker 在真正开始执行时把租约延长到 7300 秒，并按 `max(30, 7300/3)` 的间隔续租（`TaskQueueManagerV2Impl.java#L105-L116`、`#L900-L911`）；
- 队列更新都带 `claimed_by` 条件，失去所有权的执行者无法覆盖后继执行者的行。

### 故障恢复

| 故障 | 恢复动作 | 触发时机 |
| --- | --- | --- |
| Worker 心跳消失 | Master 在确认消失超过 60 秒后释放其所有 running 行 | 每次分派周期 |
| Master 刚上任 | 等待 2 个心跳周期后，释放“不在存活列表”的实例所认领的任务 | 成为 Master 时一次性 |
| 租约到期 | `recoverExpiredLeases` 把 running 行改回 pending | 每 30 秒（仅 Master） |
| 分派后无订阅者 | 立即释放认领 | 推送返回 0 时 |
| 节点关闭 | 删除自己的心跳 key；Master 主动释放锁 | `@PreDestroy` |

60 秒的死亡宽限期大于 45 秒心跳 TTL，是为了避免把一次 Redis 抖动误判为 Worker 死亡而重复分派（`TaskQueueManagerV2Impl.java#L107-L118`、`#L450-L490`；`EtlJobQueueService.java#L214-L223`、`#L307-L338`）。

### 重试

失败时按 `attempts` 计算指数退避：30 秒起步、每次乘 2、上限 1800 秒。`attempts < max_attempts` 时队列行回到 pending，否则置为 failed；成功写入 completed，用户取消写入 cancelled（`EtlJobQueueService.java#L145-L204`，调用点 `TaskQueueManagerV2Impl.java#L917-L949`）。终态与告警语义见 [采集执行链路](execution-pipeline.md)。

## 刷新期间的队列保护

`TaskService.updateParams()` 在 Master 上执行一次切日刷新（`backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java#L80-L160`）：

1. 先取得 Redis 锁 `schema:refresh:lock`（TTL 为刷新超时加 60 秒），避免多个触发并发执行；
2. 停止队列监控器，阻止新的 pending 行被分派；已经在运行的任务不中断；
3. `resetAllFlags()` 只重置“今天确实有采集义务”的日历对应的表，休息日不把表刷回 `N`（`TableService.java#L570-L590`）；
4. 刷新所有表资源与任务模板；
5. 删除切日开始前创建的、非 running 的队列行，但保留带 payload 的 pending 行（手动提交/补数）以及刷新期间由其他节点新入队的行（`EtlJobQueueService.java#L269-L283`）；
6. 重启队列监控器并把刷新期间被拒绝的入队请求补写进队列（`TaskQueueManagerV2Impl.java#L1250-L1342`）；
7. 写入完成标记 `schema:refresh:done:{bizDate}`，供新 Master 判断是否需要补跑。

刷新期间 `addTaskToQueue` 不会直接失败，而是把请求放入本地 `deferredEnqueues`，等监控器恢复后统一 flush，避免丢失 2 分钟补偿窗口内的触发。

## 跨节点配置传播

采集源调度变更通过 Redis 频道 `etl:source:updated` 广播，所有节点重新注册自己的本地定时器（`backend/src/main/java/com/wgzhao/addax/admin/service/SourceUpdateSubscriber.java#L20-L80`）。因此每个节点都持有定时器，但只有 Master 的定时器会真正触发入队。

## 运维检查点

- `GET /api/v1/tasks/queue` 返回 pending 数、本节点运行数、当前 Master 实例 ID；
- `GET /api/v1/cluster/nodes` 查看心跳节点；
- `GET /api/v1/system/refresh/status` 判断是否处于切日刷新中；
- 关注日志关键字：`Became master`、`Lost master`、`No listener on channel`、`recovering its claimed tasks`、`Recovered N expired leases`。

相关配置见 [部署与配置](../operations/deployment-and-configuration.md)；补数与业务日期的关系见 [补数与业务日历](../workflows/fillback-and-calendar.md)。
