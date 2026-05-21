# Backend Code Review Fix Summary

> Date: 2026-05-20
> Branch: `fix/backend-security-and-quality-issues`
> Build: ✅ BUILD SUCCESS

## 修复文件清单（13 个文件）

| # | 严重等级 | 问题描述 | 涉及文件 | 修复方式 |
|---|---------|---------|---------|---------|
| 1 | 🔴 高 | JWT Secret 硬编码在源码中 | `JwtService.java`, `application.properties` | 通过 `@Value("${jwt.secret}")` 构造器注入，支持 `JWT_SECRET` 环境变量覆盖 |
| 2 | 🔴 高 | 过期 Token 被 JwtFilter 放行（安全漏洞） | `JwtFilter.java`, `JwtService.java` | 新增 `parseTokenClaims()` 抛出异常；Filter 改用 try-catch 捕获 `ExpiredJwtException` 返回 401 |
| 3 | 🔴 高 | 数据源密码（`pass` 字段）暴露于 API 响应 | `EtlSource.java`, `DbConnectDto.java`, `SourceController.java`, `EtlTargetService.java`, `AddSource.vue`, `source-service.ts` | 哨兵值方案：序列化始终返回 `**UNCHANGED**`；保存时若收到哨兵则从 DB 取原密码；`testConnect` 附带 `sourceId`，哨兵时从 DB 解析真实密码 |
| 4/5 | 🔴 高 | CORS 配置矛盾 + httpBasic/formLogin 未关闭 | `SecurityConfiguration.java` | `cors(withDefaults())` 替换 `cors(disable)`；`allowedOrigins` 改为可配置属性；disable httpBasic/formLogin |
| 6 | 🔴 高 | 全局异常处理器泄露原始错误信息（SQL/路径等） | `GlobalExceptionHandler.java` | 兜底 handler 返回通用 `"Internal server error"`，原始信息只写日志 |
| 7 | 🔴 高 | 登录失败返回 HTTP 200（应为 401） | `AuthController.java` | 改为 `ResponseEntity<>` 返回，失败时正确设置 HTTP 401 |
| 11 | 🟡 中 | `ObjectMapper` 在每次请求中重新实例化 | `CustomAuthenticationEntryPoint.java`, `CustomAccessDeniedHandler.java` | 添加 `@Component`，注入 Spring 托管的共享 `ObjectMapper` bean |
| 14 | 🟡 中 | 缺少 400 参数校验异常处理，导致返回 500 | `GlobalExceptionHandler.java` | 添加 `MethodArgumentNotValidException` 和 `HttpMessageNotReadableException` 处理器 |
| 15 | 🟡 中 | `AuthRequestDTO` 无参数校验注解 | `AuthRequestDTO.java`, `AuthController.java` | 添加 `@NotBlank`；Controller 方法加 `@Valid` |
| 17 | 🟡 中 | `UserAdminService.listUsers()` N+1 查询（2N+1 次 DB 调用） | `UserAdminService.java` | 改为单次 `LEFT JOIN` 查询，使用 `ResultSetExtractor` 聚合结果 |
| 21 | 🟡 中 | `restartQueueMonitor()` 每次调用都累积新调度任务 | `TaskQueueManagerV2Impl.java` | 维护 `listenFuture`/`pollFuture`/`recoverFuture` 字段，restart 前先 cancel |
| 23 | 🟡 中 | `URLClassLoader` 在 Hive 重连时泄漏（Metaspace OOM 风险） | `TargetServiceWithHiveImpl.java` | 保存 classloader 到字段，re-init 时关闭旧实例，`@PreDestroy` 时释放 |

## 未修复项（中低优先级）

