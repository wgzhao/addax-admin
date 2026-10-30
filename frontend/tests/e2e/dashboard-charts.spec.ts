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

const UNIT_TO_BYTES: Record<string, number> = {
  B: 1,
  KiB: 1024,
  MiB: 1024 ** 2,
  GiB: 1024 ** 3,
  TiB: 1024 ** 4,
  PiB: 1024 ** 5,
};

const DURATION_TO_SECS: Record<string, number> = { 分钟: 60, 小时: 3600, 天: 86400 };

interface StatCardReading {
  title: string;
  numeric: string;
  unit: string;
  unitFontSize: number;
  valueFontSize: number;
}

// 读取统计卡片上拆分的数值与单位，并记录两者字号
async function readStatCard(page: Page, title: string): Promise<StatCardReading> {
  const card = page
    .locator('.stat-card')
    .filter({ has: page.locator('.stat-title', { hasText: title }) });
  const value = card.locator('.stat-value');
  const unit = card.locator('.stat-unit');
  await expect(unit).toHaveCount(1);

  const unitText = (await unit.innerText()).trim();
  const valueText = (await value.innerText()).trim();
  return {
    title: (await card.locator('.stat-title').innerText()).trim(),
    numeric: valueText.replace(unitText, '').trim(),
    unit: unitText,
    unitFontSize: await unit.evaluate(el => Number.parseFloat(getComputedStyle(el).fontSize)),
    valueFontSize: await value.evaluate(el => Number.parseFloat(getComputedStyle(el).fontSize)),
  };
}

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

  test('shows adaptive units next to stat values', async ({ page }) => {
    const initialSummary = page.waitForResponse(
      res => res.url().includes('/api/v1/dashboard/summary') && res.status() === 200
    );
    await page.goto('/');
    await initialSummary;
    await expect(page.locator('.stat-card')).toHaveCount(6);

    const token = await page.evaluate(() => localStorage.getItem('authToken'));
    const response = await page.request.get('/api/v1/dashboard/summary', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.status()).toBe(200);
    const summary = await response.json();

    // 单位不能出现在标题里，只能跟在数值后面
    const yesterday = await readStatCard(page, '昨日数据采集');
    const total = await readStatCard(page, '累计数据采集');
    const duration = await readStatCard(page, '最近采集总耗时');

    for (const card of [yesterday, total, duration]) {
      expect(card.title, `${card.title} title must not carry a unit`).not.toContain('(');
      expect(card.numeric).not.toBe('');
      expect(Number.isFinite(Number(card.numeric)), `${card.title} value must be numeric`).toBe(
        true
      );
      expect(
        card.unitFontSize,
        `${card.title} unit must render smaller than the value`
      ).toBeLessThan(card.valueFontSize);
      // Why: the unit encodes the 1024x magnitude, so it must not shrink into noise
      expect(
        card.unitFontSize / card.valueFontSize,
        `${card.title} unit must stay legible next to the value`
      ).toBeGreaterThanOrEqual(0.5);
    }

    // 精确的单位切换规则由下面的注入用例覆盖，这里只校验真实数据落在合法单位内
    expect(Object.keys(UNIT_TO_BYTES)).toContain(yesterday.unit);
    expect(Object.keys(UNIT_TO_BYTES)).toContain(total.unit);

    const totalBytes = Number(total.numeric) * UNIT_TO_BYTES[total.unit];
    // 三位有效数字的舍入误差上限约 0.5%
    expect(Math.abs(totalBytes - summary.totalDataBytes)).toBeLessThanOrEqual(
      Math.max(1, summary.totalDataBytes * 0.006)
    );

    const yesterdayBytes = Number(yesterday.numeric) * UNIT_TO_BYTES[yesterday.unit];
    expect(Math.abs(yesterdayBytes - summary.lastDataBytes)).toBeLessThanOrEqual(
      Math.max(1, summary.lastDataBytes * 0.006)
    );

    const durationSecs = Number(duration.numeric) * DURATION_TO_SECS[duration.unit];
    expect(Math.abs(durationSecs - summary.totalTimeSecs)).toBeLessThanOrEqual(
      Math.max(60, summary.totalTimeSecs * 0.05)
    );
    expect(duration.unit).toBe(
      summary.totalTimeSecs >= 86400 ? '天' : summary.totalTimeSecs >= 3600 ? '小时' : '分钟'
    );

    fs.mkdirSync(artifactDir, { recursive: true });
    await page
      .locator('.stats-row')
      .screenshot({ path: path.join(artifactDir, 'dashboard-stat-units-live.png') });

    // 真实环境数据量小，再注入跨档数值覆盖 MiB/TiB/PiB、边界进位和空数据
    const mockSummary = { ...summary };
    await page.route('**/api/v1/dashboard/summary', route => route.fulfill({ json: mockSummary }));

    const cases = [
      {
        name: 'MiB range',
        input: { lastDataBytes: 289_910_538, totalDataBytes: 289_910_538, totalTimeSecs: 158 },
        expected: {
          yesterday: ['276', 'MiB'],
          total: ['276', 'MiB'],
          duration: ['3', '分钟'],
        },
      },
      {
        // 复刻生产现场：昨日 224 GiB 与累计 226 TiB，两者数字相近但量级差 1024 倍
        name: 'GiB vs TiB (production-like)',
        input: {
          lastDataBytes: 240_518_168_576,
          totalDataBytes: 248_489_627_877_376,
          totalTimeSecs: 158,
        },
        expected: {
          yesterday: ['224', 'GiB'],
          total: ['226', 'TiB'],
          duration: ['3', '分钟'],
        },
      },
      {
        name: 'boundary promote + PiB',
        input: {
          lastDataBytes: 1_099_124_222_878,
          totalDataBytes: 2_684_354_560_000_000,
          totalTimeSecs: 90_000,
        },
        expected: {
          yesterday: ['1.00', 'TiB'],
          total: ['2.38', 'PiB'],
          duration: ['1.0', '天'],
        },
      },
      {
        name: 'empty data',
        input: { lastDataBytes: 0, totalDataBytes: 0, totalTimeSecs: 0 },
        expected: {
          yesterday: ['0', 'B'],
          total: ['0', 'B'],
          duration: ['0', '分钟'],
        },
      },
    ] as const;

    const mockedCards: Record<string, Record<string, StatCardReading>> = {};
    for (const item of cases) {
      Object.assign(mockSummary, item.input);
      const mockedResponse = page.waitForResponse(
        res => res.url().includes('/api/v1/dashboard/summary') && res.status() === 200
      );
      await page.reload();
      await mockedResponse;
      await expect(page.locator('.stat-card')).toHaveCount(6);

      const rendered = {
        yesterday: await readStatCard(page, '昨日数据采集'),
        total: await readStatCard(page, '累计数据采集'),
        duration: await readStatCard(page, '最近采集总耗时'),
      };
      for (const key of ['yesterday', 'total', 'duration'] as const) {
        expect(
          [rendered[key].numeric, rendered[key].unit],
          `${item.name}: ${key} should render as expected`
        ).toEqual([...item.expected[key]]);
      }
      mockedCards[item.name] = rendered;

      if (item.name === 'GiB vs TiB (production-like)') {
        fs.mkdirSync(artifactDir, { recursive: true });
        await page
          .locator('.stats-row')
          .screenshot({ path: path.join(artifactDir, 'dashboard-stat-units.png') });
      }
    }

    const artifact = {
      generatedAt: new Date().toISOString(),
      baseURL: page.url(),
      summary,
      cards: { yesterday, total, duration },
      mockedCards,
    };
    fs.mkdirSync(artifactDir, { recursive: true });
    fs.writeFileSync(
      path.join(artifactDir, 'dashboard-stat-units.json'),
      JSON.stringify(artifact, null, 2)
    );
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
