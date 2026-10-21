import { test, expect, type APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin } from './helpers/auth';

const dbName = process.env.E2E_DB_NAME || 'addax_admin_history_e2e';
const repoRoot = fileURLToPath(new URL('../../../', import.meta.url));
const fixtureHome = path.join(repoRoot, 'frontend/tests/e2e/fixtures/fake-addax');
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

interface Execution {
  id: number;
  tid: number;
  status: string;
  sourceDb?: string;
  sourceTable?: string;
  targetDb?: string;
  targetTable?: string;
  jobDigest?: string | null;
  configSnapshot: {
    table: Record<string, unknown>;
    columns: Array<Record<string, unknown>>;
  };
}

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

function seed(): number {
  const fixtureHomeSql = fixtureHome.replaceAll("'", "''");
  const sql = `
    delete from addax_log where tid in (select id from etl_table where source_table like 'history_e2e_%');
    delete from etl_statistic where tid in (select id from etl_table where source_table like 'history_e2e_%');
    delete from etl_jour where tid in (select id from etl_table where source_table like 'history_e2e_%');
    delete from etl_execution where tid in (select id from etl_table where source_table like 'history_e2e_%');
    delete from etl_column where tid in (select id from etl_table where source_table like 'history_e2e_%');
    delete from etl_job where tid in (select id from etl_table where source_table like 'history_e2e_%');
    delete from etl_table where source_table like 'history_e2e_%';
    delete from etl_source where code = 'HX';
    delete from etl_target where code = 'HX_TARGET';

    insert into etl_source (code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar)
    values ('HX', 'History E2E Source', 'jdbc:postgresql://localhost:5432/${dbName}',
            'history_user', 'history-password-must-not-leak', '23:59:00', true, 2, 'POSTGRESQL', 'NATURAL_DAY');

    insert into etl_target (code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark)
    values ('HX_TARGET', 'History E2E Target', 'POSTGRESQL',
            '{"url":"jdbc:postgresql://localhost:5432/${dbName}","username":"history_user","password":"history-target-secret"}',
            'wR', true, false, 'snapshot e2e');

    insert into etl_table (
      source_db, source_table, target_db, target_table, target_options, filter, kind, retry_cnt,
      start_time, end_time, max_runtime, sid, duration, tbl_comment, status, split_pk, auto_pk,
      write_mode, start_at, target_id, created_at, updated_at
    )
    select 'public', 'history_e2e_source', 'public', 'history_e2e_target',
           '{"rdbms":{"businessDateColumn":"biz_date","createPrimaryKey":true}}'::jsonb,
           'updated_at >= 2026-01-01', 'A', 1, now(), now(), 120,
           s.id, 0, 'history snapshot e2e', 'N', '', true, 'insert', null, t.id, now(), now()
    from etl_source s, etl_target t
    where s.code = 'HX' and t.code = 'HX_TARGET';

    insert into etl_column (tid, column_name, column_id, source_type, data_length, data_precision, data_scale, col_comment, target_type, target_type_full)
    select id, 'id', 1, 'bigint', 8, 19, 0, 'source id', 'BIGINT', 'bigint' from etl_table where source_table = 'history_e2e_source';
    insert into etl_column (tid, column_name, column_id, source_type, data_length, data_precision, data_scale, col_comment, target_type, target_type_full)
    select id, 'updated_at', 2, 'timestamp', 8, null, null, 'source updated time', 'TIMESTAMP', 'timestamp' from etl_table where source_table = 'history_e2e_source';

    insert into etl_job (tid, job)
    select id, '{"job":{"content":{}}}' from etl_table where source_table = 'history_e2e_source';

    update sys_item set item_value = '${fixtureHomeSql}'
    where dict_code = 1000 and item_key = 'ADDAX';

    select id from etl_table where source_table = 'history_e2e_source';
  `;
  const output = psql(sql);
  return Number(output.split('\n').filter(Boolean).at(-1));
}

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

