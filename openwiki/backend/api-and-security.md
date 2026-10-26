---
type: backend
title: REST API 与安全模型
description: 控制器 API 分组、JWT 认证流程、admin/user 权限规则、公开端点、CORS 与统一异常响应。
tags: [api, security, jwt, authorization, cors, exception-handling]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-3dd49f31d731544c452809f2
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/audit/AuditRequestFilter.java
  - id: openwiki-source-37c5a0c421093f4e26fe9c62
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/CustomAccessDeniedHandler.java
  - id: openwiki-source-b3c17f7f73889bdfb7e51293
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/CustomAuthenticationEntryPoint.java
  - id: openwiki-source-b5e891abafe2aa3501cd9e8d
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/JwtFilter.java
  - id: openwiki-source-b93ec68ea14da4d409f9d27a
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/config/SecurityConfiguration.java
  - id: openwiki-source-093b8179f5ec7cdf36e800e5
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/controller/AuthController.java
  - id: openwiki-source-3681b32180ebaeb93080e06e
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/exception/GlobalExceptionHandler.java
  - id: openwiki-source-1b379056405333f7d4d4c5b4
    resource: repo://backend/src/main/java/com/wgzhao/addax/admin/service/JwtService.java
  - id: openwiki-source-97ae9adf7f31e5151e176dc2
    resource: repo://backend/src/main/resources/application.properties
  - id: openwiki-source-77c413f182fc2eead5535edb
    resource: repo://frontend/src/router/index.ts
  - id: openwiki-source-5e730aa183f97f99de9c8e6c
    resource: repo://frontend/src/utils/requests.ts
  - id: openwiki-source-49f5868b9bc0b9372e54c787
    resource: repo://scripts/data.sql
  - id: openwiki-source-56ca36fad3cacc52c6c8f19a
    resource: repo://scripts/schema.sql
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# REST API 与安全模型

后端所有接口挂在 `/api/v1` 下。认证使用 JWT，授权基于 `admin` / `user` 两种 authority；写操作默认仅管理员可执行。审计记录与请求 ID 贯穿认证、授权和业务写操作。

## API 分组

| 前缀 | 控制器 | 主要职责 |
| --- | --- | --- |
| `/auth` | `AuthController` | 登录、修改当前账号密码 |
| `/users` | `UserController` | 账号列表、创建/修改/删除、启用/停用、当前用户 |
| `/sources` | `SourceController` | 采集源 CRUD、连接测试、库表发现 |
| `/targets` | `TargetController` | 目标端 CRUD、连接测试 |
| `/tables` | `TableController` | 采集定义 CRUD、字段/映射、目标契约、批量创建、执行历史、任务模板 |
| `/tasks` | `TaskController` | 队列状态、手动执行、批量执行、补数、取消、最近错误 |
| `/log` | `LogController` | Addax 日志、流水、日志清理、Addax 指标回报 |
| `/dashboard` | `IndexController` | 首页统计与图表数据 |
| `/monitor` | `MonitorController` | 完成率、特殊任务、风险、字段变化、数据洞察 |
| `/cluster` | `ClusterController` | 集群节点列表 |
| `/dicts` | `ParamController` | 字典与字典项、日历生成、交易日历刷新 |
| `/settings` | `SettingController` | 系统配置、Job 模板、Hive 连接测试、切日任务重注册 |
| `/system` | `SystemFlagController` | 切日刷新状态与手动触发 |
| `/alert` | `AlertController` | 告警记录 |
| `/user-notifications` | `UserNotificationController` | 站内通知 |
| `/audit-events` | `AuditController` | 安全审计查询与导出 |

端点定义分散在各控制器中，完整路径以 `@RequestMapping` + 方法注解为准。部分接口返回统一的 `ApiResponse<T>`（`code/message/data`），部分直接返回 DTO 或 `ResponseEntity`，前端按接口分别处理。

## 认证流程

### 登录

`POST /api/v1/auth/login` 是公开端点。`AuthController` 使用 Spring Security 的 `AuthenticationManager` 校验账号密码，数据来自 `users` / `authorities` 表（`JdbcUserDetailsManager`）：

1. 认证成功：读取 authorities，规范化为 `admin` 或 `user`，生成带 `authorities` claim 和 `jti` 的 JWT，返回 `ApiResponse.success(token)`；
2. 认证失败：返回 401 和“账号或密码不正确”，并写入 `AUTH_LOGIN_FAILED` 审计；
3. 内部异常：返回 500，不暴露异常细节。

实现见 `backend/src/main/java/com/wgzhao/addax/admin/controller/AuthController.java#L61-L107`。默认安装数据在 `scripts/data.sql` 中创建 `admin / admin123`，生产部署应立刻修改。

JWT 由 `JwtService` 生成（`backend/src/main/java/com/wgzhao/addax/admin/service/JwtService.java#L72-L112`）：

- HMAC-SHA 密钥来自 `jwt.secret`，生产通过 `JWT_SECRET` 环境变量覆盖；
- 有效期来自 `jwt.expiration`，默认 8 小时；
- `authorities` claim 保存角色列表，`jti` 用于审计关联。

### 请求认证