| # | 问题 | 建议 |
|---|------|------|
| 8 | Controller 直接注入 Repository（AlertController、TableController、MonitorController） | 添加 Service 层封装 |
| 9 | JPA 实体直接作为 API 请求/响应体（EtlTable、EtlTarget） | 创建对应 DTO |
| 10 | `@Data @Getter @Setter` 冗余 Lombok 注解 | 保留 `@Data` 即可 |
| 12 | `LogController` 过滤参数（q、status、sortField、sortOrder）声明但未使用 | 实现过滤逻辑 |
| 13 | `TableController.listTableViews()` 返回空列表（死代码） | 删除或实现 |
| 16 | 硬编码 `ZoneOffset.ofHours(8)` | 改为读取系统时区或配置 |
| 18 | `pageSize=-1` 时使用 `Integer.MAX_VALUE`（OOM 风险） | 设置合理上限（如 10000） |
| 19 | `SourceController` 使用 `DriverManager.getConnection()` 无连接池/超时 | 加超时限制或连接池 |
| 20 | 租约续期回调每次都查数据库 `getTable()` | 缓存结果，减少 DB 访问 |
| 22 | `listenLoop` 异常后无自动重启机制 | 添加异常捕获 + 延迟重启逻辑 |
| 24 | `Constants.SQL_RESERVED_KEYWORDS` 为 `volatile Set`，写操作非原子 | 改为 `CopyOnWriteArraySet` 或加锁 |
| 25 | `CollectionScheduler` lambda 捕获 `EtlSource` 快照，数据可能过时 | 改为每次执行时重新查询 |

---

# Master-Worker 任务分配架构迁移

> Date: 2026-05-21
> Branch: `feat/master-worker-task-dispatch`
> Build: ✅ BUILD SUCCESS

## 背景

原系统采用"抢夺式"任务分配：定时器触发后，所有节点通过 `FOR UPDATE SKIP LOCKED` + Redis 分布式锁竞争任务。已引入 Redis 选举但未利用。

## 目标

引入 master-worker 分配模式：Redis 选举出唯一 master，由 master 统一分配任务给 worker，worker 通过 Redis pub/sub 接收。

## 新增文件

| 文件 | 说明 |
|------|------|
| `redis/MasterElectionService.java` | Redis NX 选举；TTL=30s，每 10s 续约；`onBecameMaster`/`onLostMaster` 回调；`@PreDestroy` 主动释放锁加速 failover |
| `redis/WorkerHeartbeatService.java` | 每 15s 上报 `availableSlots/sourceRunning` 到 `addax:worker:{instanceId}`（TTL=45s）；master 通过 SCAN 读取存活 worker |

## 修改文件

| 文件 | 改动要点 |
|------|---------|
| `service/EtlJobQueueService.java` | 新增 `assignToWorker(targetInstanceId, leaseSeconds)`：master 专用，指定 `claimed_by` 入队 |
| `service/impl/TaskQueueManagerV2Impl.java` | 核心重构：删除三个 permit token map 及全部 Redis 信号量逻辑；新增 `masterDispatch()`（仅 master 执行）；实现 `MessageListener` 订阅 `addax:task:assign:{instanceId}` 频道接收任务 |
| `scheduler/CollectionScheduler.java` | 删除 `collection:source:{code}:lock` Redis 锁；改为仅 master 注册 cron，`onBecameMaster` 时 `rescheduleAllTasks()`，`onLostMaster` 时 `cancelAllScheduledTasks()` |
| `scheduler/SchemaRefreshScheduler.java` | 删除 Redis 分布式锁 + renewer 线程；改为仅 master 注册 cron，failover 后新 master 重新注册；`reschedule()` 加 `isMaster()` 守卫 |

## 关键设计决策

- **无降级兜底**：master failover 失败则系统停摆，等待人工介入
- **Kill 指令**：沿用原有 `etl:kill` pub/sub，`ExecutionManager` 无需修改
- **Worker 容量**：worker 自报 slot（含 `weight` 配置），master 不推断，降低 master 复杂度
- **入队幂等性**：DB 唯一约束防重复，`CollectionScheduler` 不再需要分布式锁

