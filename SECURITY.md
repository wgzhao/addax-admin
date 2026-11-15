# 安全策略

## 支持范围

安全修复优先覆盖最新 Release。`master` 分支与历史版本不承诺安全更新，请升级到最新版本后再验证问题。

## 报告安全问题

请 **不要** 通过公开 Issue、Discussion 或 Pull Request 披露安全问题。

优先使用 GitHub 的私密漏洞报告：仓库 **Security → Report a vulnerability**。报告中请包含：

- 受影响的版本与部署模式（Docker / 主机模式）
- 复现步骤或 PoC，以及影响说明
- 相关配置、日志片段（请脱敏密码、Token、连接串）

我们会在确认后尽快回复并同步修复计划；修复发布后，如你同意会在 Release Notes 中致谢。

## 安全边界

Addax Admin 管理与调度 Addax 采集作业，部署时应遵循：

- 不要将管理端口直接暴露到公网，使用反向代理与 HTTPS。
- 首次登录后立即修改默认密码，按最小权限分配 admin / user / guest 角色。
- 数据库与 Redis 使用独立账号和网络隔离；生产环境启用 `ALERT_SECRET_KEY` 保护告警通道配置。
- 妥善保管 `.env`、后端 `config/` 与日志目录，避免提交到版本库。
