# ADR-0010: 采集结果对账（Reconciliation）

- 状态：Accepted
- 日期：2026-11-14

## 背景

ETL 工具最基础的可信问题是「数据是否完整落地」。Addax 的 job-report 上报了读取行数
（`totalReadRecords`）与错误行数（`totalErrorRecords`），写入行数可由二者推算：

```
totalWriteRecords = totalReadRecords - totalErrorRecords
```

有错误行数说明采集过程中有脏数据/失败行被丢弃，落地不完整。这目前只是 `etl_statistic`
里的一个统计数字，没有被显式断言、告警，也没有进入失败事实。

## 决策

### 对账语义：读取 vs 写入

- 源端行数 = `totalReadRecords`（读取行数）
- 目标端行数 = `totalWriteRecords` = `totalReadRecords - totalErrorRecords`（写入成功行数）
- `diff` = `totalErrorRecords`（错误行数）

不去目标端重新 `COUNT(*)`：Addax 上报的写入行数是权威、即时的；去目标端 count 会引入
时序、分区/业务日期对齐和大表 count 开销，且无法区分「本次落地」与历史数据。

### 触发

1. **自动**：job-report 到达时（`LogController.jobReport`）同步执行。
2. **手动**：指定 `tid + bizDate`，从 `etl_statistic` 重新取 read/error 补对账。

### 结果落表

新表 `etl_reconcile`：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| tid | 采集定义 id |
| execution_id | 采集执行 id（可空） |
| biz_date | 业务日期 |
| source_rows | 读取行数 |
| target_rows | 写入行数（= read - error） |
| diff | 错误行数 |
| tolerance | 容差，默认 0 |
| status | `PASS` / `FAIL` |
| message | 差异说明 |
| created_at | 记录时间 |

### 失败处理

`diff`（错误行数）超过容差 → `FAIL` + `FailureRecorder.record(...)`，新增阶段
`FailureStage.RECONCILE`，进入错误中心，可被告警规则命中。

## 后果

- 正面：把「错误行数」从被动统计升级为主动对账事实，落地不完整可被断言、告警。
- 局限：read 与 write 同源（都是 Addax 上报），不能发现 Addax 自身的静默丢数；如需
  独立核实，后续可再加「目标端 count」作为第二层校验。