| # | 严重等级 | 问题描述 | 涉及文件 | 修复方式 |
|---|---------|---------|---------|---------|
| 1 | 🔴 高 | JWT Secret 硬编码在源码中 | `JwtService.java`, `application.properties` | 通过 `@Value("${jwt.secret}")` 构造器注入，支持 `JWT_SECRET` 环境变量覆盖 |
| 2 | 🔴 高 | 过期 Token 被 JwtFilter 放行（安全漏洞） | `JwtFilter.java`, `JwtService.java` | 新增 `parseTokenClaims()` 抛出异常；Filter 改用 try-catch 捕获 `ExpiredJwtException` 返回 401 |
| 3 | 🔴 高 | 数据源密码（`pass` 字段）暴露于 API 响应 | `EtlSource.java`, `DbConnectDto.java`, `SourceController.java`, `EtlTargetService.java`, `AddSource.vue`, `source-service.ts` | 哨兵值方案：序列化始终返回 `**UNCHANGED**`；保存时若收到哨兵则从 DB 取原密码；`testConnect` 附带 `sourceId`，哨兵时从 DB 解析真实密码 |
| 4/5 | 🔴 高 | CORS 配置矛盾 + httpBasic/formLogin 未关闭 | `SecurityConfiguration.java` | `cors(withDefaults())` 替换 `cors(disable)`；`allowedOrigins` 改为可配置属性；disable httpBasic/formLogin |
| 6 | 🔴 高 | 全局异常处理器泄露原始错误信息（SQL/路径等） | `GlobalExceptionHandler.java` | 兜底 handler 返回通用 `"Internal server error"`，原始信息只写日志 |
| 7 | 🔴 高 | 登录失败返回 HTTP 200（应为 401） | `AuthController.java` | 改为 `ResponseEntity<>` 返回，失败时正确设置 HTTP 401 |
| 11 | 🟡 中 | `ObjectMapper` 在每次请求中重新实例化 | `CustomAuthenticationEntryPoint.java`, `CustomAccessDeniedHandler.java` | 添加 `@Component`，注入 Spring 托管的共享 `ObjectMapper` bean |
| 14 | 🟡 中 | 缺少 400 参数校验异常处理，导致返回 500 | `GlobalExceptionHandler.java` | 添加 `MethodArgumentNotValidException` 和 `HttpMessageNotReadableException` 处理器 |
| 15 | 🟡 中 | `AuthRequestDTO` 无参数校验注解 | `AuthRequestDTO.java`, `AuthController.java` | 添加 `@NotBlank`；Controller 方法加 `@Valid` |
| 17 | 🟡 中 | `UserAdminService.listUsers()` N+1 查询（2N+1 次 DB 调用） | `UserAdminService.java` | 改为单次 `LEFT JOIN` 查询，使用 `ResultSetExtractor` 聚合结果 |
| 21 | 🟡 中 | `restartQueueMonitor()` 每次调用都累积新调度任务 | `TaskQueueManagerV2Impl.java` | 维护 `listenFuture`/`pollFuture`/`recoverFuture` 字段，restart 前先 cancel |
| 23 | 🟡 中 | `URLClassLoader` 在 Hive 重连时泄漏（Metaspace OOM 风险） | `TargetServiceWithHiveImpl.java` | 保存 classloader 到字段，re-init 时关闭旧实例，`@PreDestroy` 时释放 |

## 未修复项（中低优先级）

| # | 问题 | 建议 |
|---|------|------|
| 8 | Controller 直接注入 Repository（AlertController、TableController、MonitorController） | 添加 Service 层封装 |
| 9 | JPA 实体直接作为 API 请求/响应体（EtlTable、EtlTarget） | 创建对应 DTO |
| 10 | `@Data @Getter @Setter` 冗余 Lombok 注解 | 保留 `@Data` 即可 |
| 12 | `LogController` 过滤参数（q、status、sortField、sortOrder）声明但未使用 | 实现过滤逻辑 |
| 13 | `TableController.listTableViews()` 返回空列表（死代码） | 删除或实现 |
| 16 | 硬编码 `ZoneOffset.ofHours(8)` | 改为读取系统时区或配置 |
| 18 | `pageSize=-1` 时使用 `Integer.MAX_VALUE`（OOM 风险） | 设置合理上限（如 10000） |
| 19 | `SourceController` 使用 `DriverManager.getConnection()` 无连接池/超时 | 加超时限制或连接池 |
| 20 | 租约续期回调每次都查数据库 `getTable()` | 缓存结果，减少 DB 访问 |
| 22 | `listenLoop` 异常后无自动重启机制 | 添加异常捕获 + 延迟重启逻辑 |
| 24 | `Constants.SQL_RESERVED_KEYWORDS` 为 `volatile Set`，写操作非原子 | 改为 `CopyOnWriteArraySet` 或加锁 |
| 25 | `CollectionScheduler` lambda 捕获 `EtlSource` 快照，数据可能过时 | 改为每次执行时重新查询 |

