import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Guards the shared table surface: every table page goes through AppDataTable,
// so card chrome, loading/empty states and the server table wiring stay in one
// place. Artifacts: tests/e2e/artifacts/app-data-table/
const artifactDir = fileURLToPath(new URL('./artifacts/app-data-table', import.meta.url));

test.describe('AppDataTable', () => {
  test.beforeAll(() => {
    fs.mkdirSync(artifactDir, { recursive: true });
  });

  test('client table keeps filtering and the empty-state action', async ({ page }) => {
    await page.goto('/type-mappings');
    await expect(page.getByRole('heading', { name: '类型映射' })).toBeVisible();

    const table = page.getByTestId('app-data-table');
    const empty = page.getByTestId('app-data-table-empty');
    await expect(table.or(empty)).toBeVisible({ timeout: 20_000 });

    const search = page.getByRole('textbox', { name: '搜索源类型' });
    await search.fill('__no_such_type__');
    await expect(empty).toBeVisible();
    await expect(empty.getByRole('button', { name: '新增映射' })).toBeVisible();

    await search.fill('');
    await expect(table).toBeVisible();
    await expect(table.locator('tbody tr').first()).toBeVisible();

    await page.screenshot({ path: path.join(artifactDir, 'type-mappings.png') });
  });

  test('server table keeps its pagination and row drill-down', async ({ page }) => {
    await page.goto('/audit');
    await expect(page.getByRole('heading', { name: '安全审计' })).toBeVisible();

    const table = page.getByTestId('app-data-table');
    const empty = page.getByTestId('app-data-table-empty');
    await expect(table.or(empty)).toBeVisible({ timeout: 20_000 });

    if ((await table.count()) > 0) {
      // Server mode must keep the Vuetify footer (每页数目 / 分页).
      await expect(table.locator('.v-data-table-footer')).toBeVisible();

      const rows = table.locator('tbody tr');
      if ((await rows.count()) > 0) {
        await rows.first().click();
        await expect(page.locator('.v-dialog')).toBeVisible();
        await page.keyboard.press('Escape');
      }
    }

    await page.screenshot({ path: path.join(artifactDir, 'audit.png') });
  });

  test('wide tables keep the first column pinned while scrolling', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1200 });
    await page.goto('/source');
    const table = page.getByTestId('app-data-table');
    await expect(table).toBeVisible({ timeout: 20_000 });
    await expect(table.locator('tbody tr').first()).toBeVisible();

    const card = page.locator('.ds-table-sticky-first');
    await expect(card).toHaveCount(1);
    const pinned = await table
      .locator('thead th')
      .first()
      .evaluate(el => {
        const style = getComputedStyle(el);
        return { position: style.position, left: style.left };
      });
    expect(pinned.position).toBe('sticky');
    expect(pinned.left).toBe('0px');

    await page.screenshot({ path: path.join(artifactDir, 'source-sticky.png') });
  });

  test('accounts uses the same table surface', async ({ page }) => {
    await page.goto('/accounts');
    await expect(page.getByTestId('app-data-table')).toBeVisible({ timeout: 20_000 });
    await page.screenshot({ path: path.join(artifactDir, 'accounts.png') });
  });

  test('failures keeps both detail and grouped modes on the same surface', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/failures');
    await expect(page.getByRole('heading', { name: '错误中心' })).toBeVisible();

    const table = page.getByTestId('app-data-table');
    await expect(table.or(page.getByTestId('app-data-table-empty'))).toBeVisible({
      timeout: 20_000,
    });
    await page.getByRole('button', { name: '按错误聚合' }).click();
    await expect(table.or(page.getByTestId('app-data-table-empty'))).toBeVisible();

    await page.screenshot({ path: path.join(artifactDir, 'failures.png') });
  });

  test('monitor renders all six sections through the shared surface', async ({ page }) => {
    await page.goto('/monitor');
    await expect(page.getByRole('heading', { name: '运行监控' })).toBeVisible();
    await expect(page.getByTestId('app-data-table')).toHaveCount(6, { timeout: 20_000 });
    await page.screenshot({ path: path.join(artifactDir, 'monitor.png') });
  });

  test('embedded detail tabs reuse the shared surface', async ({ page }) => {
    await page.goto('/logs');
    await expect(page.getByRole('heading', { name: '日志管理' })).toBeVisible();
    await expect(page.getByTestId('app-data-table')).toBeVisible({ timeout: 20_000 });
    await page.screenshot({ path: path.join(artifactDir, 'logs.png') });

    // The failure/reconcile tabs live inside the table-detail card, so they use
    // the bare variant; skip when the local fixture table is absent.
    await page.goto('/table/detail/1?tab=failures');
    const failuresTable = await page
      .waitForSelector('[data-testid="app-data-table"]', { timeout: 15_000 })
      .catch(() => null);
    test.skip(!failuresTable, 'table detail fixture #1 is not available');
    await expect(page.getByTestId('app-data-table')).toBeVisible();

    await page.goto('/table/detail/1?tab=reconcile');
    await expect(page.getByTestId('app-data-table')).toBeVisible({ timeout: 20_000 });
  });

  test('empty state hides the primary action when the caller has none', async ({ page }) => {
    // The audit table renders EmptyState without a primary action; before the
    // shared wrapper every empty table showed a stray “创建” button.
    await page.goto('/audit');
    const empty = page.getByTestId('app-data-table-empty');
    if ((await empty.count()) > 0) {
      await expect(empty.getByRole('button', { name: '创建' })).toHaveCount(0);
    }
  });
});
