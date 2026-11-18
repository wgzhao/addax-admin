import { test, expect, type APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin } from './helpers/auth';

/**
 * Manual runs and fillbacks are allowed on paused ('X') tables, but they must not resume automatic
 * scheduling: the run records its timestamps and execution trail while the table stays paused.
 *
 * Requires the backend under test to use a dedicated database (E2E_DB_NAME) and psql to honor the
 * standard PG* environment variables (PGHOST/PGPORT/PGUSER/PGPASSWORD).
 */
const dbName = process.env.E2E_DB_NAME || 'addax_admin_paused_e2e';
const repoRoot = fileURLToPath(new URL('../../../', import.meta.url));
const fixtureHome = path.join(repoRoot, 'frontend/tests/e2e/fixtures/fake-addax');
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

interface TableState {
  status: string;
  retryCnt: number;
  endTime: string | null;
  duration: number;
}

function tableState(tid: number): TableState {
  const row = psql(`
    select coalesce(status, ''), coalesce(retry_cnt, 0),
           coalesce(to_char(end_time, 'YYYY-MM-DD HH24:MI:SS'), ''), coalesce(duration, 0)
    from etl_table where id = ${tid};
  `);
  const [status, retryCnt, endTime, duration] = row.split('|');
  return { status, retryCnt: Number(retryCnt), endTime: endTime || null, duration: Number(duration) };
}

function latestExecutionStatus(tid: number): string {
  return psql(`
    select coalesce(status, '') from etl_execution
    where tid = ${tid} order by id desc limit 1;
  `);
}

function seed(): number {
  const fixture = fixtureHome.replaceAll("'", "''");
  const sql = `
    delete from etl_failure where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from addax_log where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from etl_statistic where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from etl_jour where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from etl_execution where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from etl_job_queue where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from etl_column_mapping where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from etl_column where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from etl_job where tid in (select id from etl_table where source_table like 'paused_e2e_%');
    delete from etl_table where source_table like 'paused_e2e_%';
    delete from etl_source where name = 'Paused E2E Source';
    delete from etl_target where code = 'PZ_TARGET';

    insert into etl_source (name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar)
    values ('Paused E2E Source', 'jdbc:postgresql://localhost:5432/${dbName}',
            'paused_user', 'paused-source-secret', '23:59:00', true, 2, 'POSTGRESQL', 'NATURAL_DAY');

    insert into etl_target (code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark)
    values ('PZ_TARGET', 'Paused E2E Target', 'POSTGRESQL',
            '{"url":"jdbc:postgresql://localhost:5432/${dbName}","username":"paused_user","password":"paused-target-secret"}',
            'wR', true, false, 'paused manual run e2e');

    insert into etl_table (
      source_db, source_table, target_db, target_table, target_options, filter, retry_cnt,
      start_time, end_time, max_runtime, sid, duration, tbl_comment, status, split_pk, auto_pk,
      write_mode, start_at, target_id, created_at, updated_at
    )
    select 'public', 'paused_e2e_source', 'public', 'paused_e2e_target',
           '{"rdbms":{"businessDateColumn":"biz_date","createPrimaryKey":true}}'::jsonb,
           '1=1', 2, now(), null, 120,
           s.id, 0, 'paused manual run e2e', 'X', '', true, 'insert', null, t.id, now(), now()
    from etl_source s, etl_target t
    where s.name = 'Paused E2E Source' and t.code = 'PZ_TARGET';

    insert into etl_column (tid, column_name, column_id, source_type, data_length, data_precision, data_scale, col_comment, target_type, target_type_full)
    select id, 'id', 1, 'bigint', 8, 19, 0, 'source id', 'BIGINT', 'bigint' from etl_table where source_table = 'paused_e2e_source';

    insert into etl_job (tid, job)
    select id, '{"job":{"content":{}}}' from etl_table where source_table = 'paused_e2e_source';

    update sys_item set item_value = '${fixture}'
    where dict_code = 1000 and item_key = 'ADDAX';

    select id from etl_table where source_table = 'paused_e2e_source';
  `;
  const output = psql(sql);
  return Number(output.split('\n').filter(Boolean).at(-1));
}

function setFailureMode(tid: number, mode: string) {
  fs.writeFileSync(path.join(fixtureHome, `fail-mode.${tid}`), mode);
}

function clearFailureMode(tid: number) {
  fs.rmSync(path.join(fixtureHome, `fail-mode.${tid}`), { force: true });
}

