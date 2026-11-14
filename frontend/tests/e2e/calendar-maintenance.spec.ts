import { expect, test, type APIRequestContext } from '@playwright/test';
import { apiLogin } from './helpers/auth';

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

// 用 2099 年（任何环境都不会有日历数据）做单日增删的干净画布，避免依赖种子库是否为空。
const TEST_DICT = 1022;
const TEST_YEAR = 2099;
const TEST_DATE = '20990101';

async function removeDateViaApi(
  request: APIRequestContext,
  token: string,
  date: string
): Promise<void> {
  await request.delete(`/api/v1/calendars/${TEST_DICT}/dates/${date}`, {
    headers: authHeaders(token),
  });
}

test('采集日历页支持按年查看与单日增删', async ({ page, request }) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  const token = await apiLogin(request);
  // Start clean in case a previous run aborted before cleanup.
  await removeDateViaApi(request, token, TEST_DATE);

  try {
    await page.goto('/calendars');
    await expect(page.getByRole('heading', { name: '采集日历' })).toBeVisible({ timeout: 20_000 });

    // 默认选中交易日历，切到工作日历做增删。
    await page.getByRole('button', { name: /工作日历/ }).click();

    // 切到 2099 年：任何环境都没有该年份数据，稳定落在空态。
    await page.getByRole('spinbutton', { name: '年份' }).fill(String(TEST_YEAR));
    await expect(page.getByText('该年份暂无日期')).toBeVisible({ timeout: 20_000 });

    // 新增一个单日。
    await page.getByRole('button', { name: '新增日期' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await page.locator('#date-input').fill(TEST_DATE);
    await dialog.getByRole('button', { name: '保存' }).click();
    await expect(page.getByText('日期已加入日历')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('tbody')).toContainText(TEST_DATE);

    // 删除该日期，回到空态。
    const row = page.locator('tbody tr').filter({ hasText: TEST_DATE });
    await row.getByRole('button', { name: '删除' }).click();
    const confirm = page.getByRole('dialog').filter({ hasText: '删除日历日期' });
    await confirm.getByRole('button', { name: '删除' }).click();
    await expect(page.getByText('日期已删除')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('该年份暂无日期')).toBeVisible({ timeout: 20_000 });

    await test.info().attach('calendar-maintenance-summary', {
      body: [
        `dict=${TEST_DICT} year=${TEST_YEAR} date=${TEST_DATE}`,
        '工作日历单日增删往返通过，按年过滤生效',
      ].join('\n'),
      contentType: 'text/plain',
    });
  } finally {
    await removeDateViaApi(request, token, TEST_DATE);
  }
});
