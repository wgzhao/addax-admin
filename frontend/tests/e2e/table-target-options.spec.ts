import { test, expect, type APIRequestContext } from '@playwright/test';

interface TableRow {
  id: number;
  sourceTable: string;
  targetDb: string;
  targetTable: string;
  targetType?: string | null;
  targetName?: string | null;
}

const RDBMS_TYPES = ['RDBMS', 'MYSQL', 'POSTGRESQL', 'SQLSERVER'];

async function login(request: APIRequestContext): Promise<string> {
  const res = await request.post('/api/v1/auth/login', {
    data: {
      username: process.env.E2E_USERNAME || 'admin',
      password: process.env.E2E_PASSWORD || 'admin123',
    },
  });
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  expect(body.code).toBe(0);
  return body.data as string;
}

async function listTables(request: APIRequestContext, token: string): Promise<TableRow[]> {
  const res = await request.get('/api/v1/tables?page=0&pageSize=500', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  return body.content as TableRow[];
}

function familyOf(table: TableRow): 'hdfs' | 'rdbms' {
  const type = (table.targetType || 'HDFS').toUpperCase();
  return RDBMS_TYPES.includes(type) ? 'rdbms' : 'hdfs';
}

/**
 * Table attributes are stored per target family (etl_table.target_options), so the detail form must
 * render Hive fields for a Hive target and the RDBMS fields for a relational target.
 */
test('table detail renders target-specific attributes per target family', async ({
  page,
  request,
}) => {
  const token = await login(request);
  const tables = await listTables(request, token);

  const hdfs = tables.find(t => familyOf(t) === 'hdfs');
  const rdbms = tables.find(t => familyOf(t) === 'rdbms');
  expect(hdfs, 'expected at least one HDFS target table').toBeTruthy();
  expect(rdbms, 'expected at least one RDBMS target table').toBeTruthy();

  await page.goto(`/table/detail/${hdfs!.id}`);
  await expect(page.getByText('抽取与存储配置')).toBeVisible();
  await expect(page.getByText('分区字段', { exact: true })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText('存储格式', { exact: true })).toBeVisible();
  await expect(page.getByText('快照业务日期列', { exact: true })).toHaveCount(0);

  await page.goto(`/table/detail/${rdbms!.id}`);
  await expect(page.getByText('抽取与存储配置')).toBeVisible();
  await expect(page.getByText('快照业务日期列', { exact: true })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText('目标表主键', { exact: true })).toBeVisible();
  await expect(page.getByText('分区字段', { exact: true })).toHaveCount(0);
  await expect(page.getByText('存储格式', { exact: true })).toHaveCount(0);

  const summary = [
    `hdfs table id=${hdfs!.id} ${hdfs!.targetDb}.${hdfs!.targetTable} target=${hdfs!.targetName}`,
    `rdbms table id=${rdbms!.id} ${rdbms!.targetDb}.${rdbms!.targetTable} target=${
      rdbms!.targetName
    }`,
    'hdfs detail rendered partition/storage fields; rdbms detail rendered snapshot/key fields',
  ].join('\n');
  console.log(`[table-target-options]\n${summary}`);
  await test.info().attach('table-target-options-summary', {
    body: summary,
    contentType: 'text/plain',
  });
});
