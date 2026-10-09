import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 首页图表 E2E：验证四个 tab 的图表渲染状态，并把各图表 API 的实际返回落成可复现产物
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const screenshotsDir = fileURLToPath(new URL('../../../screenshots', import.meta.url));

const TABS: { value: string; label: string; screenshot: string }[] = [
  { value: 'overview', label: '总览', screenshot: 'dashboard-overview.png' },
  { value: 'health', label: '运行健康', screenshot: 'dashboard-health.png' },
  { value: 'quality', label: '数据质量', screenshot: 'dashboard-quality.png' },
  { value: 'sla', label: '耗时与 SLA', screenshot: 'dashboard-sla.png' },
];

const API_ENDPOINTS = [
  '/api/v1/dashboard/summary',
  '/api/v1/dashboard/table-status-dist',
  '/api/v1/dashboard/last-14d-fail-trend',
  '/api/v1/dashboard/fail-top-tables?days=14&limit=10',
  '/api/v1/dashboard/last-30d-error-rate',
  '/api/v1/dashboard/missing-collect-calendar?days=56',
  '/api/v1/dashboard/last-12m-recs-bytes',
  '/api/v1/dashboard/last-30d-source-contribution',
  '/api/v1/dashboard/slow-top-tables?days=30&limit=10',
  '/api/v1/dashboard/take-secs-histogram?days=30',
];

// 收集控制台错误，返回读取函数
function collectConsoleErrors(page: Page): () => string[] {
  const errors: string[] = [];
  page.on('console', msg => {
    if (msg.type() === 'error') errors.push(msg.text());
  });
  page.on('pageerror', err => errors.push(String(err)));
  return () => errors;
}

// 断言当前激活窗口内的每个图表都进入 ready 或 empty（容忍数据稀疏），且画布有实际尺寸
async function assertChartsSettled(page: Page) {
  const charts = page.locator('[data-chart-state]');
  await expect(charts.first()).toBeVisible();
  for (let i = 0; i < (await charts.count()); i++) {
    await expect(charts.nth(i)).toHaveAttribute('data-chart-state', /ready|empty/, {
      timeout: 20_000,
    });
  }
  const readyCharts = page.locator('[data-chart-state="ready"]');
  for (let i = 0; i < (await readyCharts.count()); i++) {
    const canvas = readyCharts.nth(i).locator('canvas');
    if ((await canvas.count()) === 0) continue;
    const box = await canvas.first().boundingBox();
    expect(box?.width ?? 0).toBeGreaterThan(100);
    expect(box?.height ?? 0).toBeGreaterThan(50);
  }
}

test.describe('dashboard charts', () => {
  test('renders every tab and captures screenshots', async ({ page }) => {
    const readErrors = collectConsoleErrors(page);
    fs.mkdirSync(screenshotsDir, { recursive: true });

    await page.goto('/');
    await expect(page.getByRole('tab', { name: '总览' })).toBeVisible();

    for (const tab of TABS) {
      await page.getByRole('tab', { name: tab.label }).click();
      await assertChartsSettled(page);
      await page.screenshot({
        path: path.join(screenshotsDir, tab.screenshot),
        fullPage: true,
      });
    }

    expect(readErrors()).toEqual([]);
  });

  test('dashboard chart APIs return data', async ({ page }) => {
    await page.goto('/');
    const token = await page.evaluate(() => localStorage.getItem('authToken'));
    expect(token).toBeTruthy();

    const results: {
      path: string;
      status: number;
      rows: number;
      firstRow: unknown;
    }[] = [];

    for (const endpoint of API_ENDPOINTS) {
      const response = await page.request.get(endpoint, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const status = response.status();
      const body = status === 200 ? await response.json() : null;
      const isList = Array.isArray(body);
      results.push({
        path: endpoint,
        status,
        rows: isList ? body.length : body ? 1 : 0,
        firstRow: isList ? body[0] : body ?? null,
      });
      expect(status, `${endpoint} should return 200`).toBe(200);
    }

    const artifact = {
      generatedAt: new Date().toISOString(),
      baseURL: page.url(),
      endpoints: results,
    };
    fs.mkdirSync(artifactDir, { recursive: true });
    const artifactPath = path.join(artifactDir, 'dashboard-api.json');
    fs.writeFileSync(artifactPath, JSON.stringify(artifact, null, 2));

    const summary = results.map(r => `${r.path} -> ${r.status}, rows=${r.rows}`).join('\n');
    await test.info().attach('dashboard-api', {
      body: summary,
      contentType: 'text/plain',
    });
    // 全部图表接口必须都返回数据行（本环境已造数；空数据仅允许出现在统计窗口外）
    const emptyEndpoints = results
      .filter(r => r.path.includes('/dashboard/') && r.rows === 0)
      .map(r => r.path);
    expect(emptyEndpoints, `endpoints without data:\n${emptyEndpoints.join('\n')}`).toEqual([]);
  });

  test('renders in light theme', async ({ browser }) => {
    const context = await browser.newContext({ storageState: undefined });
    const page = await context.newPage();
    const readErrors = collectConsoleErrors(page);

    await page.goto('/login');
    await page.evaluate(() => localStorage.setItem('theme', 'light'));
    await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME || 'admin');
    await page
      .locator('input[autocomplete="current-password"]')
      .fill(process.env.E2E_PASSWORD || 'admin123');
    await page.getByRole('button', { name: 'Login' }).click();
    await expect(page).toHaveURL('/');

    for (const tab of TABS) {
      await page.getByRole('tab', { name: tab.label }).click();
      await assertChartsSettled(page);
    }
    expect(readErrors()).toEqual([]);
    await context.close();
  });
});
