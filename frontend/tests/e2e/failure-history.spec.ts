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

interface LatestFailure {
  available: boolean;
  reason: string | null;
  failure: {
    id: number;
    tid: number;
    summary: string;
    stage: string;
    category: string;
    logId: number | null;
    executionId: number | null;
    attemptNo: number | null;
    finalAttempt: boolean;
  } | null;
  occurrences: { count: number };
  evidence: { detail: string; log: string; execution: string };
  hint: string | null;
}

interface FailurePage {
  content: Array<{ id: number; stage: string; category: string; summary: string }>;
  totalElements: number;
}

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

function seed(): number {
  const fixture = fixtureHome.replaceAll("'", "''");
  const sql = `
    delete from etl_failure where tid in (select id from etl_table where source_table like 'failure_e2e_%');
    delete from addax_log where tid in (select id from etl_table where source_table like 'failure_e2e_%');
    delete from etl_statistic where tid in (select id from etl_table where source_table like 'failure_e2e_%');
    delete from etl_jour where tid in (select id from etl_table where source_table like 'failure_e2e_%');
    delete from etl_execution where tid in (select id from etl_table where source_table like 'failure_e2e_%');
    delete from etl_column_mapping where tid in (select id from etl_table where source_table like 'failure_e2e_%');
    delete from etl_column where tid in (select id from etl_table where source_table like 'failure_e2e_%');
    delete from etl_job where tid in (select id from etl_table where source_table like 'failure_e2e_%');
    delete from etl_table where source_table like 'failure_e2e_%';
    delete from etl_source where code = 'FX';
    delete from etl_target where code = 'FX_TARGET';

    insert into etl_source (code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar)
    values ('FX', 'Failure E2E Source', 'jdbc:postgresql://localhost:5432/${dbName}',
            'failure_user', 'failure-password-must-not-leak', '23:59:00', true, 2, 'POSTGRESQL', 'NATURAL_DAY');

    insert into etl_target (code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark)
    values ('FX_TARGET', 'Failure E2E Target', 'POSTGRESQL',
            '{"url":"jdbc:postgresql://localhost:5432/${dbName}","username":"failure_user","password":"failure-target-secret"}',
            'wR', true, false, 'failure facts e2e');

    insert into etl_table (
      source_db, source_table, target_db, target_table, target_options, filter, kind, retry_cnt,
      start_time, end_time, max_runtime, sid, duration, tbl_comment, status, split_pk, auto_pk,
      write_mode, start_at, target_id, created_at, updated_at
    )
    select 'public', 'failure_e2e_source', 'public', 'failure_e2e_target',
           '{"rdbms":{"businessDateColumn":"biz_date","createPrimaryKey":true}}'::jsonb,
           'updated_at >= 2026-01-01', 'A', 1, now(), now(), 120,
           s.id, 0, 'failure facts e2e', 'N', '', true, 'insert', null, t.id, now(), now()
    from etl_source s, etl_target t
    where s.code = 'FX' and t.code = 'FX_TARGET';

    insert into etl_column (tid, column_name, column_id, source_type, data_length, data_precision, data_scale, col_comment, target_type, target_type_full)
    select id, 'id', 1, 'bigint', 8, 19, 0, 'source id', 'BIGINT', 'bigint' from etl_table where source_table = 'failure_e2e_source';
    insert into etl_column (tid, column_name, column_id, source_type, data_length, data_precision, data_scale, col_comment, target_type, target_type_full)
    select id, 'updated_at', 2, 'timestamp', 8, null, null, 'source updated time', 'TIMESTAMP', 'timestamp' from etl_table where source_table = 'failure_e2e_source';

    insert into etl_job (tid, job)
    select id, '{"job":{"content":{}}}' from etl_table where source_table = 'failure_e2e_source';

    update sys_item set item_value = '${fixture}'
    where dict_code = 1000 and item_key = 'ADDAX';

    select id from etl_table where source_table = 'failure_e2e_source';
  `;
  return Number(psql(sql).split('\n').filter(Boolean).at(-1));
}

function setFailureMode(tid: number, mode: string) {
  fs.writeFileSync(path.join(fixtureHome, `fail-mode.${tid}`), mode);
}

function clearFailureMode(tid: number) {
  fs.rmSync(path.join(fixtureHome, `fail-mode.${tid}`), { force: true });
}

