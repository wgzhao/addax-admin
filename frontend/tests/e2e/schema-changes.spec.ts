import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Schema evolution moved out of the retired 运行监控 page. The table is server-paged,
// so this test covers the contract the page depends on: filters, sorting, paging and
// the change-summary rendering. Artifacts: tests/e2e/artifacts/schema-changes/
const artifactDir = fileURLToPath(new URL('./artifacts/schema-changes', import.meta.url));

interface Row {
  id: number;
  tid: number;
  sourceDb: string;
  sourceTable: string;
  columnName: string;
  changeType: string;
  oldSourceType?: string | null;
  newSourceType?: string | null;
  oldDataLength?: number | null;
  newDataLength?: number | null;
  changeAt: string;
}

const ROWS: Row[] = [
  {
    id: 1,
    tid: 101,
    sourceDb: 'shop',
    sourceTable: 'orders',
    columnName: 'order_status',
    changeType: 'TYPE_CHANGE',
    oldSourceType: 'varchar',
    newSourceType: 'varchar(128)',
    oldDataLength: 64,
    newDataLength: 128,
    changeAt: '2026-11-20 10:00:00',
  },
  {
    id: 2,
    tid: 102,
    sourceDb: 'shop',
    sourceTable: 'orders',
    columnName: 'archived_flag',
    changeType: 'ADD',
    newSourceType: 'boolean',
    changeAt: '2026-11-20 11:00:00',
  },
  ...Array.from({ length: 23 }, (_, i) => ({
    id: 100 + i,
    tid: 200 + i,
    sourceDb: 'crm',
    sourceTable: `customer_${i}`,
    columnName: `attr_${i}`,
    changeType: i % 2 === 0 ? 'ADD' : 'DELETE',
    oldSourceType: i % 2 === 0 ? null : 'varchar',
    newSourceType: i % 2 === 0 ? 'bigint' : null,
    changeAt: `2026-11-${String(10 + (i % 10)).padStart(2, '0')} 09:00:00`,
  })),
];

function pageResponse(rows: Row[], page: number, pageSize: number) {
  const start = page * pageSize;
  const content = rows.slice(start, start + pageSize);
  return {
    content,
    page,
    pageSize,
    totalElements: rows.length,
    totalPages: Math.ceil(rows.length / pageSize),
    empty: rows.length === 0,
  };
}

async function mockSchemaChanges(page: Page, requests: URL[]) {
  await page.route('**/api/v1/schema-changes**', route => {
    const url = new URL(route.request().url());
    requests.push(url);

    const pageNo = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('pageSize') ?? 20);
    const keyword = (url.searchParams.get('keyword') ?? '').toLowerCase();
    const changeType = url.searchParams.get('changeType');
    const sortField = url.searchParams.get('sortField');
    const sortOrder = url.searchParams.get('sortOrder');

    let rows = ROWS.filter(row => {
      const matchesKeyword =
        !keyword ||
        [row.sourceDb, row.sourceTable, row.columnName].some(value =>
          value.toLowerCase().includes(keyword)
        );
      const matchesType = !changeType || row.changeType === changeType;
      return matchesKeyword && matchesType;
    });

    if (sortField) {
      const direction = sortOrder === 'asc' ? 1 : -1;
      rows = [...rows].sort((a, b) => {
        const left = String((a as unknown as Record<string, unknown>)[sortField] ?? '');
        const right = String((b as unknown as Record<string, unknown>)[sortField] ?? '');
        return left.localeCompare(right) * direction;
      });
    }

    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(pageResponse(rows, pageNo, size)),
    });
  });
}

test('表结构变更支持筛选、排序、分页并跳转采集表', async ({ page }) => {
  const requests: URL[] = [];
  await mockSchemaChanges(page, requests);
  fs.mkdirSync(artifactDir, { recursive: true });

  await page.goto('/schema-changes');
  await expect(page.getByRole('heading', { name: '表结构变更' })).toBeVisible();

  const table = page.getByTestId('app-data-table');
  await expect(table).toBeVisible({ timeout: 20_000 });
  await expect(table.locator('tbody')).toContainText('order_status');
  await expect(table.locator('tbody')).toContainText('类型: varchar → varchar(128)');
  await expect(table.locator('a[href="/table/detail/101"]')).toBeVisible();
  await expect(table.locator('tbody tr')).toHaveCount(20);

  // Keyword filtering goes to the server and narrows the result set.
  await page.getByRole('textbox', { name: '库名 / 表名 / 字段名' }).fill('order_status');
  await page.getByRole('button', { name: '查询' }).click();
  await expect
    .poll(() => requests.some(url => url.searchParams.get('keyword') === 'order_status'))
    .toBe(true);
  await expect(table.locator('tbody tr')).toHaveCount(1);
  await expect(table.locator('tbody')).toContainText('order_status');

  // Reset restores the full first page.
  await page.getByRole('button', { name: '重置' }).click();
  await expect(table.locator('tbody tr')).toHaveCount(20);

  // Sorting is server-side; the header key must reach the backend.
  await page.locator('th').filter({ hasText: '库名' }).first().click();
  await expect
    .poll(() => requests.some(url => url.searchParams.get('sortField') === 'sourceDb'))
    .toBe(true);

  // The footer's next button requests the second page (0-based backend page).
  await page.locator('.v-data-table-footer button').last().click();
  await expect.poll(() => requests.some(url => url.searchParams.get('page') === '1')).toBe(true);
  await expect(table.locator('tbody')).toContainText('attr_');

  await page.screenshot({ path: path.join(artifactDir, 'schema-changes.png') });
  fs.writeFileSync(
    path.join(artifactDir, 'requests.json'),
    JSON.stringify(
      requests.map(url => url.searchParams.toString()),
      null,
      2
    )
  );
  await test.info().attach('schema-changes-requests', {
    body: requests.map(url => url.searchParams.toString()).join('\n'),
    contentType: 'text/plain',
  });
});
