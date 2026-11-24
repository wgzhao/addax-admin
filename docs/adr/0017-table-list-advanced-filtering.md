# ADR-0017: 采集表列表的高级筛选与目标端可见性

> 局部修订：ADR-0018（2026-11-24）移除了目标族 / 目标类型 / 源库类型 / 表注释控件，并新增耗时区间筛选。

- 状态：Accepted
- 日期：2026-11-23
- 关联：ADR-0004（采集身份与目标端范围）、ADR-0007（源 code 退役）、ADR-0008（源级授权只约束写）、ADR-0012（目标端族/方言建模）、ADR-0015（Doris 目标端建模）

## 背景

采集表（`etl_table`）的信息维度相比最初显著增加：目标端从单一 HDFS 扩展到 HDFS / RDBMS / Doris 多个族与多个可配置端点，
表本身也有写入模式、结构管理模式、表注释、三类时间戳、增量过滤表达式等属性。但列表页的能力没有跟上：

1. **目标端不可见。** 列表的「目标库表」列只显示 `target_db.target_table`，用户必须进入详情页才知道这张表最终落到哪个目标端。
   目标端种类增加后，这成为最常见的信息缺口。
2. **可筛维度极少。** `GET /api/v1/tables` 只接受 `q / statuses / sourceId / scope / sortField / sortOrder`。
   其余维度只能靠一个关键字近似表达，而 `q` 实际只匹配 `filterColumn`（id、源库表、目标库表、HDFS partition、源名称），
   连目标端名称和表注释都搜不到。
3. **数据其实已经在位。** 列表视图 `vw_etl_table_with_source` 已经 join 了 `etl_target`，冗余了
   `target_id / target_type / target_writer_template_key / target_code / target_name / target_enabled / target_db`。
   筛选目标端不需要新增 join，只是接口没有暴露。
4. **没有 `owner / tags / category` 列。** 「按负责人/标签筛选」在现有模型下无从实现，本 ADR 不做。

同时发现两个既有缺陷，与本次改动强相关，一并收敛：

- 排序无白名单：`QueryUtil.generateSort` 直接 `Sort.by(sortField)`，任意实体属性名都可排序。
- 无默认排序：`Sort.unsorted()` 导致分页结果不稳定。

## 决策

### 1. 目标是「可见 + 可筛」，不是引入查询语言

满足的是"按已知维度缩小列表"，不是"任意条件组合取数"。因此采用**固定字段集 + 固定算子 + AND 组合**，
不引入 JQL 式自由查询、不引入开放式过滤器 DSL、不做 OR 分组（P3 再议）。

### 2. 接口保持 `GET /api/v1/tables`，纯增量扩展类型化参数

不新增端点、不引入 `filters=[{field,op,values}]` 形式的 JSON 参数。理由：

- **URL 必须可读、可分享。** 该页已有基于 query 的深链接（错误中心/dashboard 下钻 `?q=`、E2E 直接访问
  `/table?q=&scope=`），JSON 参数会破坏这一点。
- **字段集是产品定义的封闭集合**，类型化参数天然可校验、可用索引、无注入面，与错误中心/表结构变更的既有做法一致。
- 多值用逗号分隔（Spring `StringToCollectionConverter` 原生支持），旧参数 `sourceId` 保留为 `sourceIds` 的单值别名，
  现有 E2E 与下钻链接不受影响。

新增参数（P1）：

| 维度 | 参数 | 语义 |
| --- | --- | --- |
| 目标端 | `targetTypes` | `target_type IN (...)`，族由前端展开为类型集 |
| 目标端 | `targetIds` | `target_id IN (...)` |
| 目标端 | `targetDbContains` | `target_db LIKE %...%` |
| 目标端 | `targetEnabled` | `target_enabled = ?` |
| 采集源 | `sourceIds` | `sid IN (...)`（替代单值 `sourceId`，保留别名） |
| 采集源 | `dbTypes` | `db_type IN (...)` |
| 采集源 | `sourceEnabled` | `enabled = ?` |
| 采集行为 | `writeModes` | `write_mode IN (...)` |
| 采集行为 | `schemaModes` | `schema_management_mode IN (...)` |
| 采集行为 | `tblCommentContains` | `tbl_comment LIKE %...%` |
| 时间 | `endTimeFrom/To`、`createdFrom/To`、`updatedFrom/To` | 闭区间 |
| 关键字 | `q` | 视图 `filterColumn`（扩展，见第 4 条） |

