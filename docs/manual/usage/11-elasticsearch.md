# Elasticsearch 采集

Elasticsearch 在本版本中**只作为采集源**，不支持作为目标端。采集到的文档写入 HDFS/Hive 或关系型数据库。

## 支持范围

- 一张采集表对应一个索引或别名（alias）。需要采集多个索引时，分别新增采集表。
- 一个 Elasticsearch 采集源可以访问同一集群下的所有索引和别名。Elasticsearch 没有数据库概念，管理端用一个固定的逻辑命名空间 `indices` 承载全部索引，新增采集表时选择它即可。
- Addax 必须安装 `elasticsearchreader` 插件。插件缺失时保存采集源会提示，任务运行会失败。
- 管理端使用原生 REST 访问集群做连接测试和元数据发现；实际数据采集由 Addax 的 `elasticsearchreader` 执行。该 Reader 基于 Jest 客户端，按 `/{index}/_search` 访问，适配 Elasticsearch 7.x/8.x；本版本在 8.11 上完成端到端验证，更早版本未逐一验证。

## 新建 Elasticsearch 采集源

在「采集源管理」中新增，连接类型选择 `Elasticsearch`。保存后连接类型不可修改。

| 字段                | 说明                                                                 |
| ------------------- | -------------------------------------------------------------------- |
| Elasticsearch 地址  | 一个或多个 `http(s)://host:port`，逗号分隔；管理端按顺序尝试，采集任务使用第一个 |
| 高级选项 (JSON)     | 可选，见下表                                                         |
| 用户名 / 密码       | Basic Auth 凭据；无认证时都留空，有认证时两者必须同时填写             |

高级选项支持：

| 选项                 | 默认值                  | 说明                                                         |
| -------------------- | ----------------------- | ------------------------------------------------------------ |
| `insecureSkipVerify` | `false`                 | 跳过 HTTPS 证书与主机名校验，适合自签名证书                   |
| `connectTimeoutMs`   | `5000`                  | 管理端连接超时（毫秒）                                       |
| `readTimeoutMs`      | `60000`                 | 管理端读取超时（毫秒），同时决定 Reader 的 `timeout`（秒）    |
| `scroll`             | `5m`                    | Reader 滚动查询上下文保留时间；留空表示不使用 scroll          |
| `batchSize`          | `1000`                  | 每页文档数，同时写入 search body 的 `size`                    |
| `searchType`         | `DFS_QUERY_THEN_FETCH`   | `QUERY_THEN_FETCH` 或 `DFS_QUERY_THEN_FETCH`                  |
| `multiThread`        | `true`                  | Jest 连接池是否多线程                                         |
| `compression`        | `true`                  | 是否启用请求压缩                                              |
| `discovery`          | `false`                 | Jest 是否从第一个节点发现其他节点                             |

选项值统一使用字符串，例如：

```json
{"insecureSkipVerify":"true","scroll":"3m","batchSize":"2000"}
```

用户名、密码和 API Key 不要写进高级选项；本版本只支持用户名/密码认证。

## 索引、别名与采集标识

- 索引和别名都可以作为采集表的源表。别名常用于日索引（例如 `logs-2026.11.06` 通过 `logs` 别名访问），采集别名可以避免每天修改采集表。
- 别名指向多个索引时，平台会合并各索引的 mapping；同一字段 mapping 冲突时按 JSON 文本读取并给出告警。
- 以 `.` 开头的系统索引（如 `.kibana`、`.security-7`）不会出现在索引列表中。
- 索引或别名不存在、账号无权访问时，预览会明确失败，不会静默返回空字段。

## 字段从哪里来

Elasticsearch 的 mapping 是字段契约的主要来源，平台同时采样文档校正 mapping 与实际文档不一致的情况。

### 采样规则

- 默认最多读取 1000 条文档（`addax.source.es.sampleSize`）。
- 采样使用 `match_all`，按 `_doc` 排序，只读取前 N 条；采样结果不参与业务数据，只用于字段推断。
- mapping 中声明但样本中没有出现的字段仍会保留，采样统计显示为“缺失”。
- 文档中存在但 mapping 未声明的字段（例如 `dynamic: false` 的索引）会按采样推断并追加，同时给出告警。

### 字段规则

- **只采集顶层字段**。`elasticsearchreader` 按 `_source` 的顶层 key 取值，嵌套对象不能按 `user.name` 单独采集。对象、`nested`、`flattened`、数组字段整体按 JSON 文本读取，子字段不会出现在字段列表中。
- `_id` 不在 `_source` 中，**不能作为采集列**。如果需要文档标识，请在索引中写入一个业务字段。
- 日期字段（`date`、`date_nanos`）按 `string` 映射：`_source` 保留写入时的日期文本，Reader 输出的是字符串，映射成目标 `timestamp` 会导致写入失败。
- 数值 mapping 与文档实际类型不一致时（例如 `long` 字段中写入字符串），以采样到的实际类型为准，必要时降级为 `text` 并给出告警。
- `keyword`、`text`、`ip`、`binary` 等都按字符串读取；`binary` 的值在 `_source` 中是 Base64 文本，不是二进制。
- `long` 字段在 Hive 目标端固定映射为 `bigint`，不受全局 Hive 类型字典中 `bigint` 规则的影响；ES `long` 与 Hive `bigint` 同为有符号 64 位。`unsigned_long` 仍按 `decimal(38,10)` 处理。
- 字符串长度取样本中的最大长度，下限 255，关系型目标上限 16383，超过上限按 `text` 处理。
- 默认最多 500 个字段（`addax.source.es.maxFields`），超过限制会截断并给出告警。
- 字段名包含 `.`、`*`、属于 `_id`/`_source` 等保留元数据字段或超过 255 个字符时会被跳过并给出告警。`_class` 这类以下划线开头的普通文档字段可以正常采集。
- 空索引且 mapping 也为空时没有可采集字段；请先写入数据或建立 mapping，数据写入后重新采样。

