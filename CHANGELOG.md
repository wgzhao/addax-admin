# Changelog

## 4.7.0

### Added

- 主机部署发行包（ADR-0014）：每个 tag 同时发布 `addax-admin-<version>-host.tar.gz` 与 `.sha256`，内含预编译后端 jar、前端静态文件、`deploy/install.sh`、Nginx 与 systemd 示例，以及 `BUILD-INFO`（版本、commit、构建时间）。重复执行安装脚本即为升级，保留 `config/`、`drivers/`、`job/`、`logs/`，旧 jar 备份为 `.before-<时间戳>`。
- 顶级导航按采集生命周期重组（PR #135）：采集配置（采集源/目标端/采集表/类型映射/采集日历）、采集运行（任务/运行监控/错误中心/系统状态/日志/告警配置）、数据治理（洞察/血缘）与管理员可见的系统管理；两个全库「更新表信息」动作移入采集表管理页的「结构更新」菜单，帮助中心加入账号菜单。
- 命令面板改为从导航树生成（PR #136），与菜单的分组、命名和角色过滤保持一致。

### Changed

- `spring.jpa.show-sql` 默认改为关闭，本地开发使用 `SPRING_JPA_SHOW_SQL=true` 覆盖；发行包升级不会覆盖已有的 `config/application.properties`，新模板写到同目录 `.new` 文件。
- 采集源列表改为固定像素列宽，连接串是唯一弹性列；1280 及更窄视口横向滚动并把操作列固定在右端。
- 停采表（状态 `X`）的手工执行与补数只记录起止时间、耗用与结果，不再改写为 `R`/`Y`/`E`，也不消耗自动重试预算；文档明确停采表保持停采直到手工恢复。
- 移除 `etl_table.kind`：该字段从未参与调度，所有表都按采集源日历自动采集，「只手工采集」一直由停采状态 `X` 表达。启动迁移删除该列并重建 `vw_etl_table_with_source`，采集表接口不再返回该字段，新执行快照 `snapshotVersion` 为 3。
- PostgreSQL JDBC 驱动升级到 42.7.12（GHSA-j92g-9f8w-j867 / CVE-2026-54291），minimatch 固定到 11.0.1（GHSA-g3hv-75wg-m249）。
- 删除未使用的根目录 `package-lock.json`、`bun.lock`，`frontend/yarn.lock` 为唯一锁文件。
- Docker 镜像只在推送 tag 时构建，发布 GitHub Release 不再重复触发。

### Fixed

- 表格详情深链 `?tab=logs`、`?tab=job`、`?tab=fillback` 在详情接口返回前被重置到 `info`，导致日志、作业与补数标签无法通过链接直达。
- 采集状态矩阵与过期单测仍断言旧的状态码文案和通知 store 结构，已与当前界面和 store 对齐。

### 升级提示

- **破坏性变更**：启动迁移删除 `etl_table.kind`，旧版本节点仍映射该列，必须停止所有节点后统一升级，不支持滚动升级；回退只能恢复升级前备份。
- 历史上被手工执行悄悄解除停采的表无法自动识别，升级后需要人工复查停采名单。
- 主机部署推荐使用发行包：`deploy/install.sh` 幂等，重复执行即升级；如需回滚，把 `lib/addax-admin-*.jar.before-<时间戳>` 改回原名并重启（若已执行数据库迁移，需同时恢复备份）。
- `show-sql` 默认值变化只影响新安装；已有安装保留原 `config/application.properties`。
- Docker 与发行包由同一个 tag 构建；发布 Release 前确认 tag 已推送。

## 4.6.0

### Added

