---
type: operations
title: 可观测性、审计与告警
description: 仪表盘与监控页的统计口径、执行快照与原始日志、风险与字段变更记录、企业微信告警状态机、安全审计的写入/查询/保留，以及集群节点与队列状态接口。
tags: [observability, monitoring, alerting, wecom, audit, cluster, risk-log]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-8306185a2baf94d02966cce6
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/audit/AuditAlertScheduler.java
  - id: openwiki-source-460cb119a469e9a36e7ef93c
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/audit/AuditController.java
  - id: openwiki-source-dad452b8a576ad7c8abdd943
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/audit/AuditMaintenanceService.java
  - id: openwiki-source-b07747ddbbbd9df27bbafe07
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/audit/AuditSanitizer.java
  - id: openwiki-source-b93ec68ea14da4d409f9d27a
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/SecurityConfiguration.java
  - id: openwiki-source-13c28397cd56f59e8e35d63c
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/ClusterController.java
  - id: openwiki-source-c63ad6796f0c515a529b6de7
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/IndexController.java
  - id: openwiki-source-9cc70e02acfa740508044688
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/LogController.java
  - id: openwiki-source-4e4bbbc081c8019a5091e3fc
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/MonitorController.java
  - id: openwiki-source-e4dfbcff7a63eabc250d04aa
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/TaskController.java
  - id: openwiki-source-dd863ba2f45046991646abe2
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/redis/WorkerHeartbeatService.java
  - id: openwiki-source-b16d0cc1df9237560c3aca3f
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/repository/EtlStatisticRepo.java
  - id: openwiki-source-a6aba7cf003bf9fc4074e119
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/repository/EtlTableRepo.java
  - id: openwiki-source-55a3c4182262f33d158776d1
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/AddaxLogService.java
  - id: openwiki-source-cbe0612165577672a134d133
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/AlertService.java
  - id: openwiki-source-66cdfb8d7795b464beec9445
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/DashboardStatService.java
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-6ae4d778f72d0e2b1d1fbb06
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/RiskLogService.java
  - id: openwiki-source-efe64f3a9625804df5d63374
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/StatService.java
  - id: openwiki-source-f85a5f4701b168e36912f3a1
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java
  - id: openwiki-source-c25e7d96ac57d8285ec64f28
    resource: repo://frontend/src/service/monitor-service.ts
  - id: openwiki-source-b3db73c2979c1ee7e5c2bfe5
    resource: repo://frontend/src/views/cluster.vue
  - id: openwiki-source-3965c457b8596df81ea14865
    resource: repo://frontend/src/views/index.vue
  - id: openwiki-source-e4844182ba17d07de66186d8
    resource: repo://frontend/src/views/monitor.vue
  - id: openwiki-source-56ca36fad3cacc52c6c8f19a
    resource: repo://scripts/schema.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 可观测性、审计与告警

系统的可观测性分为四个互不替代的层次：

| 层次 | 数据来源 | 回答的问题 |
| --- | --- | --- |
| 采集结果 | `etl_statistic` | 采了多少、错了多少、耗时多少 |
| 执行事实 | `etl_execution`、`etl_jour`、`addax_log` | 这次运行用了什么配置、为什么失败、原始输出是什么 |
| 风险与变更 | `risk_log`、`schema_change_log`、`etl_table_change_log` | 结构演进或配置变化是否留下隐患 |
| 安全审计 | `audit_event` | 谁在什么时候做了什么、结果如何 |

监控页面消费前两类，风险页面消费第三类，安全审计独立管理。执行链路的完整时序见 [采集执行链路](../architecture/execution-pipeline.md)。

## 首页仪表盘

首页 `/` 使用五个 Tab：总览、今日运行、运行健康、数据质量、耗时与 SLA（`frontend/src/views/index.vue#L11-L20`）。顶部卡片和大部分图表来自 `/dashboard` 接口。

### 聚合卡片

`GET /dashboard/summary` 一次返回：

- 各采集源完成率；
- 采集源总数与采集表数/全部表数；
- 汇总指标（最近数据量、累计数据量、累计天数、总耗时等）。

（`backend/src/main/java/com/wgzhao/addax/admin/controller/IndexController.java#L125-L137`）

减少卡片请求次数的代价是任何一个统计查询变慢都会拖慢整张卡片区。

### 统计时间口径

