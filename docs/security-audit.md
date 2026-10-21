# 安全审计

安全审计记录人员身份、权限边界、操作结果和必要上下文。它不同于采集定义变更日志和执行快照：

- `audit_event` 记录登录、越权拒绝、账号管理、配置修改和调度操作。
- `etl_table_change_log` 记录采集定义的字段级业务变更。
- `etl_execution` 保存实际运行的不可变配置快照。

## 数据约束

`audit_event` 只允许插入。数据库触发器拒绝常规 `UPDATE` 和 `DELETE`；保留任务在事务内设置
`addax.audit_purge=on` 后才能删除过期事件。

审计写入前会递归脱敏密码、Token、私钥、访问密钥及 URL 中的用户信息。连接配置和 Job 模板中的
JSON 字符串会先解析再脱敏。不要绕过 `AuditRecorder` 直接写入原始请求体。

## 管理员接口

- `GET /api/v1/audit-events`：分页查询，默认最近 7 天，单次查询范围最多 366 天。
- `GET /api/v1/audit-events/{id}`：查看事件详情。
- `GET /api/v1/audit-events/export.csv`：最多导出 10,000 条事件。

以上接口仅管理员可访问。查询和导出行为本身也会写入审计。

## 运维配置

以下配置可通过环境变量或启动参数覆盖：

| 配置 | 默认值 | 说明 |
|---|---:|---|
| `audit.trust-forwarded-headers` | `false` | 是否信任代理传入的 `X-Forwarded-For` / `X-Real-IP` |
| `audit.retention-days` | `365` | 审计事件在线保留天数，低于 30 天时拒绝清理 |
| `audit.retention-cron` | `0 30 2 * * *` | 每日清理任务 |
| `audit.alerts.failed-login-threshold` | `5` | 触发登录失败告警的阈值 |
| `audit.alerts.failed-login-window-minutes` | `10` | 登录失败统计窗口 |
| `audit.alerts.interval-ms` | `300000` | 异常行为扫描周期 |

告警复用企业微信机器人配置 `WECOM_ROBOT_KEY`。未配置时，告警内容只写入应用日志。
