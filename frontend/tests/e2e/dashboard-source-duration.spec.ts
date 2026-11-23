import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 各采集源批次耗时：覆盖正常变慢、变快、进行中、无上次、无数据五种行状态，
// 以及今日运行心跳里的耗时提示。数据来自 mock，断言的是排序、口径与文案。
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

test.use({ viewport: { width: 1440, height: 900 } });

const DURATIONS = [
  {
    sid: 101,
    source_name: '慢源A',
    total_tables: 12,
    running: false,
    biz_date: '2026-11-23',
    begin_at: '2026-11-23 10:00:00',
    finish_at: '2026-11-23 12:00:00',
    take_secs: 7200,
    table_count: 10,
    in_progress: false,
    prev_biz_date: '2026-11-22',
    prev_take_secs: 3600,
    delta_pct: 100.0,
    trend_secs: [1800, 2400, 3600, 7200],
  },
  {
    sid: 102,
    source_name: '快源B',
    total_tables: 5,
    running: false,
    biz_date: '2026-11-23',
    begin_at: '2026-11-23 09:00:00',
    finish_at: '2026-11-23 09:20:00',
    take_secs: 1200,
    table_count: 5,
    in_progress: false,
    prev_biz_date: '2026-11-22',
    prev_take_secs: 2400,
    delta_pct: -50.0,
    trend_secs: [3000, 2400, 1200],
  },
  {
    sid: 103,
    source_name: '进行中源C',
    total_tables: 10,
    running: true,
    biz_date: '2026-11-23',
    begin_at: '2026-11-23 14:00:00',
    finish_at: '2026-11-23 14:05:00',
    take_secs: 300,
    table_count: 3,
    in_progress: true,
    prev_biz_date: '2026-11-22',
    prev_take_secs: 7200,
    delta_pct: null,
    trend_secs: [5400, 7200],
  },
  {
    sid: 104,
    source_name: '新源D',
    total_tables: 8,
    running: false,
    biz_date: '2026-11-23',
    begin_at: '2026-11-23 08:00:00',
    finish_at: '2026-11-23 08:15:00',
    take_secs: 900,
    table_count: 8,
    in_progress: false,
    prev_biz_date: null,
    prev_take_secs: null,
    delta_pct: null,
    trend_secs: [900],
  },
  {
    sid: 105,
    source_name: '无数据源E',
    total_tables: 0,
    running: false,
    biz_date: null,
    begin_at: null,
    finish_at: null,
    take_secs: null,
    table_count: null,
    in_progress: false,
    prev_biz_date: null,
    prev_take_secs: null,
    delta_pct: null,
    trend_secs: [],
  },
];

const HEARTBEAT = [
  {
    sid: 101,
    sys_name: '慢源A(慢源A)',
    start_at: '10:00:00',
    total_cnt: 12,
    succ_cnt: 12,
    run_cnt: 0,
    fail_cnt: 0,
    no_run_cnt: 0,
    t_begin_at: '2026-11-23 10:00:00',
    t_finish_at: '2026-11-23 12:00:00',
    t_take_secs: 7200,
    y_take_secs: 3600,
  },
  {
    sid: 102,
    sys_name: '快源B(快源B)',
    start_at: '09:00:00',
    total_cnt: 5,
    succ_cnt: 5,
    run_cnt: 0,
    fail_cnt: 0,
    no_run_cnt: 0,
    t_begin_at: '2026-11-23 09:00:00',
    t_finish_at: '2026-11-23 09:20:00',
    t_take_secs: 1200,
    y_take_secs: 2400,
  },
  {
    sid: 105,
    sys_name: '无耗时源E(无耗时源E)',
    start_at: '11:00:00',
    total_cnt: 3,
    succ_cnt: 0,
    run_cnt: 0,
    fail_cnt: 0,
    no_run_cnt: 3,
    t_begin_at: null,
    t_finish_at: null,
    t_take_secs: null,
    y_take_secs: 1800,
  },
];

async function mockDurations(page: Page) {
  await page.route('**/api/v1/dashboard/source-durations**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(DURATIONS),
    })
  );
}

async function mockHeartbeat(page: Page) {
  await page.route('**/api/v1/monitor/accomplish**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(HEARTBEAT),
    })
  );
}

