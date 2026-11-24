import { test, expect, type Page } from '@playwright/test';

// The table list is a server-mode table whose page/size live in the URL. Two history entries can
// differ only by page, so the route watcher must compare the slice as well as the filters, and a
// page change must push a history entry. The table endpoint is stubbed so the scenario needs no
// fixture rows and stays deterministic; every other API call is real.
const TOTAL = 42;
const DEFAULT_SIZE = 15;

interface SliceRequest {
  page: number;
  size: number;
}

async function stubTables(page: Page): Promise<SliceRequest[]> {
  const requests: SliceRequest[] = [];
  await page.route('**/api/v1/tables**', async route => {
    const url = new URL(route.request().url());
    if (!url.pathname.endsWith('/api/v1/tables')) {
      await route.continue();
      return;
    }
    const pageIndex = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('pageSize') ?? DEFAULT_SIZE);
    requests.push({ page: pageIndex, size });
    const start = pageIndex * size;
    const content = Array.from(
      { length: Math.max(0, Math.min(size, TOTAL - start)) },
      (_, offset) => {
        const id = start + offset + 1;
        return {
          id,
          sourceDb: 'stub',
          sourceTable: `stub_table_${id}`,
          targetDb: 'ods',
          targetTable: `stub_target_${id}`,
          targetOptions: { hdfs: {}, rdbms: {}, doris: {} },
          schemaManagementMode: 'MANAGED',
          filter: '',
          status: 'N',
          retryCnt: 0,
          sid: 9001,
          duration: 0,
          name: 'Stub Source',
          dbType: 'mysql',
          enabled: true,
          writeMode: 'overwrite',
          tblComment: '',
          targetId: 9001,
          targetCode: 'STUB',
          targetName: 'Stub Target',
          targetEnabled: true,
          targetWriterTemplateKey: 'wH',
          targetType: 'HDFS',
          createdAt: '2026-11-24 10:00:00',
          updatedAt: '2026-11-24 10:00:00',
        };
      }
    );
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content,
        page: pageIndex,
        pageSize: size,
        totalElements: TOTAL,
        totalPages: Math.ceil(TOTAL / size),
        empty: content.length === 0,
      }),
    });
  });
  return requests;
}

test('table pagination pushes history and restores the slice on back/forward', async ({ page }) => {
  const requests = await stubTables(page);

  await page.goto('/table');
  await expect(page.getByText('stub.stub_table_1', { exact: true })).toBeVisible();
  expect(requests.at(-1)).toEqual({ page: 0, size: DEFAULT_SIZE });
  await expect(page).not.toHaveURL(/[?&]page=/);

  await page.getByRole('button', { name: '下一页' }).click();
  await expect(page.getByText('stub.stub_table_16', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/[?&]page=2(&|$)/);
  expect(requests.at(-1)).toEqual({ page: 1, size: DEFAULT_SIZE });

  // Back must restore the first slice instead of leaving the list or staying on page two.
  await page.goBack();
  await expect(page.getByText('stub.stub_table_1', { exact: true })).toBeVisible();
  expect(requests.at(-1)).toEqual({ page: 0, size: DEFAULT_SIZE });
  await expect(page).not.toHaveURL(/[?&]page=/);

  // Forward must return to the second slice.
  await page.goForward();
  await expect(page.getByText('stub.stub_table_16', { exact: true })).toBeVisible();
  expect(requests.at(-1)).toEqual({ page: 1, size: DEFAULT_SIZE });
  await expect(page).toHaveURL(/[?&]page=2(&|$)/);
});

test('size deep link requests the matching slice and a page change keeps it', async ({ page }) => {
  const requests = await stubTables(page);

  await page.goto('/table?page=2&size=20');
  await expect(page.getByText('stub.stub_table_21', { exact: true })).toBeVisible();
  expect(requests.at(-1)).toEqual({ page: 1, size: 20 });
  await expect(page).toHaveURL(/[?&]page=2(&|$)/);
  await expect(page).toHaveURL(/[?&]size=20(&|$)/);

  // Changing the page keeps the non-default size in the URL and in the request.
  await page.getByRole('button', { name: '下一页' }).click();
  await expect(page.getByText('stub.stub_table_41', { exact: true })).toBeVisible();
  expect(requests.at(-1)).toEqual({ page: 2, size: 20 });
  await expect(page).toHaveURL(/[?&]page=3(&|$)/);
  await expect(page).toHaveURL(/[?&]size=20(&|$)/);
});
