# ADR-0012: 目标端「族 + 方言」建模与作业模板占位符契约

- 状态：Accepted
- 日期：2026-11-14

## 背景

退役「字典维护」（ADR-0011）后，作业模板与目标端暴露出两个建模问题。

**问题一：目标端类型枚举了具体方言，但方言其实由 JDBC URL 决定。**

「目标端管理」的下拉框把 `HDFS / MYSQL / POSTGRESQL / SQLSERVER` 并列。但真正需要具体方言的地方——writer
插件名（`mysqlwriter` / `postgresqlwriter` / `sqlserverwriter`）、DDL 方言、writeMode 映射、插件存在性校验——
全部由 `DbUtil.getDbType(connectConfig.url)` 从 JDBC URL 推断，没有一处真正消费 `target_type` 里的具体方言值。
`target_type` 只被当作路由兜底和列表里的展示 chip。

这带来两个后果：

1. **冗余**：用户必须在「MYSQL/POSTGRESQL/SQLSERVER」里手工选一次，系统却忽略它、改从 URL 推断。
2. **双真相源**：用户可以在下拉选 `MYSQL`，却贴一个 `jdbc:postgresql://` 的 URL，系统不校验两者一致，
   静默按 URL 当 PostgreSQL 处理，埋下隐蔽的一致性坑。

**问题二：模板占位符是生成契约，但没有任何保护。**

作业模板里的 `${name}`、`${column}`、`${jdbcUrl}` 等占位符，是模板编辑器与作业生成器
（`StringSubstitutor`）之间的硬编码契约。渲染时 `RdbmsSourceAdapter` / `RdbmsTargetAdapter` /
`HdfsTargetAdapter` / `JobContentService` 只认这组固定 key。管理员在「系统配置」里一旦改名/删除/新增占位符，
生成的 job 里会残留一个未被替换的 `${xxx}` 字面量，addax 拿到后要么解析失败要么把字面量当值写入，任务静默跑挂。

同时，模板命名存在误导：`R2H`（采集主模板）与 `R2R`（关系型主模板）结构完全相同，只差服务对象，但
「采集主模板」这个名字读起来像"唯一的那个主模板"。

## 决策

### 1. 目标端建模为「族 + 方言」，各只有一个真相源

- **族**（HDFS / RDBMS）← 由 `writer_template_key`（`wH` / `wR`）决定，这是路由已经采用的主信号
  （`TargetTypeResolver` 优先看 writer key）。`target_type` 降级为粗粒度族标签，不再枚举具体方言。
- **方言**（MySQL / PostgreSQL / SQL Server / …）← 由 JDBC URL 推断（`DbUtil.getDbType`），**只读展示**，
  不让用户选择。
- 新增 `POST /targets/detect-dialect`，返回 `{ family, dialect, writerPlugin, supported, supportedDialects }`，
  供前端在 URL 失焦时只读展示识别结果；保存时的硬校验仍由 `RdbmsTargetAdapter.validateForSave` 负责
  （不支持的方言显式报错，而不是运行期静默失败）。
- 下拉框收缩为 `['HDFS', 'RDBMS']` 两个族；`Constants.RDBMS_TARGET_TYPES` 保留 `MYSQL`/`POSTGRESQL`/
  `SQLSERVER` 等历史值，兼容存量 `etl_target` 行与直接写库的脚本，不改数据。
- 未来要支持 Oracle / DB2 / ClickHouse，只需在 `RdbmsWriterPlugins.PLUGIN_BY_DIALECT` 加一行 + 补 DDL 分支，
  UI 无需改动——消掉"把主流 RDBMS 都列出来"的维护负担。

### 2. 模板命名带方向

- `R2H` 标签「采集主模板」→「RDBMS→HDFS 主模板」。
- `R2R` 标签「关系型主模板」→「RDBMS→RDBMS 主模板」。
- 两个主模板**保留分开、不合并**：它们结构相同但服务两条落库路径，未来 HDFS 走大吞吐、
  RDBMS 走事务型小批量的 `setting.speed` 可以各自调，合并会丢掉这个旋钮。

### 3. 模板占位符契约守卫

- **规则**：编辑后模板里的 `${...}` 占位符名集合，必须与内置默认模板的占位符名集合**完全相等**。
  改名 = 缺一个 + 多一个，双重拦截；删必需占位符 = 缺；新增未知占位符 = 多（未知 key 不会被替换，
  残留成字面量）。只比较占位符名，改 JSON 里的 batchSize、加参数、重排格式都放行。
- **落点**：后端 `PUT /settings/job-templates`（`DictService.updateJobTemplates`）做权威校验，
  前端只是镜像 UX——绕开 UI 直接 curl 也拦得住。
- **单一真相源**：契约从内置默认模板推导（默认模板里出现的 `${}` 名字就是契约），不另起硬编码清单，
  永不漂移。为此把 `rR` / `wH` / `R2H` 的默认值从 `scripts/data.sql` 上移到 Java 常量（`wR`/`R2R` 已是
  Java 常量），与 `ensureRdbmsTemplates()` 的"Java 常量是唯一真相源"意图一致。
- **防漂移单测**：断言每个默认模板的 token 集合 == 其契约集合，防止有人改了默认模板却没同步契约。
- **边界**：严格模式暂不允许在模板里新增 `${biz_*}` / `${curr_*}` 日期变量（渲染层其实会替换它们，但今日
  无此用法）；将来要放开，白名单加一行 + 改测试即可。未知模板 key 也一并拒绝，避免绕过。

## 影响

- **API**：新增 `POST /targets/detect-dialect`；`PUT /settings/job-templates` 增加占位符契约校验，
  非法占位符返回 400。
- **数据零迁移**：`target_type` 存量值（MYSQL/POSTGRESQL/SQLSERVER）继续合法；新建目标端写 `HDFS`/`RDBMS`。
  模板默认值上移到 Java 常量后，`scripts/data.sql` 里的重复种子不再作为唯一来源（保留或清理按实现定）。
- **权限**：目标端、系统配置仍为 `admin`。
- **回滚**：三个阶段各自独立 PR，可单独回滚。

## 分阶段迁移

- **Phase A（目标端族+方言）**：下拉收缩为 `HDFS`/`RDBMS`；新增 `detect-dialect` 端点与只读方言展示；
  更新 `target-dialog` e2e。
- **Phase B（模板命名+文档）**：`R2H`/`R2R` 标签带方向；更新 `docs/manual/install/03-config.md`。
- **Phase C（占位符契约守卫）**：模板默认值统一到 Java 常量；`updateJobTemplates` 增加占位符校验；
  前端镜像 UX；防漂移单测 + 保存时 JSON 结构校验；更新 e2e 与文档。
