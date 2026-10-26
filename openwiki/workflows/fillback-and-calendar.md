---
type: workflow
title: 补数与业务日历
description: 说明采集日历（自然日/工作日/交易日）、切日如何决定业务日期与触发门禁，以及补数如何按目标日期逐日入队、重建 job、限制数量并隔离失败。
tags: [workflow, fillback, business-date, calendar, switch-time, scheduling]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-0860e20409f81ea8edf1588a
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/common/CalendarType.java
  - id: openwiki-source-0ddb7db5a905036ede1ebeaa
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/common/Constants.java
  - id: openwiki-source-b6ca715f19499c0e39a2e163
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/ParamController.java
  - id: openwiki-source-32370565f17c2a43f503ae76
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/SourceController.java
  - id: openwiki-source-e4dfbcff7a63eabc250d04aa
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/TaskController.java
  - id: openwiki-source-98c1e6e4abcca885022cf52e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/scheduler/CollectionScheduler.java
  - id: openwiki-source-429caa4a71ba43e99fb89128
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/scheduler/TableOverrideScheduler.java
  - id: openwiki-source-426b19e33f952927446f80c6
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/CalendarService.java
  - id: openwiki-source-131075c0b1a6fd22773f5f72
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java
  - id: openwiki-source-a850b14847118325bacc355d
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/EtlJobQueueService.java
  - id: openwiki-source-5c0cf649ebaa6721d0c06f4e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java
  - id: openwiki-source-650482944d35601234caaec8
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java
  - id: openwiki-source-9ce32dae5edb9d3bb91ad747
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/SourceService.java
  - id: openwiki-source-efe64f3a9625804df5d63374
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/StatService.java
  - id: openwiki-source-a827bea50fa37c6d2717f0e8
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/SystemConfigService.java
  - id: openwiki-source-8e53bcc120d6cbf603fa935b
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java
  - id: openwiki-source-f85a5f4701b168e36912f3a1
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java
  - id: openwiki-source-655b513ec887c95a07bc8661
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/TradeCalendarService.java
  - id: openwiki-source-027880894ad026680c8e7690
    resource: repo://frontend/src/components/table/FillbackDialog.vue
  - id: openwiki-source-d9e37a20680c10ff6c5fcc96
    resource: repo://frontend/src/views/dict.vue
  - id: openwiki-source-cf93ab3be0236723515ecb9e
    resource: repo://frontend/src/views/table/index.vue
  - id: openwiki-source-49f5868b9bc0b9372e54c787
    resource: repo://scripts/data.sql
  - id: openwiki-source-56ca36fad3cacc52c6c8f19a
    resource: repo://scripts/schema.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 补数与业务日历

采集系统里有两个容易混淆的日期：**今天是否应该触发**，以及**这次运行属于哪个业务日**。前者由采集日历门禁回答，后者由切日规则和日历共同推导。补数则相反：它不再推导日期，而是由用户显式给出目标业务日，再围绕这个日期重新生成 job。

这三个概念必须分开，否则会出现“周末仍在采集”“节假日数据被标成当天”“补数覆盖了正常任务”等问题。

## 三种采集日历

采集日历挂在**采集源**上，是一份可空枚举：

| 值 | 存储 | 含义 | 数据来源 |
| --- | --- | --- | --- |
| 空 | `null` | 自然日 | 不需要字典，每天都是业务日 |
| `WORK_DAY` | 字典 `1022` | 工作日（含调休） | 字典行集合 |
| `TRADE_DAY` | 字典 `1021` | 交易日 | 字典行集合 |

（`backend/src/main/java/com/wgzhao/addax/admin/common/CalendarType.java#L10-L19`）

字典里的每一行就是日历中的一个日期，`item_key` 为 `yyyyMMdd`；没有额外的“是否业务日”属性列。这样查询可以直接把字典当日期集合使用（`CalendarType.java#L10-L13`、`backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java#L597-L632`）。

### 写入与运行时解析

