# Memory

## 2026-05-04 - 采集日期维度调度改造

### 目标
- 采集源新增日期维度，支持 `每天` / `工作日` / `周末`
- 工作日判定按 `bizDate` 的周一到周五
- 手工单任务执行绕过日期约束（用于补数）

### 关键改动
- 后端新增枚举与匹配器：
  - `backend/src/main/java/com/wgzhao/addax/admin/common/CollectDateMode.java`
  - `backend/src/main/java/com/wgzhao/addax/admin/service/SourceScheduleMatcher.java`
- 扩展采集源模型与默认值：
  - `backend/src/main/java/com/wgzhao/addax/admin/model/EtlSource.java`
  - 新增字段 `collectDateMode`（默认 `DAILY`）
- 调度与初始化链路接入日期过滤：
  - `backend/src/main/java/com/wgzhao/addax/admin/scheduler/CollectionScheduler.java`
  - `backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java`
  - `backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java`
  - `backend/src/main/java/com/wgzhao/addax/admin/repository/EtlTableRepo.java`
- Source 保存逻辑修正：
  - `backend/src/main/java/com/wgzhao/addax/admin/service/SourceService.java`
  - 修复 `startAt` 比较，调度变更判定纳入 `enabled`、`collectDateMode`
- 前端新增配置与展示：
  - `frontend/src/types/database.ts`
  - `frontend/src/components/source/AddSource.vue`
  - `frontend/src/views/source.vue`
- 数据库脚本更新：
  - `scripts/schema.sql`
  - `etl_source` 增加 `collect_date_mode varchar(16) not null default 'DAILY'`

### 验证结果
- 后端编译：`mvn -pl backend -DskipTests compile` 通过
- 前端类型检查：`npm run type-check` 通过
- 前端打包：`npm run build-only` 失败（环境缺少 `vite`）

### 备注
- 已在手工提交任务入口补充英文注释，说明绕过日期约束的原因（补数场景）。
- 后续每次代码修改按同样格式追加新条目（不要覆盖历史）。

## 2026-05-04 - 提交与 PR 创建

### 操作
- 创建分支：`feat/source-collect-date-mode`
- 提交：`c300646` (`feat(scheduler): add biz-date collect date mode for sources`)
- 创建 PR：`https://github.com/wgzhao/addax-admin/pull/19`

### 提交前验证
- 后端编译：`mvn -pl backend -DskipTests compile` 通过
- 前端类型检查：`npm run type-check` 通过

### 风险与备注
- 前端完整打包在当前环境不可用（`vite` 命令缺失），需在具备前端构建依赖的环境补充验证。

## 2026-05-04 - missing collect 统计口径修正（日期调度）

### 目标
- `vw_etl_table_with_source` 视图增加 `collect_date_mode` 字段。
- 缺失采集统计按采集源日期模式（`DAILY/WEEKDAY/WEEKEND`）计算应采集日。

### 关键改动
- 更新视图定义：`scripts/schema.sql`
  - `vw_etl_table_with_source` 增加 `s.collect_date_mode`。
- 更新视图实体映射：`backend/src/main/java/com/wgzhao/addax/admin/model/VwEtlTableWithSource.java`
  - 新增 `collectDateMode` 字段。
- 更新缺失采集统计 SQL：`backend/src/main/java/com/wgzhao/addax/admin/service/StatService.java`
  - 基于 `collect_date_mode` 过滤“应采集日期”。
  - `daily` 改为 `DISTINCT tid,biz_date`，避免同日多次采集重复计数。
  - `expected_days` / `actual_days` / `missing_days` 全部按“应采集日集合”计算。

### 验证结果
- 后端编译：`mvn -pl backend -DskipTests compile` 通过。

