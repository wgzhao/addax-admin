import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 错误中心 -> 失败详情 -> 执行快照 的入口链路：详情页拿不到调用方的 tblname 参数，
// 表标识必须来自详情接口本身；停留在非 info 标签时，标签切换不能被未挂载的编辑器拦住。
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const dbName = process.env.E2E_DB_NAME || 'addax_admin';
const dbHost = process.env.E2E_DB_HOST || 'localhost';
const dbUser = process.env.E2E_DB_USER || 'addax_admin';
const dbPassword = process.env.E2E_DB_PASSWORD || 'addax_admin@123';

function psql(sql: string): string {
  return execFileSync(
    'psql',
    ['-h', dbHost, '-U', dbUser, '-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql],
    { encoding: 'utf8', env: { ...process.env, PGPASSWORD: dbPassword } }
  ).trim();
}

interface SeededFailureLink {
  tid: number;
  executionId: number;
  identity: string;
}

function seed(): SeededFailureLink {
  const tid = Number(
    psql(`
    delete from etl_failure
    where tid in (select id from etl_table where source_table like 'failure_link_e2e_%');
    delete from etl_execution
    where tid in (select id from etl_table where source_table like 'failure_link_e2e_%');
    delete from etl_table where source_table like 'failure_link_e2e_%';
    delete from etl_source where name = 'Failure Link E2E Source';
    delete from etl_target where code = 'FL_TARGET';

    insert into etl_source (name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar)
    values ('Failure Link E2E Source', 'jdbc:postgresql://localhost:5432/${dbName}',
            'failure_link_user', 'failure-link-password', '23:10:00', true, 2, 'POSTGRESQL', 'NATURAL_DAY');

    insert into etl_target (code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark)
    values ('FL_TARGET', 'Failure Link E2E Target', 'POSTGRESQL',
            '{"url":"jdbc:postgresql://localhost:5432/${dbName}","username":"failure_link_user","password":"failure-link-target-secret"}',
            'wR', true, false, 'failure link e2e');

    insert into etl_table (
      source_db, source_table, target_db, target_table, target_options, filter, retry_cnt,
      start_time, end_time, max_runtime, sid, duration, tbl_comment, status, split_pk, auto_pk,
      write_mode, start_at, target_id, created_at, updated_at
    )
    select 'failure_link_db', 'failure_link_e2e_source', 'ods', 'failure_link_e2e_target',
           '{"rdbms":{"businessDateColumn":"biz_date","createPrimaryKey":true}}'::jsonb,
           '1=1', 3, now() - interval '2 hour', now() - interval '1 hour', 120,
           s.id, 0, 'failure link e2e', 'E', '', true, 'overwrite', null, t.id, now(), now()
    from etl_source s, etl_target t
    where s.name = 'Failure Link E2E Source' and t.code = 'FL_TARGET';

    insert into etl_execution (
      tid, biz_date, source_db, source_table, status, started_at, finished_at, config_snapshot, error_message
    )
    select id, current_date, 'failure_link_db', 'failure_link_e2e_source', 'failed',
           now() - interval '1 hour', now() - interval '1 hour' + interval '40 second',
           '{}'::jsonb, 'fake addax exited with status 3'
    from etl_table where source_table = 'failure_link_e2e_source';

    insert into etl_failure (
      tid, execution_id, occurred_at, stage, category, error_class, summary, detail, fingerprint,
      attempt_no, max_attempts, final_attempt, source_id, source_db, source_table, target_db, target_table
    )
    select t.id, e.id, now(), 'COLLECT', 'EXECUTION', 'AddaxExitException',
           '采集进程以状态码 3 退出', 'at com.example.Collector.run(Collector.java:42)',
           md5('failure-link-e2e'), 1, 3, true, t.sid,
           'failure_link_db', 'failure_link_e2e_source', 'ods', 'failure_link_e2e_target'
    from etl_table t, etl_execution e
    where t.source_table = 'failure_link_e2e_source' and e.tid = t.id;

    select id from etl_table where source_table = 'failure_link_e2e_source';
  `)
  );
  const executionId = Number(
    psql(`select id from etl_execution where tid = ${tid} order by id desc limit 1;`)
  );
  return { tid, executionId, identity: 'failure_link_db.failure_link_e2e_source' };
}

test('failure center deep-links execution snapshots with identity and switchable tabs', async ({
  page,
}) => {
  const { tid, executionId, identity } = seed();

  await page.goto(`/failures?tid=${tid}`);
  const row = page.getByRole('row', { name: /failure_link_e2e_source/ });
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole('button', { name: '查看' }).click();

  const drawer = page.locator('.failure-drawer');
  await expect(drawer.getByText('采集进程以状态码 3 退出')).toBeVisible();
  await drawer
    .locator('.evidence-row')
    .filter({ hasText: '执行快照' })
    .getByRole('link', { name: '查看' })
    .click();

  // 这个入口只带 tab 参数，表标识由详情接口自己解析
  await expect(page).toHaveURL(new RegExp(`/table/detail/${tid}\\?tab=executions$`));
  await expect(page.locator('.page-title__identity')).toHaveText(identity);
  await expect(page.getByText(`#${executionId} / 1`)).toBeVisible();

  // 非 info 标签直达时 info 编辑器还没挂载，标签切换不能被它拦住
  await page.getByRole('tab', { name: '基本详情' }).click();
  await expect(page).toHaveURL(new RegExp(`/table/detail/${tid}\\?tab=info$`));
  await expect(page.locator('.v-tab--selected')).toHaveText('基本详情');
  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible();

  await page.getByRole('tab', { name: '采集日志' }).click();
  await expect(page).toHaveURL(new RegExp(`/table/detail/${tid}\\?tab=logs$`));
  await expect(page.getByText('日志列表', { exact: true })).toBeVisible();

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifact = path.join(artifactDir, 'failure-execution-deep-link.json');
  fs.writeFileSync(
    artifact,
    JSON.stringify(
      {
        checkedAt: new Date().toISOString(),
        database: dbName,
        tid,
        executionId,
        identity,
        finalUrl: page.url(),
      },
      null,
      2
    )
  );
  await page.screenshot({
    path: path.join(artifactDir, 'failure-execution-deep-link.png'),
    fullPage: true,
  });
  await test.info().attach('failure-execution-deep-link', {
    path: artifact,
    contentType: 'application/json',
  });
});
