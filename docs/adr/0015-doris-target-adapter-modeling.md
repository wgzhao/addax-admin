# ADR-0015: Doris 目标端「独立适配器 + RDBMS 家族」建模

- 状态：Accepted
- 日期：2026-11-23
- 关联：ADR-0002（目标契约与字段映射）、ADR-0006（目标端作用域的 Hive 配置）、ADR-0012（目标端族、方言与模板契约）

## 背景

平台当前把目标端建模为「族 + 方言 + 作业模板」三轴：

- **族**（`TargetFamily`）只有 `HDFS` / `RDBMS`，决定表结构展示、日期维度和契约形态；
- **适配器类型**由 `writer_template_key` 优先决定（`wH` → HDFS，`wR` → RDBMS），`target_type` 作为展示与兜底；
- **RDBMS 方言**由 JDBC URL 推断（`DbUtil.getDbType`），writer 插件与 DDL 方言都跟着 URL 走；
- 所有 RDBMS 目标共用 `wR` 一个 writer 模板。

现在要支持 Doris 作为目标端。Doris 可以通过 MySQL 协议用 JDBC 访问，看起来可以归入 RDBMS；但它的 `doriswriter` 走 HTTP Stream Load，需要 `loadUrl`，且 DDL 与数据模型语义和普通 RDBMS 不同。本 ADR 决定 Doris 的建模方式，不涉及具体实现。

## 事实

以下事实来自现有代码与随包插件，不依赖具体实现方案：

1. **方言无法从 URL 判别。** Doris 的 JDBC 地址是 `jdbc:mysql://fe:9030/...`，`DbType.MYSQL` 已占用 `jdbc:mysql` 前缀，`DbUtil.getDbType` 必然返回 `MYSQL`。ADR-0012 确立的「方言由 URL 推断、只有一个真相源」对 Doris 失效；按端口 9030 猜测属于脆弱启发式。

2. **写通道不是 JDBC。** `doriswriter` 的参数为 `connection.{database,jdbcUrl,table[]}`、`username/password`、`column[]`、`loadUrl[]`、`batchSize`、`flushInterval`、`flushQueueLength`、`loadProps`、`preSql/postSql`，**没有 `writeMode`**。`loadUrl` 是 Stream Load 的 HTTP 端点列表，缺失时写入阶段没有可用 host；它是写通路的组成部分，不是可选的性能参数。`preSql/postSql` 通过 JDBC 执行，可作为按业务日期清理的补偿手段，但不等价于 RDBMS 的 `writeMode=replace/overwrite`。

3. **模板参数形状不兼容。** `wR` 模板包含 `writeMode`、`connection.table` 数组和 `${table}` 的引号/转义规则；Doris 需要 `connection.database`、裸列名和 `loadUrl`。同一模板无法同时服务 `mysqlwriter` 与 `doriswriter`。现有列名序列化会生成带反引号的标识符，而 Stream Load 的 `columns` 头需要裸列名。

4. **DDL 与演化语义不同。** 现有 MySQL DDL 分支生成 `ENGINE=InnoDB`（字符集/排序规则继承目标库，见 ADR-0016）、内联 `PRIMARY KEY`、可能的 `AUTO_INCREMENT`，变更走 `MODIFY COLUMN`。Doris 需要 `ENGINE=OLAP` + `DUPLICATE/UNIQUE/AGGREGATE KEY` + `DISTRIBUTED BY`（可选 `PARTITION BY`），key 列和类型受数据模型约束，schema change 能力有限，不能照搬现有列加宽策略。

5. **其余能力与 RDBMS 一致。** Doris 通过 MySQL 协议支持 JDBC 元数据、`information_schema` 查询、max 查询；业务日期列、主键映射、R2R 主作业模板、目标契约比对和类型映射族都可以复用 RDBMS 语义。

6. **已有先例支持「家族下的具体产品」。** `Constants.HDFS_TARGET_TYPES = {HDFS, HIVE}`，HIVE 作为 HDFS 家族下的具体产品，适配器仍按族工作。`writer_template_key` 本来就是为目标端选择不同 writer 模板而存在的。

## 决策

### 1. Doris 是独立适配器类型，不是 RDBMS 方言

- 新增适配器类型 `DORIS`（`TargetAdapter.getType()` 返回 `DORIS`）；
- `TargetFamily` 保持 `RDBMS`，**不新增 `TargetFamily.DORIS`**；
- `writer_template_key = wD`，使用 Doris 专属模板；
- `target_type` 取值为 `DORIS`，作为展示标签与显式判别依据。

### 2. 路由与保存校验

- `TargetTypeResolver` 增加 `WD -> DORIS`；`target_type = DORIS` 也解析到同一适配器；
- `EtlTargetService.inferWriterKey` 增加 `DORIS -> wD`；
- 保存时校验 `target_type` 与 `writer_template_key` 一致，拒绝 `DORIS + wR` 这类静默错配；
- `detect-dialect` 不用于 Doris；前端在 Doris 类型下固定展示 `doriswriter`，不展示由 URL 推断出的 `MYSQL`。

