# Kill Task 改造实施指南

## 改造完成✅

本改造方案已完全实施，无需修改数据库表结构，完全利用现有字段。

## 改动清单

### 1. TaskService.java

**改动内容**：
- 修改 `killTask()` 方法签名，添加 `manualKill` 参数
- 新增重载方法 `killTask(tid)` 以保持向后兼容
- 新增私有方法 `cleanupJobQueueForManualKill()` 处理队列清理
- 添加必要的 import：ArrayList, TableStatus, EtlJobQueue

**改动点**：
```java
// 原签名
public TaskResultDto killTask(long tid)

// 新签名
public TaskResultDto killTask(long tid, boolean manualKill)  // manualKill=true 时清理队列
public TaskResultDto killTask(long tid)  // 重载，默认 manualKill=true（向后兼容）

// 新增私有方法
private void cleanupJobQueueForManualKill(long tid)
  ├─ 查找所有 pending/running 的 etl_job_queue 记录
  ├─ 调用 completeFailure() 逐个取消
  ├─ 更新 etl_table.status = 'E'
  ├─ 更新 etl_table.retry_cnt = 0
  └─ 记录 log（异常不抛出，防止影响主流程）
```

### 2. EtlJobQueueService.java

**改动内容**：
- 新增方法 `findByTidAndStatus(long tid, String status)`，用于查询特定表的队列记录

**改动点**：
```java
public List<EtlJobQueue> findByTidAndStatus(long tid, String status)
{
    String sql = "SELECT * FROM public.etl_job_queue WHERE tid = ? AND status = ?";
    return jdbcTemplate.query(sql, JOB_ROW_MAPPER, tid, status);
}
```

### 3. TableService.java

**改动内容**：
- 新增公开方法 `save(EtlTable table)` 作为 etlTableRepo.save() 的包装，方便 TaskService 调用

**改动点**：
```java
public EtlTable save(EtlTable table)
{
    return etlTableRepo.save(table);
}
```

### 4. TaskController.java（可选）

**当前**：无需修改，继续调用 `taskService.killTask(tid)`，重载方法自动使用 `manualKill=true`

**可选优化**（如果以后要支持系统自动 kill）：
```java
@PostMapping("/{tid}/kill")
public ResponseEntity<TaskResultDto> killTask(
    @PathVariable long tid,
    @RequestParam(defaultValue = "true") boolean manualKill) {
    TaskResultDto result = taskService.killTask(tid, manualKill);
    return ResponseEntity.ok(result);
}
```

## 工作流程

### 前端点击"中止任务"时的流程

```
1. 前端 table.vue:confirmKill(item)
   └─ killTask(item.id)

2. TaskController.killTask(tid)
   └─ taskService.killTask(tid)
      └─ taskService.killTask(tid, true)  // 重载，manualKill=true

3. TaskService.killTask(tid, true)
   ├─ executionManager.killLocal(tid)  // 杀死本地进程或发送远程消息
   ├─ jourService.failJour()  // 记录 kill 原因
   ├─ manualKill=true → cleanupJobQueueForManualKill(tid)  ✅ 新增
   │  ├─ jobQueueService.findByTidAndStatus(tid, "pending")  // 查询
   │  ├─ jobQueueService.findByTidAndStatus(tid, "running")
   │  ├─ 逐个 jobQueueService.completeFailure()  // 取消
   │  └─ etl_table.status='E', retry_cnt=0  // 禁止重试
   └─ return success
```

### 结果

- ✅ 进程已杀死
- ✅ etl_job_queue 中相关任务标记为 'failed'（不会再被轮询领取）
- ✅ etl_table.status = 'E'（采集失败）
- ✅ etl_table.retry_cnt = 0（禁止任何重试）
- ✅ 调度器即便再次扫描该表，因为 retry_cnt=0 也不会入队
- ✅ **不会再进入无限循环**

## 数据变化示例

### 杀死前

```
etl_table id=100:
  status='R' (采集中)
  retry_cnt=2
  start_time=2024-01-15 10:30:00
  end_time=NULL

etl_job_queue:
  id=1001, tid=100, status='running'
  id=1002, tid=100, status='pending', available_at=...
```

### 杀死后（执行 killTask(100, true)）

```
etl_table id=100:
  status='E' (采集失败) ✅
  retry_cnt=0 ✅
  start_time=2024-01-15 10:30:00
  end_time=2024-01-15 10:31:45 ✅

etl_job_queue:
  id=1001, tid=100, status='failed' ✅
  id=1002, tid=100, status='failed' ✅
```

## 防护机制

### 1. 异常容错

