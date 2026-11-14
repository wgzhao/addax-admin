import { expect, test } from '@playwright/test';
import { apiLogin } from './helpers/auth';

test('系统配置补全表结构刷新超时与 R2R/wR 模板', async ({ page, request }) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await apiLogin(request);

  await page.goto('/sys-settings');
  await expect(page.getByRole('heading', { name: '运行基础' })).toBeVisible({ timeout: 20_000 });

  // 运行参数补上了表结构刷新超时。
  await expect(page.locator('#cfg-schema-refresh-timeout')).toBeVisible();

  // 作业模板编辑器包含五个 tab：采集主模板、关系型主模板、RDBMS 读取、HDFS 写入、RDBMS 写入。
  await expect(page.getByRole('heading', { name: '作业模板' })).toBeVisible();
  for (const label of ['采集主模板', '关系型主模板', 'RDBMS 读取', 'HDFS 写入', 'RDBMS 写入']) {
    await expect(page.locator('.editor-tab', { hasText: label })).toBeVisible();
  }

  // 关系型主模板（R2R）与 RDBMS 写入（wR）是新增 tab，点击后可切换且不报错。
  await page.locator('.editor-tab', { hasText: '关系型主模板' }).click();
  await expect(page.locator('textarea.editor-input')).toHaveValue(/job/);
  await page.locator('.editor-tab', { hasText: 'RDBMS 写入' }).click();
  await expect(page.locator('textarea.editor-input')).toHaveValue(/name/);

  await test.info().attach('sys-settings-templates-summary', {
    body: 'SCHEMA_REFRESH_TIMEOUT 可见，作业模板五个 tab 齐备且可切换',
    contentType: 'text/plain',
  });
});
