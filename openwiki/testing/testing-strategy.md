---
type: testing
title: 测试策略与 E2E
description: 构建与测试命令、后端/前端单元测试、Playwright E2E 配置与登录态、fake-addax fixture、采集状态矩阵、其他关键 E2E、脚本化日历测试及可审计产物与覆盖边界。
tags: [testing, e2e, playwright, vitest, junit, fake-addax, artifacts]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-6d4b4e707b8d60b6ccfa3425
    resource: repo://.github/workflows/openwiki-update.yml
  - id: openwiki-source-19edf87e657e5f728a22aa4e
    resource: repo://.github/workflows/release-docker.yml
  - id: openwiki-source-9ede0a4b1d189d428085c320
    resource: repo://backend/src/test/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImplTest.java
  - id: openwiki-source-f5e8cb3e4e0c56bbc399f36f
    resource: repo://backend/src/test/java/com/wgzhao/addax/admin/service/TargetContractServiceTest.java
  - id: openwiki-source-828d24ee9ccc8738afe410fb
    resource: repo://deploy.sh
  - id: openwiki-source-bb1ebe868e35e9e500714501
    resource: repo://Dockerfile
  - id: openwiki-source-1047363cf615000e4c9bb694
    resource: repo://frontend/package.json
  - id: openwiki-source-2090dca405aa9c3acd6c7ff8
    resource: repo://frontend/playwright.config.ts
  - id: openwiki-source-54f5fa30d451d50115128e31
    resource: repo://frontend/tests/e2e/audit-log.spec.ts
  - id: openwiki-source-fe862803afa3832e7f48296c
    resource: repo://frontend/tests/e2e/auth.setup.ts
  - id: openwiki-source-86e50a13f47da849754be87d
    resource: repo://frontend/tests/e2e/batch-plan-review.spec.ts
  - id: openwiki-source-860068e68badcc20d95b862e
    resource: repo://frontend/tests/e2e/collection-state-matrix.spec.ts
  - id: openwiki-source-5e8850e333b49d8c47b98cd1
    resource: repo://frontend/tests/e2e/dashboard-charts.spec.ts
  - id: openwiki-source-d1bcdfb7dc37206320c4297d
    resource: repo://frontend/tests/e2e/external-schema-mapping.spec.ts
  - id: openwiki-source-74a0f3e47e21c0fe9966216e
    resource: repo://frontend/tests/e2e/fields-mapping-edit-mode.spec.ts
  - id: openwiki-source-538ae825bd7feffe74b2d60b
    resource: repo://frontend/tests/e2e/fixtures/fake-addax/bin/addax.sh
  - id: openwiki-source-63665dcf6ec6e8a9e693da27
    resource: repo://frontend/tests/e2e/helpers/auth.ts
  - id: openwiki-source-d06faebea14b2c4e66fd40bc
    resource: repo://frontend/tests/e2e/history-snapshot.spec.ts
  - id: openwiki-source-4d532eeeeb2baf59356e6c7e
    resource: repo://frontend/tests/e2e/README.md
  - id: openwiki-source-b5e451039f43c2aaee5090ae
    resource: repo://frontend/tests/e2e/table-detail-editing.spec.ts
  - id: openwiki-source-f6ccde2440cc497427ba6702
    resource: repo://frontend/vitest.config.ts
  - id: openwiki-source-cb2c1c5f3ec222acb444cb1a
    resource: repo://scripts/e2e/calendar-scheduling-e2e.sh
  - id: openwiki-source-7929fcd1da7cb1b68f38bc72
    resource: repo://scripts/e2e/override-rollover-e2e.sh
  - id: openwiki-source-9523cbe86596509a2f84cf50
    resource: repo://scripts/e2e/trade-calendar-refresh-e2e.sh
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 测试策略与 E2E

本项目的测试重心是 **E2E**：关键行为（生命周期、契约阻断、执行快照、批量预览、日历门禁）都需要真实数据库、真实服务和真实时间语义才能证明。单元测试只用于纯函数和窄边界，不作为主要验证手段。

## 命令总览

