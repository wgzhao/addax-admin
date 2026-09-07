# Frontend Agents

Purpose: provide guidance and agent definitions for working on the `frontend` app (Vue 3 + Vite + TypeScript + Vuetify).

Usage: invoke these agents when performing frontend-specific tasks such as UI design, component implementation, E2E testing, or local debugging.

Recommended agents
- `Explore`: quick codebase reads and Q&A about frontend structure.
- `frontend-design`: design and implement Vue components, styles, and tokens.
- `vue`: author and refactor Vue 3 Composition API code and SFCs.
- `gstack`: run headless browser checks, responsive tests, and capture screenshots.

Key responsibilities
- Keep UI changes minimal and consistent with design tokens in `src/plugins/modern-theme.ts` (imported by `vuetify.ts`; `theme.ts` and `custom-theme.ts` are unused legacy files).
- Prefer Composition API and script-setup style for new components.
- Add tests under `tests/unit` for pure logic and `tests/e2e` for interactive behavior or flows.

Engineering conventions (Addax UI)

Toolchain
- `yarn type-check` (vue-tsc), `yarn lint` / `yarn lint:fix` (ESLint 9 flat, lenient rules, 0-error gate), `yarn format` (prettier 2)
- `yarn test` / `yarn test:watch` (vitest, `tests/unit/`), `yarn e2e` (playwright, requires the backend on 50601), `yarn build`
- pre-commit runs lint-staged (prettier + eslint --fix on staged files)

Vuetify 4 gotchas (things AI agents often get wrong)
- Components are registered by vite-plugin-vuetify's autoImport at build time — never import VBtn/VCard etc. manually in SFCs, and never import runtime deep paths from `vuetify/lib`
- Do not use Vuetify 2 idioms: no `value` prop or `@input` on fields (use v-model), no boolean `:success`/`:error` props
- v-data-table: column slots are named after the header `key` (`#item.sourceTable`); the slot scope `{ item }` is the row object itself; selection uses `v-model` + `show-select` + `item-value` + `return-object`
- v-model must bind to a writable ref, never a computed
- `<v-form ref>` validation collects inputs from nested child components automatically (provide/inject)
- Theme: `--v-theme-*` variables are comma-separated RGB triplets — always wrap with `rgb(var(--v-theme-primary))`; change tokens in `src/plugins/modern-theme.ts`; use `useAppTheme()` for runtime switching

File-based routing
- Pages live in `src/views/`; the path maps from the file path; add `<route lang="json">` meta (title/navTitle/icon/navGroup/navOrder/requiresAuth/navHidden/layout) to surface a page in the topbar, which aggregates `router.getRoutes()` via `src/layouts/default/build-nav.ts`
- Do not register routes manually (the legacy duplicate `/dict` registration is an anti-pattern)

Component conventions
- Group components by domain under `src/components/<domain>/`; keep files under ~300 lines and split larger ones (see the table/detail and table/batch splits)
- Use `defineModel` for two-way bindings; object-level models share the parent's object — mutate fields, never reassign
- Shared scoped styles go in a `_xxx-shared.scss` partial imported by each component's scoped style
- Types live in `src/types/database.ts`; API access goes through `src/service/` wrappers around the axios singleton

Testing conventions
- Pure logic → `tests/unit/**/*.spec.ts` (mirror the src path); interactions → `tests/e2e`; component tests use `tests/unit/helpers/mount.ts`, which registers all Vuetify components
- After a change run at least `yarn type-check` and the related `yarn test`; UI changes additionally get an e2e or manual pass

Important files to inspect
- `src/main.ts`
- `src/plugins/` (vuetify.ts, modern-theme.ts)
- `src/components/` and `src/layouts/`
- `vite.config.ts`, `vitest.config.ts`, `eslint.config.mjs` and `package.json`

How to use (example prompts)
- "Explore the `EmptyState.vue` component and suggest improvements for its empty-copy and CTA." 
- "Implement a theme token change: update `light.primary` in `modern-theme.ts` and adjust affected components." 
- "Run E2E checklist: start dev server, run `playwright` checks, and capture a failing screenshot for the login flow." 

Extending agents
- Add or adjust entries here when introducing new workflows (design system, accessibility audits, performance tracing).

Safety & conventions
- Do not modify `.env` or CI configs from frontend agents without explicit approval.
- Show diffs before committing; include test runs for any behavior changes.