async function runSync(
  request: APIRequestContext,
  token: string,
  tid: number
): Promise<{ success: boolean }> {
  const response = await request.post(`/api/v1/tasks/${tid}/executions?isSync=true`, {
    headers: authHeaders(token),
    data: {},
    timeout: 120_000,
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as { success: boolean };
}

test('manual runs on a paused table record results without resuming automatic scheduling', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000);
  const tid = seed();
  const token = await apiLogin(request);
  const evidence: Array<Record<string, unknown>> = [];
  const check = (name: string, expected: unknown, actual: unknown) => {
    evidence.push({ check: name, expected, actual, result: expected === actual ? 'pass' : 'fail' });
    expect(actual, name).toEqual(expected);
  };

  const before = tableState(tid);
  check('seeded status', 'X', before.status);
  check('seeded retry budget', 2, before.retryCnt);

  // 1. A successful manual run keeps the pause and records the run.
  const success = await runSync(request, token, tid);
  check('manual run succeeded', true, success.success);
  const afterSuccess = tableState(tid);
  check('status stays paused after success', 'X', afterSuccess.status);
  check('end time recorded on success', true, afterSuccess.endTime !== null);
  check('latest execution succeeded', 'succeeded', latestExecutionStatus(tid));

  // The snapshot no longer carries the retired table kind; its version states that shape.
  const snapshot = psql(`
    select snapshot_version, coalesce(config_snapshot->'table'->>'kind', '<absent>')
    from etl_execution where tid = ${tid} order by id desc limit 1;
  `).split('|');
  check('snapshot version', 3, Number(snapshot[0]));
  check('snapshot table has no kind', '<absent>', snapshot[1]);

  // 2. A failed manual run also keeps the pause and does not consume the automatic retry budget.
  setFailureMode(tid, 'table_not_found');
  let failure: { success: boolean };
  try {
    failure = await runSync(request, token, tid);
  } finally {
    clearFailureMode(tid);
  }
  check('manual run failed', false, failure.success);
  const afterFailure = tableState(tid);
  check('status stays paused after failure', 'X', afterFailure.status);
  // A paused table's failure must not consume the automatic retry budget; the successful run
  // before it already reset the budget to the normal maximum.
  check('retry budget untouched on paused failure', afterSuccess.retryCnt, afterFailure.retryCnt);
  check('latest execution failed', 'failed', latestExecutionStatus(tid));
  check('end time advanced', true, afterFailure.endTime !== before.endTime);

  // 2b. The same holds for the queued path the UI uses (no isSync): dispatch runs on a worker with
  // a detached entity, so a full-row flush cannot overwrite the guarded status update there either.
  const executionsBefore = Number(
    psql(`select count(*) from etl_execution where tid = ${tid};`)
  );
  const submit = await request.post(`/api/v1/tasks/${tid}/executions`, {
    headers: authHeaders(token),
    data: {},
  });
  expect(submit.ok()).toBeTruthy();
  expect(((await submit.json()) as { success: boolean }).success).toBeTruthy();
  await expect
    .poll(
      () =>
        psql(`
          select coalesce(status, '') from etl_execution
          where tid = ${tid} order by id desc limit 1;
        `),
      { timeout: 60_000 }
    )
    .toBe('succeeded');
  check('queued run recorded', true, Number(psql(`select count(*) from etl_execution where tid = ${tid};`)) > executionsBefore);
  check('status stays paused after queued run', 'X', tableState(tid).status);

  // 3. The scheduler must still refuse the table: a full queue rescan enqueues nothing for it.
  const queueMaxBefore = Number(
    psql(`select coalesce(max(id), 0) from etl_job_queue where tid = ${tid};`)
  );
  const resetResponse = await request.post('/api/v1/tasks/queue/actions/reset', {
    headers: authHeaders(token),
  });
  expect(resetResponse.ok()).toBeTruthy();
  const newQueueRows = psql(`
    select count(*) from etl_job_queue where tid = ${tid} and id > ${queueMaxBefore};
  `);
  check('queue rescan leaves paused table out', '0', newQueueRows);
  check(
    'no pending queue row for paused table',
    '0',
    psql(`select count(*) from etl_job_queue where tid = ${tid} and status = 'pending';`)
  );

  // 4. The UI still presents the table as paused and still offers the manual run.
  await page.goto(`/table?scope=DORMANT&q=paused_e2e_source`);
  const row = page.getByRole('row', { name: /paused_e2e_source/ });
  await expect(row).toBeVisible({ timeout: 20_000 });
  const runButton = page.getByRole('button', { name: '发起采集 paused_e2e_source' });
  await expect(runButton).toBeVisible();
  await expect(runButton).toBeEnabled();
  await page.screenshot({
    path: path.join(artifactDir, 'paused-table-list.png'),
    fullPage: true,
  });

  await page.goto(`/table/detail/${tid}`);
  await expect(page.getByText('该采集表已停采', { exact: false }).first()).toBeVisible({
    timeout: 20_000,
  });
  await page.screenshot({
    path: path.join(artifactDir, 'paused-table-detail.png'),
    fullPage: true,
  });

  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(
    path.join(artifactDir, 'paused-table-manual-run.json'),
    JSON.stringify({ tid, dbName, checks: evidence }, null, 2)
  );
});
