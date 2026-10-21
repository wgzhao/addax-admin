import { test, expect, type APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin } from './helpers/auth';

const dbName = process.env.E2E_DB_NAME || 'addax_admin_history_e2e';
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

const SOURCE_STATES = [
  { key: 'enabled', code: 'MTE', enabled: true, name: 'Matrix Enabled Source' },
  { key: 'disabled', code: 'MTD', enabled: false, name: 'Matrix Disabled Source' },
] as const;
const TABLE_STATUSES = ['N', 'W', 'R', 'Y', 'E', 'U', 'X'] as const;
const LIFECYCLES = ['current', 'retired'] as const;
const SCOPES = ['CURRENT', 'DORMANT', 'RETIRED', 'ALL'] as const;
type Scope = (typeof SCOPES)[number];

interface MatrixCase {
  key: string;
  source: (typeof SOURCE_STATES)[number]['key'];
  sourceEnabled: boolean;
  status: (typeof TABLE_STATUSES)[number];
  lifecycle: (typeof LIFECYCLES)[number];
  tid: number;
  initial: Record<Scope, boolean>;
  afterRestore: Record<Scope, boolean>;
  detailReadable?: boolean;
  editStatus?: number;
  jobEditStatus?: number;
  jobContentPreserved?: boolean;
  retireStatus?: number;
  restoreStatus?: number;
}

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

function seedMatrix(): MatrixCase[] {
  const tables: string[] = [];
  for (const source of SOURCE_STATES) {
    for (const lifecycle of LIFECYCLES) {
      for (const status of TABLE_STATUSES) {
        const tableName = `matrix_${source.key}_${lifecycle}_${status}`;
        const targetTable = `matrix_target_${source.key}_${lifecycle}_${status}`;
        tables.push(`
          insert into etl_table (
            source_db, source_table, target_db, target_table, target_options, filter, kind, retry_cnt,
            start_time, end_time, max_runtime, sid, duration, tbl_comment, status, split_pk, auto_pk,
            write_mode, target_id, created_at, updated_at, retired_at, retired_by
          )
          select 'public', '${tableName}', 'public', '${targetTable}',
                 '{"rdbms":{"createPrimaryKey":true}}'::jsonb, '1=1', 'A', 3,
                 now(), now(), 120, s.id, 0, '${source.key}/${lifecycle}/${status}', '${status}',
                 '', true, 'insert', t.id, now(), now(),
                 ${lifecycle === 'retired' ? 'now()' : 'null'},
                 ${lifecycle === 'retired' ? "'admin'" : 'null'}
          from etl_source s, etl_target t
          where s.code = '${source.code}' and t.code = 'MT_TARGET';
        `);
      }
    }
  }

  const output = psql(`
    delete from etl_job_queue where tid in (
      select id from etl_table where source_table like 'matrix_%'
    );
    delete from etl_execution where tid in (
      select id from etl_table where source_table like 'matrix_%'
    );
    delete from etl_job where tid in (
      select id from etl_table where source_table like 'matrix_%'
    );
    delete from etl_column where tid in (
      select id from etl_table where source_table like 'matrix_%'
    );
    delete from etl_table where source_table like 'matrix_%';
    delete from etl_source where code in ('MTE', 'MTD');
    delete from etl_target where code = 'MT_TARGET';

    insert into etl_target (
      code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark
    ) values (
      'MT_TARGET', 'Matrix E2E Target', 'POSTGRESQL',
      '{"url":"jdbc:postgresql://localhost:5432/${dbName}","username":"matrix","password":"matrix-secret"}',
      'wR', true, false, 'collection state matrix'
    );

    insert into etl_source (
      code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar
    ) values
      ('MTE', 'Matrix Enabled Source', 'jdbc:postgresql://localhost:5432/${dbName}',
       'matrix', 'matrix-secret', '23:59:00', true, 2, 'POSTGRESQL', 'NATURAL_DAY'),
      ('MTD', 'Matrix Disabled Source', 'jdbc:postgresql://localhost:5432/${dbName}',
       'matrix', 'matrix-secret', '23:59:00', false, 2, 'POSTGRESQL', 'NATURAL_DAY');

    ${tables.join('\n')}

    select
      case when s.enabled then 'enabled' else 'disabled' end || '|' ||
      t.status || '|' ||
      case when t.retired_at is null then 'current' else 'retired' end || '|' ||
      t.id
    from etl_table t
    join etl_source s on s.id = t.sid
    where t.source_table like 'matrix_%'
    order by s.code, t.retired_at nulls first, t.status;
  `);

  const ids = new Map(
    output
      .split('\n')
      .filter(Boolean)
      .map(line => {
        const [source, status, lifecycle, tid] = line.split('|');
        return [`${source}|${status}|${lifecycle}`, Number(tid)] as const;
      })
  );

  return SOURCE_STATES.flatMap(source =>
    LIFECYCLES.flatMap(lifecycle =>
      TABLE_STATUSES.map(status => {
        const key = `${source.key}|${status}|${lifecycle}`;
        return {
          key,
          source: source.key,
          sourceEnabled: source.enabled,
          status,
          lifecycle,
          tid: ids.get(key)!,
          initial: expectedMembership(source.enabled, status, lifecycle === 'retired'),
          afterRestore: expectedMembership(source.enabled, status, false),
        };
      })
    )
  );
}