| 目标 | 命令 |
| --- | --- |
| 后端编译与单元测试 | 根目录 `mvn test`（模块 `backend`） |
| 前端类型检查 | `cd frontend && yarn type-check` |
| 前端构建 | `yarn build`（并行 type-check + vite build） |
| 前端单元测试 | `yarn test`（Vitest） |
| 前端 E2E | `E2E_USERNAME=admin E2E_PASSWORD=admin123 yarn e2e` |
| E2E（有头） | `yarn e2e:headed` |
| 代码检查 | `yarn lint` / `yarn lint:fix` |

（`frontend/package.json#L8-L18`）

Docker 构建和 `deploy.sh` 都显式跳过测试（`mvn clean package -DskipTests`），因此“镜像构建成功”不等于测试通过（`Dockerfile#L21`、`deploy.sh#L2`）。

## 后端测试

后端只有两个 JUnit 测试，均为针对具体回归点的窄测试：

- `TargetContractServiceTest`：Hive 分区列同时出现在普通列区时，不应被误报为 `TARGET_EXTRA_COLUMN`；
- `TargetServiceWithHiveImplTest`：Hive JDBC `DESCRIBE` 输出中重复出现的分区列必须从普通列中剔除。

（`backend/src/test/java/com/wgzhao/addax/admin/service/TargetContractServiceTest.java#L1-L67`、`backend/src/test/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImplTest.java#L1-L36`）

没有队列、调度、安全、迁移或执行链路的集成测试，这些区域依赖 E2E 或手工验证。

## 前端单元测试

Vitest 使用独立的 `vitest.config.ts`，不加载 Vite 的主插件链：

- `environment: jsdom`；
- 只包含 `tests/unit/**/*.spec.ts`；
- `setup.ts` 为 `window.matchMedia` 提供 stub；
- `clearMocks` / `restoreMocks` 自动清理；
- Vuetify 内联处理，绕过其 per-component CSS 在 Node ESM 下的加载问题。

（`frontend/vitest.config.ts#L1-L30`）

覆盖范围偏工具与基础组件：

- `AppBreadcrumb`、`EmptyState`；
- `table-labels`（身份、调度、状态文案）；
- `buildNavMenu` 与路径激活；
- `auth`、`dict-store`、`theme-store`；
- `useSeriesPivot`、`csv`、`debounce`、`json-plugin`、`requests`、`chart-colors`。

没有覆盖率阈值配置，也没有针对复杂页面的组件测试——这些页面交给 Playwright。

## Playwright E2E

### 配置

`frontend/playwright.config.ts`：

- 测试目录 `tests/e2e`，全并行；
- 单测超时 30 秒，断言超时 8 秒；
- CI 下失败重试 1 次；
- HTML + list reporter；
- 首次重试记录 trace，失败截图，失败保留视频；
- `webServer` 执行 `yarn dev`，默认地址 `http://127.0.0.1:3030`，已运行则复用；
- `setup` project 先登录，`chromium` project 复用 `tests/e2e/.auth/user.json`。

（`frontend/playwright.config.ts#L1-L42`）

### 登录态

`auth.setup.ts` 不驱动登录表单，而是调用 `POST /api/v1/auth/login` 取 token，再通过 `page.addInitScript` 写入 `localStorage.authToken` / `authUsername`，最后保存 storage state。注释说明这是为了避开 `vite --force` 依赖重优化导致的中途刷新；登录表单本身由 `auth.spec.ts` 单独覆盖（`frontend/tests/e2e/auth.setup.ts#L1-L29`）。

默认账号 `admin/admin123`，可通过 `E2E_USERNAME` / `E2E_PASSWORD` 覆盖（`frontend/tests/e2e/helpers/auth.ts#L1-L20`）。

### 前置条件

E2E 不是自包含的：

- 后端必须运行在 `http://localhost:50601`；
- 前端由 Playwright 的 `webServer` 启动，或手动运行在 3030；
- 本机必须有 `psql` 客户端，因为大量用例直接以 SQL 建表、插入种子数据、读取断言结果；
- 默认使用独立数据库 `addax_admin_history_e2e`，可通过 `E2E_DB_NAME` 覆盖；
- 测试会修改 `sys_item` 中的 `ADDAX` 路径指向 fake fixture。

