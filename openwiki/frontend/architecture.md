---
type: frontend
title: 前端架构
description: Vue 3 + Vuetify 应用的分层、文件路由与布局、Pinia 状态、service/axios 封装、主题约定、开发代理与构建配置。
tags: [frontend, vue, vuetify, pinia, routing, vite, axios]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-26T07:51:00.698Z
sources:
  - id: openwiki-source-1047363cf615000e4c9bb694
    resource: repo://frontend/package.json
  - id: openwiki-source-09e51b51306bcc3e077e1c3a
    resource: repo://frontend/src/App.vue
  - id: openwiki-source-1897bd25f4a1d6688509fe20
    resource: repo://frontend/src/layouts/default/build-nav.ts
  - id: openwiki-source-b12495a7a81b6db3da96b912
    resource: repo://frontend/src/layouts/default/Topbar.vue
  - id: openwiki-source-69b522f10ffdb79d601b4fcf
    resource: repo://frontend/src/main.ts
  - id: openwiki-source-a1bc0c707097259df8899d0c
    resource: repo://frontend/src/plugins/index.ts
  - id: openwiki-source-a1308a62dd71071ae8279977
    resource: repo://frontend/src/plugins/vuetify.ts
  - id: openwiki-source-77c413f182fc2eead5535edb
    resource: repo://frontend/src/router/index.ts
  - id: openwiki-source-d70a29676ece6708fba2de92
    resource: repo://frontend/src/service/table-service.ts
  - id: openwiki-source-a24b8c7addf4043e826237ec
    resource: repo://frontend/src/stores/auth.ts
  - id: openwiki-source-665c042733d83265da88e827
    resource: repo://frontend/src/stores/dict-store.ts
  - id: openwiki-source-5fedbfe08df4096237dbe2d5
    resource: repo://frontend/src/stores/notification-center.ts
  - id: openwiki-source-1418aedd7488e07b3b696d15
    resource: repo://frontend/src/stores/notifier.ts
  - id: openwiki-source-bcffd251e63b6a60adbdff1f
    resource: repo://frontend/src/stores/task-polling.ts
  - id: openwiki-source-5e730aa183f97f99de9c8e6c
    resource: repo://frontend/src/utils/requests.ts
  - id: openwiki-source-378e3cf05ab0d05d335c68d5
    resource: repo://frontend/vite.config.ts
generated: { by: "claude-code", at: "2026-10-26T07:51:00.698Z" }
---

# 前端架构

前端是纯 SPA（`frontend/`），使用 Vue 3 + Vite + Vuetify + Pinia + TypeScript。它只通过 `/api/v1` 访问后端，不承担任何采集逻辑。采集表相关的复杂交互见 [采集表管理界面](table-management.md)。

## 技术栈与构建

`frontend/package.json` 中的关键版本：

- Vue 3.5、vue-router 5、Pinia 3；
- Vuetify 4 + `vite-plugin-vuetify`，MDI 图标；
- Vite 8、TypeScript 5.9、`vue-tsc`；
- Playwright 1.58（E2E）与 Vitest 5（单元测试）。

脚本：

| 命令 | 作用 |
| --- | --- |
| `yarn dev` | 启动 Vite 开发服务器（3030） |
| `yarn build` | `type-check` + `build-only` |
| `yarn type-check` | `vue-tsc --build --force` |
| `yarn lint` / `lint:fix` | ESLint |
| `yarn format` | Prettier |
| `yarn test` | Vitest |
| `yarn e2e` | Playwright |

`tsconfig.json` 启用 `strict`，但 `strictNullChecks=false`、`noImplicitAny=false`，属于偏宽松的业务项目配置。

## 启动与组合根

`frontend/src/main.ts`：

1. 创建 Vue 应用；
2. `registerPlugins(app)` 注册 Pinia、Vuetify、Router（`frontend/src/plugins/index.ts`）；
3. 从 `localStorage` 恢复认证 token 与用户名；
4. 初始化主题（优先用户偏好，其次系统主题）；
5. 挂载 `#app`。

`App.vue` 根据当前路由的 `meta.layout` 选择 `DefaultLayout` 或 `LoginLayout`，并在最外层渲染全局 `Notifier`，保证通知条在任何布局下都可见（`frontend/src/App.vue#L1-L20`）。

## 路由

路由由 `unplugin-vue-router` 从 `src/views` 文件自动生成（`frontend/vite.config.ts#L12-L30`）：

- `src/views/index.vue` → `/`；
- `src/views/table/index.vue` → `/table`；
- `src/views/table/detail.vue` → `/table/detail`；
- `src/views/target.vue` → `/target`，以此类推；
- `login.vue`、`change-password.vue` 与 `components/**` 被排除在自动路由之外，由 `router/index.ts` 手工注册。

`vite-plugin-vue-layouts-next` 按 `meta.layout` 选择布局，默认 `Default`；`router/index.ts` 再为自动路由补上 `layout` meta（`frontend/src/router/index.ts#L1-L76`）。

路由守卫只做本地检查（`frontend/src/router/index.ts#L60-L83`）：

- `/login` 直接放行；
- 无 token → `/login`；
- token 可解析且已过期 → 清空本地状态并跳转登录；
- 其余放行。

真正的授权由后端完成，前端不做角色判断。

## 布局与导航