- 创建或更新采集源时，空值统一归一化为 `null`，未知的日历名直接返回 400，避免拼写错误静默退化成自然日（`backend/src/main/java/com/wgzhao/addax/admin/controller/SourceController.java#L315-L325`）。
- 数据库中已经存在的非法值在运行时按自然日兜底并记录告警，而不是让调度器停止工作（`CalendarType.java#L57-L65`）。
- 修改源日历会和修改采集时间一样，通过 Redis 广播通知其他节点重新注册定时任务（`backend/src/main/java/com/wgzhao/addax/admin/service/SourceService.java#L124-L143`）。

## 业务日期与切日

`CalendarService` 提供两个不同的问题的答案：

| 方法 | 回答的问题 | 规则 |
| --- | --- | --- |
| `businessPeriodDate()` | 当前触发属于哪个业务周期 | 切日之前是昨天，切日之后是今天 |
| `businessDate(calendar)` | 这次运行应该记到哪个业务日 | 切日后游标指向明天，再取该日历中游标之前最近的业务日 |

（`backend/src/main/java/com/wgzhao/addax/admin/service/CalendarService.java#L89-L131`）

切日时间来自字典 1000 的 `SWITCH_TIME`：种子数据是 `16:20`，字典项缺失时代码兜底为 `16:30`（`backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java#L82-L100`、`scripts/data.sql#L31`、`backend/src/main/java/com/wgzhao/addax/admin/common/Constants.java#L19`）。

### 为什么凌晨要用“昨天”

`businessPeriodDate()` 在切日之前返回前一天。这样周六 01:30 触发的任务会被判定为**周五**的业务周期，交易日历检查的是周五而不是周六；否则周五的数据会在周六凌晨被拒绝，下一次再也不会补上（`CalendarService.java#L97-L118`、`backend/src/main/java/com/wgzhao/addax/admin/scheduler/TableOverrideScheduler.java#L88-L92`）。

### 正常任务的 biz_date

正常调度任务入队时，`biz_date` 来自每张表所属采集源的日历，而不是全局 `BIZ_DATE`：

```text
businessDate(calendar)
  = 切日后 ? previousBusinessDay(calendar, 明天)
           : previousBusinessDay(calendar, 今天)
```

（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L1272-L1281`、`CalendarService.java#L120-L150`）

例子：

- 周一 10:00（切日之前）→ 游标为周一 → 交易日历取上周五；
- 周一 17:00（切日之后）→ 游标为周二 → 交易日历取周一；
- 自然日 → 直接取游标前一天。

全局 `BIZ_DATE` 仍保留历史口径（以交易日历为锚），用于看板和缓存模板；队列中的每张表使用自己的日历，两者在跨节假日时可能不同（`DictService.java#L116-L136`、`TaskQueueManagerV2Impl.java#L1272-L1281`）。

### 补数是唯一显式传日期的调用方

`addTaskToQueue` 使用 `businessDateOf(table)`；只有 `addFillbackTaskToQueue(table, fillbackDate, payload)` 接受调用方给出的日期。补数收集的不是“当前业务日”，所以不能再套一次日历推导（`TaskQueueManagerV2Impl.java#L1266-L1311`）。

## 触发门禁

### 采集源定时任务

采集源 cron 每天触发，但触发时重新读取当前源日历，并用 `businessPeriodDate()` 判定是否放行：

```text
cron 触发
  -> 读取 source.calendar（不是注册时捕获的副本）
  -> evaluate(calendar, businessPeriodDate())
  -> due=false 时记录拒绝原因并 return
  -> due=true 时 executeTasksForSource
```

（`backend/src/main/java/com/wgzhao/addax/admin/scheduler/CollectionScheduler.java#L70-L101`）

在触发时读取日历，而不是注册时缓存，意味着运行期直接改数据库中的日历也会生效，不要求重新注册任务或重启 master。

### 表级覆盖调度

表级 `start_at` 由 master 每分钟扫描一次 `[now-2min, now]` 的闭区间，补偿 scheduler 抖动或重启造成的漏触发（`backend/src/main/java/com/wgzhao/addax/admin/scheduler/TableOverrideScheduler.java#L48`、`#L82-L128`）。

