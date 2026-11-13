# Changelog

## 4.5.0

### Added

- 新增 MongoDB 采集源：源端适配器 SPI、确定性字段采样、手动字段、`mongodbreader` 作业生成与增量过滤。
- 新增 Elasticsearch 采集源：索引/别名、mapping 与文档采样、Query DSL 过滤、`_source` 与脚本限制。
- 新增告警中心：企业微信、钉钉、飞书、Webhook 与邮件（SMTP）通道，规则路由、投递重试、静默窗口与通道配置加密。
- 采集表详情新增补数 tab：按日期选择、批次进度、逐日状态与错误。
- 新增源级授权：admin / user / guest 三角色与 `user_source_grant` 的 OPERATE 授权，按采集源控制写操作与日志查看。
- 账号资料：姓名、邮箱、备注、创建时间、最后登录与改密时间。
- 经营总览首页，包含健康度、KPI、风险队列、采集量趋势、数据源贡献与规模价值。
- 通知中心入口、首次使用引导、空环境引导与演示环境横幅。
- 系统状态页：集群节点、队列与调度、运行概况、服务信息。
- 品牌外壳与客户白标配置：产品名、副标题、Logo、公司名、支持与文档链接。
- E2E 覆盖：MongoDB/Elasticsearch 表单、告警配置、经营总览、品牌外壳、通知中心、状态页与权限门禁。

### Changed

- `drivers/` 改为隔离类加载器加载（移除 `-Dloader.path`，新增 `-Daddax.drivers.dir`）；HDP/Cloudera standalone Hive JDBC jar 可直接放入，驱动损坏不再阻止应用启动。
- HiveServer2 连接、HDFS 前缀、`defaultFS`、存储/压缩格式下沉到目标端，系统配置不再维护 Hive/HDFS 全局项；目标端无法解析时不再回退 HDFS。
- 新建采集表的存储/压缩默认值来自所选目标端；目标端被删除或被引用时显式报错。
- 采集源身份以 `id` 为准：调度任务 id 使用 `source-<id>`，源级统计按 `sid` 分组，`etl_source.code` 保留为兼容字段。
- 日期变量在作业生成时冻结：`biz_*` 与 `curr_*` 都写入作业 JSON，`addax.sh` 不再注入日期变量；补数按目标日期重新生成。
- 集群页并入系统状态，`/cluster` 重定向到 `/status`。
- 默认主题改为浅色优先，深色保留为一等公民。
- 图表统一为单轴、状态色与分类色分离，并采用通过校验的色板；采集表状态由单字母改为「图标 + 文字 + 颜色」。
- 监控/洞察页失败可见，图表补齐 loading / empty / error 状态。
- 非管理员权限收窄：user 仅可操作被授权的采集源，guest 只读且不能查看日志和作业内容。

### Fixed

- 修复 `/dashboard/fail-top-tables` 查询缺少 `execution_id` 导致的 500。
- 修复 PostgreSQL `int4` 目标端 id 读取错误导致所有调度失败的问题。
- 修复横向柱状图把分类标签格式化成耗时的问题。
- 修复页脚固定定位遮挡页面内容、favicon 指向 `#`、路由标题不更新与重复 `/help` `/dict` 路由。
- 修复通知中心、告警通道按钮换行、代码面板主题与状态标签显示。
- 修复数据源贡献接口返回已格式化数值而非原始行的问题。

### 升级提示

- 首次启动自动创建 `alert_*`、`user_source_grant` 表，并补齐 `users`、`etl_source`、`etl_column`、`etl_execution` 的新列；无需手工迁移。
- 创建告警通道前应设置 `ALERT_SECRET_KEY`；`WECOM_ROBOT_KEY` 与 `alert.wechat.*` 不再读取。
- 现有非管理员账号保持只读，需要管理员在「账号管理 → 授权」中按采集源授予「可操作」。
- `guest` 账号将无法查看采集日志和作业内容。