async function runFailure(
  request: APIRequestContext,
  token: string,
  tid: number,
  mode: string
): Promise<void> {
  setFailureMode(tid, mode);
  try {
    const response = await request.post(`/api/v1/tasks/${tid}/executions?isSync=true`, {
      headers: authHeaders(token),
      data: {},
      timeout: 120_000,
    });
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body.success).toBeFalsy();
  } finally {
    clearFailureMode(tid);
  }
}

async function runSyncFailure(
  request: APIRequestContext,
  token: string,
  tid: number
): Promise<void> {
  const response = await request.post(`/api/v1/tasks/${tid}/executions?isSync=true`, {
    headers: authHeaders(token),
    data: {},
    timeout: 120_000,
  });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.success).toBeFalsy();
}

async function latest(
  request: APIRequestContext,
  token: string,
  tid: number
): Promise<LatestFailure> {
  const response = await request.get(`/api/v1/failures/tables/${tid}/latest`, {
    headers: authHeaders(token),
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as LatestFailure;
}

test('collection failures are stored, linked, listed, and cleaned independently of raw logs', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000);
  const tid = seed();
  const token = await apiLogin(request);
  const startedAt = new Date().toISOString();
  const evidence: Array<Record<string, unknown>> = [];
  const check = (name: string, expected: unknown, actual: unknown) => {
    evidence.push({ check: name, expected, actual, result: expected === actual ? 'pass' : 'fail' });
    expect(actual, name).toEqual(expected);
  };

  // 1. First failure: the drawer must explain the missing table, not a bare exit code.
  await runFailure(request, token, tid, 'table_not_found');
  const first = await latest(request, token, tid);
  check('first failure available', true, first.available);
  check('first failure stage', 'COLLECT', first.failure?.stage);
  check('first failure category', 'TABLE_NOT_FOUND', first.failure?.category);
  check('first failure has raw log', 'available', first.evidence.log);
  check('first failure has execution', 'available', first.evidence.execution);
  check('first failure occurrences', 1, first.occurrences.count);
  expect(first.failure?.summary).toContain('missing_table');
  expect(first.failure?.finalAttempt).toBe(true);

  // 2. A different failure is a distinct fact, not an overwrite.
  await runFailure(request, token, tid, 'permission_denied');
  const second = await latest(request, token, tid);
  check('second failure category', 'AUTH', second.failure?.category);

  // 3. The same failure twice is two occurrences of one fingerprint.
  await runFailure(request, token, tid, 'table_not_found');
  const third = await latest(request, token, tid);
  check('repeated failure occurrences', 2, third.occurrences.count);

  const historyResponse = await request.get(
    `/api/v1/failures?tid=${tid}&page=0&pageSize=20&finalOnly=false`,
    { headers: authHeaders(token) }
  );
  expect(historyResponse.ok()).toBeTruthy();
  const history = (await historyResponse.json()) as FailurePage;
  check('history row count', 3, history.totalElements);
  check('history has collect stage', true, history.content.every(row => row.stage === 'COLLECT'));

  const groupsResponse = await request.get(`/api/v1/failures/groups?tid=${tid}&limit=20`, {
    headers: authHeaders(token),
  });
  expect(groupsResponse.ok()).toBeTruthy();
  const groups = (await groupsResponse.json()) as Array<{ count: number }>;
  check('group count', 2, groups.length);
  check('largest group count', 2, Math.max(...groups.map(group => group.count)));

  // 4. The table detail tab lists the history.
  await page.goto(`/table/detail/${tid}?tab=failures`);
  await expect(page.getByRole('heading', { name: '采集表详情' })).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByText('失败记录', { exact: true }).first()).toBeVisible();
  await expect(page.locator('.failure-history__summary').first()).toBeVisible({
    timeout: 20_000,
  });
  await page.screenshot({
    path: path.join(artifactDir, 'failure-history-detail-tab.png'),
    fullPage: true,
  });

  // 5. The status drawer opens from the error centre and shows the linked evidence.
  await page.goto(`/failures?tid=${tid}`);
  await expect(page.getByRole('heading', { name: '错误中心' })).toBeVisible({ timeout: 20_000 });
  await page.screenshot({
    path: path.join(artifactDir, 'failure-history-error-center.png'),
    fullPage: true,
  });
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: '导出 CSV' }).click(),
  ]);
  expect(download.suggestedFilename()).toContain('failures-');
  const firstRow = page.locator('tbody tr').first();
  await expect(firstRow).toBeVisible({ timeout: 20_000 });
  await firstRow.getByRole('button', { name: '查看' }).click();
  await expect(page.getByText('失败详情', { exact: true })).toBeVisible();
  await expect(page.getByText('原始日志', { exact: true })).toBeVisible();
  await expect(page.getByText('执行快照', { exact: true })).toBeVisible();
  await page
    .locator('.evidence-row', { hasText: '原始日志' })
    .getByRole('button', { name: '查看' })
    .click();
  await expect(page.locator('.log-dialog-content')).toContainText('ERROR', { timeout: 20_000 });
  // Let the overlay transition finish before capturing the artifact.
  await page.waitForTimeout(500);
  await page.screenshot({
    path: path.join(artifactDir, 'failure-history-drawer-log.png'),
    fullPage: true,
  });

  // 5b. The table list status icon leads to the same diagnosis.
  await page.goto(`/table?q=failure_e2e_source`);
  const tableRow = page.getByRole('row', { name: /failure_e2e_source/ });
  await expect(tableRow).toBeVisible({ timeout: 20_000 });
  await tableRow.locator('.mdi-alert-circle-outline.cursor-pointer').click();
  await expect(page.getByText('失败详情', { exact: true })).toBeVisible({ timeout: 20_000 });

  // 6. Purging raw logs must not delete the failure fact; the drawer reports the gap.
  psql(`delete from addax_log where tid = ${tid};`);
  const afterLogPurge = await latest(request, token, tid);
  check('failure survives log purge', true, afterLogPurge.available);
  check('log state after purge', 'purged', afterLogPurge.evidence.log);
  await page.goto(`/failures?tid=${tid}`);
  await page.locator('tbody tr').first().getByRole('button', { name: '查看' }).click();
  await expect(page.getByText('原始日志已按保留策略清理')).toBeVisible({ timeout: 20_000 });

  // 6b. A failure before the addax process starts (missing job template) is recorded too.
  // This used to leave the table failed with no journal row and no evidence at all.
  psql(`update etl_job set job = '' where tid = ${tid};`);
  await runSyncFailure(request, token, tid);
  const templateFailure = await latest(request, token, tid);
  check('template failure stage', 'JOB_TEMPLATE', templateFailure.failure?.stage);
  check('template failure has execution', 'available', templateFailure.evidence.execution);
  check('template failure has no raw log', 'missing', templateFailure.evidence.log);
  expect(templateFailure.failure?.summary).toContain('Job template not generated');

  // 7. Cleanup is scoped and deletes only failure facts.
  const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
  const dryRun = await request.post('/api/v1/failures/cleanup', {
    headers: authHeaders(token),
    data: { before: tomorrow, tid, dryRun: true },
  });
  expect(dryRun.ok()).toBeTruthy();
  const preview = await dryRun.json();
  check('cleanup dry-run matched', 4, preview.matched);
  check('cleanup dry-run deletes nothing', 0, preview.deleted);

  const cleanup = await request.post('/api/v1/failures/cleanup', {
    headers: authHeaders(token),
    data: { before: tomorrow, tid, dryRun: false },
  });
  expect(cleanup.ok()).toBeTruthy();
  const cleaned = await cleanup.json();
  check('cleanup deleted', 4, cleaned.deleted);

  const afterCleanup = await latest(request, token, tid);
  check('after cleanup unavailable', false, afterCleanup.available);
  check('after cleanup reason', 'PURGED', afterCleanup.reason);
  const emptyHistory = await request.get(`/api/v1/failures?tid=${tid}&page=0&pageSize=20`, {
    headers: authHeaders(token),
  });
  check('history empty after cleanup', 0, (await emptyHistory.json()).totalElements);

  // 8. A failed status with no fact is reported as missing evidence, never as a text error.
  // Remove the execution trail too; otherwise the table correctly reports PURGED instead.
  psql(`
    delete from etl_statistic where tid = ${tid};
    delete from etl_jour where tid = ${tid};
    delete from etl_execution where tid = ${tid};
    update etl_table set status = 'E' where id = ${tid};
  `);
  const noEvidence = await latest(request, token, tid);
  check('no evidence available', false, noEvidence.available);
  check('no evidence reason', 'NO_EVIDENCE_FOR_STATE', noEvidence.reason);
  expect(noEvidence.hint).toContain('没有对应的失败记录');

  // Repeatable artifact.
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(
    path.join(artifactDir, `failure-history-${Date.now()}.json`),
    JSON.stringify({ tid, startedAt, finishedAt: new Date().toISOString(), checks: evidence }, null, 2)
  );
});