这段逻辑有两个边界处理：

- 窗口跨午夜时拆成两段，`[from, 23:59]` 属于昨天的业务周期，`[00:00, now]` 属于当前周期；
- 切日边界会把扫描起点裁剪到 `switchTime`，避免同一分钟在新旧 `biz_date` 下各入队一次。

每张表仍然用自己源日历的 `businessPeriodDate()` 做门禁（`TableOverrideScheduler.java#L97-L118`、`#L136-L148`）。

### 门禁只在调度层

补数不经过 `CollectionScheduler` 或 `TableOverrideScheduler` 的日历判定，而是直接调用 `addFillbackTaskToQueue` 入队。也就是说：

- 自动触发会被日历拒绝；
- 手工补数不会被日历拒绝，可以补休息日或历史日期。

（`CollectionScheduler.java#L88-L95`、`TaskQueueManagerV2Impl.java#L1302-L1311`）

## 日历判定与耗尽

`evaluate(calendar, date)` 返回 `Verdict(due, reason)`：

| 情况 | due | reason |
| --- | --- | --- |
| 自然日 | `true` | `NATURAL_DAY` |
| 日期在字典集合中 | `true` | `BUSINESS_DAY` |
| 日期不在集合中，但日历仍覆盖该日 | `false` | `NOT_BUSINESS_DAY` |
| 日期超过日历最后覆盖日；日历为空或读取失败 | `false` | `CALENDAR_EXPIRED` |

（`CalendarService.java#L64-L82`、`#L222-L240`）

注意**门禁和日期推导的失败方向不同**：

- `evaluate` 对耗尽或读不到的日历**拒绝**采集，避免在未知日期上继续跑；
- `previousBusinessDay` 在日历耗尽或读不到时**退化为自然日减一天**并告警，保证正在写入的数据仍能推导出一个日期。

（`CalendarService.java#L138-L150`）

### 耗尽告警

日历耗尽时，每个日历每天最多发送一次企业微信告警。去重键是 `etl:calendar:expired:{calendar}:{yyyyMMdd}`，TTL 2 天；Redis 写入失败时仍然发送本次告警（`CalendarService.java#L185-L201`、`#L274-L289`）。

字典页还提供覆盖范围接口 `GET /calendar-coverage`：返回每个日历的最后日期，以及 30 天内即将到期或已经过期的提示（`backend/src/main/java/com/wgzhao/addax/admin/controller/ParamController.java#L49-L71`、`CalendarService.java#L204-L219`）。前端在字典页加载并展示这些 warning（`frontend/src/views/dict.vue#L657-L668`）。

### 缓存

日历查询结果缓存 1 分钟。修改日历后最多约 1 分钟生效，不需要重启服务；缓存过期后重新查字典（`CalendarService.java#L28-L30`、`#L39`、`#L313-L323`）。

## 状态重置与缺数监控

非业务日不应该把表重置成“待采集”。因此切日重置和资源刷新后的状态重置都会先判断日历：

- `resetAllFlags()` 只重置“今天确实是业务日”的日历下的表；所有日历都不应采时直接返回，保留上一次状态（`backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java#L562-L587`）。
- `setNotCollectIfDue()` 在刷新完成后使用 `businessPeriodDate()` 判断，非业务日保留原状态（`TableService.java#L452-L469`）。

这样周末的工作日/交易日表会保持上一次的 `Y`，而不是全部变成 `N`，看板成功率也不会被休息日拉低。

缺数监控同样不是按自然日算：`getMissingCollectTablesInDays` 按每张表所属日历生成期望日期集合，再与实际 `etl_statistic` 比对。工作日/交易日源在休息日不会被报缺失（`backend/src/main/java/com/wgzhao/addax/admin/service/StatService.java#L505-L527`、`CalendarService.java#L164-L183`）。

## 日历维护

### 批量生成日期

字典页的“批量新增日历日期”调用：

```text
POST /params/{dictCode}/generate-calendar/{year}/{includeWeekend}
```