- 采集对账（ADR-0010）：新增 `etl_reconcile` 事实表，按「写入 = 读取 - 错误行数」判定 PASS/FAIL，差异记入 `RECONCILE` 失败事实并触发告警；表详情新增「对账」tab，支持按业务日期手动补对账（`POST /reconciles/run`）。
- 字段级血缘搜索：`GET /lineage/columns` 基于 `etl_column_mapping` 跨采集表搜索字段来源与去向，新增「数据血缘」页面，零迁移。
- ODS 主动数据质量：以近 7 天非零采集量为基线，0 行判空采、不足基线 5% 判漏采，命中记 `DATA_QUALITY` 失败事实。
- 采集完成信号外发：`COLLECTION_COMPLETE_WEBHOOK_URL` 启用后，job-report 到达时向下游 POST `collection_complete` 事件，复用告警的 SSRF 防护，失败只记日志。
- 持久化失败事实：新增 `etl_failure`（阶段、分类、指纹、摘要/详情、执行与日志关联），`FailureRecorder` 覆盖采集、schema、分区、作业模板、目标契约、准备与队列失败；状态列失败抽屉、表详情「失败记录」tab、全局错误中心（筛选、指纹聚合、CSV 导出、管理员清理），并一次性回填近 365 天 `etl_jour` 失败。
- 目标端健康校验：`POST /targets/health-check` 系列接口按项执行 Addax 目录、hdfswriter 插件、wH 模板、HiveServer2 与真实写入探针；HDFS 目标端弹窗内以渐进式清单逐项展示，编辑配置即重置。
- Addax 程序目录校验：`POST /settings/addax-validate` 检查目录、`bin/addax.sh` 与 `conf/core.json` 上报地址，并跑一次 stream→stream 冒烟作业；系统设置新增「校验」按钮。
- 采集日历与类型映射专用管理页（ADR-0011 阶段 1）：覆盖情况、按年日期维护、批量生成、年度交易日刷新与源类型→ODS 类型映射 CRUD。
- 目标方言由 JDBC URL 推导：新增 `POST /targets/detect-dialect`，目标端只选 HDFS/RDBMS 族，方言只读展示。
- 作业模板占位符契约：五个模板由 Java 常量播种，保存时校验占位符集合与 JSON 合法性，前端锁定占位符 chips。
- 仓库品牌刷新：README 重写、社交预览图与生成脚本、六边形 Logo，新增 CONTRIBUTING、SECURITY、issue forms 与 PR 模板。
- E2E：新增对账、血缘、数据质量、完成信号、Addax 目录校验、空库初始化等脚本。

### Changed

- 后端升级到 Spring Boot 4.1.2 / Hibernate 7.4 / Jackson 3 / springdoc 3.2；PostgreSQL 与 MongoDB 驱动按要求保持旧版。
- 前端依赖升级到 Vue 3.5.45 / Vuetify 4.3.2 / Vite 8.3.8 / Pinia 4.1 / ESLint 10 / Prettier 3，移除未使用依赖；TypeScript 受 typescript-eslint 上限约束留在 5.9。
- 移除 `etl_source.code`：启动迁移删除该列与依赖视图，采集源按 `id` 与名称标识，`/sources/check-code` 一并删除。
- 退役通用字典维护：移除 `/dicts`、`/param` 接口与前端字典编辑页，改为采集日历、类型映射与系统配置；启动迁移清理废弃字典项。
- 数据库初始化改由应用负责（ADR-0013）：镜像内置 `schema.sql` / `data.sql`，空库首次启动自动建表，docker-compose 不再挂载 initdb 脚本。
- 失败详情抽屉改为视口自适应宽度（85vw，上限 920px）并固定头尾。
- 顶栏搜索、主题、通知与账号移入右侧工具区；账号管理移入系统管理菜单，页脚精简。
- 新增 `SMOKE_TEST_TID`（-1）与健康检查保留 tid（-2），job-report 接受保留负数 tid 并跳过对账/质量/信号副作用。

### Fixed

- 采集状态图标只读最新 `etl_jour` 错误且失败路径无记录的问题（改由失败事实承载）。
- Docker 镜像与宿主机初始化脚本版本错配导致的启动失败 `column s.code does not exist`（issue #125）。
- 失败抽屉固定 560px 导致内容与操作按钮超出视口。
- 顶栏账号按钮样式与导航项不一致、工具区全部挤在左侧。
- Hive `TEXTFILE` 存储格式未映射为 addax 的 `TEXT` writer 格式，textfile 目标端运行期才失败。

### 升级提示

- **破坏性变更**：`etl_source.code` 由启动迁移删除，旧节点仍会查询该列，必须停止所有节点后再升级，不支持滚动升级；回退只能恢复升级前备份。
- 首次启动自动创建 `etl_failure`、`etl_reconcile`、`app_migration`，并回填近 365 天失败事实、清理废弃字典项（日志可见 `Purged N deprecated sys_item ...`）。
- 目标端 `target_type` 现存族名（HDFS/RDBMS），历史 `MYSQL`/`POSTGRESQL`/`SQLSERVER` 仍兼容。
- 作业模板只允许修改非占位符部分，占位符名不可变更。
- 采集完成信号默认关闭，需要时设置 `COLLECTION_COMPLETE_WEBHOOK_URL`（内网地址需同时设置 `COLLECTION_COMPLETE_WEBHOOK_ALLOW_PRIVATE_NETWORK`）。
- Docker 部署请使用与本版本一致的 `docker-compose.yml`，旧 compose 的 initdb 挂载不再使用。

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