`DashboardStatService` 明确区分两类时间：

- **基于 `etl_statistic` 的查询**以配置的业务日期 `biz_date` 为窗口锚点；
- **基于 `etl_jour` 的查询**以挂钟 `current_date` 为窗口，因为流水没有业务日期维度，且业务日 D 的任务可能在 D+1 凌晨才执行。

（`backend/src/main/java/com/wgzhao/addax/admin/service/DashboardStatService.java#L9-L25`）

这解释了一个容易误判的现象：某个业务日的采集量可能晚于自然日才出现在图表里。

### 图表口径

| 图表 | 口径 |
| --- | --- |
| 采集表状态分布 | 未退役表按 `status` 分组，实时值 |
| 近 14 天失败趋势 | `etl_jour.status=false`，按 `kind` 分组，按自然日生成完整日期轴 |
| 失败排行 | 近 N 天失败次数最多的表，附最近一次错误 |
| 错误行占比 | 近 30 个业务日 `sum(total_errors)/sum(total_recs)` |
| 采集缺失日历 | 每日应采表数 − 实际有统计的 distinct tid |
| 近 12 个月记录数/数据量 | `etl_statistic` 月度求和，双轴展示 |
| 数据源贡献 | 近 30 天按源汇总字节数，Top 8 + 其他 |
| 慢表排行 | 近 N 天 `avg(take_secs)` 最高的表 |
| 耗时直方图 | 固定 9 个区间 + P50/P95/max |

（`DashboardStatService.java#L34-L360`）

缺失日历的“应采”集合只包含未退役、源启用、状态不为 `X`、且在当天之前已创建的表；实际采集按 `etl_statistic.tid` 去重。`missing` 用 `greatest(expected - collected, 0)`，避免重复统计导致负数（`DashboardStatService.java#L152-L195`）。

完成率卡片同样排除 `X`、源停用和已退役表（`backend/src/main/java/com/wgzhao/addax/admin/service/StatService.java#L75-L105`）。

## 监控与告警页

`/monitor` 只加载五类数据（`frontend/src/views/monitor.vue#L330-L350`）：

1. 采集源完成情况（`GET /monitor/accomplish`）；
2. 特殊任务（`GET /monitor/special-task`）；
3. 拒绝行任务（`GET /monitor/reject-task`）；
4. 系统风险（`GET /monitor/sys-risk`）；
5. 字段变更（`GET /monitor/field-change`，服务端分页）。

另外还加载历史短信记录（`/monitor/sms-detail`），该表来自旧的 `notification` 机制。

### 特殊任务

特殊任务的判定是：

```text
status = 'E' OR duration >= 1200 OR retryCnt < 3
并且 status <> 'X' 且未退役
```

按耗时倒序返回（`backend/src/main/java/com/wgzhao/addax/admin/repository/EtlTableRepo.java#L39-L47`）。因此它同时覆盖“失败”“单次超过 20 分钟”“重试额度已被消耗”三类需要人工关注的状态。

### 拒绝行任务

`findErrorTask` 只取每张表**最新业务日期**且 `total_errors > 0` 的统计记录（`backend/src/main/java/com/wgzhao/addax/admin/repository/EtlStatisticRepo.java#L15-L27`）。它不表示“历史上曾经出错”，而是“最近一次有错误的业务日”。

### 风险与字段变更

- `GET /monitor/sys-risk` 返回最近 7 天内最新的 10 条 `risk_log`；
- `GET /monitor/field-change` 分页返回 `schema_change_log`。

（`backend/src/main/java/com/wgzhao/addax/admin/controller/MonitorController.java#L70-L110`）

`risk_log` 的去重键是“消息 + tid”：同一张表重复出现同一条风险只保留第一行；没有 tid 的风险按消息去重（`backend/src/main/java/com/wgzhao/addax/admin/service/RiskLogService.java#L20-L45`）。因此风险列表适合看“当前仍存在的隐患”，不适合当事件流做次数统计。

`MonitorController` 还暴露了 `/insight/*` 系列（无变化、低变化、高变化、耗时变化、缺失采集），但当前 `/monitor` 页面没有调用这些接口，属于 API 已存在、UI 未接入的能力（`MonitorController.java#L110-L165`）。

## 执行历史与日志

### 执行快照

每张表的详情页“执行快照”Tab 分页展示 `etl_execution`：

