import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const screenshotsDir = fileURLToPath(new URL('../../../screenshots', import.meta.url));
const token = [
  Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url'),
  Buffer.from(
    JSON.stringify({ sub: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 })
  ).toString('base64url'),
  'signature',
].join('.');

async function preparePage(page: Page) {
  await page.addInitScript(
    ([authToken]) => {
      localStorage.setItem('authToken', authToken);
      localStorage.setItem('authUsername', 'admin');
    },
    [token]
  );
  await page.route('**/api/v1/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    let body: unknown = [];
    if (pathname === '/api/v1/users/me') {
      body = { username: 'admin', enabled: true, authorities: ['ROLE_ADMIN'] };
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
  await page.goto('/help');
  await expect(page.getByRole('heading', { name: '帮助中心', level: 1 })).toBeVisible({
    timeout: 15_000,
  });
}

test('help center renders the manual, supports search, deep links and in-app navigation', async ({
  page,
}) => {
  await preparePage(page);

  await expect(page.locator('.help-sidebar')).toContainText('使用指南');
  await expect(page.locator('.help-sidebar')).toContainText('运维排障');
  await expect(page.getByRole('heading', { name: '快速上手', level: 1 })).toBeVisible();
  await expect(page).toHaveURL(/chapter=usage%2F01-quick-start|chapter=usage\/01-quick-start/);

  await page
    .locator('.help-sidebar')
    .getByText('目标表结构与字段映射', { exact: true })
    .click();
  await expect(page.getByRole('heading', { name: '目标表结构与字段映射', level: 1 })).toBeVisible();
  await expect(page.getByText('EXTERNAL（外部管理）')).toBeVisible();
  await expect(page.locator('.help-toc')).toContainText('目标表结构管理');

  const adrLink = page.locator('.help-content a[href*="github.com/wgzhao/addax-admin"]').first();
  await expect(adrLink).toHaveAttribute('target', '_blank');
  await expect(
    page.locator('.help-content a[href^="../"], .help-content a[href^="./"]')
  ).toHaveCount(0);

  await page.goto('/help?chapter=usage/05-target-contract#目标表结构管理');
  await expect(page.getByRole('heading', { name: '目标表结构管理', level: 2 })).toBeVisible();
  await expect(page).toHaveURL(/#%E7%9B%AE%E6%A0%87%E8%A1%A8%E7%BB%93%E6%9E%84%E7%AE%A1%E7%90%86|#目标表结构管理/);

  const search = page.getByPlaceholder('搜索章节或关键词');
  await search.fill('EXTERNAL');
  await expect(page.getByText(/找到 \d+ 个结果/)).toBeVisible();
  await page.locator('.help-sidebar').getByText('目标表结构与字段映射', { exact: true }).first().click();
  await expect(page.getByRole('heading', { name: '目标表结构与字段映射', level: 1 })).toBeVisible();

  await page
    .locator('.help-content')
    .getByRole('link', { name: '目标端能力与表属性', exact: true })
    .first()
    .click();
  await expect(page.getByRole('heading', { name: '目标端能力与表属性', level: 1 })).toBeVisible();
  await expect(page).toHaveURL(/chapter=usage%2F06-target-engines|chapter=usage\/06-target-engines/);
  await expect(page.locator('.help-sidebar .v-list-item--active')).toContainText(
    '目标端能力与表属性'
  );
  await page.goto('/help?chapter=usage/06-target-engines');
  await expect(page.getByRole('heading', { name: '目标端能力与表属性', level: 1 })).toBeVisible();
  await page.mouse.move(640, 400);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'auto' }));

  await page.screenshot({
    path: path.join(screenshotsDir, 'help-center-desktop.png'),
    fullPage: false,
  });

  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(
    path.join(artifactDir, 'help-center.json'),
    JSON.stringify(
      {
        checkedAt: new Date().toISOString(),
        chapter: 'usage/06-target-engines',
        sidebarGroups: ['使用指南', '运维排障'],
        searchQuery: 'EXTERNAL',
        screenshots: ['screenshots/help-center-desktop.png'],
      },
      null,
      2
    )
  );
});

test('help center keeps navigation usable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await preparePage(page);

  await expect(page.locator('.help-mobile-nav')).toBeVisible();
  await expect(page.locator('.help-sidebar')).toBeHidden();
  await expect(page.locator('.help-rail')).toBeHidden();
  await expect(page.getByRole('heading', { name: '快速上手', level: 1 })).toBeVisible();

  await page.locator('.help-mobile-nav .v-field').click();
  await page.getByRole('option', { name: /执行监控与日志/ }).click();
  await expect(page.getByRole('heading', { name: '执行监控与日志', level: 1 })).toBeVisible();
  await expect(page).toHaveURL(/chapter=operations%2F01-monitoring|chapter=operations\/01-monitoring/);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'auto' }));

  await page.screenshot({
    path: path.join(screenshotsDir, 'help-center-mobile.png'),
    fullPage: false,
  });

  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(
    path.join(artifactDir, 'help-center-mobile.json'),
    JSON.stringify(
      {
        checkedAt: new Date().toISOString(),
        viewport: { width: 390, height: 844 },
        chapter: 'operations/01-monitoring',
        screenshots: ['screenshots/help-center-mobile.png'],
      },
      null,
      2
    )
  );
});
