# Kill Task 流程改造方案

## 问题

当前 `killTask()` 接口仅杀死了 Linux 进程，但没有清理任务队列 `etl_job_queue` 中的相关记录，导致：
1. 已关闭的进程对应的队列任务仍为 `pending` 状态
2. 下次轮询（3s 一次）时仍会被重新领取执行
3. 由于 `etl_table.retry_cnt` 还有剩余，失败后按指数退避重试
4. 形成**无限循环**状态

## 根本原因分析

### 调用链梳理

| 场景 | 调用链 | 是否调用 killTask |
|------|-------|------------------|
| 前端点击"中止任务" | TaskController.killTask() → TaskService.killTask() | ✅ 是 |
| 任务超时自动停止 | CommandExecutor.waitForProcessWithResult() 超时 → destroyForcibly() → failOrReschedule() | ❌ 否 |

**结论**：`TaskService.killTask()` 仅由前端调用，不涉及系统自动处理。

### 流程图

```
前端页面 (table.vue)
  ↓
confirmKill(item) → killTask(item.id)
  ↓
TaskController.killTask(@PathVariable tid)
  ↓
TaskService.killTask(tid)
  ├─ 杀死本地进程 or 发送 Redis kill 消息到远程
  ├─ 记录到 etl_jour
  └─ return success
  
问题：没有清理 etl_job_queue！
```

### 为什么会立即重新执行

```
TaskQueueManagerV2Impl.pollAndDispatch() [每 3 秒轮询一次]
  ↓
dispatchUntilFull()
  ↓
jobQueueService.claimNext()
  → SELECT FROM etl_job_queue WHERE status='pending' AND available_at <= now()
  → ✅ 找到之前 kill 的任务（仍为 pending）
  ↓
重新执行任务
  ↓
失败（因为队列没清，调度器也还会继续入队新的）
  ↓
failOrReschedule() 检查 retry_cnt
  → 如果还有重试次数，设置 available_at = now() + backoff
  → 等待重试
```

同时，定时调度器继续运作：
- `CollectionScheduler`: 按 `etl_source.start_at` 定期扫描该源下所有可运行表，再次入队
- `TableOverrideScheduler`: 每分钟 tick，扫描符合 `etl_table.start_at` 的表，再次入队

→ **形成无限循环**

## 解决方案

### 核心思路

**不修改表结构**，在 `TaskService.killTask()` 中增加：

1. **区分 kill 来源**：增加 `manualKill` 参数（true=用户手动 kill，false=系统自动 kill 如超时）
2. **清理队列**：删除或标记所有属于该表的 `pending`/`running` 队列记录
3. **禁止重试**：将 `etl_table.retry_cnt` 设为 0，防止调度器再次入队或队列重试
4. **更新状态**：将 `etl_table.status` 设为 'E'（采集失败）

### 实现步骤

#### 1. 修改 `TaskService.killTask()` 方法

**新签名**（添加可选参数）：
```java
public TaskResultDto killTask(long tid, boolean manualKill)
```

**旧签名兼容**（重载）：
```java
public TaskResultDto killTask(long tid) {
    return killTask(tid, true);  // 默认认为是手动 kill
}
```

**核心逻辑**：
```java
if (killedLocal || publishedRemotely) {
    // 记录 kill 原因
    String reason = manualKill 
        ? "Killed by user request" 
        : "Killed by system (timeout/other)";
    jourService.failJour(etlJour, reason);
    
    // 仅当手动 kill 时，清理队列和禁止重试
    if (manualKill) {
        cleanupJobQueueForManualKill(tid);
    }
}
```

#### 2. 新增辅助方法 `cleanupJobQueueForManualKill()`

```java
private void cleanupJobQueueForManualKill(long tid) {
    try {
        // 查找所有 pending 和 running 的队列记录
        List<EtlJobQueue> jobs = new ArrayList<>();
        jobs.addAll(jobQueueService.findByTidAndStatus(tid, "pending"));
        jobs.addAll(jobQueueService.findByTidAndStatus(tid, "running"));
        
        // 逐个取消
        for (EtlJobQueue job : jobs) {
            jobQueueService.completeFailure(job.getId(), "Cancelled by manual kill request");
        }
        
        // 更新 etl_table：状态改为 E，retry_cnt 改为 0
        EtlTable table = tableService.getTable(tid);
        if (table != null) {
            table.setStatus(TableStatus.COLLECT_FAIL);
            table.setRetryCnt(0);
            table.setEndTime(new Timestamp(System.currentTimeMillis()));
            etlTableRepo.save(table);
        }
    } catch (Exception e) {
        log.error("Failed to cleanup job queue for manual kill", e);
        // 不抛异常，因为进程已 kill，队列清理失败不应影响主流程
    }
}
```

