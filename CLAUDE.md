## Design Context

### Users

- 主要用户：数据工程师与运维工程师（值班/生产环境）。
- 使用情境：在内网的桌面浏览器中进行 ETL 作业的配置、调度、运行监控与故障排查；需要快速定位错误并执行恢复或重跑操作。

### Brand Personality

- 倾向（基于项目目标与已有讨论）：技术（Technical）、简洁（Minimal）、高端（Premium）。

### Aesthetic Direction

- 视觉目标：清晰、专业、高对比；以受控的强调色突出关键操作与 CTA，整体风格偏“企业级 + 科技感”。
- 主题支持：项目使用 Vuetify 主题化（light / dark）。已发现的主题文件：
  - `frontend/src/plugins/theme.ts`（默认 token，light.primary: `#2563EB`，dark.primary: `#4F83FF`）
  - `frontend/src/plugins/custom-theme.ts`（已添加的定制 token，示例：light.primary `#0E4C8A`；dark.primary `#5B8CFF`）
- 视觉要点：以深蓝系建立信任感，搭配温暖的金色系用于高价值 CTA；避免过多饱和色，保持信息密度。
- 资产：登录页使用 `/logo.png`，Topbar 使用 `/logo.svg`；字体与图标依赖 `roboto-fontface` 与 `@mdi/font`。

### Design Principles

1. 优先可读性与快速决策：界面在值班/故障场景中应便于快速定位问题（状态、时间戳、错误摘要显著可见）。
2. 一致性与可控性：所有颜色、间距与组件行为通过主题 token 管理，禁止零散硬编码颜色。
3. 可操作的空状态：空列表提供明确的 CTA（新建 / 导入 / 使用模板）并给出示例模板与快速上手入口。
4. 可访问性与主题切换：默认达到 WCAG AA 对比度，支持暗/亮模式与“减动画（reduced motion）”偏好。
5. 低装饰、高质感：以精细的阴影、层次与留白提升高端感，避免不必要的视觉噪音。

### 已发现（证据）/技术栈

- 前端：Vue 3 + Vite + TypeScript + Vuetify 3（详见 `frontend/package.json`）。
- 关键文件：
  - `frontend/src/plugins/theme.ts`（现有主题 token）
  - `frontend/src/plugins/custom-theme.ts`（定制主题）
  - `frontend/src/layouts/default/Topbar.vue`（顶部条，引用 `/logo.svg`）
  - `frontend/src/views/login.vue`（登录页，引用 `/logo.png`）
- 依赖：`@mdi/font`、`roboto-fontface`、`monaco-editor` 等。

### 尚不清楚 / 需你确认的点

- 品牌的最终“3 个词”与参考/反例站点（用于样式参考）：科技、信任、高端。
- 是否有必须保留或禁止的颜色（例如 logo 的主色）：暂无。
- 可访问性硬性要求（WCAG AA / AAA / 其它）：小尺寸（低于 17 寸）显示器不考虑支持。
- 明/暗模式优先（同时支持或只选一）：暗模式优先；两个模式均支持。
- 是否同意将本节写入项目根的 `CLAUDE.md`（若不同意，我可写入其它文件）：已同意，将本节写入项目根的 `CLAUDE.md`。

---

下一步建议（可选优先级）：

- 1) 做一个可逆的预览补丁（例如将全局按钮或空状态 CTA 临时调整为强调色），便于快速视觉对比。
- 1) 为 `EmptyState` 添加示例插画与更具体的文案示例（多语言考虑）。
- 1) 进行一次对比度检查并修正不满足 WCAG AA 的元素。

*注：这是基于仓库扫描与目前可得回答的初步 Design Context。我会在收到你对先前问题的补充回答后，把文档更新为最终版本。*
