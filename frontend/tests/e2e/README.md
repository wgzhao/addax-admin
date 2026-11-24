# E2E Tests (Playwright)

## Prerequisites

1. Backend is running at `http://localhost:50601`
2. Frontend can be started by `yarn dev` on `http://127.0.0.1:3030`
3. Default credentials exist: `admin / admin123`

## Install

```bash
cd frontend
yarn add -D @playwright/test
npx playwright install chromium
```

## Run

```bash
cd frontend
E2E_USERNAME=admin E2E_PASSWORD=admin123 yarn e2e
```

Specs that seed through `psql` also need the database credentials; without them psql falls back to
the OS user role (`ai`) and fails with `role "ai" does not exist`:

```bash
PGHOST=127.0.0.1 PGUSER=addax_admin PGPASSWORD='addax_admin@123' \
E2E_USERNAME=admin E2E_PASSWORD=admin123 yarn e2e tests/e2e/table-advanced-filter.spec.ts
```

`playwright.config.ts` 使用了 `setup` project：

- 先执行 `auth.setup.ts` 登录并写入 `tests/e2e/.auth/user.json`
- 其他用例默认复用该登录态（减少重复登录耗时）

Headed mode:

```bash
cd frontend
E2E_USERNAME=admin E2E_PASSWORD=admin123 yarn e2e:headed
```

## Core state matrix

采集源与采集表状态矩阵位于 [`docs/testing/collection-state-matrix.md`](../../../docs/testing/collection-state-matrix.md)，覆盖：

- 2 种源状态：启用、停用。
- 7 种表状态：`N/W/R/Y/E/U/X`。
- 2 种生命周期：当前、已退役。
- 每个组合验证 `CURRENT/DORMANT/RETIRED/ALL` 可见性、详情、编辑、退役和恢复。
- 额外验证旧参数兼容、源/状态筛选、重复操作及活动任务阻断。

建议使用独立数据库运行核心矩阵：

```bash
cd frontend
VITE_API_HOST=http://localhost:50601 \
E2E_DB_NAME=addax_admin_history_e2e \
PLAYWRIGHT_BASE_URL=http://127.0.0.1:3030 \
yarn e2e tests/e2e/collection-state-matrix.spec.ts tests/e2e/history-snapshot.spec.ts
```

矩阵执行结束后会生成可审计制品：

- `tests/e2e/artifacts/collection-state-matrix.json`
- `tests/e2e/artifacts/history-snapshot.json`

## 首页采集缺失日历（后端口径）

`dashboard-missing-calendar.spec.ts` 不 mock API：在专用库里落自然日/工作日/交易日三种源，再写入与日历一致的采集统计，断言近 56 天每天的应采/实采/缺失。工作日和交易日源在周末、日历里的节假日不应被算成应采。

```bash
cd frontend
# 后端需要连这个库，并用独立 Redis DB 启动（避免抢本地实例的配置与租约）：
# DB_NAME=addax_admin_missing_cal_e2e REDIS_DB=4 SERVER_PORT=50602 java -jar backend/target/addax-admin-*.jar
VITE_API_HOST=http://localhost:50602 \
E2E_DB_NAME=addax_admin_missing_cal_e2e \
E2E_API_HOST=http://localhost:50602 \
PLAYWRIGHT_BASE_URL=http://127.0.0.1:3030 \
yarn e2e tests/e2e/dashboard-missing-calendar.spec.ts
```

制品 `tests/e2e/artifacts/missing-calendar-api.json` 记录锚点、种子规则、每天的期望值与实际返回值。

## 切日恢复通知（破坏性用例）

`schedule-recovery-notice.spec.ts` 真的触发切日刷新（重置全部表状态、停/启队列监控、刷新表结构），
必须使用专用库和独立 Redis DB，不要指向开发库或测试环境。它验证：切日失败后重试成功时，恢复通知
只投递给当初收到失败告警的通道；没有未决故障的成功切日保持静默。用例默认跳过，需显式开启。

```bash
# 1. 克隆一个专用库，并把源/目标连接改到克隆库自身，补建刷新所需的源表
dropdb --if-exists addax_admin_daycut_e2e
createdb addax_admin_daycut_e2e
PGPASSWORD='addax_admin@123' pg_dump -h localhost -U addax_admin addax_admin \
  | PGPASSWORD='addax_admin@123' psql -q -h localhost -U addax_admin -d addax_admin_daycut_e2e
PGPASSWORD='addax_admin@123' psql -h localhost -U addax_admin -d addax_admin_daycut_e2e -v ON_ERROR_STOP=1 <<'SQL'
update etl_source set url = 'jdbc:postgresql://localhost:5432/addax_admin_daycut_e2e',
       username = 'addax_admin', pass = 'addax_admin@123';
update etl_target set connect_config = '{"url":"jdbc:postgresql://localhost:5432/addax_admin_daycut_e2e","username":"addax_admin","password":"addax_admin@123"}';
update sys_item set item_value = '23:59' where dict_code = 1000 and item_key = 'SWITCH_TIME';
create table history_e2e_source (id bigint primary key, updated_at timestamp);
-- 表名与 etl_table.source_table 完全一致，PostgreSQL 的 JDBC 元数据按大小写精确匹配
create table "matrix_enabled_current_N" (id bigint primary key, label varchar(50));
create table "matrix_enabled_current_W" (id bigint primary key, label varchar(50));
create table "matrix_enabled_current_R" (id bigint primary key, label varchar(50));
create table "matrix_enabled_current_Y" (id bigint primary key, label varchar(50));
create table "matrix_enabled_current_E" (id bigint primary key, label varchar(50));
create table "matrix_enabled_current_U" (id bigint primary key, label varchar(50));
SQL

# 2. 用独立 Redis DB 启动专用后端（SWITCH_TIME 设到 23:59 避免启动即补跑）
cd /home/ai/code/personal/addax-admin
DB_NAME=addax_admin_daycut_e2e REDIS_DB=5 SERVER_PORT=50602 \
  java -jar backend/target/addax-admin-*.jar

# 3. 运行用例（另一个终端）
cd frontend
E2E_DAYCUT_REFRESH=1 E2E_DB_NAME=addax_admin_daycut_e2e \
E2E_API_HOST=http://localhost:50602 VITE_API_HOST=http://localhost:50602 \
yarn e2e tests/e2e/schedule-recovery-notice.spec.ts
```

制品 `tests/e2e/artifacts/schedule-recovery-notice.json` 记录失败事件、恢复事件的受众，以及没有未决故障时的静默结果。