工作日历生成的是“周一至周五”的起点，法定节假日需要维护者删除，调休上班日需要手工补上。重新生成不会覆盖已存在的日期，因此人工修正不会被一次误操作抹掉（`backend/src/main/java/com/wgzhao/addax/admin/service/DictService.java#L597-L632`、`backend/src/main/java/com/wgzhao/addax/admin/controller/ParamController.java#L284-L303`）。

### 交易日历年度补全

交易日历优先从柜台接口获取 SSE 休市日，再用“工作日 − 休市日”得到交易日（`backend/src/main/java/com/wgzhao/addax/admin/service/TradeCalendarService.java#L43-L55`、`#L269-L308`）。

补全策略有两种结果：

- **SSE 成功**：官方数据是权威，会补齐缺失日期，也会移除该年已经不再交易的日期；
- **接口失败或数据异常**：退化为固定规则（周一至周五，扣除元旦 1 月 1-3 日、国庆 10 月 1-7 日），只新增、不删除，避免一次接口故障抹掉人工维护的节假日。

（`TradeCalendarService.java#L100-L155`、`#L158-L175`）

SSE 响应中的休市日少于 5 条会被视为“未发布或结构变化”，直接回退，不写入不完整数据（`TradeCalendarService.java#L69-L73`、`#L269-L308`）。

这两类日历操作都会写审计：`CALENDAR_GENERATED` 与 `TRADE_CALENDAR_REFRESHED`（`ParamController.java#L298-L301`、`#L323-L326`）。

## 补数流程

### 入口与前端限制

补数入口在采集表列表的批量操作栏，勾选行后出现；`RETIRED` 范围不显示该操作（`frontend/src/views/table/index.vue#L145-L160`）。

对话框接收选中的表 ID 列表，让用户选择开始日期和结束日期（都包含端点），并实时显示：

```text
任务数 = 选中表数 × 天数
```

前端上限是 20；超过上限时禁用提交并提示缩小范围（`frontend/src/components/table/FillbackDialog.vue#L48-L64`、`#L122`、`#L140-L153`）。

### 后端入队

`POST /tasks/fillback` 接收 `tids`、`startDate`、`endDate`，按**表 × 日期**逐项入队（`backend/src/main/java/com/wgzhao/addax/admin/controller/TaskController.java#L254-L279`）。

前置校验：

| 条件 | 结果 |
| --- | --- |
| 表列表为空、日期为空、开始晚于结束 | 400 |
| 表数 × 天数 > 20 | 400 |
| 队列正在刷新（`isRefreshing()`） | 409 |
| 表不存在 / 已退役 / 表视图不存在 | 该表跳过并返回原因 |
| 目标没有日期维度 | 该表跳过，并写 `risk_log` WARN |

（`backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java#L347-L404`）

日期维度由目标适配器判断：HDFS 分区字段或 RDBMS 快照业务日期列都算；没有日期维度的表无法补数，因为多个日期会互相覆盖（`TaskService.java#L395-L404`）。

### 范围按自然日展开

后端循环使用 `date = startDate; !date.isAfter(endDate); date = date.plusDays(1)`，**不会跳过周末或节假日**。如果用户选择了一段包含休息日的范围，这些日期也会各生成一个任务（`TaskService.java#L406-L417`）。

需要只补业务日时，调用方必须自己缩窄日期范围；后端不会按采集日历过滤补数日期。

### 队列去重

每个 `(tid, biz_date)` 对应一条队列行。活跃唯一索引只约束 `status in ('pending','running')` 的行，因此：

- 同一张表的**相同日期**已有活跃任务时，重复入队被抑制，计入 `skipped`；
- 同一张表的**不同日期**可以同时存在活跃队列行，互不阻塞。

（`backend/src/main/java/com/wgzhao/addax/admin/service/EtlJobQueueService.java#L66-L79`、`scripts/schema.sql#L355-L359`）

补数不会查询 `etl_statistic` 判断该日期是否已经采过；重复提交同一日期时，只要没有活跃队列行就会再次入队并重新采集（`TaskService.java#L406-L416`、`EtlJobQueueService.java#L66-L79`）。