`DefaultLayout` 由 Topbar、CommandPalette、主内容区和 Footer 组成（`frontend/src/layouts/DefaultLayout.vue`）。Topbar 负责：

- 桌面端顶部导航（`NavCluster`）；
- 宽度小于 1000px 时切换为抽屉导航（`NavDrawer`）；
- “新增”快捷菜单（采集源、目标端、批量新增采集表）；
- 全局搜索（Ctrl+K 打开 CommandPalette）；
- 主题切换与用户菜单。

导航树不是手写菜单，而是由 `buildNavMenu(router.getRoutes())` 从路由 meta 聚合（`frontend/src/layouts/default/build-nav.ts#L20-L100`）：

- `navGroup` 取值 `source` / `target` / `collect` / `data` / `systemManage`；
- `navTitle` 覆盖显示名，`navOrder` 控制排序；
- 隐藏在 `navHidden` 或 login 布局的路由不进入导航；
- `collect` 组额外挂两个动作项：“更新表信息（按需）”和“强制更新全部表信息”。

因此新增页面通常只需创建 `src/views/*.vue` 并设置正确的 route meta，导航会自动出现。

## 状态管理

Pinia store 按职责划分：

| store | 职责 |
| --- | --- |
| `stores/auth.ts` | token、用户名、localStorage 持久化、过期预检、logout |
| `stores/theme-store.ts` | light/dark 主题，用户偏好与系统主题 |
| `stores/dict-store.ts` | 流水类型、表状态、任务状态字典，统一从后端加载 |
| `stores/task-center.ts` | 前端任务列表（进行中/成功/失败） |
| `stores/task-polling.ts` | 5 秒轮询进行中任务的后端状态 |
| `stores/notification-center.ts` | 站内通知列表、未读数、已读操作 |
| `stores/ods-store.ts` | 当前采集表详情的轻量共享状态 |
| `stores/command-palette.ts` | 命令面板开关 |
| `stores/notifier.ts` | 全局 toast（非 Pinia，模块级 `ref`） |

`task-polling.ts` 每 5 秒调用 `GET /tasks/status`，匹配本地任务 ID 后更新状态与进度（`frontend/src/stores/task-polling.ts#L1-L35`）。通知中心通过 `/user-notifications` 接口刷新列表与未读数。

## Service 层与 HTTP

每个业务域一个 service 类，内部持有 `prefix` 并调用统一的 `Requests`：

```ts
class TableService {
  prefix = '/tables';
  fetchTableList(...) { return Requests.get(this.prefix, params) }
  saveColumnMappings(...) { ... }
}
```

`Requests` 是对 axios 的封装（`frontend/src/utils/requests.ts`）：

- `baseURL` 来自 `VITE_API_BASE_URL`，默认超时 5 秒；
- 请求拦截器注入 `Authorization: Bearer <token>`；
- 响应拦截器直接返回 `response.data`；
- 非登录接口的 401：清空认证、提示“登录已过期”、跳转 `/login`；
- 其他错误优先读取后端 `message`，网络/超时错误转换为中文提示；
- 暴露 `get/post/put/delete/patch`。

Service 返回的数据类型定义在 `src/types/database.ts`、`src/types/index.ts` 等文件中，页面只依赖 service 接口而不直接使用 axios。

## UI 约定

`frontend/src/plugins/vuetify.ts`：

- locale 固定 `zhHans`，因为控制台是中文界面；
- 默认主题为 `dark`，主题色来自 `modern-theme.ts`；
- 默认图标集为 MDI；
- 全局 `VTextField` 默认 `outlined`；
- 全局启用 ripple。

主题切换由 `useAppTheme` composable 与 `theme-store` 驱动，Topbar 的按钮调用 `toggleTheme()`；用户选择写入 `localStorage.theme`，清除后回到系统偏好。

通用组件包括 `PageHeader`、`EmptyState`、`ConfirmDialog`、`Notifier`；页面普遍使用 `page-shell`、`ds-card` 等样式类。`notifier.ts` 对错误提示不自动消失（timeout=0），成功提示 3 秒后消失，避免运维漏看失败信息（`frontend/src/stores/notifier.ts#L15-L50`）。

## 开发代理与环境变量

`vite.config.ts` 的 dev server 监听 `0.0.0.0:3030`，把 `VITE_API_BASE_URL` 代理到 `VITE_API_HOST`（`frontend/vite.config.ts#L88-L96`）。典型开发命令：

```bash
VITE_API_HOST=http://localhost:50601 bun run dev
```

生产构建产物由 Nginx 托管，`/api/v1/` 反代到后端；前端不依赖运行时的环境变量注入，API 前缀在构建时由 `VITE_API_BASE_URL` 决定。

## 测试组织

- 单元测试在 `frontend/tests/unit/`，按 components/composables/stores/utils 分组；
- E2E 在 `frontend/tests/e2e/`，用 Playwright + `auth.setup.ts` 复用登录态；
- E2E 的 fake-addax fixture 让执行链路无需真实 Addax 即可跑通。

测试细节见 [测试策略与 E2E](../testing/testing-strategy.md)。

## 相关页面

- [采集表管理界面](table-management.md)
- [REST API 与安全模型](../backend/api-and-security.md)
- [快速开始](../quickstart.md)
- [测试策略与 E2E](../testing/testing-strategy.md)