test('renders source batch durations with delta and partial-batch states', async ({ page }) => {
  await mockDurations(page);
  await page.goto('/');
  await page.getByRole('tab', { name: '耗时与 SLA' }).click();

  const table = page.locator('.duration-table');
  await expect(table).toBeVisible({ timeout: 20_000 });
  const rows = table.locator('tbody tr');
  await expect(rows).toHaveCount(DURATIONS.length);

  // 默认按环比降序：变慢 → 变快 → 没有环比的行保持接口顺序
  const order = await rows.allInnerTexts();
  expect(order[0]).toContain('慢源A');
  expect(order[1]).toContain('快源B');
  expect(order[2]).toContain('进行中源C');
  expect(order[3]).toContain('新源D');
  expect(order[4]).toContain('无数据源E');

  const rowA = rows.filter({ hasText: '慢源A' });
  await expect(rowA).toContainText('2h');
  await expect(rowA).toContainText('1h');
  await expect(rowA).toContainText('100.0%');
  await expect(rowA).toContainText('10/12 表');
  await expect(rowA.locator('.cell-delta--slow')).toBeVisible();
  await expect(rowA.locator('svg.cell-spark')).toBeVisible();

  const rowB = rows.filter({ hasText: '快源B' });
  await expect(rowB).toContainText('20m');
  await expect(rowB).toContainText('40m');
  await expect(rowB).toContainText('50.0%');
  await expect(rowB.locator('.cell-delta--fast')).toBeVisible();

  // 进行中批次：显示已跑时长，但不给环比，且趋势不含这批半成品
  const rowC = rows.filter({ hasText: '进行中源C' });
  await expect(rowC).toContainText('进行中');
  await expect(rowC).toContainText('3/10 表');
  await expect(rowC).toContainText('5m');
  await expect(rowC.locator('.cell-delta--flat')).toHaveText('—');
  await expect(rowC.locator('svg.cell-spark')).toBeVisible();

  const rowD = rows.filter({ hasText: '新源D' });
  await expect(rowD).toContainText('15m');
  await expect(rowD.locator('.cell-delta--flat')).toHaveText('—');
  await expect(rowD.locator('svg.cell-spark')).toHaveCount(0);

  const rowE = rows.filter({ hasText: '无数据源E' });
  await expect(rowE).toContainText('0/0 表');
  await expect(rowE.locator('.cell-duration')).toHaveText('—');

  await expect(page.getByText('批次耗时 = 该源当日第一张表开始到最后一张表结束')).toBeVisible();

  // 仅看变慢：只剩环比为正的源
  await page.getByRole('button', { name: '仅看变慢' }).click();
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('慢源A');
  await page.getByRole('button', { name: '显示全部' }).click();
  await expect(rows).toHaveCount(DURATIONS.length);

  fs.mkdirSync(artifactDir, { recursive: true });
  await page
    .locator('.chart-card')
    .filter({ hasText: '各采集源批次耗时' })
    .screenshot({
      path: path.join(artifactDir, 'source-duration-table.png'),
    });
  fs.writeFileSync(
    path.join(artifactDir, 'source-duration.json'),
    JSON.stringify({ order, rows: DURATIONS }, null, 2)
  );
});

test('shows batch duration and slowdown hint in the heartbeat', async ({ page }) => {
  await mockHeartbeat(page);
  await mockDurations(page);
  await page.goto('/');
  await expect(page.locator('.v-window-item--active .pulse-rows')).toBeVisible({ timeout: 20_000 });

  const rowA = page.locator('.v-window-item--active .pulse-row').filter({ hasText: '慢源A' });
  const durationA = rowA.locator('.pulse-time__duration');
  await expect(durationA).toContainText('2h');
  // 100% 慢于上一次，达到 40% 阈值，箭头提示变慢
  await expect(durationA).toHaveClass(/pulse-time__duration--slow/);
  await expect(durationA.locator('.v-icon')).toBeVisible();

  const rowB = page.locator('.v-window-item--active .pulse-row').filter({ hasText: '快源B' });
  const durationB = rowB.locator('.pulse-time__duration');
  await expect(durationB).toContainText('20m');
  await expect(durationB).not.toHaveClass(/pulse-time__duration--slow/);

  // 没有 T 日耗时就不显示第二行，避免把 0 当成 0 秒
  const rowE = page.locator('.v-window-item--active .pulse-row').filter({ hasText: '无耗时源E' });
  await expect(rowE.locator('.pulse-time__duration')).toHaveCount(0);

  fs.mkdirSync(artifactDir, { recursive: true });
  await page.locator('.v-window-item--active .pulse-card').screenshot({
    path: path.join(artifactDir, 'heartbeat-duration.png'),
  });
});