### 任务负载与审计

每个补数任务携带 JSON payload：

```json
{"submitter": "...", "action": "fillback", "fillback_date": "yyyy-MM-dd"}
```

每张表写一条 `FILLBACK` 操作流水；接口层再写一条 CRITICAL 审计 `COLLECTION_FILLBACK_SUBMITTED`，包含表 ID、日期范围和入队/跳过数量（`TaskService.java#L419-L439`、`TaskController.java#L271-L278`）。

## 补数任务的执行

### 何时重建 job

执行时如果任务带有 `overrideBizDate`，并且它与当前全局 `systemBizDate` 不同，就调用 `getJobContentForDate(tid, overrideBizDate)` **在内存中重新生成** job；相同则复用已缓存的模板（`backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java#L1070-L1083`）。

重新生成的结果不会写回 `etl_job`，只用于本次执行（`backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java#L74-L105`）。

### 日期变量

补数模板中：

- `biz_*` 全部按目标日期计算（`biz_date_short`、`biz_year`、`biz_month`、分区路径等）；
- `curr_*` 始终取真实当前时间；
- 增量 reader 的“上一次采集日”使用目标日期在**同一日历**上的上一个业务日，而不是自然日减一天。

（`backend/src/main/java/com/wgzhao/addax/admin/service/SystemConfigService.java#L338-L374`、`JobContentService.java#L92-L100`）

writer 也通过 `buildWriterJobForDate(etlTable, targetDate)` 按目标日期生成分区路径和动态表名（`JobContentService.java#L98-L104`）。

### 执行快照与作业文件

执行快照记录 `executionBizDate = overrideBizDate`，因此补数的执行历史会落在目标业务日，而不是当前业务日（`TaskQueueManagerV2Impl.java#L1083`）。

作业文件名包含 `bizDateStr`，例如 `{targetDb}.{targetTable}_{tid}_{bizDate}.json`，避免同一张表的并发补数互相覆盖 job 文件（`TaskQueueManagerV2Impl.java#L1118-L1127`）。

## 失败语义速查

| 场景 | 结果 |
| --- | --- |
| 自动触发日不在日历 | 跳过该次触发，记录 `NOT_BUSINESS_DAY` |
| 日历已耗尽 | 拒绝采集，每个日历每天告警一次 |
| 日历编辑后立即触发 | 最多等待约 1 分钟缓存过期 |
| 自然日源 | 不受门禁影响 |
| 补数任务数 > 20 | 400，前端也会禁用提交 |
| 补数时队列正在刷新 | 409 |
| 补数表已退役 | 该表跳过并返回原因 |
| 补数表没有日期维度 | 该表跳过并写风险日志 |
| 同表同日已有活跃队列行 | 该日期跳过，计入 `skipped` |
| 同表不同日期 | 各自独立排队 |
| 补数日期与全局业务日相同 | 复用缓存 job，不重建 |
| 补数日期与全局业务日不同 | 内存重建 job，不写 `etl_job` |
| job 生成失败 | 执行快照直接记为失败，不启动 Addax |

## 排查顺序

1. **自动任务没跑**：先看源日历与字典覆盖范围，再看触发时刻是否早于切日、被判定成前一天。
2. **跑了但 biz_date 不对**：检查源日历、切日前后时刻，以及该表是否使用了表级 `start_at`。
3. **补数没入队**：检查是否超过 20、队列是否在刷新、表是否退役或无日期维度。
4. **补数入队但结果不对**：检查任务的 `biz_date`、job 中的 `biz_*` 变量、增量 reader 的上一个业务日，以及作业文件是否按目标日期生成。

## 相关页面

- [调度与分布式队列](../architecture/scheduling-and-queue.md)
- [采集定义与生命周期](../domain/collection-definition-lifecycle.md)
- [Addax 任务契约与目标适配器](../integrations/addax-job-contract.md)
- [可观测性、审计与告警](../operations/observability-and-alerts.md)
- [测试策略与 E2E](../testing/testing-strategy.md)