#### 3. 在 `EtlJobQueueService` 中增加查询方法

```java
public List<EtlJobQueue> findByTidAndStatus(long tid, String status) {
    String sql = "SELECT * FROM public.etl_job_queue WHERE tid = ? AND status = ?";
    return jdbcTemplate.query(sql, JOB_ROW_MAPPER, tid, status);
}
```

#### 4. 更新 TaskController（可选，向前兼容）

```java
@PostMapping("/{tid}/kill")
public ResponseEntity<TaskResultDto> killTask(@PathVariable long tid) {
    // 前端调用时默认认为是手动 kill
    TaskResultDto result = taskService.killTask(tid, true);
    return ResponseEntity.ok(result);
}
```

可选：如果以后需要支持超时自动 kill，可改为：
```java
@PostMapping("/{tid}/kill")
public ResponseEntity<TaskResultDto> killTask(
    @PathVariable long tid,
    @RequestParam(defaultValue = "true") boolean manualKill) {
    TaskResultDto result = taskService.killTask(tid, manualKill);
    return ResponseEntity.ok(result);
}
```

## 效果验证

### 场景 1：用户前端点击"中止任务"

```
killTask(tid, manualKill=true)
  ↓
杀死进程 ✅
  ↓
清理 etl_job_queue 中的 pending/running 记录 ✅
  ↓
etl_table.status = 'E' ✅
etl_table.retry_cnt = 0 ✅
  ↓
结果：任务彻底停止，不再触发
     调度器即便再次扫描该表，也因为 retry_cnt=0 而不会入队
     队列中的失败任务不会再重试
```

### 场景 2：任务超时自动停止（保持现有行为）

```
executeAddax(..., maxRuntimeSeconds)
  → CommandExecutor.waitForProcessWithResult(process, maxRuntimeSeconds)
    → 超时 → destroyForcibly()
  ↓
executeClaimedJob() 捕获失败
  ↓
failOrReschedule(job, error, backoff)
  → 因为没有调用 killTask()，不走 cleanupJobQueueForManualKill()
  → 按正常重试机制处理
  ↓
如果 attempts < maxAttempts，重新入队重试 ✅（符合预期）
```

### 场景 3：定时调度继续正常工作

```
CollectionScheduler 或 TableOverrideScheduler 定期触发
  ↓
executeTasksForSource() 或 enqueueRange()
  → 扫描 etl_table 查询可运行的表
  → getRunnableTasks() 或 getRunnableOverrideTasksBetween()
    → 过滤条件中可能包括检查 status 或 retry_cnt
  ↓
被 kill 的表（retry_cnt=0，status=E）
  → 如果查询条件中会排除这些表 ✅（需验证）
  → 或虽然被扫到，但入队时因为已存在 etl_job_queue pending 记录而被跳过
```

## 改动清单

| 文件 | 改动 | 备注 |
|------|------|------|
| TaskService.java | 修改 killTask()，增加 manualKill 参数；新增 cleanupJobQueueForManualKill() | 核心改动 |
| EtlJobQueueService.java | 新增 findByTidAndStatus() | 辅助查询 |
| TaskController.java | 可选：调整 killTask() 调用以传递 manualKill 参数 | 保持向前兼容 |
| 数据库 | 无需修改 | 利用现有字段和逻辑 |

## 避免的副作用

✅ **调度任务不受影响**：仅当 manualKill=true 时才清理，超时等其他场景保持原逻辑  
✅ **无表结构变更**：完全利用现有字段  
✅ **向前兼容**：重载 killTask() 方法，旧调用方式自动默认为手动 kill  
✅ **安全容错**：cleanupJobQueueForManualKill() 异常不会影响主流程  

## 后续优化（可选）

1. 在 etl_jour 中记录 kind="manual_kill"/"system_kill" 便于审计
2. 在前端加上确认对话框，提示"中止任务后不可自动重试"
3. 定期监控是否存在 retry_cnt=0 且 status=E 的"僵尸表"，考虑是否需要清理
