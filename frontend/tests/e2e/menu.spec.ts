import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

interface MenuCase {
  title: string;
  path: string;
  group?: string;
}

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

// Why: the navigation follows the collection lifecycle (configure -> run -> inspect data
// -> administer). Keep this table in sync with the navGroup values in the view route blocks.
const menus: MenuCase[] = [
  { title: '首页', path: '/' },
  { title: '采集源管理', path: '/source', group: '采集配置' },
  { title: '目标端管理', path: '/target', group: '采集配置' },
  { title: '采集表管理', path: '/table', group: '采集配置' },
  { title: '类型映射', path: '/type-mappings', group: '采集配置' },
  { title: '采集日历', path: '/calendars', group: '采集配置' },
  { title: '采集任务', path: '/task', group: '采集运行' },
  { title: '错误中心', path: '/failures', group: '采集运行' },
  { title: '系统状态', path: '/status', group: '采集运行' },
  { title: '日志管理', path: '/logs', group: '采集运行' },
  { title: '告警配置', path: '/alert-config', group: '采集运行' },
  { title: '数据洞察', path: '/data-insight', group: '数据治理' },
  { title: '数据血缘', path: '/lineage', group: '数据治理' },
  { title: '表结构变更', path: '/schema-changes', group: '数据治理' },
  { title: '系统配置', path: '/sys-settings', group: '系统管理' },
  { title: '账号管理', path: '/accounts', group: '系统管理' },
  { title: '安全审计', path: '/audit', group: '系统管理' },
];

async function openGroup(page: Page, group: string) {
  // Why: menus open after an 80ms hover delay and the previous one stays visible through
  // its 120ms close delay; wait for the old overlay to go away instead of sleeping, so the
  // helper stays stable under parallel workers.
  await page.mouse.move(0, 0);
  await expect(page.locator('.top-nav-menu-list:visible:not(.user-nav-menu-list)')).toHaveCount(0);
  await page.getByRole('button', { name: group, exact: true }).hover();
  const menu = page.locator('.top-nav-menu-list:visible:not(.user-nav-menu-list)').last();
  await expect(menu).toBeVisible();
  return menu;
}

for (const menu of menus) {
  test(`menu routing: ${menu.title}`, async ({ page }) => {
    await page.goto('/');

    if (menu.group) {
      // The group menu opens on hover after 80ms; clicking it can toggle a
      // hover-opened menu closed and detach the item mid-click.
      const list = await openGroup(page, menu.group);
      // The dropdown item is a listitem without an accessible name; scope by
      // the menu-item class so the group button and footer links never match.
      const item = list.locator('.top-nav-menu-item').filter({ hasText: menu.title });
      await expect(item).toBeVisible();
      await item.click();
    } else {
      // The brand lockup's accessible name also contains "首页"; exact avoids
      // matching it instead of the nav item.
      await page.getByRole('link', { name: menu.title, exact: true }).click();
    }

    await expect(page).toHaveURL(new RegExp(`${menu.path === '/' ? '\\/$' : `${menu.path}$`}`));
  });
}

test('every top-level group has at least two children', async ({ page }) => {
  await page.goto('/');

  for (const group of ['采集配置', '采集运行', '数据治理', '系统管理']) {
    const list = await openGroup(page, group);
    const labels = await list.locator('.top-nav-menu-item .v-list-item-title').allInnerTexts();
    expect(labels.length, `${group} should not be a single-child dropdown`).toBeGreaterThan(1);
    await page.mouse.move(0, 0);
  }
});

test('system management is administration only', async ({ page }) => {
  await page.goto('/');

  const list = await openGroup(page, '系统管理');
  const labels = await list.locator('.top-nav-menu-item .v-list-item-title').allInnerTexts();
  expect(labels).toEqual(['系统配置', '账号管理', '安全审计']);
});

test('destructive structure refresh lives on the table page, not in navigation', async ({
  page,
}) => {
  await page.goto('/table');

  const trigger = page.getByRole('button', { name: '结构更新' });
  await expect(trigger).toBeVisible();
  await trigger.click();
  await expect(page.getByText('按需更新表信息')).toBeVisible();
  await expect(page.getByText('强制更新全部表信息')).toBeVisible();
  await page.keyboard.press('Escape');

  for (const group of ['采集配置', '采集运行']) {
    const list = await openGroup(page, group);
    await expect(list.locator('.top-nav-menu-item', { hasText: '更新表信息' })).toHaveCount(0);
    await page.mouse.move(0, 0);
  }
});

test('help center is reachable from the account menu', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('button', { name: '账号菜单', exact: true }).click();
  const help = page.locator('.top-nav-menu-item', { hasText: '帮助中心' });
  await expect(help).toBeVisible();
  await help.click();
  await expect(page).toHaveURL(/\/help/);
});

test('command palette mirrors the navigation tree', async ({ page }) => {
  await page.goto('/');
  await page.keyboard.press('Control+k');

  const palette = page.locator('.palette');
  await expect(palette).toBeVisible();

  // Exact order guards against the palette drifting back into its own hand-written list.
  const labels = await palette.locator('.palette-label').allInnerTexts();
  expect(labels).toEqual([
    '首页',
    '采集源管理',
    '目标端管理',
    '采集表管理',
    '类型映射',
    '采集日历',
    '采集任务',
    '错误中心',
    '系统状态',
    '日志管理',
    '告警配置',
    '数据洞察',
    '数据血缘',
    '表结构变更',
    '系统配置',
    '账号管理',
    '安全审计',
    '新增采集源',
    '新增目标端',
    '新增采集表',
    '切换浅色 / 深色主题',
  ]);

  const groups = await palette.locator('.palette-group').allInnerTexts();
  expect(groups).toEqual(['跳转', '采集配置', '采集运行', '数据治理', '系统管理', '操作']);
});

test('navigation regroup artifact', async ({ page }) => {
  await page.goto('/');
  fs.mkdirSync(artifactDir, { recursive: true });

  const snapshot: Record<string, string[]> = {};
  for (const group of ['采集配置', '采集运行', '数据治理', '系统管理']) {
    const list = await openGroup(page, group);
    snapshot[group] = await list.locator('.top-nav-menu-item .v-list-item-title').allInnerTexts();
    // Let the overlay enter transition finish before capturing, otherwise the panel
    // is still unpainted.
    await page.waitForTimeout(200);
    // Why: element screenshots wait for stability, but the hover menu detaches on
    // mouse-out; a viewport capture records the open menu without that race.
    await page.screenshot({
      path: path.join(artifactDir, `navigation-${group}.png`),
    });
    await page.mouse.move(0, 0);
    await page.waitForTimeout(200);
  }

  const artifact = {
    generatedAt: new Date().toISOString(),
    groups: snapshot,
  };
  fs.writeFileSync(
    path.join(artifactDir, 'navigation-regroup.json'),
    `${JSON.stringify(artifact, null, 2)}\n`
  );
});
