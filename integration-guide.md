# 采集任务队列管理框架集成指南

## 需要在 SpAloneService.java 中添加的方法

请在 SpAloneService 类的末尾添加以下方法：

```java
// ==================== 队列管理框架辅助方法 ====================

/**
 * 执行SQL语句（包装现有的jdbcTemplate.execute方法）
 */
public void executeSqlStatement(String sql) { 
    jdbcTemplate.execute(sql); 
}

/**
 * 查询单列数据列表（包装现有的querySingleColumn方法）
 */
public List<Map<String, Object>> querySingleList(String sql) {
    try { 
        return jdbcTemplate.queryForList(sql); 
    }
    catch (Exception e) { 
        log.error("查询失败: {}", sql, e); 
        return Collections.emptyList(); 
    }
}

/**
 * 公开dispatchStartWkf方法供队列管理器使用
 */
public void dispatchStartWkf(String kind, String val) {
    executorService.submit(() -> {
        switch (kind) {
            case "plan" -> executeSpEtl(val, "plan");
            case "judge" -> executeJudgeEtl(val);
            case "ds" -> executeDataServiceEtl(val);
            case "soutab" -> executeSourceTableEtl(val);
            case "sp" -> executeSpEtl(val, null);
            case "spcom" -> executeSpEtl(val, "manual");
            case "etl" -> executeEtlTaskFromQueue(val); // 新增：从队列执行的ETL任务
            default -> runShell(kind + " " + val);
        }
    });
}

/**
 * 从队列执行ETL任务的具体实现
 * 这是队列管理框架调用的核心方法，您可以在这里补充具体的采集逻辑
 */
public String executeEtlTaskFromQueue(String taskId) {
    log.info("开始执行队列中的ETL任务: {}", taskId);
    
    try {
        // TODO: 在这里实现具体的采集逻辑
        // 您可以根据taskId查询tb_imp_etl表获取任务详情
        // 然后调用相应的采集程序
        
        // 示例：查询任务详情
        String taskDetailSql = """
            SELECT etl_id, sys_id, table_name, etl_type, etl_config 
            FROM tb_imp_etl 
            WHERE etl_id = ?
            """;
        List<Map<String, Object>> taskDetails = jdbcTemplate.queryForList(taskDetailSql, taskId);
        
        if (taskDetails.isEmpty()) {
            log.warn("未找到ETL任务: {}", taskId);
            return "任务不存在";
        }
        
        Map<String, Object> taskDetail = taskDetails.get(0);
        String sysId = String.valueOf(taskDetail.get("sys_id"));
        String tableName = String.valueOf(taskDetail.get("table_name"));
        String etlType = String.valueOf(taskDetail.get("etl_type"));
        
        log.info("执行ETL任务详情: taskId={}, sysId={}, tableName={}, etlType={}", 
                taskId, sysId, tableName, etlType);
        
        // 这里可以根据etlType调用不同的采集逻辑
        // 例如：
        // - 如果是数据库采集，调用executeSourceDbSql
        // - 如果是文件采集，调用相应的文件处理方法
        // - 如果是API采集，调用API接口方法
        
        // 模拟采集过程（请替换为实际的采集逻辑）
        Thread.sleep(2000 + (long)(Math.random() * 3000)); // 模拟2-5秒采集时间
        
        recordSystemLog(String.format("ETL任务执行完成: %s - %s.%s", taskId, sysId, tableName));
        
        return "ETL任务执行成功";
        
    } catch (Exception e) {
        log.error("ETL任务执行失败: {}", taskId, e);
        recordSystemLog(String.format("ETL任务执行失败: %s - %s", taskId, e.getMessage()));
        throw new RuntimeException("ETL任务执行失败: " + e.getMessage(), e);
    }
}
```

## 修改现有的 executePlanStart 方法

可以选择以下两种方式之一：

### 方式1：替换现有方法
将现有的 `executePlanStart()` 方法重命名为 `executePlanStartOld()`，然后添加：

```java
/**
 * 计划任务主控制 - 使用队列管理
 */
public String executePlanStart() {
    // 注入EtlTaskEntryService
    EtlTaskEntryService entryService = SpringContextUtil.getBean(EtlTaskEntryService.class);
    return entryService.executePlanStartWithQueue();
}
```

### 方式2：添加配置开关
在现有方法中添加开关：

```java
@Value("${sp.alone.use.queue:true}")
private boolean useQueueManagement;

public String executePlanStart() {
    if (useQueueManagement) {
        EtlTaskEntryService entryService = SpringContextUtil.getBean(EtlTaskEntryService.class);
        return entryService.executePlanStartWithQueue();
    } else {
        // 保持原有逻辑
        return executePlanStartOld();
    }
}
```

## 使用方式

### 1. API调用
```bash
# 启动采集任务
curl -X POST http://localhost:8080/api/etl/start

# 查看队列状态
curl -X GET http://localhost:8080/api/etl/status

# 手动添加任务
curl -X POST http://localhost:8080/api/etl/add/12345
```

### 2. 配置参数
在 application.properties 中添加：
```properties
# 队列大小（默认100）
sp.alone.queue.size=100

# 并发限制（默认30）
sp.alone.concurrent.limit=30

# 是否使用队列管理（默认true）
sp.alone.use.queue=true
```

## 框架特性

1. **固定队列长度**：严格控制队列大小为100
2. **并发控制**：精确控制最多30个并发采集程序
3. **自动监控**：队列监控线程自动分发任务
4. **状态管理**：自动更新数据库中的任务状态
5. **错误处理**：完整的异常处理和告警机制
6. **优雅关闭**：应用关闭时自动清理资源

## 下一步工作

1. 添加上述辅助方法到 SpAloneService
2. 在 `executeEtlTaskFromQueue` 方法中实现具体的采集逻辑
3. 根据不同的 etl_type 调用相应的采集程序
4. 测试和调优队列管理效果
