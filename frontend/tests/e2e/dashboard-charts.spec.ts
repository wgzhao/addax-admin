import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 首页图表 E2E：验证四个 tab 的图表渲染状态，并把各图表 API 的实际返回落成可复现产物
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

// 买方演示常见分辨率；1280x720 会让 KPI 行折成两行，不能反映首屏信息层级
test.use({ viewport: { width: 1440, height: 900 } });

const TABS: { value: string; label: string; screenshot: string }[] = [
  { value: 'overview', label: '经营总览', screenshot: 'dashboard-chart-overview.png' },
  { value: 'health', label: '运行健康', screenshot: 'dashboard-chart-health.png' },
  { value: 'quality', label: '数据质量', screenshot: 'dashboard-chart-quality.png' },
  { value: 'sla', label: '耗时与 SLA', screenshot: 'dashboard-chart-sla.png' },
];

const UNIT_TO_BYTES: Record<string, number> = {
  B: 1,
  KiB: 1024,
  MiB: 1024 ** 2,
  GiB: 1024 ** 3,
  TiB: 1024 ** 4,
  PiB: 1024 ** 5,
};

interface StatTileReading {
  label: string;
  value: string;
  unit: string;
  delta: string;
  hint: string;
  unitFontSize: number;
  valueFontSize: number;
}

