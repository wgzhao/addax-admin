import { test, expect, type APIRequestContext } from '@playwright/test';
import { apiLogin } from './helpers/auth';

interface TableRow {
  id: number;
  sourceTable: string;
  targetDb: string;
  targetTable: string;
  targetType?: string | null;
  targetName?: string | null;
  targetOptions?: {
    hdfs?: { partName?: string };
    rdbms?: { businessDateColumn?: string; createPrimaryKey?: boolean };
  } | null;
}

const RDBMS_TYPES = ['RDBMS', 'MYSQL', 'POSTGRESQL', 'SQLSERVER'];

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

async function listTables(request: APIRequestContext, token: string): Promise<TableRow[]> {
  const res = await request.get('/api/v1/tables?page=0&pageSize=500', {
    headers: authHeaders(token),
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
  const token = await apiLogin(request);
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

/**
 * Fillback needs a per-target date dimension (an HDFS partition column or an RDBMS business date
 * column). An RDBMS target without a business date column must be refused, otherwise every fillback
 * date would write into the same rows.
 */
test('fillback refuses an RDBMS table without a business date column', async ({ request }) => {
  const token = await apiLogin(request);
  const tables = await listTables(request, token);

  const target = tables.find(
    t => familyOf(t) === 'rdbms' && !t.targetOptions?.rdbms?.businessDateColumn
  );
  expect(target, 'expected an RDBMS target table without a business date column').toBeTruthy();

  const res = await request.post('/api/v1/tasks/fillback', {
    headers: authHeaders(token),
    data: { tids: [target!.id], startDate: '2026-01-01', endDate: '2026-01-01' },
  });
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  const detail = body.data.details[0];

  expect(detail.enqueued).toBe(0);
  expect(detail.skipped).toBe(true);
  expect(detail.reason).toContain('日期维度');

  const summary = `tid=${target!.id} ${target!.targetDb}.${target!.targetTable} → skipped=${
    detail.skipped
  }, reason=${detail.reason}`;
  console.log(`[fillback-date-dimension] ${summary}`);
  await test.info().attach('fillback-date-dimension', {
    body: summary,
    contentType: 'text/plain',
  });
});

/**
 * The save payload must write the section the picked target family uses and leave the other section
 * exactly as stored. Writing a fabricated Hive section for an RDBMS target left a misleading `partName`
 * behind, while replacing the bag wholesale dropped the other family's settings when a table switched
 * targets.
 */
test('saving an RDBMS table writes rdbms options and keeps the stored hdfs options', async ({
  page,
  request,
}) => {
  const token = await apiLogin(request);
  const tables = await listTables(request, token);
  const rdbms = tables.find(t => familyOf(t) === 'rdbms');
  expect(rdbms, 'expected at least one RDBMS target table').toBeTruthy();
  const storedOptions = rdbms!.targetOptions ?? {};

  let payload: Record<string, unknown> | null = null;
  // Intercept the save so the assertion cannot mutate the fixture table.
  await page.route('**/api/v1/tables/*', async route => {
    if (route.request().method() === 'PUT') {
      payload = route.request().postDataJSON();
      await route.abort();
    } else {
      await route.continue();
    }
  });

  await page.goto(`/table/detail/${rdbms!.id}`);
  await expect(page.getByText('快照业务日期列', { exact: true })).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: '编辑配置' }).click();
  await page.getByRole('button', { name: '保存更改' }).click();

  await expect.poll(() => payload !== null, { timeout: 15_000 }).toBe(true);
  const options = (payload as unknown as { targetOptions: Record<string, unknown> }).targetOptions;
  expect(options.rdbms).toBeTruthy();
  // Present as stored, absent (undefined) when the row never had one — never a fabricated default.
  expect(options.hdfs).toEqual(storedOptions.hdfs);

  const summary = `tid=${rdbms!.id} payload.targetOptions=${JSON.stringify(options)}`;
  console.log(`[target-options-payload] ${summary}`);
  await test.info().attach('target-options-payload', {
    body: summary,
    contentType: 'text/plain',
  });
});

/**
 * Snapshot collection only works while the reader injects the business date last and the writer appends
 * the matching column last, in the same position. Nothing but these two code paths enforce that, so pin
 * the generated job: same amount of reader and writer columns, with the probe column in both.
 *
 * The probe is written through the API and reverted in `finally`; the target table itself is never
 * touched because regenerating a job does not run the target DDL.
 */
test('snapshot job keeps reader and writer columns aligned', async ({ request }) => {
  const token = await apiLogin(request);
  const tables = await listTables(request, token);
  const target = tables.find(t => familyOf(t) === 'rdbms');
  expect(target, 'expected at least one RDBMS target table').toBeTruthy();

  const detailUrl = `/api/v1/tables/${target!.id}`;
  const detail = await (await request.get(detailUrl, { headers: authHeaders(token) })).json();
  const storedOptions = detail.targetOptions ?? {};
  const probe = 'addax_snapshot_probe';

  const jobColumns = async () => {
    const res = await request.get(`/api/v1/tables/${target!.id}/addax-job`, {
      headers: authHeaders(token),
    });
    expect(res.ok()).toBeTruthy();
    const job = await res.json();
    return {
      reader: job.job.content.reader.parameter.column as string[],
      writer: job.job.content.writer.parameter.column as string[],
    };
  };

  const writeOptions = async (targetOptions: unknown) => {
    const res = await request.put(detailUrl, {
      headers: authHeaders(token),
      data: { ...detail, targetOptions },
    });
    expect(res.ok()).toBeTruthy();
    const regenerate = await request.put(`/api/v1/tasks/${target!.id}/addax-job`, {
      headers: authHeaders(token),
    });
    expect(regenerate.ok()).toBeTruthy();
  };

  try {
    await writeOptions({
      ...storedOptions,
      rdbms: { ...(storedOptions.rdbms ?? {}), businessDateColumn: probe, createPrimaryKey: true },
    });

    const { reader, writer } = await jobColumns();
    expect(reader).toHaveLength(writer.length);
    expect(reader[reader.length - 1]).toContain(`as ${probe}`);
    expect(writer[writer.length - 1]).toContain(probe);

    const summary = `tid=${target!.id} columns=${reader.length}\nreader.last=${
      reader[reader.length - 1]
    }\nwriter.last=${writer[writer.length - 1]}`;
    console.log(`[snapshot-column-alignment] ${summary}`);
    await test.info().attach('snapshot-column-alignment', {
      body: summary,
      contentType: 'text/plain',
    });
  } finally {
    await writeOptions(storedOptions);
    const restored = await (await request.get(detailUrl, { headers: authHeaders(token) })).json();
    expect(restored.targetOptions ?? {}).toEqual(storedOptions);
  }
});