---

# 调度审计 Bug 修复

> Date: 2026-05-21
> Branch: `feat/master-worker-task-dispatch`
> Commits: `577eb32`, `a1b9d63`, `012ae1d`
> Build: ✅ BUILD SUCCESS

## 背景

master-worker 架构迁移完成后，对全部调度相关代码进行综合 review，发现并修复以下 bug。

## Bug 修复清单

| # | 严重等级 | 问题描述 | 涉及文件 | 修复方式 |
|---|---------|---------|---------|---------|
| 1 | 🔴 高 | `getAllTaskStatus()` SQL 返回全部表（包含 Y/X 状态），无任务运行时前端显示 1260 条运行中 | `TaskService.java` | `WHERE rn=1 OR rn IS NULL` 改为 `WHERE t.status IN ('R','W') AND (rn=1 OR rn IS NULL)`；LEFT JOIN 使非 R/W 表 rn=NULL，外层缺少状态过滤导致全表被选出 |
| 2 | 🔴 高 | `CollectionScheduler.cancelAllScheduledTasks()` 定时任务泄漏 | `CollectionScheduler.java` | 替换为 `taskSchedulerService.cancelAll()`；原实现只取 `findByEnabled(true)` 遍历，master 持有期间被禁用的 source 其定时任务不会被取消 |
| 3 | 🟡 中 | `TableOverrideScheduler` 仍用 `@Scheduled` 全节点跑 + Redis 锁去重，与 master-only 架构不一致 | `TableOverrideScheduler.java` | 移除 `@Scheduled` 和 `RedisLockService`；改为通过 `MasterElectionService` 回调注册/注销，与 `CollectionScheduler` 和 `SchemaRefreshScheduler` 保持一致 |
| 4 | 🟡 中 | `TableOverrideScheduler` 使用 `@AllArgsConstructor` 导致 `volatile ScheduledFuture<?>` 字段被纳入构造器，Spring 找不到 Bean 启动失败 | `TableOverrideScheduler.java` | 改为 `@RequiredArgsConstructor`，仅注入 `final` 字段 |
| 5 | 🟡 中 | `WorkerHeartbeatService.getAliveWorkers()` 使用 `redisTemplate.keys()` O(N) 阻塞 | `WorkerHeartbeatService.java` | 改为 SCAN cursor（`ScanOptions.scanOptions().match(...).count(100)`），避免生产环境 Redis 阻塞 |
| 6 | 🟡 中 | `TaskQueueManagerV2Impl` 遗留死字段 `redisLockService` + 死 import `SCHEMA_REFRESH_LOCK_KEY` | `TaskQueueManagerV2Impl.java` | 删除字段和 import |

## 根本原因说明（Bug #1）

`getAllTaskStatus()` SQL 问题是两次修改叠加导致的：

1. 原始 `WHERE rn = 1`：正确排除无统计记录的表，但也漏掉了首次运行的 R/W 表（因 LEFT JOIN 得 rn=NULL）
2. 上一轮修复改为 `WHERE rn = 1 OR rn IS NULL`：修复了首次运行场景，但忽略了 LEFT JOIN 条件 `and t.status in ('R','W')` 会让非 R/W 表 rn=NULL，导致全表都被返回

正确做法：外层加 `t.status IN ('R','W')` 过滤，内层保留 `OR rn IS NULL` 覆盖首次运行场景。