（`frontend/tests/e2e/README.md#L1-L59`、`frontend/tests/e2e/collection-state-matrix.spec.ts#L8-L38`）

### fake-addax fixture

`tests/e2e/fixtures/fake-addax/bin/addax.sh` 模拟 Addax：

- 从 `-DjobName` 和 `-Dlog.file.name` 解析参数；
- 在 fixture 自己的 `log/` 下写一行 `fake addax execution for <jobName>`；
- 若设置 `ADDAX_E2E_REPORT_URL`，用 `curl` 向 `/log/job-report` 回报固定统计。

（`frontend/tests/e2e/fixtures/fake-addax/bin/addax.sh#L1-L18`）

用例通过把字典 1000 的 `ADDAX` 值改成 fixture 路径，让真实执行链路在不安装 Addax 的情况下跑通，并且仍会走“读日志→写 addax_log→回报统计”的真实代码路径。

## 采集状态矩阵

`collection-state-matrix.spec.ts` 是生命周期语义的公开行为契约：

- 2 种源状态 × 7 种表状态 × 2 种生命周期 = 28 个组合；
- 每个组合检查 `CURRENT/DORMANT/RETIRED/ALL` 四个范围的可见性；
- 检查详情可读、当前表可编辑（200）、退役表编辑 409；
- 检查 Addax job 编辑：当前表 200，退役表 409 且原内容保持不变；
- 检查退役、重复退役（200）、恢复、对当前表恢复（400）；
- 额外检查旧参数 `lifecycle=ACTIVE` 兼容、源筛选、状态 X 筛选；
- 活动队列行阻止退役（400）和恢复（409）；
- 最后用一条停用源 + X 状态的表做 UI 冒烟：列表提示“源已停用”、详情提示“采集源已停用”、DORMANT 范围可见。

（`frontend/tests/e2e/collection-state-matrix.spec.ts#L196-L492`）

测试通过 `psql` 直接插入 28 行并清理旧数据，因此必须在专用数据库运行，不能指向生产库。

执行后写出 `tests/e2e/artifacts/collection-state-matrix.json`，内容包含时间、数据库名、维度、组合数、失败列表、全部 contractChecks 和每行最终状态，并作为附件挂到 Playwright 报告（`collection-state-matrix.spec.ts#L470-L490`）。

## 其他关键 E2E

| 用例 | 验证内容 |
| --- | --- |
| `history-snapshot.spec.ts` | 执行快照不可变、凭据脱敏、统计/日志按 execution 关联、退役后历史可查、原始日志清理不删执行与统计 |
| `external-schema-mapping.spec.ts` | EXTERNAL 预览、改名映射、job reader/writer 列一致、目标列漂移 → ERROR、执行被阻断且状态 U、恢复后回到 WARNING、MANAGED 回归 |
| `batch-plan-review.spec.ts` | MANAGED 计划生成、EXTERNAL 缺表阻断、批量向导逐表复核、重复目标/缺失映射拦截、快照列、legacy 批量初始化 |
| `table-target-options.spec.ts` | HDFS/RDBMS 目标属性按目标族渲染、无业务日期列的 RDBMS 补采被拒、保存不丢另一族 options、快照列对齐 |
| `table-detail-editing.spec.ts` | 只读→显式编辑、校验后保存、退役表只读、job 编辑可用性、加载/失败态不暴露空表单、响应式布局 |
| `fields-mapping-edit-mode.spec.ts` | 字段映射默认只读，必须显式进入编辑 |
| `dashboard-charts.spec.ts` | 四个 Tab 图表进入 ready/empty、画布尺寸、10 个 dashboard API 返回 200、亮/暗主题截图 |
| `audit-log.spec.ts` | 失败登录、越权拒绝、管理员变更的归因、request id、敏感字段脱敏、审计查询拒绝 |
| `auth.spec.ts` / `accounts-password-confirm.spec.ts` | 登录成功/失败、账号密码二次确认 |
| `source-calendar.spec.ts` / `source-editor-layout.spec.ts` | 采集日历往返、源编辑弹窗跨断点布局 |
| `menu.spec.ts` / `table-search.spec.ts` / `target-dialog.spec.ts` / `help-dialogs.spec.ts` | 导航、搜索、目标端弹窗校验、帮助弹窗可用性 |