- 执行 ID / 尝试次数；
- 业务日期；
- 源表与目标表快照；
- 状态、开始时间、job 摘要；
- 失败时悬停可看错误信息。

数据来自 `GET /tables/{tid}/executions`，见 [采集表管理界面](../frontend/table-management.md)。

### 原始日志

每张表的“采集日志”Tab 列出 `addax_log` 记录，可查看全文、复制和下载。日志在采集结束后从 `${ADDAX_HOME}/log/<logName>` 读取并写入数据库（见 [Addax 任务契约与目标适配器](../integrations/addax-job-contract.md)）。

保留策略（`backend/src/main/java/com/wgzhao/addax/admin/service/AddaxLogService.java#L55-L84`）：

- 默认保留 180 天，可用 `ADDAX_LOG_RETENTION_DAYS` 覆盖；
- 每日 03:15 自动清理；
- 管理员也可调用 `POST /log/addax/cleanup?before=yyyy-MM-dd` 手动清理，该操作写入 CRITICAL 级审计事件（`backend/src/main/java/com/wgzhao/addax/admin/controller/LogController.java#L70-L85`）。

原始日志清理不影响 `etl_execution`、`etl_statistic` 和 `etl_jour`，历史配置与统计仍然保留。

### 操作流水

`etl_jour` 记录模板生成、采集、补数、日志清理等操作，`kind` 区分类型，`status=false` 的流水是失败趋势图的数据源。它比原始日志更轻量，适合长期聚合。

## 企业微信告警

### 故障/恢复状态机

`AlertService` 采用与 Zabbix/Nagios 类似的 hard-state 语义：

- **只有最后一次尝试失败才告警**；中间重试失败只写 debug 日志；
- 告警状态写入 Redis `addax:alert:state:{tid}`，TTL 7 天；
- 之后任意节点采集成功，若存在告警状态，发送“已恢复正常”并清除状态。

（`backend/src/main/java/com/wgzhao/addax/admin/service/AlertService.java#L30-L160`）

告警状态放在 Redis 而不是数据库，是因为失败和恢复可能发生在不同 worker 上；7 天 TTL 用于兜底清理已停用或已删除表的状态。

### 恢复通知的交付顺序

恢复通知先发送，**成功后才删除告警状态**。如果 webhook 发送失败，状态保留，下一次成功会重试。为避免两个并发成功重复发送，发送前先抢占 `addax:alert:recovery:{tid}` 标记，TTL 60 秒（`AlertService.java#L100-L165`）。

### 发送细节

- `WECOM_ROBOT_KEY` 为空时只写告警日志，不发送；
- 失败告警走异步线程，企业微信 `errcode=45009`（限流）时最多重试 3 次，首次延迟 2 秒并指数退避；
- 恢复通知使用独立同步客户端，连接超时 3 秒、读取超时 5 秒，避免恢复路径被限流拖住；
- 消息为 markdown，包含告警时间、主机名和内容。

（`AlertService.java#L60-L90`、`#L170-L300`）

## 安全审计

### 三类历史不能混用

| 表 | 记录内容 |
| --- | --- |
| `audit_event` | 登录、越权拒绝、账号管理、配置修改、调度操作 |
| `etl_table_change_log` | 采集定义的字段级业务变更 |
| `etl_execution` | 执行时的不可变配置快照 |

文档明确要求不要把审计当成业务变更日志，也不要把执行快照当成审计（`docs/security-audit.md#L1-L10`）。

### 只追加

`audit_event` 的数据库触发器拒绝常规 `UPDATE` 和 `DELETE`，只有事务内设置 `addax.audit_purge=on` 时才允许删除（`scripts/schema.sql#L2262-L2276`）。

写入路径统一经过 `AuditRecorder`。敏感字段在写入前递归脱敏：密码、passwd、pwd、secret、token、access key、private key，以及 URL 中的 `user:password@`；JSON 字符串会先解析再脱敏（`backend/src/main/java/com/wgzhao/addax/admin/audit/AuditSanitizer.java#L22-L45`）。绕过 `AuditRecorder` 直接写原始请求体会破坏这个保证。

### 请求上下文

`AuditRequestFilter` 为每个请求生成 `X-Request-Id` 并写入 MDC，同时解析客户端 IP：

