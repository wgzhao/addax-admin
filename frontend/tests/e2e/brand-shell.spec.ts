import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

test.use({ viewport: { width: 1440, height: 900 } });

test.describe('brand shell', () => {
  test('uses one product identity across the shell', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/Addax Admin/);
    await expect(page.locator('.brand-name').first()).toHaveText('Addax Admin');
    await expect(page.locator('.brand-sub').first()).toHaveText('数据采集管理平台');

    const favicon = page.locator('link[rel="icon"]');
    await expect(favicon).toHaveAttribute('href', '/favicon.svg');

    await expect(page.locator('.footer-link', { hasText: '文档' })).toBeVisible();
    await expect(page.locator('.footer-link', { hasText: '系统状态' })).toBeVisible();
    await expect(page.locator('.footer-link', { hasText: '支持' })).toBeVisible();

    fs.mkdirSync(artifactDir, { recursive: true });
    await page.locator('.topbar-bar').screenshot({
      path: path.join(artifactDir, 'brand-shell-topbar.png'),
    });
    await page.locator('.app-footer').screenshot({
      path: path.join(artifactDir, 'brand-shell-footer.png'),
    });

    const artifact = {
      generatedAt: new Date().toISOString(),
      title: await page.title(),
      favicon: await favicon.getAttribute('href'),
      product: await page.locator('.brand-name').first().innerText(),
      subtitle: await page.locator('.brand-sub').first().innerText(),
    };
    fs.writeFileSync(
      path.join(artifactDir, 'brand-shell.json'),
      JSON.stringify(artifact, null, 2)
    );
  });

  test('shows build and environment in the version dialog', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'admin', exact: true }).click();
    await page.getByText('系统版本', { exact: true }).click();

    const dialog = page.locator('.version-dialog-card');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('构建');
    await expect(dialog).toContainText('环境');
    await expect(dialog).toContainText('许可');
    await expect(dialog).toContainText('更新日志');
    await dialog.getByRole('button', { name: '关闭' }).click();
  });

  test('renders the notification menu from the existing store', async ({ page }) => {
    await page.goto('/');
    const trigger = page.locator('.notification-trigger');
    await expect(trigger).toBeVisible();
    await trigger.click();

    const card = page.locator('.notification-card');
    await expect(card).toBeVisible();
    await expect(card.locator('.notification-title')).toHaveText('通知');
    await expect(card.locator('.notification-item').first()).toBeVisible();
    await expect(card.locator('.notification-item-title').first()).not.toBeEmpty();
  });

  test('renders system status and 404 pages', async ({ page }) => {
    await page.goto('/status');
    await expect(page.getByRole('heading', { name: '系统状态' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '集群节点' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '队列与调度' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '服务信息' })).toBeVisible();
    await page.screenshot({
      path: path.join(artifactDir, 'status-page.png'),
      fullPage: true,
    });

    await page.goto('/this-page-does-not-exist');
    await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible();
    await expect(page.getByText('404')).toBeVisible();
    await page.screenshot({
      path: path.join(artifactDir, 'not-found-page.png'),
    });
  });
});
