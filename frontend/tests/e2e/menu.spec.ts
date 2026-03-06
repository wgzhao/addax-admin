import { test, expect } from '@playwright/test'

const menus = [
  { title: '首页', path: '/' },
  { title: '监控与告警', path: '/monitor' },
  { title: '数据源管理', path: '/source' },
  { title: '采集表管理', path: '/table' },
  { title: '目标端管理', path: '/target' },
  { title: '采集任务管理', path: '/task' },
  { title: '系统配置', path: '/sys-settings' },
  { title: '日志管理', path: '/logs' },
  { title: '数据洞察', path: '/data-insight' },
  { title: '字典维护', path: '/dict' }
]

for (const menu of menus) {
  test(`menu routing: ${menu.title}`, async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: menu.title }).click()
    await expect(page).toHaveURL(new RegExp(`${menu.path === '/' ? '\\/$' : `${menu.path}$`}`))
  })
}