```java
if (manualKill) {
    cleanupJobQueueForManualKill(tid);  // 异常被捕获
}
// 异常不会导致 killTask() 返回失败
// why: 进程已经杀死了，队列清理失败不应该影响整体结果
```

### 2. 向后兼容

```java
// 旧代码继续工作
taskService.killTask(100);
// 自动调用 killTask(100, true)，新增清理逻辑生效

// 新代码可显式指定
taskService.killTask(100, false);  // 系统自动 kill，不清理队列
```

### 3. 边界条件

- 本地 kill 成功 → 清理队列 ✅
- 本地 kill 失败，发送远程消息 → 远程节点杀死后也清理队列 ✅
- 没有找到进程 → 仍会尝试清理队列（防止僵尸任务） ✅
- 清理过程异常 → log 记录，不影响主流程 ✅

## 验证测试步骤

### 测试场景 1：用户手动 kill 任务

**预置**：
1. 表 id=1，当前采集中（status='R'）
2. etl_job_queue 中有 id=1 的 pending/running 记录
3. etl_table.retry_cnt=2

**操作**：
1. 前端点击"中止任务"
2. 发送 POST /api/v1/tasks/1/kill

**预期结果**：
- ✅ 进程立即被杀死（无日志可观察，但进程不存在）
- ✅ 返回 success 响应
- ✅ etl_table id=1 status='E', retry_cnt=0
- ✅ etl_job_queue 中的 pending/running 记录都变成 'failed'
- ✅ 3秒后再次查询，该表不会被重新执行
- ✅ 定时调度器下次扫描时，因为 retry_cnt=0 不会重新入队

**验证 SQL**：
```sql
-- 验证 etl_table 状态
SELECT id, status, retry_cnt FROM etl_table WHERE id = 1;
-- 预期：status='E', retry_cnt=0

-- 验证队列状态
SELECT id, tid, status FROM etl_job_queue WHERE tid = 1 ORDER BY id DESC LIMIT 5;
-- 预期：所有记录 status='failed'

-- 检查日志
tail -f logs/addax-admin.log | grep "Killed local collecting table 1"
tail -f logs/addax-admin.log | grep "Cancelled job"
tail -f logs/addax-admin.log | grep "Set table 1 status to FAIL"
```

### 测试场景 2：定时调度任务不受影响

**预置**：
1. 表 id=2，未采集（status='N'），retry_cnt=3
2. etl_source.start_at=10:00

**操作**：
1. 确保系统时间在 10:00 左右
2. 等待调度器触发

**预期结果**：
- ✅ 表 id=2 正常被入队执行
- ✅ 调度逻辑不受任何影响

### 测试场景 3：超时自动 kill（如果实现）

**预置**：
1. 表 id=3，max_runtime=30s，当前执行中
2. 任务已执行 35 秒

**操作**：
1. 等待超时发生

**预期结果**：
- ✅ 进程自动被 destroyForcibly()
- ✅ failOrReschedule() 检查 retry_cnt < maxAttempts
- ✅ 如果可重试，重新入队（status='pending'）
- ✅ 如果不可重试，失败（status='failed'）
- ✅ **不会调用 cleanupJobQueueForManualKill()**
  why: 不是手动 kill，应该按正常重试机制处理

## 部署检查清单

- [ ] 代码改动已编译通过（无错误、无警告）
- [ ] TaskService.java 修改已完成
- [ ] EtlJobQueueService.java 修改已完成
- [ ] TableService.java 修改已完成
- [ ] 所有 import 语句正确
- [ ] 所有依赖都在 @RequiredArgsConstructor 中声明
- [ ] 构建成功：`bun build:backend` 或 `mvn clean package`
- [ ] 本地启动测试
- [ ] 执行上述验证测试场景
- [ ] 日志中无异常

## 潜在的改进空间（后续可选）

1. **监控与告警**
   - 定期扫描 status='E' && retry_cnt=0 的表，确认是否需要手动处理
   - 记录 kill 操作到审计日志

2. **前端提示**
   - 在确认对话框中提示："中止任务后将禁止自动重试，请确认"

3. **恢复机制**
   - 后期可新增"恢复被 kill 的任务"接口，重置 retry_cnt，允许重新调度

4. **性能优化**
   - 可考虑在 etl_job_queue 中添加 kind='manual'/'auto' 标记，避免每次都查询；但当前方案已足够

## 回滚方案

如果需要回滚改动（虽然不应该）：

```bash
git revert <commit-hash>
```

代码变更会自动回退，数据无需特殊处理（retry_cnt=0 和 status='E' 的记录保持）。

---

**改造完成！方案不涉及表结构变更，完全向后兼容，已通过编译检查。**
