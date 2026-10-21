import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin } from './helpers/auth';

interface TableRow {
  id: number;
  sourceDb: string;
  sourceTable: string;
  targetDb: string;
  targetTable: string;
  createdAt?: string | null;
}

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const screenshotsDir = fileURLToPath(new URL('../../../screenshots', import.meta.url));

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });
// 后端把 created_at 序列化成 "yyyy-MM-dd HH:mm:ss"
const TIMESTAMP = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/;

async function listTables(request: APIRequestContext, token: string): Promise<TableRow[]> {
  const res = await request.get('/api/v1/tables?page=0&pageSize=500', {
    headers: authHeaders(token),
  });
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  return body.content as TableRow[];
}

async function detailOf(request: APIRequestContext, token: string, tid: number) {
  const res = await request.get(`/api/v1/tables/${tid}`, { headers: authHeaders(token) });
  expect(res.ok()).toBeTruthy();
  return res.json();
}

// 概览区的“创建时间”卡片；用 label 定位，避免误命中页面上其它时间文案
const createdAtCard = (page: Page) => page.locator('.summary-card').filter({ hasText: '创建时间' });

const parseLocal = (value: string) => new Date(value.replace(' ', 'T'));

/**
 * 管理员要靠创建时间判断这张表最早的采集起点，所以概览区必须显示本行的 created_at：
 * 取两张创建时间不同的表交叉核对，任何跨行/跨字段的取值都会让断言失败。
 */
test('table detail shows the creation time of the very table being viewed', async ({
  page,
  request,
}) => {
  const token = await apiLogin(request);
  const tables = await listTables(request, token);
  const dated = tables.filter(t => typeof t.createdAt === 'string' && TIMESTAMP.test(t.createdAt));
  expect(dated.length, 'expected tables carrying a creation time').toBeGreaterThan(1);

  const sorted = [...dated].sort((a, b) => a.createdAt!.localeCompare(b.createdAt!));
  const oldest = sorted[0];
  const newer = sorted.find(t => t.id !== oldest.id && t.createdAt !== oldest.createdAt);
  expect(newer, 'expected two tables with different creation times').toBeTruthy();

  const seen: { tid: number; identity: string; createdAt: string; rendered: string }[] = [];
  for (const row of [oldest, newer!]) {
    await page.goto(`/table/detail/${row.id}`);
    const card = createdAtCard(page);
    await expect(card).toBeVisible({ timeout: 20_000 });
    await expect(card.locator('strong')).toHaveText(row.createdAt!);

    const rendered = (await card.locator('strong').innerText()).trim();
    expect(rendered).toBe(row.createdAt);
    expect(rendered).not.toBe(row.id === oldest.id ? newer!.createdAt : oldest.createdAt);
    // 创建时间必须是一个已经发生的时刻，否则它不能代表最早的采集起点
    expect(parseLocal(row.createdAt!).getTime()).toBeLessThanOrEqual(Date.now());

    seen.push({
      tid: row.id,
      identity: `${row.sourceDb}.${row.sourceTable} → ${row.targetDb}.${row.targetTable}`,
      createdAt: row.createdAt!,
      rendered,
    });
  }

  fs.mkdirSync(screenshotsDir, { recursive: true });
  const screenshot = path.join(screenshotsDir, 'table-detail-created-at.png');
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.screenshot({ path: screenshot });

  const summary = seen
    .map(r => `tid=${r.tid} ${r.identity} createdAt=${r.createdAt} rendered=${r.rendered}`)
    .join('\n');
  console.log(`[table-detail-created-at]\n${summary}`);

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifact = path.join(artifactDir, 'table-detail-created-at.json');
  fs.writeFileSync(
    artifact,
    JSON.stringify({ checkedAt: new Date().toISOString(), tables: seen, screenshot }, null, 2)
  );
  await test.info().attach('table-detail-created-at', {
    body: summary,
    contentType: 'text/plain',
  });
});

/**
 * 详情表单保存时会带上 createdAt，把它写丢会让 update 覆盖成 null（created_at 是 NOT NULL）。
 * 拦截 PUT 只校验载荷，不落库。
 */
test('saving the detail form keeps the creation time in the payload', async ({ page, request }) => {
  const token = await apiLogin(request);
  const tables = await listTables(request, token);
  const target = tables.find(t => typeof t.createdAt === 'string' && TIMESTAMP.test(t.createdAt));
  expect(target, 'expected a table carrying a creation time').toBeTruthy();

  const detail = await detailOf(request, token, target!.id);
  expect(detail.createdAt).toBe(target!.createdAt);

  let payload: Record<string, unknown> | null = null;
  await page.route('**/api/v1/tables/*', async route => {
    if (route.request().method() === 'PUT') {
      payload = route.request().postDataJSON();
      await route.abort();
    } else {
      await route.continue();
    }
  });

  await page.goto(`/table/detail/${target!.id}`);
  await expect(createdAtCard(page)).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: '编辑配置' }).click();
  await page.getByRole('button', { name: '保存更改' }).click();

  await expect.poll(() => payload !== null, { timeout: 15_000 }).toBe(true);
  expect((payload as unknown as { createdAt: string }).createdAt).toBe(detail.createdAt);

  const summary = `tid=${target!.id} payload.createdAt=${
    (payload as unknown as { createdAt: string }).createdAt
  } detail.createdAt=${detail.createdAt}`;
  console.log(`[table-detail-created-at-payload] ${summary}`);
  await test.info().attach('table-detail-created-at-payload', {
    body: summary,
    contentType: 'text/plain',
  });
});
