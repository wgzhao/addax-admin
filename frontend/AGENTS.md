# Frontend Agents

Purpose: provide guidance and agent definitions for working on the `frontend` app (Vue 3 + Vite + TypeScript + Vuetify).

Usage: invoke these agents when performing frontend-specific tasks such as UI design, component implementation, E2E testing, or local debugging.

Recommended agents
- `Explore`: quick codebase reads and Q&A about frontend structure.
- `frontend-design`: design and implement Vue components, styles, and tokens.
- `vue`: author and refactor Vue 3 Composition API code and SFCs.
- `gstack`: run headless browser checks, responsive tests, and capture screenshots.

Key responsibilities
- Keep UI changes minimal and consistent with design tokens in `src/plugins/theme.ts` and `src/plugins/custom-theme.ts`.
- Prefer Composition API and script-setup style for new components.
- Add tests under `tests/e2e` when adding interactive behavior or flows.

Important files to inspect
- `src/main.ts`
- `src/plugins/` (theme.ts, custom-theme.ts, vuetify.ts)
- `src/components/` and `src/layouts/`
- `vite.config.ts` and `package.json`

How to use (example prompts)
- "Explore the `EmptyState.vue` component and suggest improvements for its empty-copy and CTA." 
- "Implement a theme token change: update `light.primary` in `custom-theme.ts` and adjust affected components." 
- "Run E2E checklist: start dev server, run `playwright` checks, and capture a failing screenshot for the login flow." 

Extending agents
- Add or adjust entries here when introducing new workflows (design system, accessibility audits, performance tracing).

Safety & conventions
- Do not modify `.env` or CI configs from frontend agents without explicit approval.
- Show diffs before committing; include test runs for any behavior changes.
