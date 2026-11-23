# ADR-0016: MySQL 目标表继承库级字符集与排序规则

- 状态：Accepted
- 日期：2026-11-23
- 关联：ADR-0002（目标契约与字段映射）、ADR-0012（目标端族、方言与模板契约）、ADR-0015（Doris 目标端建模）

## 背景

RDBMS 目标端的建表模板固定生成 `) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`，平台自动建库时也固定
`DEFAULT CHARSET utf8mb4`。这带来两个问题：

1. **覆盖了目标方的治理策略。** 目标库是 DBA/业务方的资产，平台只是租户。字符集和排序规则是库级策略；
   平台静默改写，会让平台表与库内既有表使用不同字符集，既有 DBA 用 `ALTER DATABASE` 统一策略的部署也会被绕过。
2. **只可能继承 charset，永远无法继承 collation。** MySQL 的规则是：显式 `DEFAULT CHARSET` 而不写 `COLLATE`
   时，排序规则取该字符集的默认排序规则，而不是库的默认排序规则。实测（MySQL 8.0.46）：

   | 库设置 | 不带子句建表 | 带 `DEFAULT CHARSET=utf8mb4` 建表 |
   | --- | --- | --- |
   | `latin1` | `latin1_swedish_ci` | `utf8mb4_0900_ai_ci` |
   | `gbk` | `gbk_chinese_ci` | `utf8mb4_0900_ai_ci` |
   | `utf8mb4` + `utf8mb4_bin` | `utf8mb4_bin` | `utf8mb4_0900_ai_ci` |

   因此哪怕目标库本身是 utf8mb4，平台建出的表也可能与库内既有表排序规则不同。

## 决策

1. **建表不写 `DEFAULT CHARSET` / `COLLATE`。** 字符集和排序规则都继承目标库的默认值。这是同时继承两者的唯一方式。
   `ENGINE=InnoDB` 保留：它服务于现有写语义（`REPLACE INTO`、按主键幂等写入、事务），是功能前提而非格式偏好。

2. **平台自动创建的库使用 `utf8mb4` 作为库默认字符集。** 库不存在时没有可继承的对象，平台是该对象的唯一所有者，
   需要一个确定的、能装 Unicode 的默认值；之后建表自然继承。已存在的库一律不做 `ALTER`。

3. **非 Unicode 的有效表字符集写入服务日志告警。** 表结构同步后读取表的实际字符集
   （`information_schema.TABLES` + `COLLATIONS`），不在 `{utf8mb4, utf8mb3, utf8, ucs2, utf16, utf32}` 内时
   `warnTarget` 记录：源数据含非 ASCII 字符时可能写入失败或乱码。`risk_log` 已退役，告警只走服务日志。
   读取失败（权限、老版本）按 best-effort 忽略，不阻断结构同步。

4. **建库语句收敛到 `RdbmsDdlUtil`。** 预览与执行此前各拼一遍，容易漂移；现在共用同一构造函数。

5. **文档说明运维前提。** 用户在 server 级（`character_set_server` / `collation_server`）或库级设置字符集；
   平台只在新建库时给默认值。

## 影响

- 新建表的行为改变：字符集/排序规则跟随目标库。已有表不受影响（`CREATE TABLE IF NOT EXISTS` 不会 `ALTER`），
  无数据库迁移；预览 SQL、服务日志与 `etl_jour` 里的 DDL 同步变化。
- 失去"新表一定能装中文"的隐式保证。目标库默认 `latin1`/`gbk` 时：
  - `STRICT_TRANS_TABLES`（5.7+ 默认）→ 运行期 `1366`，任务失败；
  - 非严格模式 → 静默写 `?` 或截断。告警是这两种情况的唯一早期信号。
- 同方言 MySQL→MySQL 拷贝：源 `COLUMN_TYPE` 按 verbatim 复制，但不含列级字符集；目标库默认字符集决定落地表，
  这一点由文档和告警覆盖。
- 跨方言源（Oracle/PostgreSQL/MongoDB）没有源字符集可参考，只能依赖库默认。
- `varchar` 的 16383 字符阈值保持保守上界（按 4 字节字符集估算）；字符集更窄时只是少用 `varchar`、退化为
  `text`，不产生错误。

## 未采用的方案

- **在 `etl_table.target_options` 增加 `charset` 配置。** 数据库本身就是配置载体；再加平台侧配置等于第二个真相源，
  会与库级策略打架。除非出现"同一个库必须混用字符集"的真实需求。
- **按目标库有效字符集动态计算 `varchar` 阈值。** 收益仅是 latin1 下保留更大的 `varchar`，代价是额外元数据查询与分支，不值得。

## 验证

- `scripts/e2e/mysql-target-charset-e2e.sh`：真实 MySQL 上验证 latin1 库建表继承 `latin1_*`、utf8mb4+bin 库继承
  `utf8mb4_bin`、平台新建库默认 utf8mb4、`etl_jour` 记录的 DDL 含 `ENGINE=InnoDB` 且不含 `DEFAULT CHARSET`、
  latin1 有效表字符集触发服务日志告警；产出 Markdown 报告与 JSONL 证据。