（`frontend/tests/e2e/*.spec.ts`）

### 可审计产物

E2E 不只断言，还生成可复查产物：

- `tests/e2e/artifacts/*.json`：矩阵结果、历史快照、批量预览、外部映射、仪表盘 API、审计记录等；
- `tests/e2e/artifacts/*.png`：批量向导布局等关键界面；
- `screenshots/dashboard-*.png`：四个仪表盘 Tab 的全页截图；
- Playwright HTML 报告与失败 trace/截图/视频。

多数用例同时 `test.info().attach(...)`，因此 CI 报告里能直接下载同一份产物（例如 `history-snapshot.spec.ts#L300-L318`、`dashboard-charts.spec.ts#L107-L125`）。

## 脚本化 E2E

涉及日历、切日、cron 触发的行为无法用固定时钟的单元测试证明，仓库提供三个 shell E2E：

| 脚本 | 目的 |
| --- | --- |
| `scripts/e2e/calendar-scheduling-e2e.sh` | 自然日/工作日/交易日门禁、表级覆盖、biz_date 推导、缺数监控、日历耗尽告警、运行期改日历 |
| `scripts/e2e/override-rollover-e2e.sh` | 凌晨触发的表级计划必须按“前一天业务日”判定 |
| `scripts/e2e/trade-calendar-refresh-e2e.sh` | 交易日历年度补全、幂等、接口超时/结构变化时的只增不删退化 |

这些脚本驱动真实服务、真实 cron、真实队列和真实数据库，不伪造时钟；通过把当天日期写入或移出日历字典来构造业务日/非业务日，因此在任意日期都可重复执行（`scripts/e2e/calendar-scheduling-e2e.sh#L1-L30`、`override-rollover-e2e.sh#L1-L20`）。

产物写入 `scripts/e2e/artifacts/*.jsonl` 或 `*.md`，记录每个断言的期望、实际与通过状态（`calendar-scheduling-e2e.sh#L35-L40`、`trade-calendar-refresh-e2e.sh#L1-L30`）。

## 覆盖边界与风险

- **后端集成空白**：队列并发、Master 选举、租约恢复、Spring Security 规则没有自动化集成测试，主要靠 E2E 和运行验证。
- **E2E 依赖本机环境**：需要运行中的后端、PostgreSQL、`psql` 和正确的 `ADDAX` 配置；不是 Testcontainers 式的隔离环境。
- **测试数据直接写库**：矩阵与历史用例会删除/插入业务表，必须使用专用 `E2E_DB_NAME`。
- **构建不跑测试**：Docker 与 `deploy.sh` 使用 `-DskipTests`，发布流水线也不会阻止回归。
- **无 CI 测试工作流**：`.github/workflows` 只有镜像发布和 OpenWiki 更新，没有自动执行 `mvn test` / `yarn test` / Playwright 的任务（`.github/workflows/release-docker.yml#L1-L70`）。
- **无覆盖率门槛**：Vitest 与 JUnit 都没有覆盖率阈值，新增代码可能在没有测试的情况下合入。
- **Playwright 全并行 + 共享数据库**：用例通过唯一 runId 和清理降低冲突，但并行度提高时仍需关注共享字典（如 `ADDAX`）被并发修改的影响。

## 新增测试的落点

- 纯函数、格式化、小型 store/composable → `frontend/tests/unit`；
- 页面交互、生命周期、契约、执行链路 → `frontend/tests/e2e`，优先使用真实 API + `psql` 种子；
- 依赖真实时钟、cron、切日的行为 → `scripts/e2e` 的 shell 脚本；
- 后端窄回归 → `backend/src/test`，但不要用它替代端到端验证。

## 相关页面

- [采集定义与生命周期](../domain/collection-definition-lifecycle.md)
- [前端架构](../frontend/architecture.md)
- [采集表管理界面](../frontend/table-management.md)
- [部署与配置](../operations/deployment-and-configuration.md)
- [目标契约与字段映射](../domain/target-contract-and-mapping.md)
