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