function expectedMembership(
  sourceEnabled: boolean,
  status: string,
  retired: boolean
): Record<Scope, boolean> {
  if (retired) {
    return { CURRENT: false, DORMANT: false, RETIRED: true, ALL: true };
  }
  const dormant = !sourceEnabled || status === 'X';
  return { CURRENT: true, DORMANT: dormant, RETIRED: false, ALL: true };
}

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

async function listScope(
  request: APIRequestContext,
  token: string,
  scope: Scope
): Promise<{ content: Array<{ id: number }>; totalElements: number }> {
  const response = await request.get(
    `/api/v1/tables?page=0&pageSize=100&q=matrix_&scope=${scope}`,
    { headers: authHeaders(token) }
  );
  expect(response.ok()).toBeTruthy();
  return response.json();
}

async function memberships(
  request: APIRequestContext,
  token: string,
  tid: number
): Promise<Record<Scope, boolean>> {
  const result = {} as Record<Scope, boolean>;
  for (const scope of SCOPES) {
    const page = await listScope(request, token, scope);
    result[scope] = page.content.some(row => row.id === tid);
  }
  return result;
}

async function waitForDatabaseCount(expected: number): Promise<void> {
  await expect
    .poll(
      () => Number(psql("select count(*) from etl_table where source_table like 'matrix_%';")),
      { timeout: 5_000 }
    )
    .toBe(expected);
}