P2 追加：`incremental`（`filter LIKE '%__max__%'`）、`startAtOverride`（`start_at` 是否为空）、保存筛选视图。
P3 追加：`hasFailure` / `reconcileFailed`（对 `etl_failure` / `etl_reconcile` 的 EXISTS 子查询）。

### 3. 目标端「族」是前端展开，不在后端/视图再派生一份

`target_type` 是展示标签，`writer_template_key` 才是路由真值（ADR-0012）。但创建时服务端已校验二者一致
（`EtlTargetService` 拒绝 `DORIS + wR`），因此筛选直接打在 `target_type` 上：

- `HDFS → {HDFS, HIVE}`
- `RDBMS → {RDBMS, MYSQL, POSTGRESQL, SQLSERVER}`
- `DORIS → {DORIS}`

族→类型集的映射放在前端字段定义中一次；后端只认 `target_type IN (...)`。避免在视图或 Java 里复制
`TargetTypeResolver` 的派生逻辑造成漂移。

### 4. 关键字 `q` 扩展到目标端与表注释

`vw_etl_table_with_source` 对应的 `filterColumn` 公式增加 `target_name`、`target_code`、`tbl_comment`。
「按目标端名搜」是最常见的临时查询，不应强迫用户进面板。公式在两处同步：`EtlTableView` 的视图定义与
`VwEtlTableWithSource` 的 `@Formula`；`scripts/schema.sql` 保持同步（仅空库初始化使用，但不得漂移）。

### 5. 交互：常驻简单搜索 + 弹出式高级筛选面板 + 已选条件 chips

- 工具栏保留关键字、状态、采集源、管理范围；新增「筛选」按钮（带激活条件数角标）。
  目标端不占工具栏：它的筛选频率低于采集源/状态，放进面板即可，避免工具栏继续膨胀。
- 面板按「目标端 / 采集源 / 采集行为 / 时间范围」分组，改动即时生效（300ms 去抖），面板不自动关闭。
- 已选条件以可单独删除的 chips 呈现，并提供「清除全部」。
- 筛选后显示「共 N 条（已筛选）」；空结果区分"无数据"与"无匹配"，后者给清除入口。
- 全部条件写入 URL query，支持分享、刷新恢复、浏览器前进/后退（补齐现有页面只读一次 query 的缺陷）。

### 6. 目标端在列表内可见，且只出现一次

「目标库表」单元格上方增加一行 muted 二级信息（目标端名称 + 族标识），不新增独立列、不加剧横向拥挤。
**不提供单独的「目标端」列**：目标端已经与它的落库目标在同一格里展示，再开一列是重复信息；
需要按目标端收窄时用筛选，而不是再加一个展示位。

**区分不同采集源用分组，不用配色**：源的类别数量无界，颜色不可扩展、不可访问，且与源名称重复。
「显示设置」提供「按采集源分组」：开启后按源名称排序，同源行连续，块首行加分隔线并加粗源名；
选择其他排序时自动关闭分组。

### 7. 只读权限模型不变

按 ADR-0008，读取是全局的，源级授权只约束写与操作型数据。筛选参数是用户显式输入，不是授权范围过滤器；
`readableSourceIds()` 仍为 `null`。

### 8. 排序收敛

- 新增 `SORTABLE_FIELDS` 白名单（`id / sourceTable / targetTable / filter / status / duration / endTime / startTime / createdAt / updatedAt`），
  非法值回退默认，消除任意属性排序。
- 默认排序 `id DESC`，保证分页稳定。

### 9. LIKE 输入转义