- 默认使用 `request.getRemoteAddr()`；
- 只有 `audit.trust-forwarded-headers=true` 时才信任 `X-Forwarded-For` / `X-Real-IP`。

（`backend/src/main/java/com/wgzhao/addax/admin/audit/AuditRequestFilter.java#L27-L60`）

反向代理部署时如果不开启该开关，所有事件会记录代理 IP。

### 查询与导出

`/audit-events` 系列接口仅 `admin` 权限可访问（`backend/src/main/java/com/wgzhao/addax/admin/config/SecurityConfiguration.java#L49`）：

- 默认查询最近 7 天，单次范围最多 366 天；
- 页大小上限 100；
- 导出 CSV 最多 10,000 条，带 UTF-8 BOM；
- 查询、查看详情、导出本身都会再写一条 `AUDIT_EVENTS_VIEWED` / `AUDIT_EVENT_VIEWED` / `AUDIT_EVENTS_EXPORTED` 审计。

（`backend/src/main/java/com/wgzhao/addax/admin/audit/AuditController.java#L20-L130`）

前端 `/audit` 页面提供日期、操作者、动作、结果、级别筛选，展示严重级别与拒绝/失败计数，并可导出（`frontend/src/views/audit.vue#L1-L120`、`#L330-L405`）。

### 保留与安全告警

- 保留默认 365 天；低于 30 天拒绝清理；每日 02:30 执行，每批删除 5000 条；清理本身写审计。
- `AuditAlertScheduler` 每 5 分钟扫描：
  - 同一账号 + 来源 IP 在 10 分钟内失败登录达到 5 次 → 安全告警；
  - 最近 10 分钟内的 CRITICAL 事件 → 汇总告警。
- 告警通过 Redis 去重 30 分钟，避免每个扫描周期重复发送。

（`backend/src/main/java/com/wgzhao/addax/admin/audit/AuditMaintenanceService.java#L20-L45`、`AuditAlertScheduler.java#L15-L85`）

安全告警复用 `AlertService.sendSecurityAlert`，因此同样依赖 `WECOM_ROBOT_KEY`。

## 集群与队列状态

### 节点视图

`GET /cluster/nodes` 返回：

- 存活 worker 列表；
- `masterInstanceId`；
- 每个节点的 `instanceId`、`host`、`role`（`MASTER/WORKER` 或 `WORKER`）、`running`、`availableSlots`、`concurrentLimit`、`weight`、`sourceRunning`、`lastSeen`；
- 总节点数与服务器时间。

（`backend/src/main/java/com/wgzhao/addax/admin/controller/ClusterController.java#L20-L65`）

存活判定是最近心跳在 30 秒内，尽管 Redis key 的 TTL 是 45 秒；因此节点停止心跳后最多 30 秒就从“在线节点”中消失，但 key 还会短暂存在（`backend/src/main/java/com/wgzhao/addax/admin/redis/WorkerHeartbeatService.java#L38-L39`、`#L114-L139`）。

前端 `/cluster` 以卡片展示主节点、并发使用率、权重、可用 slot、各源并发和最近心跳，需要手动刷新（`frontend/src/views/cluster.vue#L1-L130`）。

### 队列状态接口

`GET /tasks/queue` 返回：

- `pendingInDatabase`；
- `runningTasks`；
- `isMaster`；
- `masterInstanceId`。

并额外补充数据库中的待处理/运行中数量（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L1364-L1372`、`backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java#L210-L229`）。`PATCH /tasks/queue` 可启停队列监控，`POST /tasks/queue/actions/reset` 清空并重新扫描；这两个操作都写审计，重置为 CRITICAL。

## 排查顺序建议

1. **单表失败**：详情页执行快照 → 最近错误 → 原始日志 → 风险日志。
2. **大面积未采集**：监控页采集源完成情况 → 缺失日历 → 集群节点/队列状态。
3. **目标结构问题**：字段比较 Tab 的目标契约 → `risk_log` → 目标端实际 DDL。
4. **权限或异常操作**：安全审计按 actor/action/outcome 过滤，结合 `request_id` 关联同一次请求。

## 相关页面

- [采集执行链路](../architecture/execution-pipeline.md)
- [REST API 与安全模型](../backend/api-and-security.md)
- [数据模型与持久化](../backend/persistence-model.md)
- [部署与配置](deployment-and-configuration.md)
