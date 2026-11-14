import { expect, test, type APIRequestContext } from '@playwright/test';
import { apiLogin } from './helpers/auth';

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

// 用不存在的源类型做测试，避免与种子映射冲突。
const SOURCE_TYPE = `e2e_${Date.now()}_type`;

async function removeMappingViaApi(
  request: APIRequestContext,
  token: string,
  sourceType: string
): Promise<void> {
  await request.delete(`/api/v1/type-mappings/${encodeURIComponent(sourceType)}`, {
    headers: authHeaders(token),
  });
}

test('类型映射页支持源类型到目标类型的增改删', async ({ page, request }) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  const token = await apiLogin(request);
  await removeMappingViaApi(request, token, SOURCE_TYPE);

  try {
    await page.goto('/type-mappings');
    await expect(page.getByRole('heading', { name: '类型映射' })).toBeVisible({ timeout: 20_000 });
    // 种子数据可见。
    await expect(page.locator('tbody')).toContainText('bigint');

    // 新增映射。
    await page.getByRole('button', { name: '新增映射' }).click();
    let dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await page.locator('#source-type').fill(SOURCE_TYPE);
    await page.locator('#target-type').fill('string');
    await dialog.getByRole('button', { name: '保存' }).click();
    await expect(page.getByText('映射已保存')).toBeVisible({ timeout: 20_000 });

    // 搜索定位到新行并编辑。
    await page.getByRole('textbox', { name: '搜索源类型' }).fill(SOURCE_TYPE);
    const row = page.locator('tbody tr').filter({ hasText: SOURCE_TYPE });
    await expect(row).toBeVisible({ timeout: 20_000 });
    await row.getByRole('button', { name: '编辑' }).click();
    dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await page.locator('#target-type').fill('decimal(19,4)');
    await dialog.getByRole('button', { name: '保存' }).click();
    await expect(page.getByText('映射已保存')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('tbody')).toContainText('decimal(19,4)');

    // 删除映射。
    await page.locator('tbody tr').filter({ hasText: SOURCE_TYPE }).getByRole('button', { name: '删除' }).click();
    const confirm = page.getByRole('dialog').filter({ hasText: '删除映射' });
    await confirm.getByRole('button', { name: '删除' }).click();
    await expect(page.getByText('映射已删除')).toBeVisible({ timeout: 20_000 });
    // 清空搜索，确认源类型已从完整列表移除（搜索未清空时无结果会切换到空态、没有 tbody）。
    await page.getByRole('textbox', { name: '搜索源类型' }).fill('');
    await expect(page.locator('tbody')).not.toContainText(SOURCE_TYPE);

    await test.info().attach('type-mapping-summary', {
      body: `sourceType=${SOURCE_TYPE} 增改删往返通过`,
      contentType: 'text/plain',
    });
  } finally {
    await removeMappingViaApi(request, token, SOURCE_TYPE);
  }
});
