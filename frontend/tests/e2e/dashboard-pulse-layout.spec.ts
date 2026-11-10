import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Regression: with dozens of sources the heartbeat card grew to ~1500px and
// left a large empty area under the short risk card. The overview now caps the
// heartbeat to an internal scroll while the 今日运行 tab keeps the full list.
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const screenshotsDir = fileURLToPath(new URL('../../../screenshots', import.meta.url));

test.use({ viewport: { width: 1440, height: 900 } });

const NAMES = [
  '股后台',
  'SCRM系统',
  '聚源数据库',
  '3 号客服系统',
  '微信聊天',
  '内容中心',
  '杭州中间库',
  '新CRM系统',
  '广州投放系统',
  '采集系统数据库',
];

function buildRows(count: number) {
  return Array.from({ length: count }, (_, i) => {
    const name = NAMES[i % NAMES.length];
    const hour = String((2 + i) % 24).padStart(2, '0');
    const finish = String((Number(hour) + 1) % 24).padStart(2, '0');
    return {
      sys_name: `${name}${i + 1}(${name}${i + 1})`,
      start_at: `${hour}:10:00`,
      total_cnt: 12,
      succ_cnt: i % 7 === 0 ? 9 : 12,
      run_cnt: i % 9 === 0 ? 1 : 0,
      fail_cnt: i % 11 === 0 ? 1 : 0,
      no_run_cnt: 0,
      t_begin_at: `${hour}:11:00`,
      t_finish_at: `${finish}:20:00`,
    };
  });
}

async function mockHeartbeat(page: Page) {
  await page.route('**/api/v1/monitor/accomplish**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildRows(42)),
    })
  );
}

async function readLayout(page: Page) {
  return page.locator('.v-window-item--active .pulse-card').evaluate(card => {
    const scroll = card.querySelector('.pulse-scroll') as HTMLElement;
    return {
      cardHeight: card.getBoundingClientRect().height,
      scrollClientHeight: scroll?.clientHeight ?? 0,
      scrollHeight: scroll?.scrollHeight ?? 0,
      overflowY: scroll ? getComputedStyle(scroll).overflowY : '',
      rowCount: card.querySelectorAll('.pulse-row').length,
    };
  });
}

test('caps the heartbeat on the overview and keeps the full list in its tab', async ({ page }) => {
  await mockHeartbeat(page);
  await page.goto('/');
  await expect(page.locator('.v-window-item--active .pulse-rows')).toBeVisible({ timeout: 20_000 });

  const overview = await readLayout(page);
  expect(overview.rowCount).toBe(42);
  expect(overview.cardHeight).toBeLessThan(700);
  expect(overview.overflowY).toBe('auto');
  expect(overview.scrollHeight).toBeGreaterThan(overview.scrollClientHeight);

  // The risk card must not be stranded far below the capped heartbeat.
  const pulseBox = await page.locator('.v-window-item--active .pulse-card').boundingBox();
  const riskBox = await page
    .locator('.v-window-item--active .section-card')
    .filter({ has: page.getByText('风险与待办', { exact: true }) })
    .boundingBox();
  const gap = (pulseBox?.y ?? 0) + (pulseBox?.height ?? 0) - ((riskBox?.y ?? 0) + (riskBox?.height ?? 0));
  expect(gap).toBeLessThan(220);

  fs.mkdirSync(artifactDir, { recursive: true });
  fs.mkdirSync(screenshotsDir, { recursive: true });
  await page.locator('.v-window-item--active .pulse-card').screenshot({
    path: path.join(artifactDir, 'pulse-overview-compact.png'),
  });

  await page.getByRole('tab', { name: '今日运行' }).click();
  await expect(page.locator('.v-window-item--active .pulse-rows')).toBeVisible();
  const full = await readLayout(page);
  expect(full.rowCount).toBe(42);
  expect(full.cardHeight).toBeGreaterThan(900);
  expect(full.scrollHeight).toBe(full.scrollClientHeight);

  await page.locator('.v-window-item--active .pulse-card').screenshot({
    path: path.join(artifactDir, 'pulse-tab-full.png'),
  });
  await page.screenshot({ path: path.join(screenshotsDir, 'dashboard-pulse-full.png') });

  fs.writeFileSync(
    path.join(artifactDir, 'pulse-layout.json'),
    JSON.stringify({ overview, full, gap }, null, 2)
  );
});

test('show-all action opens the full heartbeat tab', async ({ page }) => {
  await mockHeartbeat(page);
  await page.goto('/');
  await expect(page.locator('.v-window-item--active .pulse-rows')).toBeVisible({ timeout: 20_000 });

  await page
    .locator('.v-window-item--active .pulse-card')
    .getByRole('button', { name: '查看全部', exact: true })
    .click();
  await expect(page.getByRole('tab', { name: '今日运行' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('.v-window-item--active .pulse-card')).toBeVisible();
  expect((await readLayout(page)).cardHeight).toBeGreaterThan(900);
});
