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