test('collection table state matrix matches lifecycle, source and table boundaries', async ({
  page,
  request,
}) => {
  test.setTimeout(120_000);
  const matrix = seedMatrix();
  expect(matrix).toHaveLength(SOURCE_STATES.length * LIFECYCLES.length * TABLE_STATUSES.length);
  await waitForDatabaseCount(matrix.length);

  const token = await apiLogin(request);
  const failures: string[] = [];
  const checks: Array<{ label: string; expected: unknown; actual: unknown; pass: boolean }> = [];
  const check = (condition: boolean, label: string, expected: unknown, actual: unknown) => {
    checks.push({ label, expected, actual, pass: condition });
    if (!condition) {
      failures.push(
        `${label}: expected=${JSON.stringify(expected)} actual=${JSON.stringify(actual)}`
      );
    }
  };

  const allScope = await listScope(request, token, 'ALL');
  check(
    allScope.content.length === matrix.length,
    'ALL contains every matrix row',
    matrix.length,
    allScope.content.length
  );

  const initialResponse = await request.get('/api/v1/tables?page=0&pageSize=100&q=matrix_', {
    headers: authHeaders(token),
  });
  const initialDefault = await initialResponse.json();
  check(
    initialDefault.totalElements === matrix.length / 2,
    'default list contains only current definitions',
    matrix.length / 2,
    initialDefault.totalElements
  );

  const legacyResponse = await request.get(
    '/api/v1/tables?page=0&pageSize=100&q=matrix_&lifecycle=ACTIVE',
    { headers: authHeaders(token) }
  );
  const legacyCurrent = await legacyResponse.json();
  check(
    legacyCurrent.totalElements === matrix.length / 2,
    'legacy ACTIVE aliases CURRENT',
    matrix.length / 2,
    legacyCurrent.totalElements
  );

  const sourcesResponse = await request.get('/api/v1/sources', {
    headers: authHeaders(token),
  });
  const sources = await sourcesResponse.json();
  for (const source of SOURCE_STATES) {
    const sourceId = sources.find((row: { code: string }) => row.code === source.code)?.id;
    const sourceResponse = await request.get(
      `/api/v1/tables?page=0&pageSize=100&q=matrix_&scope=CURRENT&sourceId=${sourceId}`,
      { headers: authHeaders(token) }
    );
    const sourcePage = await sourceResponse.json();
    check(
      sourcePage.totalElements === TABLE_STATUSES.length,
      `${source.key} source filter includes every current status`,
      TABLE_STATUSES.length,
      sourcePage.totalElements
    );
  }

  const xResponse = await request.get(
    '/api/v1/tables?page=0&pageSize=100&q=matrix_&scope=CURRENT&statuses=X',
    { headers: authHeaders(token) }
  );
  const xPage = await xResponse.json();
  check(
    xPage.totalElements === SOURCE_STATES.length,
    'table status X filter returns both source states',
    SOURCE_STATES.length,
    xPage.totalElements
  );

  const activeJobCase = matrix.find(item => item.lifecycle === 'current' && item.status === 'N')!;
  psql(`
    insert into etl_job_queue (
      tid, biz_date, priority, status, available_at, attempts, max_attempts
    ) values (
      ${activeJobCase.tid}, current_date, 100, 'pending', now() + interval '1 day', 0, 3
    );
  `);
  const blockedRetire = await request.delete(`/api/v1/tables/${activeJobCase.tid}`, {
    headers: authHeaders(token),
  });
  check(blockedRetire.status() === 400, 'active job blocks retire', 400, blockedRetire.status());
  psql(`delete from etl_job_queue where tid = ${activeJobCase.tid};`);

  const activeRestoreCase = matrix.find(
    item => item.lifecycle === 'retired' && item.status === 'N'
  )!;
  psql(`
    insert into etl_job_queue (
      tid, biz_date, priority, status, available_at, attempts, max_attempts
    ) values (
      ${activeRestoreCase.tid}, current_date, 100, 'pending', now() + interval '1 day', 0, 3
    );
  `);
  const blockedRestore = await request.post(`/api/v1/tables/${activeRestoreCase.tid}/restore`, {
    headers: authHeaders(token),
  });
  check(blockedRestore.status() === 409, 'active job blocks restore', 409, blockedRestore.status());
  psql(`delete from etl_job_queue where tid = ${activeRestoreCase.tid};`);

  for (const item of matrix) {
    const initial = await memberships(request, token, item.tid);
    for (const scope of SCOPES) {
      check(
        initial[scope] === item.initial[scope],
        `${item.key} initial ${scope}`,
        item.initial[scope],
        initial[scope]
      );
    }

    const detailResponse = await request.get(`/api/v1/tables/${item.tid}`, {
      headers: authHeaders(token),
    });
    const detailReadable = detailResponse.ok();
    item.detailReadable = detailReadable;
    check(detailReadable, `${item.key} detail readable`, true, detailReadable);

    const detail = detailReadable ? await detailResponse.json() : null;
    if (detail) {
      detail.filter = `matrix edited ${item.key}`;
    }
    const editResponse = await request.put(`/api/v1/tables/${item.tid}`, {
      headers: authHeaders(token),
      data: detail,
    });
    item.editStatus = editResponse.status();
    const expectedEditStatus = item.lifecycle === 'retired' ? 409 : 200;
    check(
      item.editStatus === expectedEditStatus,
      `${item.key} edit status`,
      expectedEditStatus,
      item.editStatus
    );

    const originalJob = JSON.stringify({ marker: item.key, lifecycle: item.lifecycle });
    psql(`
      delete from etl_job where tid = ${item.tid};
      insert into etl_job (tid, job) values (${item.tid}, '${originalJob}');
    `);
    const forgedJob = JSON.stringify({ marker: `tampered ${item.key}` });
    const jobEditResponse = await request.put(`/api/v1/tables/${item.tid}/addax-job`, {
      headers: {
        ...authHeaders(token),
        'Content-Type': 'text/plain',
      },
      data: forgedJob,
    });
    item.jobEditStatus = jobEditResponse.status();
    const expectedJobEditStatus = item.lifecycle === 'retired' ? 409 : 200;
    check(
      item.jobEditStatus === expectedJobEditStatus,
      `${item.key} addax job edit status`,
      expectedJobEditStatus,
      item.jobEditStatus
    );

    const persistedJob = psql(`select job from etl_job where tid = ${item.tid};`);
    const expectedJob = item.lifecycle === 'retired' ? originalJob : forgedJob;
    item.jobContentPreserved = persistedJob === expectedJob;
    check(
      item.jobContentPreserved,
      `${item.key} addax job content after edit attempt`,
      expectedJob,
      persistedJob
    );

    if (item.lifecycle === 'current') {
      const retireResponse = await request.delete(`/api/v1/tables/${item.tid}`, {
        headers: authHeaders(token),
      });
      item.retireStatus = retireResponse.status();
      check(item.retireStatus === 200, `${item.key} retire succeeds`, 200, item.retireStatus);
      const repeatedRetire = await request.delete(`/api/v1/tables/${item.tid}`, {
        headers: authHeaders(token),
      });
      check(
        repeatedRetire.status() === 200,
        `${item.key} retire is idempotent`,
        200,
        repeatedRetire.status()
      );
      const retired = await memberships(request, token, item.tid);
      for (const scope of SCOPES) {
        check(
          retired[scope] === expectedMembership(item.sourceEnabled, item.status, true)[scope],
          `${item.key} retired ${scope}`,
          expectedMembership(item.sourceEnabled, item.status, true)[scope],
          retired[scope]
        );
      }
    }

    const restoreResponse = await request.post(`/api/v1/tables/${item.tid}/restore`, {
      headers: authHeaders(token),
    });
    item.restoreStatus = restoreResponse.status();
    check(item.restoreStatus === 200, `${item.key} restore succeeds`, 200, item.restoreStatus);
    const repeatedRestore = await request.post(`/api/v1/tables/${item.tid}/restore`, {
      headers: authHeaders(token),
    });
    check(
      repeatedRestore.status() === 400,
      `${item.key} restore on current is rejected`,
      400,
      repeatedRestore.status()
    );
    const restored = await memberships(request, token, item.tid);
    for (const scope of SCOPES) {
      check(
        restored[scope] === item.afterRestore[scope],
        `${item.key} restored ${scope}`,
        item.afterRestore[scope],
        restored[scope]
      );
    }

    const retireBackResponse = await request.delete(`/api/v1/tables/${item.tid}`, {
      headers: authHeaders(token),
    });
    check(
      retireBackResponse.ok(),
      `${item.key} final retire succeeds`,
      200,
      retireBackResponse.status()
    );
  }

  const finalCurrent = await listScope(request, token, 'CURRENT');
  const finalDormant = await listScope(request, token, 'DORMANT');
  const finalRetired = await listScope(request, token, 'RETIRED');
  check(finalCurrent.totalElements === 0, 'final CURRENT count', 0, finalCurrent.totalElements);
  check(finalDormant.totalElements === 0, 'final DORMANT count', 0, finalDormant.totalElements);
  check(
    finalRetired.totalElements === matrix.length,
    'final RETIRED count',
    matrix.length,
    finalRetired.totalElements
  );

  // Seed one current row again to cover the management UI without duplicating the full matrix.
  const uiCase = matrix.find(item => item.source === 'disabled' && item.status === 'X')!;
  psql(`update etl_table set retired_at = null, retired_by = null where id = ${uiCase.tid};`);

  await page.goto('/table?q=matrix_disabled_current_X');
  const uiRow = page.getByRole('row', { name: /matrix_disabled_current_X/ });
  await expect(uiRow).toBeVisible({ timeout: 20_000 });
  await expect(uiRow.getByText('源已停用')).toBeVisible();
  await expect(uiRow.getByText('X', { exact: true })).toBeVisible();

  await page.locator('.ds-filters__source').click();
  await expect(page.getByText('MTD_Matrix Disabled Source（已停用）')).toBeVisible();
  await page.keyboard.press('Escape');

  await page.goto(`/table/detail/${uiCase.tid}?tab=info&tblname=matrix_disabled_current_X`);
  await expect(page.getByRole('alert').getByText('采集源已停用')).toBeVisible();
  await expect(page.getByRole('button', { name: '编辑配置' })).toBeVisible();

  await page.goto('/table?scope=DORMANT&q=matrix_disabled_current_X');
  await expect(page.getByRole('row', { name: /matrix_disabled_current_X/ })).toBeVisible();

  const artifact = path.join(artifactDir, 'collection-state-matrix.json');
  const payload = {
    checkedAt: new Date().toISOString(),
    database: dbName,
    dimensions: {
      sourceStates: SOURCE_STATES.map(source => source.key),
      tableStatuses: TABLE_STATUSES,
      lifecycles: LIFECYCLES,
    },
    combinationCount: matrix.length,
    failures,
    contractChecks: checks,
    matrix,
  };
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(artifact, JSON.stringify(payload, null, 2));
  await test.info().attach('collection-state-matrix', {
    path: artifact,
    contentType: 'application/json',
  });

  expect(failures).toEqual([]);
});