### 3. 连接配置由目标端持有

沿用 ADR-0006「目标端持有集群身份与写入默认值」的模式，`etl_target.connect_config` 扩展：

```json
{
  "url": "jdbc:mysql://fe:9030",
  "username": "...",
  "password": "...",
  "loadUrl": ["fe1:8030", "fe2:8030"],
  "batchSize": 2048,
  "flushInterval": 3000,
  "flushQueueLength": 1,
  "loadProps": { "format": "csv", "column_separator": "\t" }
}
```

保存时强校验 `loadUrl` 非空且每项为 `host:port`；JDBC 可达性与 HTTP 端点可达性是两项独立检查。

### 4. 表级选项由 `target_options.doris` 承载

Doris 的复杂性在数据模型，放在表级，与 `target_options.hdfs` 对称：

- `keyModel`：`DUPLICATE` / `UNIQUE` / `AGGREGATE`；
- `keyColumns`：默认取映射后的源主键，业务日期是否进 key 必须显式配置；
- `distribution`：`HASH(cols)` 或 `RANDOM`，`buckets: AUTO` 或数值；
- `partition`：不分区、按业务日期 RANGE 或 dynamic partition 属性。

### 5. DDL 独立实现，不支持的变更显式失败

- 新增 Doris 专用 DDL/类型渲染，不复用 MySQL 分支；
- 对 Doris 不支持的 schema change 显式报错并说明原因，不发出目标端不认的 DDL；
- 平台管理模式的表结构预览需按 Doris 语义展示 key、分布和分区。

### 6. 实现结构

- 抽取可复用的 JDBC 目标端支持（连接解析、元数据描述、业务日期、列映射），由 RDBMS 与 Doris 适配器共享；
- 不复制 `RdbmsTargetAdapter`；先做小步抽取，保持 RDBMS 行为不变。

### 7. 健康检查

第一阶段沿用 RDBMS 的 JDBC 检查项；后续把清单从按 family 选择改为按适配器类型选择，并增加 `doriswriter` 插件存在、`wD` 模板有效、`loadUrl` HTTP 可达三项。此项不作为 Doris 建模的前提。

## 后果

- **API**：`target_type` 增加 `DORIS`；目标端保存增加类型/模板键一致性校验；新增目标端表单字段；`detect-dialect` 在前端对 Doris 增加例外。
- **数据零迁移**：`wD` 模板通过 `ensureItem` 幂等补齐，兼容既有部署；存量目标端不受影响。
- **兼容性**：采集源侧不变，Doris 作为源仍按 MySQL 协议读取；本决策只影响目标端。
- **测试**：E2E 需要一个可用的 Doris 实例，验证目标端创建、作业生成、Stream Load 写入与行数核对，这是主要环境成本。
- **权限**：目标端与系统配置仍为 `admin`。

## 备选方案与拒绝理由

| 方案 | 拒绝理由 |
|---|---|
| A. 作为 RDBMS 方言，由 URL 推断 | `jdbc:mysql` 无法区分 Doris/MySQL；`wR` 模板参数不兼容；DDL 与演化分支会扩散进 `RdbmsDdlUtil` 和 `RdbmsTargetAdapter`，把三个真相源混在一起。 |
| C. 新增 `TargetFamily.DORIS` | family 的语义是表结构展示与契约形态，Doris 在这些方面与 RDBMS 一致；新增 family 会让契约比对、类型映射、作业模板、前端选项全部增加第三分支，收益为零。 |
| D. 短期继续用 `mysqlwriter` 写入 Doris | 功能可用但走 JDBC 行级 INSERT，性能不达标；且日后切换为 Doris 类型需要迁移 `target_type`、模板键和连接配置，留下长期债。仅可作为不发布 Doris 类型时的临时兜底，不作为建模。 |

## 分阶段落地

- **Phase 1（MVP，先拿到写入性能）**：新增 `DORIS` 类型、`wD` 模板、Stream Load writer、目标端 Doris 连接段；仅支持 `EXTERNAL` 目标表模式（表由数仓或 DBA 管理，平台做契约校验与写入，不做 DDL）；用真实 Doris 实例完成 E2E。
- **Phase 2（MANAGED 表结构）**：Doris DDL、key model/distribution/partition 表级选项、演化规则与失败提示；schema preview 增加 Doris 预览。已实现，见下节。
- **Phase 3（可选）**：健康检查补插件/模板/HTTP 探测；若后续支持 StarRocks（`starrockswriter` 参数形状相同），再抽「Stream Load 系」共享实现，现在不提前抽象。

## Phase 2 实现决议（2026-11-23）

Phase 2 的开放问题在实现时收敛为以下口径，未新增 `TargetFamily`：