// 读取经营总览 KPI 卡上的数值、单位、环比与上下文
async function readStatTile(page: Page, label: string): Promise<StatTileReading> {
  const tile = page
    .locator('.stat-tile')
    .filter({ has: page.locator('.stat-label', { hasText: label }) });
  await expect(tile).toHaveCount(1);

  const value = tile.locator('.stat-value');
  const unit = tile.locator('.stat-unit');
  const hasUnit = (await unit.count()) > 0;
  const unitText = hasUnit ? (await unit.innerText()).trim() : '';
  const delta = tile.locator('.stat-delta');
  const hint = tile.locator('.stat-hint');
  return {
    label: (await tile.locator('.stat-label').innerText()).trim(),
    value: (await value.innerText()).trim(),
    unit: unitText,
    delta: (await delta.count()) > 0 ? (await delta.innerText()).trim() : '',
    hint: (await hint.count()) > 0 ? (await hint.innerText()).trim() : '',
    unitFontSize: hasUnit
      ? await unit.evaluate(el => Number.parseFloat(getComputedStyle(el).fontSize))
      : 0,
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
  // Why: inactive and leaving tab panes stay in the DOM, and their canvases
  // have zero width; only the active pane's charts are meaningful here.
  const activePane = page.locator('.v-window-item--active');
  const charts = activePane.locator('[data-chart-state]:visible');
  await expect(charts.first()).toBeVisible();
  for (let i = 0; i < (await charts.count()); i++) {
    await expect(charts.nth(i)).toHaveAttribute('data-chart-state', /ready|empty/, {
      timeout: 20_000,
    });
  }
  const readyCharts = activePane.locator('[data-chart-state="ready"]:visible');
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
    fs.mkdirSync(artifactDir, { recursive: true });

    await page.goto('/');
    await expect(page.getByRole('tab', { name: '经营总览' })).toBeVisible();

    for (const tab of TABS) {
      await page.getByRole('tab', { name: tab.label }).click();
      await expect(page.locator('.v-window-item--active')).toHaveCount(1);
      // Let the window slide finish before asserting or screenshotting.
      await page.waitForTimeout(350);
      await assertChartsSettled(page);
      await page.screenshot({
        path: path.join(artifactDir, tab.screenshot),
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
    // 状态码是硬约束；空数据是合法业务状态（例如近 14 天没有失败），
    // 由界面呈现 empty，而不是让接口测试失败。
    const failedEndpoints = results.filter(r => r.status !== 200).map(r => r.path);
    expect(failedEndpoints, `endpoints not returning 200:\n${failedEndpoints.join('\n')}`).toEqual(
      []
    );
  });

  test('renders executive KPI tiles with units and context', async ({ page }) => {
    const initialSummary = page.waitForResponse(
      res => res.url().includes('/api/v1/dashboard/summary') && res.status() === 200
    );
    await page.goto('/');
    const response = await initialSummary;
    const summary = await response.json();

    await expect(page.locator('.health-label')).toHaveText('今日采集健康度');
    await expect(page.locator('.stat-tile')).toHaveCount(4);

    const today = await readStatTile(page, '今日数据量');
    const done = await readStatTile(page, '今日完成表数');
    const avg = await readStatTile(page, '平均单表耗时');
    const total = await readStatTile(page, '累计数据量');

    expect([today.label, done.label, avg.label, total.label]).toEqual([
      '今日数据量',
      '今日完成表数',
      '平均单表耗时',
      '累计数据量',
    ]);

    for (const tile of [today, total]) {
      expect(tile.value).not.toBe('');
      expect(Number.isFinite(Number(tile.value)), `${tile.label} value must be numeric`).toBe(
        true
      );
      expect(Object.keys(UNIT_TO_BYTES)).toContain(tile.unit);
      expect(
        tile.unitFontSize,
        `${tile.label} unit must render smaller than the value`
      ).toBeLessThan(tile.valueFontSize);
      expect(
        tile.unitFontSize / tile.valueFontSize,
        `${tile.label} unit must stay legible next to the value`
      ).toBeGreaterThanOrEqual(0.5);
    }

    // 真实数据一致性：KPI 卡与 /dashboard/summary 同口径
    const todayBytes = Number(today.value) * UNIT_TO_BYTES[today.unit];
    expect(Math.abs(todayBytes - summary.lastDataBytes)).toBeLessThanOrEqual(
      Math.max(1, summary.lastDataBytes * 0.006)
    );
    const totalBytes = Number(total.value) * UNIT_TO_BYTES[total.unit];
    expect(Math.abs(totalBytes - summary.totalDataBytes)).toBeLessThanOrEqual(
      Math.max(1, summary.totalDataBytes * 0.006)
    );

    const ratioDone = (summary.ratios ?? []).reduce(
      (sum: number, ratio: { done?: number }) => sum + Number(ratio.done ?? 0),
      0
    );
    const ratioTotal = (summary.ratios ?? []).reduce(
      (sum: number, ratio: { total?: number }) => sum + Number(ratio.total ?? 0),
      0
    );
    expect(done.value).toBe(`${ratioDone}/${ratioTotal}`);
    if (ratioTotal > 0) {
      await expect(page.locator('.health-value')).toHaveText(
        ((ratioDone / ratioTotal) * 100).toFixed(1)
      );
    }

    fs.mkdirSync(artifactDir, { recursive: true });
    await page
      .locator('.kpi-row')
      .screenshot({ path: path.join(artifactDir, 'dashboard-kpi-tiles-live.png') });

    // 注入跨档数值覆盖 MiB/TiB/PiB、边界进位和空数据
    const mockSummary = { ...summary };
    await page.route('**/api/v1/dashboard/summary', route => route.fulfill({ json: mockSummary }));

    const cases = [
      {
        name: 'MiB range',
        input: { lastDataBytes: 289_910_538, totalDataBytes: 289_910_538 },
        expected: { today: ['276', 'MiB'], total: ['276', 'MiB'] },
      },
      {
        // 复刻生产现场：当日 224 GiB 与累计 226 TiB，两者数字相近但量级差 1024 倍
        name: 'GiB vs TiB (production-like)',
        input: { lastDataBytes: 240_518_168_576, totalDataBytes: 248_489_627_877_376 },
        expected: { today: ['224', 'GiB'], total: ['226', 'TiB'] },
      },
      {
        name: 'boundary promote + PiB',
        input: { lastDataBytes: 1_099_124_222_878, totalDataBytes: 2_684_354_560_000_000 },
        expected: { today: ['1.00', 'TiB'], total: ['2.38', 'PiB'] },
      },
      {
        name: 'empty data',
        input: { lastDataBytes: 0, totalDataBytes: 0 },
        expected: { today: ['0', 'B'], total: ['0', 'B'] },
      },
    ] as const;

    const mockedTiles: Record<string, Record<string, StatTileReading>> = {};
    for (const item of cases) {
      Object.assign(mockSummary, item.input);
      const mockedResponse = page.waitForResponse(
        res => res.url().includes('/api/v1/dashboard/summary') && res.status() === 200
      );
      await page.reload();
      await mockedResponse;
      await expect(page.locator('.stat-tile')).toHaveCount(4);

      // 等待 mock 值真正渲染后再读取，避免读到上一次渲染的过渡帧
      const todayTile = page
        .locator('.stat-tile')
        .filter({ has: page.locator('.stat-label', { hasText: '今日数据量' }) });
      const totalTile = page
        .locator('.stat-tile')
        .filter({ has: page.locator('.stat-label', { hasText: '累计数据量' }) });
      await expect(todayTile.locator('.stat-unit')).toHaveText(item.expected.today[1]);
      await expect(totalTile.locator('.stat-unit')).toHaveText(item.expected.total[1]);

      const rendered = {
        today: await readStatTile(page, '今日数据量'),
        total: await readStatTile(page, '累计数据量'),
      };
      for (const key of ['today', 'total'] as const) {
        expect(
          [rendered[key].value, rendered[key].unit],
          `${item.name}: ${key} should render as expected`
        ).toEqual([...item.expected[key]]);
      }
      mockedTiles[item.name] = rendered;

      if (item.name === 'GiB vs TiB (production-like)') {
        fs.mkdirSync(artifactDir, { recursive: true });
        await page
          .locator('.kpi-row')
          .screenshot({ path: path.join(artifactDir, 'dashboard-kpi-tiles.png') });
      }
    }

    const artifact = {
      generatedAt: new Date().toISOString(),
      baseURL: page.url(),
      summary,
      tiles: { today, done, avg, total },
      mockedTiles,
    };
    fs.mkdirSync(artifactDir, { recursive: true });
    fs.writeFileSync(
      path.join(artifactDir, 'dashboard-kpi-tiles.json'),
      JSON.stringify(artifact, null, 2)
    );
  });

  test('renders in light theme', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: undefined,
      viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();
    const readErrors = collectConsoleErrors(page);

    await page.goto('/login');
    await page.evaluate(() => localStorage.setItem('theme', 'light'));
    await page.locator('input[autocomplete="username"]').fill(process.env.E2E_USERNAME || 'admin');
    await page
      .locator('input[autocomplete="current-password"]')
      .fill(process.env.E2E_PASSWORD || 'admin123');
    await page.getByRole('button', { name: '登录' }).click();
    await expect(page).toHaveURL('/');

    for (const tab of TABS) {
      await page.getByRole('tab', { name: tab.label }).click();
      await expect(page.locator('.v-window-item--active')).toHaveCount(1);
      await page.waitForTimeout(350);
      await assertChartsSettled(page);
    }
    expect(readErrors()).toEqual([]);
    await context.close();
  });
});