采样统计（样本数、出现次数、缺失次数、null 次数和实际 JSON 类型分布）保存在字段元数据中，可在字段比较页的“字段采样信息”中查看。字段注释只记录 Elasticsearch 原生类型，例如 `Elasticsearch keyword`、`Elasticsearch object`、`Elasticsearch date`。

### 字段映射与提交

- 源字段名区分大小写。
- 目标列名按大小写不敏感去重；包含 `@`、`-` 等字符时默认替换为下划线，例如 `@timestamp` → `_timestamp`。
- 预览展示的是采样快照。提交时平台会重新读取 mapping 和采样并校验映射。
- 平台管理（`MANAGED`）模式下，目标表由平台创建或演进；外部管理（`EXTERNAL`）模式下只校验目标契约，不执行 DDL。

## 切日刷新与表结构

到达切日时间后，Master 节点会逐表刷新源字段和目标结构，并重新生成采集任务模板：

- 每次刷新都会重新读取 mapping 和采样，更新字段类型、注释和采样统计。
- mapping 中新增的字段会追加到字段列表，并在 `MANAGED` 模式下同步到目标表。
- 类型或注释变化时更新字段契约，并按目标端规则尝试表结构变更；无法执行的变更会记录风险日志。
- 采样中暂时没有出现的旧字段会**保留原映射，不会自动删除**，避免稀有字段被误删。
- 源索引本身不会被修改；平台和 Addax 都只读取索引。
- 源不可达时跳过该表本轮刷新，不会把表标记为采集失败，下一轮会继续尝试。
- 手动执行「更新表结构」与切日刷新使用同一套逻辑。

## 过滤与增量

- 过滤条件是 **Elasticsearch Query DSL JSON 对象**，不是 SQL。例如：

  ```json
  {"term":{"status":"paid"}}
  ```

  日期范围：

  ```json
  {"range":{"created_at":{"gte":"2026-11-01T00:00:00+08:00"}}}
  ```

  也可以直接粘贴只包含 `query` 的 search body，平台会自动取出其中的 `query`。

- 留空或填写 `{"match_all":{}}` 表示全量读取。
- `__max__<field>` 表示按目标表该字段的最大值做增量，例如 `__max__seq`。平台会把它转换为 `range` 条件，与基础过滤条件用 `bool.filter` 组合：

  ```json
  {"bool":{"filter":[{"match_all":{}},{"range":{"seq":{"gt":5}}}]}}
  ```

  增量字段建议使用数值、`keyword` 或日期类型；`text` 字段的范围比较按分词后的 term 进行，通常不符合预期。
- 包含 `script`、`script_fields`、`runtime_mappings` 的过滤条件会被拒绝。
- Elasticsearch reader 没有 `splitPk` / `autoPk` 概念，界面不会显示这些选项；字段映射由平台的位置映射保证，不要通过 `reader_plugin_config` 覆盖 `column`，否则会破坏位置对齐。

## 快照业务日期列

Elasticsearch 采集源**不支持常量列**，因此不能由平台为关系型目标注入快照业务日期。如果目标端配置了“快照业务日期列”，必须由索引中的真实字段映射提供；否则预览会返回 `SOURCE_CONSTANT_UNSUPPORTED` 错误，任务生成也会失败。

## reader_plugin_config

采集表的 Reader 插件配置可以与平台生成的参数合并：

- `search`（JSON 对象）：合并到平台生成的单个 search body。其中的 `query` 会与采集表的过滤条件组合，其他字段（如 `sort`、`_source`、`track_total_hits`）直接覆盖。
- `query`：只合并查询条件，等价于 `search.query`。
- `column`：不允许覆盖；平台按字段映射的位置生成列列表，覆盖会导致列错位。
- 其他键：直接写入 Reader 参数，例如 `headers`、`scroll`。

日期变量（如 `${biz_date_dash}`）在自定义 `search` / `query` 中同样会被替换，替换结果在生成任务内容时冻结为确定值。

## 注意事项

- Elasticsearch 没有主键，平台不会在目标端按源主键建主键；需要去重时请在目标端或下游处理。
- 单张采集表对应一个 Reader 任务，Reader 内部用 scroll 顺序读取；没有按分片并行切片。超大索引建议通过 `_source` 过滤、时间范围过滤或拆成多个采集表控制单次读取量。
- 已有 Hive 表如果从 `decimal` 改为 `bigint`，Hive 3.1 不支持原地 `CHANGE COLUMN`；需要重建目标表并重新采集，不能只靠切日刷新。
- `_source` 被关闭（`"mappings":{"_source":{"enabled":false}}`）的索引无法采集，预览阶段会直接拒绝。
- 别名背后的多个索引字段类型不一致时，平台按 JSON 文本读取并告警；强烈建议别名指向的索引保持一致的 mapping。
- 索引名、别名、字段名区分大小写；目标端是否区分大小写取决于目标数据库。
- Elasticsearch 只支持作为采集源，不能作为目标端。
- 升级 Addax `elasticsearchreader` 或 Elasticsearch 大版本后，建议先对一张表执行「更新表结构」并检查采集任务 JSON，再批量刷新。

## 相关文档

- [采集源与目标端](03-sources-targets.md)
- [批量创建采集表](04-create-collections.md)
- [目标表结构与字段映射](05-target-contract.md)
- [增量与过滤](08-filtering.md)
- [MongoDB 采集](10-mongodb.md)