- **表级选项**：`etl_table.target_options.doris`，与 `rdbms` 并列（业务日期列仍由 `rdbms.businessDateColumn` 承载）：
  `keyModel`、`keyColumns`、`distributionType`、`distributionColumns`、`buckets`、`replicationNum`、
  `partitionMode`、`partitionColumn`、`partitionStart`、`partitionEnd`、`partitionPrefix`。
- **默认数据模型**：`AUTO`。映射后的源端主键存在（或显式配置了键列）时用 `UNIQUE`，否则用 `DUPLICATE`；
  `UNIQUE` 无键列直接失败。`AGGREGATE` 需要为每个值列指定聚合函数，平台没有该信息，显式拒绝。
- **键列**：必须位于表定义的最前面，平台按 key 顺序重排字段；键列声明为 `NOT NULL`，源端可空时给出提示。
  TEXT/STRING/BINARY/JSON/FLOAT/DOUBLE 不能作为键列，遇到即显式失败而不是让 Doris 拒绝 DDL。
- **业务日期列**：仍是 `varchar(8)`，默认不进入键列，与 RDBMS 语义一致；跨天重跑覆盖/追加的后果沿用现有提示。
  它不能作为 Doris 分区列（分区列必须是 DATE/DATETIME）。
- **分桶**：默认 `HASH(键列) BUCKETS AUTO`，要求 Doris 2.0+；`RANDOM` 仅限 `DUPLICATE`，且必须有显式分桶数
  （未配置时按 10）。
- **分区**：`NONE`（默认）或 `DYNAMIC`。动态分区必须显式指定一个映射为 DATE/DATETIME 的目标字段，
  自动加入键列，使用 `PARTITION BY RANGE(col) ()` + `dynamic_partition.*` 属性；预建/保留天数和前缀可配置，
  并开启 `create_history_partition`，使建表当时的保留窗口（`partitionStart`）立即有分区，T+1 首日装载不会落在分区外。
  业务日期列（varchar）不满足 Doris 的分区类型要求，因此“按快照业务日期自动分区”不在本阶段。
- **副本数**：`replicationNum` 可选；不填则完全交给 Doris 默认值，避免在小集群上隐式失败或隐式降副本。
- **演化规则**：存量表只做三类动作——补加缺失的值列、放宽值列类型（varchar 变长 / 整数升位 / decimal 扩精度）、
  其余变更告警并跳过。键列缺失、键列类型变化、键模型或分桶漂移不做自动修复；键列缺失直接让本次结构同步失败。
  未映射且非空无默认值的列会告警，因为它会让 Stream Load 失败。
- **最小版本**：Doris 2.0（`BUCKETS AUTO` 与动态分区属性）；更早版本需显式分桶并自行验证动态分区。
- **模式默认值**：Doris 的 MANAGED 与 EXTERNAL 均开放；向导默认仍与其它目标族一致（MANAGED），
  外部表由数仓维护时选择 EXTERNAL。
- **loadUrl**：维持 Phase 1 约定（FE 8030 或 BE 8040 的 HTTP 端点），未强制与 JDBC 地址同源。
- **验证**：在 Doris 4.0.4（3 BE）测试集群上用真实 Addax `doriswriter` 跑通建表、装载、重跑幂等、
  补列/放宽、键列变更告警、DUPLICATE 追加与动态分区（54 项检查全过）。

## 待定决策

Phase 2 实现时已收敛，见「Phase 2 实现决议」：默认数据模型为 `AUTO`（源端主键 → `UNIQUE`，否则 `DUPLICATE`）；
业务日期列默认不进 key、不能作为分区列；最低版本 Doris 2.0；`MANAGED` 在 Phase 2 开放且与 `EXTERNAL` 并存；
`loadUrl` 维持 Phase 1 约定。

## 风险

- **幂等与重复行**：Stream Load 本身不提供跨作业重跑去重；`DUPLICATE KEY` 重跑会产生重复行，必须由 key model 或 `preSql` 策略显式承担。
- **schema change 受限**：key 列与类型变更能力有限，平台的自动演化必须降级为显式失败，不能让目标端报错后静默重试。
- **双通道网络**：Addax 执行节点必须同时可达 FE 的 JDBC 端口和 `loadUrl` 的 HTTP 端口，二者不是同一检查。
- **类型映射差异**：`TEXT/VARBINARY/BLOB` 等类型需要 Doris 专用渲染，不能直接沿用 MySQL 映射。
- **模板升级**：`wD` 占位符契约需与默认模板保持同步，管理员改名/删除占位符仍由现有守卫拦截。

## 明确不做

- 不按 JDBC URL 或端口推断 Doris；
- 不把 `loadUrl` 塞进 `wR` 模板让 `mysqlwriter` 忽略；
- 不新增 `TargetFamily.DORIS`；
- 不复制 `RdbmsTargetAdapter` 的实现；
- 不把 Doris 建模为「带一个 `loadUrl` 参数的 RDBMS」。
