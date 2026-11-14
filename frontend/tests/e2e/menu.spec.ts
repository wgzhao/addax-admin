import { test, expect } from '@playwright/test';

interface MenuCase {
  title: string;
  path: string;
  group?: string;
}

const menus: MenuCase[] = [
  { title: '首页', path: '/' },
  { title: '采集源管理', path: '/source', group: '源端管理' },
  { title: '目标端管理', path: '/target', group: '目标端管理' },
  { title: '采集表管理', path: '/table', group: '采集管理' },
  { title: '采集任务', path: '/task', group: '采集管理' },
  { title: '监控与告警', path: '/monitor', group: '采集管理' },
  { title: '数据洞察', path: '/data-insight', group: '数据管理' },
  { title: '系统状态', path: '/status', group: '系统管理' },
  { title: '系统配置', path: '/sys-settings', group: '系统管理' },
  { title: '告警配置', path: '/alert-config', group: '系统管理' },
  { title: '采集日历', path: '/calendars', group: '系统管理' },
  { title: '类型映射', path: '/type-mappings', group: '系统管理' },
  { title: '日志管理', path: '/logs', group: '系统管理' },
  { title: '安全审计', path: '/audit', group: '系统管理' },
  { title: '账号管理', path: '/accounts', group: '系统管理' },
];

for (const menu of menus) {
  test(`menu routing: ${menu.title}`, async ({ page }) => {
    await page.goto('/');

    if (menu.group) {
      // The group menu opens on hover after 80ms; clicking it can toggle a
      // hover-opened menu closed and detach the item mid-click.
      await page.getByRole('button', { name: menu.group, exact: true }).hover();
      // The dropdown item is a listitem without an accessible name; scope by
      // the menu-item class so the group button and footer links never match.
      const item = page.locator('.top-nav-menu-item').filter({ hasText: menu.title });
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

test('字典维护 is no longer exposed in the system management nav', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '系统管理', exact: true }).hover();
  await expect(page.locator('.top-nav-menu-item', { hasText: '字典维护' })).toHaveCount(0);
  // 新的语义化入口已经顶上。
  await expect(page.locator('.top-nav-menu-item', { hasText: '采集日历' })).toBeVisible();
  await expect(page.locator('.top-nav-menu-item', { hasText: '类型映射' })).toBeVisible();
});