async function waitForExecution(
  request: APIRequestContext,
  token: string,
  tid: number,
  minimumCount = 1
): Promise<Execution> {
  let execution: Execution | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/v1/tables/${tid}/executions`, {
          headers: authHeaders(token),
        });
        expect(response.ok()).toBeTruthy();
        const page = await response.json();
        execution = page.content?.[0];
        return page.content?.length >= minimumCount ? execution?.status : 'waiting';
      },
      { timeout: 30_000, intervals: [500, 1_000, 2_000] }
    )
    .toBe('succeeded');
  return execution!;
}

test('retired tables keep execution snapshots, statistics, and raw logs', async ({
  page,
  request,
}) => {
  test.setTimeout(60_000);
  const tid = seed();
  const token = await apiLogin(request);

  const runResponse = await request.post(`/api/v1/tasks/${tid}/executions`, {
    headers: authHeaders(token),
    data: {},
  });
  expect(runResponse.ok()).toBeTruthy();

  const firstExecution = await waitForExecution(request, token, tid);
  expect(firstExecution.sourceTable).toBe('history_e2e_source');
  expect(firstExecution.targetTable).toBe('history_e2e_target');
  expect(firstExecution.jobDigest).toMatch(/^[a-f0-9]{64}$/);
  expect(firstExecution.configSnapshot.table.sourceTable).toBe('history_e2e_source');
  expect(firstExecution.configSnapshot.table.targetTable).toBe('history_e2e_target');
  expect(firstExecution.configSnapshot.columns).toHaveLength(2);
  expect(JSON.stringify(firstExecution)).not.toContain('history-password-must-not-leak');
  expect(JSON.stringify(firstExecution)).not.toContain('history-target-secret');

  psql(`
    update etl_table
       set target_table = 'history_e2e_target_v2',
           filter = 'updated_at is not null'
     where id = ${tid};
    update etl_column
       set col_comment = 'source updated time v2'
     where tid = ${tid} and column_name = 'updated_at';
  `);
  const secondRun = await request.post(`/api/v1/tasks/${tid}/executions`, {
    headers: authHeaders(token),
    data: {},
  });
  expect(secondRun.ok()).toBeTruthy();
  const execution = await waitForExecution(request, token, tid, 2);
  expect(execution.id).not.toBe(firstExecution.id);
  expect(execution.targetTable).toBe('history_e2e_target_v2');
  expect(execution.configSnapshot.table.targetTable).toBe('history_e2e_target_v2');
  expect(
    JSON.stringify(execution.configSnapshot.columns).includes('source updated time v2')
  ).toBeTruthy();

  const executionPage = await request.get(`/api/v1/tables/${tid}/executions`, {
    headers: authHeaders(token),
  });
  expect(executionPage.ok()).toBeTruthy();
  const executions = (await executionPage.json()).content as Execution[];
  const preservedFirst = executions.find(item => item.id === firstExecution.id);
  expect(preservedFirst?.targetTable).toBe('history_e2e_target');
  expect(preservedFirst?.configSnapshot.columns).toHaveLength(2);

  let statistics: Array<{ executionId?: number }> = [];
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/v1/tables/${tid}/statistics`, {
          headers: authHeaders(token),
        });
        expect(response.ok()).toBeTruthy();
        statistics = await response.json();
        return statistics[0]?.executionId;
      },
      { timeout: 15_000 }
    )
    .toBe(execution.id);

  let logs: Array<{ id: number; executionId?: number }> = [];
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/v1/log/addax/${tid}`, {
          headers: authHeaders(token),
        });
        expect(response.ok()).toBeTruthy();
        const body = await response.json();
        logs = body.data || [];
        return logs.some(item => item.executionId === execution.id) ? execution.id : undefined;
      },
      { timeout: 15_000 }
    )
    .toBe(execution.id);
  expect(logs.some(item => item.executionId === firstExecution.id)).toBeTruthy();

  await page.goto(`/table?q=history_e2e_source`);
  const row = page.getByRole('row', { name: /history_e2e_source/ });
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole('button', { name: /退役采集表 history_e2e_source/ }).click();
  const retireButton = page.getByRole('button', { name: '确认退役' });
  await expect(retireButton).toBeVisible();
  await retireButton.click();
  await expect(page.getByText('采集表已退役，历史数据已保留')).toBeVisible();

  const detailResponse = await request.get(`/api/v1/tables/${tid}`, {
    headers: authHeaders(token),
  });
  expect(detailResponse.ok()).toBeTruthy();
  const retiredDetail = await detailResponse.json();
  expect(retiredDetail.retiredAt).toBeTruthy();
  const hiddenActivePage = await request.get(
    `/api/v1/tables?page=0&pageSize=20&lifecycle=ACTIVE&q=history_e2e_source`,
    { headers: authHeaders(token) }
  );
  expect(hiddenActivePage.ok()).toBeTruthy();
  const hiddenActive = await hiddenActivePage.json();
  expect(hiddenActive.content.some((row: { id: number }) => row.id === tid)).toBeFalsy();
  const blockedRefresh = await request.post(`/api/v1/tables/${tid}/actions/refresh`, {
    headers: authHeaders(token),
  });
  expect(blockedRefresh.status()).toBe(409);

  await page.goto(`/table?lifecycle=RETIRED&q=history_e2e_source`);
  await expect(page.getByRole('row', { name: /history_e2e_source/ })).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByText(/已退役 · admin/)).toBeVisible();

  await page.goto(`/table/detail/${tid}?tab=executions`);
  await expect(page.getByText(/采集表详情:/)).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText('该采集表已退役')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText(`#${execution.id} / 1`)).toBeVisible();
  await expect(page.getByText(`#${firstExecution.id} / 1`)).toBeVisible();
  await expect(page.getByText('public.history_e2e_target', { exact: true })).toBeVisible();
  await expect(page.getByText('public.history_e2e_target_v2', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '保存更改' })).toHaveCount(0);

  await page.getByRole('tab', { name: '采集结果' }).click();
  await expect(page.getByText('结果明细表')).toBeVisible();

  await page.getByRole('tab', { name: '采集日志' }).click();
  await expect(page.getByText('日志列表', { exact: true })).toBeVisible();
  await page.locator('.log-list .v-list-item').first().click();
  await expect(page.getByText('fake addax execution', { exact: false })).toBeVisible({
    timeout: 20_000,
  });

  psql(`
    insert into addax_log (tid, execution_id, run_at, run_date, log)
    values (
      ${tid},
      ${execution.id},
      timestamp '2025-01-01 00:00:00',
      date '2025-01-01',
      'expired raw log sentinel'
    );
  `);
  const cleanup = await request.post('/api/v1/log/addax/cleanup?before=2025-02-01', {
    headers: authHeaders(token),
  });
  expect(cleanup.ok()).toBeTruthy();
  await expect
    .poll(
      () =>
        Number(
          psql(`
            select count(*) from addax_log
            where tid = ${tid} and log = 'expired raw log sentinel';
          `)
        ),
      { timeout: 15_000 }
    )
    .toBe(0);
  expect(Number(psql(`select count(*) from etl_execution where tid = ${tid};`))).toBe(2);
  expect(Number(psql(`select count(*) from etl_statistic where tid = ${tid};`))).toBe(1);

  const retiredPage = await request.get(
    `/api/v1/tables?page=0&pageSize=20&lifecycle=RETIRED&q=history_e2e_source`,
    { headers: authHeaders(token) }
  );
  expect(retiredPage.ok()).toBeTruthy();
  const retiredList = await retiredPage.json();

  const restore = await request.post(`/api/v1/tables/${tid}/restore`, {
    headers: authHeaders(token),
  });
  expect(restore.ok()).toBeTruthy();
  const restored = await restore.json();
  expect(restored.retiredAt).toBeNull();
  const activePage = await request.get(
    `/api/v1/tables?page=0&pageSize=20&lifecycle=ACTIVE&q=history_e2e_source`,
    { headers: authHeaders(token) }
  );
  expect(activePage.ok()).toBeTruthy();
  const activeList = await activePage.json();
  expect(activeList.content.some((row: { id: number }) => row.id === tid)).toBeTruthy();

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifact = path.join(artifactDir, 'history-snapshot.json');
  const payload = {
    checkedAt: new Date().toISOString(),
    database: dbName,
    tid,
    execution,
    statistics,
    logs,
    retiredList,
    activeList,
  };
  fs.writeFileSync(artifact, JSON.stringify(payload, null, 2));
  await test.info().attach('history-snapshot', {
    path: artifact,
    contentType: 'application/json',
  });
});
