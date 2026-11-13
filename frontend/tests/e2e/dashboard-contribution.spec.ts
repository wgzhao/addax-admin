import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Regression: the backend used to return pre-folded OTHER rows; the frontend
// folded them again and rendered two 其他 entries. The API now returns one row
// per source per day, so the fold must produce exactly one 其他.
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const screenshotsDir = fileURLToPath(new URL('../../../screenshots', import.meta.url));

test.use({ viewport: { width: 1440, height: 900 } });

interface Row {
  biz_date: string;
  name: string | null;
  gib: number;
}

const DAYS = ['2026-11-08', '2026-11-09', '2026-11-10'];
const SOURCES = [
  ['源A', 100],
  ['源B', 90],
  ['源C', 80],
  ['源D', 70],
  ['源E', 60],
  ['源F', 50],
  ['源G', 40],
  ['源H', 30],
  ['源I', 10],
  ['源J', 5],
  ['源K', 3],
  ['源L', 2],
] as const;

function buildRows(): Row[] {
  const rows: Row[] = [];
  // An empty calendar day is represented by a null placeholder; it must not
  // turn into a 未知 source.
  rows.push({ biz_date: '2026-11-07', name: null, gib: 0 });
  for (const day of DAYS) {
    for (const [name, gib] of SOURCES) {
      rows.push({ biz_date: day, name, gib });
    }
  }
  return rows;
}

async function mockContribution(page: Page) {
  await page.route('**/api/v1/dashboard/last-30d-source-contribution**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildRows()),
    })
  );
}

test('folds tail sources into a single 其他 entry', async ({ page }) => {
  await mockContribution(page);
  await page.goto('/');
  await expect(page.getByRole('tab', { name: '经营总览' })).toBeVisible();

  const card = page
    .locator('.section-card')
    .filter({ has: page.getByText('数据源贡献', { exact: true }) });
  await expect(card).toBeVisible();

  const rows = card.locator('.contribution-row');
  await expect(rows).toHaveCount(9);

  const names = card.locator('.contribution-name');
  await expect(names.first()).toHaveText('源A');
  await expect(names.filter({ hasText: /^其他$/ })).toHaveCount(1);
  await expect(names.filter({ hasText: /^未知$/ })).toHaveCount(0);

  // 源I..源L are outside the Top 8: (10 + 5 + 3 + 2) GiB/day over three days.
  const otherRow = rows.filter({
    has: page.locator('.contribution-name', { hasText: /^其他$/ }),
  });
  await expect(otherRow.locator('.contribution-value')).toHaveText('60.00 GiB');
  await expect(otherRow.locator('.contribution-bar--other')).toHaveCount(1);

  fs.mkdirSync(artifactDir, { recursive: true });
  fs.mkdirSync(screenshotsDir, { recursive: true });
  await card.screenshot({ path: path.join(artifactDir, 'dashboard-contribution-light.png') });
  await page.screenshot({ path: path.join(screenshotsDir, 'dashboard-contribution.png') });

  const rendered = await card.locator('.contribution-row').evaluateAll(items =>
    items.map(item => ({
      name: item.querySelector('.contribution-name')?.textContent?.trim() ?? '',
      value: item.querySelector('.contribution-value')?.textContent?.trim() ?? '',
    }))
  );
  fs.writeFileSync(
    path.join(artifactDir, 'dashboard-contribution.json'),
    JSON.stringify({ rendered, otherCount: rendered.filter(row => row.name === '其他').length }, null, 2)
  );
});

test('quality tab contribution card also has one 其他 entry', async ({ page }) => {
  await mockContribution(page);
  await page.goto('/');
  await page.getByRole('tab', { name: '数据质量' }).click();

  const card = page
    .locator('.chart-card')
    .filter({ has: page.getByText('各采集源采集量贡献', { exact: true }) });
  await expect(card).toBeVisible();

  const names = card.locator('.contribution-name');
  await expect(names.filter({ hasText: /^其他$/ })).toHaveCount(1);
  await expect(names.filter({ hasText: /^未知$/ })).toHaveCount(0);
  await expect(card.locator('.contribution-row')).toHaveCount(9);
});