`JwtFilter` 从 `Authorization: Bearer <token>` 解析 token（`backend/src/main/java/com/wgzhao/addax/admin/config/JwtFilter.java#L37-L99`）：

- token 缺失：直接放行，由后续授权规则决定是否 401；
- token 过期：立即返回 401 JSON，并记录 `AUTH_TOKEN_REJECTED / TOKEN_EXPIRED` 审计；
- token 非法：记录审计后放行未认证请求，最终由 entry point 返回 401；
- token 有效：把用户名和 authorities 放入 `SecurityContext`，并把 `jti` 写入请求属性供审计使用。

### 修改密码

`POST /api/v1/auth/change-password` 需要登录。它先重新校验当前密码，再要求新密码至少 6 位，通过 `JdbcUserDetailsManager.updateUser` 写入 BCrypt 哈希，成功后清空 `SecurityContext`（`AuthController.java#L109-L180`）。

## 授权规则

`SecurityConfiguration` 的规则顺序决定了最终权限（`backend/src/main/java/com/wgzhao/addax/admin/config/SecurityConfiguration.java#L34-L70`）：

| 请求 | 规则 |
| --- | --- |
| `OPTIONS /**` | 放行（CORS 预检） |
| `POST /auth/login` | 放行 |
| `POST /auth/change-password` | 已认证即可 |
| `/addax/**` | 放行（Addax 回调/兼容接口） |
| `POST /log/job-report` | 放行（Addax 指标回报） |
| `GET /audit-events`、`GET /audit-events/**` | 仅 `admin` |
| 其他 `GET /**` | 已认证即可 |
| 其他 `POST` / `PUT` / `PATCH` / `DELETE` | 仅 `admin` |
| 其他请求 | 需认证 |

角色规范化在登录时完成：`ROLE_` 前缀会被剥离，未知角色统一归为 `user`（`AuthController.java#L169-L180`）。因此数据库中的 authority 实际只有 `admin` 与 `user` 两种有效值。

未认证和越权分别由 `CustomAuthenticationEntryPoint` 与 `CustomAccessDeniedHandler` 返回统一 JSON：

- 401：`{"code":401,"message":"Unauthorized","data":null}`；
- 403：`{"code":403,"message":"Forbidden","data":null}`。

两者都会在非 GET 请求或审计接口上写审计事件，GET 请求本身不产生高频噪音（`backend/src/main/java/com/wgzhao/addax/admin/config/CustomAuthenticationEntryPoint.java`、`CustomAccessDeniedHandler.java`）。

## CORS

`cors.allowed-origins` 默认 `*`，允许的方法为 `GET`、`POST`、`PUT`、`DELETE`、`OPTIONS`（`SecurityConfiguration.java#L91-L105`）。注意允许方法列表没有 `PATCH`，而 `PATCH /api/v1/tasks/queue` 存在。生产部署走同源 Nginx 反代不受影响；如果从其它源直接跨域调用该接口，浏览器预检可能失败。

## 统一异常响应

`GlobalExceptionHandler` 把异常映射为标准 `ApiResponse`（`backend/src/main/java/com/wgzhao/addax/admin/exception/GlobalExceptionHandler.java`）：

| 异常 | HTTP | code |
| --- | --- | --- |
| `ApiException(400/403/404/409)` | 对应 4xx | 原 code |
| `ApiException` 其他 code | 500 | 原 code |
| Bean Validation 失败 | 400 | 400，消息为字段错误拼接 |
| 请求体不可读 | 400 | 400，“请求体格式错误” |
| 未捕获异常 | 500 | 500，“Internal server error” |

兜底处理器只记录日志，不把堆栈或内部信息返回给客户端。

## 请求上下文与审计边界

`AuditRequestFilter` 是最高优先级的过滤器，为每个请求生成 `requestId`、解析客户端 IP，并把 `X-Request-Id` 写回响应（`backend/src/main/java/com/wgzhao/addax/admin/audit/AuditRequestFilter.java#L26-L65`）。`audit.trust-forwarded-headers=false` 时只信任 `request.getRemoteAddr()`，为 `true` 时才读取 `X-Forwarded-For` / `X-Real-IP`。

审计事件不是由这个过滤器统一写，而是由以下位置显式记录：

- `JwtFilter`：token 过期/非法/缺少 subject；
- `AuthController`：登录成功/失败、改密；
- `CustomAuthenticationEntryPoint` / `CustomAccessDeniedHandler`：未认证/越权；
- 各写操作控制器：账号、采集源、目标端、采集定义、配置、调度等。

审计数据的保留、查询、脱敏与告警见 [可观测性、审计与告警](../operations/observability-and-alerts.md)。

## 与前端的关系

前端 axios 封装自动带上 `Authorization`，并在非登录接口收到 401 时清空本地认证、提示“登录已过期”并跳转 `/login`（`frontend/src/utils/requests.ts#L52-L120`）。前端路由守卫只做本地 token 存在性与过期时间预检，真正的授权仍由后端完成（`frontend/src/router/index.ts#L60-L83`）。

相关页面：[系统架构总览](../architecture/system-overview.md)、[前端架构](../frontend/architecture.md)、[数据模型与持久化](persistence-model.md)。