`q`、`tblCommentContains`、`targetDbContains` 统一转义 `\`、`%`、`_`。

## 影响

- **API**：`GET /api/v1/tables` 新增参数，全部可选，旧调用零改动；`sourceId` 保留为别名。
- **数据**：无表结构迁移。P1 不需要新索引；`target_id`/`status`/`sid` 的现有索引覆盖主要过滤路径。
  时间字段与 `tbl_comment`/`filter` 的 LIKE 依赖全扫，按实测数据量在 P2 决定是否补
  `created_at`/`updated_at`/`end_time` 索引或 `pg_trgm`。
- **视图**：`filterColumn` 是 Hibernate `@Formula`（查询期计算），不是视图列，扩展它**不改变**
  `vw_etl_table_with_source` 的定义，因此没有指纹重建、没有启动期副作用、无需数据迁移。
  只要求公式引用的列在视图中存在（`target_name` / `target_code` / `tbl_comment` 均已暴露）。
- **列值大小写**：`etl_source.db_type` 在存量行里存在大写（`MYSQL`/`POSTGRESQL`），新行写小写。
  `dbTypes` 过滤按 `lower(db_type)` 比较，避免同一类源因为历史写法不同而漏筛。
- **写入模式**：`write_mode` 除 `overwrite`/`append`/`nonConflict` 外，RDBMS 目标在未配置时实际落库为
  `insert`（`RdbmsTargetAdapter`），面板选项必须包含 `insert`，否则真实数据筛不出来。
- **前端**：新增 `TableFilterPanel.vue`、`TableFilterChips.vue`、`filter-fields.ts`、`useTableFilters.ts`；
  `views/table/index.vue` 的 `writeQuery`/`readQuery` 被替换为双向同步；`AppDataTable` 不改（已有 `#header` 插槽）。
- **重置按钮语义不变**：清空所有条件并回到 `scope=CURRENT` 基线。
- **已知边界**：历史行若 `target_id` 为空，其 `target_type` 为空，任何目标端条件都会将其排除；面板需提供
  "未指定目标端"选项或在文档中写明。

## 未采用的方案

- **JSON 过滤器 DSL（`filters=[{field,op,values}]`）。** 前端组件更通用、加字段不用改签名，但 URL 不可读、
  算子语义需要自有契约、错误信息更难定位，且与仓库现有的"显式参数 + JPA Specification"模式冲突。
  字段集是封闭的，本收益不成立。
- **自由查询语言（JQL 风格）。** 需要解析器、注入防护、索引策略与用户培训，远超当前需求。
- **在视图里派生 `target_family` 列。** 会把 `TargetTypeResolver` 的派生逻辑复制到 SQL，形成第二个真相源。
- **在前端做本地过滤。** 分页总数会失真，且与 `VDataTableServer` 语义冲突。
- **把筛选面板抽成全局通用组件。** P1 先服务采集表列表；P3 若有第二个页面复用同一字段模型再抽象，避免过早泛化。
- **按 owner/tags 筛选。** `etl_table` 没有这些列；若未来需要，先加列再谈筛选，不以自由文本 `remark` 冒充标签。

## 验证

遵循仓库"E2E 优先、选中等偏难场景、产出可重复产物"的原则，在 `tests/e2e/` 新增 spec：

1. 准备跨族目标端（如 Doris 与 HDFS）各挂若干表。
2. 组合筛选 `targetTypes=DORIS` + `writeModes=append` + 完成时间近 7 天，断言**分页总数**与逐行内容。
3. 带完整条件的 URL 直接访问 → 刷新后条件与结果一致。
4. 浏览器前进/后退 → 条件回滚并重新查询。
5. 逐个删除 chips → 结果集单调扩大；「清除全部」回到基线。
6. 空结果 → 出现清除入口，点击后恢复。
7. 列表默认可见目标端二级行与族标识。
8. 合法/非法 `sortField` 均不返回 500。

## 分阶段

- **P1（MVP）**：目标端可见性；目标端/目标类型/目标库/目标端点、写入模式、结构管理、表注释、三组时间范围筛选；
  chips；URL 双向同步；`q` 扩展；排序白名单与默认排序。前后端可拆 `fe` / `be` 两个 commit。
- **P2**：增量采集、调度覆盖三态；保存筛选视图（先 localStorage，后落库）；按数据量补索引。
- **P3**：最近失败 / 对账失败（EXISTS）；面板抽象为通用组件并复用到错误中心、表结构变更、审计。
