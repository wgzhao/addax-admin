import { test, expect } from '@playwright/test';

// These specs need a reachable MongoDB and a Mongo-backed table. They are opt-in so the normal
// frontend suite does not fail on a machine without those fixtures.
const mongoPort = process.env.E2E_MONGO_PORT || '27018';
const detailTableId = process.env.E2E_MONGO_TABLE_ID;

test.describe('MongoDB collection source', () => {
  test.beforeEach(() => {
    test.skip(!process.env.E2E_MONGO_PORT, 'requires E2E_MONGO_PORT pointing at a test MongoDB');
  });

  test('switches the source form to MongoDB and saves the connection', async ({ page }) => {
    const name = `Mongo UI E2E ${Date.now().toString().slice(-6)}`;
    await page.goto('/source');

    await page.getByRole('button', { name: '新增采集源' }).first().click();
    await page.locator('#src-name').fill(name);

    const typeInput = page.locator('#src-type');
    await typeInput.focus();
    await typeInput.press('ArrowDown');
    await page.getByRole('option', { name: 'MongoDB' }).click();

    // The JDBC URL field must disappear; the Mongo connection fields must appear.
    await expect(page.locator('#src-url')).toHaveCount(0);
    await expect(page.locator('#src-mongo-address')).toBeVisible();

    await page.locator('#src-mongo-address').fill(`127.0.0.1:${mongoPort}`);
    await page.locator('#src-mongo-db').fill('addax_e2e');
    await page.locator('#src-mongo-authdb').fill('admin');

    await page.getByRole('button', { name: '测试连接' }).click();
    await expect(page.getByText('连接成功，可以保存配置')).toBeVisible();

    await page.getByRole('button', { name: '创建采集源' }).click();
    await expect(page.getByText('保存成功')).toBeVisible();
    await expect(
      page.locator('tr', { hasText: name }).getByText('MongoDB', { exact: true })
    ).toBeVisible();
  });

  test('MongoDB table detail hides RDBMS split controls and shows JSON filter help', async ({ page }) => {
    test.skip(!detailTableId, 'requires E2E_MONGO_TABLE_ID for a Mongo-backed table');
    await page.goto(`/table/detail/${detailTableId}?tab=info`);
    await page.getByRole('button', { name: '编辑配置' }).click();
    await expect(page.getByText('切分字段')).toHaveCount(0);
    await expect(page.getByText('切分策略')).toHaveCount(0);
    await page.getByRole('button', { name: '规则说明' }).first().click();
    await expect(page.getByText('MongoDB 过滤')).toBeVisible();
  });

  test('MongoDB table detail exposes sampled source types without polluting target comments', async ({
    page,
  }) => {
    test.skip(!detailTableId, 'requires E2E_MONGO_TABLE_ID for a Mongo-backed table');
    await page.goto(`/table/detail/${detailTableId}?tab=fields`);
    await expect(page.getByText('字段映射与目标契约')).toBeVisible();

    const info = page.getByLabel('字段采样信息').first();
    await expect(info).toBeVisible();
    await info.hover();
    await expect(page.locator('.v-overlay--active .source-meta-tooltip')).toBeVisible();
  });
});
