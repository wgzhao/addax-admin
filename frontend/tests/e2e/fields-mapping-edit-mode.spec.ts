import { execFileSync } from 'node:child_process';
import { expect, test } from '@playwright/test';
import { apiLogin } from './helpers/auth';

const dbName = process.env.E2E_DB_NAME || 'addax_admin_history_e2e';
const dbUser = process.env.E2E_DB_USER || process.env.USER || '';

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

function sqlLiteral(value: string): string {
  return `'${value.replaceAll("'", "''")}'`;
}

test('field mapping stays read-only until the user explicitly enters edit mode', async ({
  page,
  request,
}) => {
  test.setTimeout(90_000);
  const runId = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const sourceTable = `mapping_edit_source_${runId}`;
  const targetTable = `mapping_edit_target_${runId}`;
  const sourceCode = `FE${runId.slice(-6)}`;
  const targetCode = `FT${runId.slice(-6)}`;
  const jdbcUrl = `jdbc:postgresql://localhost:5432/${dbName}`;

  psql(`
    drop table if exists public.${sourceTable};
    drop table if exists public.${targetTable};
    create table public.${sourceTable} (
      id bigint primary key,
      customer_name text
    );
    delete from etl_source where code = ${sqlLiteral(sourceCode)};
    delete from etl_target where code = ${sqlLiteral(targetCode)};
    insert into etl_source (
      code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar
    ) values (
      ${sqlLiteral(sourceCode)}, 'Field Mapping Edit E2E', ${sqlLiteral(jdbcUrl)},
      ${sqlLiteral(dbUser)}, '', '23:59:00', true, 1, 'POSTGRESQL', 'NATURAL_DAY'
    );
    insert into etl_target (
      code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark
    ) values (
      ${sqlLiteral(targetCode)}, 'Field Mapping Edit E2E Target', 'POSTGRESQL',
      ${sqlLiteral(JSON.stringify({ url: jdbcUrl, username: dbUser, password: '' }))},
      'wR', true, false, 'field mapping edit e2e'
    );
  `);

  const sourceId = Number(psql(`select id from etl_source where code = ${sqlLiteral(sourceCode)}`));
  const targetId = Number(psql(`select id from etl_target where code = ${sqlLiteral(targetCode)}`));
  const token = await apiLogin(request);
  const headers = { Authorization: `Bearer ${token}` };
  const targetOptions = {
    rdbms: { businessDateColumn: '', createPrimaryKey: true },
  };
  const requestData = {
    sourceId,
    sourceDb: dbName,
    sourceTable,
    targetId,
    targetDb: 'public',
    targetTable,
    schemaManagementMode: 'MANAGED',
    targetOptions,
  };

  const previewResponse = await request.post('/api/v1/tables/schema-plans/preview', {
    headers,
    data: [requestData],
  });
  expect(previewResponse.ok()).toBeTruthy();
  const [plan] = await previewResponse.json();

  const commitResponse = await request.post('/api/v1/tables/schema-plans/commit', {
    headers,
    data: [
      {
        table: {
          sourceDb: dbName,
          sourceTable,
          targetDb: 'public',
          targetTable,
          targetId,
          targetOptions,
          filter: '1=1',
          status: 'U',
          kind: 'A',
          retryCnt: 3,
          maxRuntime: 120,
          sid: sourceId,
          duration: 0,
          tblComment: 'field mapping edit e2e',
          splitPk: '',
          autoPk: false,
          writeMode: 'insert',
          schemaManagementMode: 'MANAGED',
        },
        mappings: plan.mappings.map(
          (mapping: { sourceColumnName: string; targetColumnName: string }, index: number) => ({
            sourceColumnName: mapping.sourceColumnName,
            targetColumnName: mapping.targetColumnName,
            ordinalNo: index + 1,
          })
        ),
      },
    ],
  });
  expect(commitResponse.ok()).toBeTruthy();
  const [created] = await commitResponse.json();
  expect(created.status).toBe('CREATED');

  await page.goto(`/table/detail/${created.tableId}`);
  await page.getByRole('tab', { name: '字段比较' }).click();

  await expect(page.getByRole('button', { name: '编辑映射' })).toBeVisible();
  await expect(page.getByRole('button', { name: '保存映射' })).toHaveCount(0);
  await expect(page.getByRole('combobox', { name: '目标字段' })).toHaveCount(0);

  await page.getByRole('button', { name: '编辑映射' }).click();
  await expect(page.getByRole('button', { name: '保存映射' })).toBeDisabled();

  const customerRow = page.getByRole('row', { name: /customer_name/ });
  const mappingInput = customerRow.getByRole('combobox', { name: '目标字段' });
  await mappingInput.fill('customer_name_std');
  await expect(page.getByRole('button', { name: '保存映射' })).toBeEnabled();

  await page.getByRole('button', { name: '取消' }).click();
  await expect(page.getByRole('button', { name: '编辑映射' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: '目标字段' })).toHaveCount(0);

  const mappingsAfterCancel = await request.get(
    `/api/v1/tables/${created.tableId}/column-mappings`,
    { headers }
  );
  const cancelledMappings = await mappingsAfterCancel.json();
  expect(
    cancelledMappings.find(
      (mapping: { sourceColumnName: string }) => mapping.sourceColumnName === 'customer_name'
    ).targetColumnName
  ).toBe('customer_name');

  await page.getByRole('button', { name: '编辑映射' }).click();
  await page
    .getByRole('row', { name: /customer_name/ })
    .getByRole('combobox', { name: '目标字段' })
    .fill('customer_name_std');
  await page.getByRole('button', { name: '保存映射' }).click();

  await expect(page.getByRole('button', { name: '编辑映射' })).toBeVisible();
  const mappingsAfterSave = await request.get(`/api/v1/tables/${created.tableId}/column-mappings`, {
    headers,
  });
  const savedMappings = await mappingsAfterSave.json();
  expect(
    savedMappings.find(
      (mapping: { sourceColumnName: string }) => mapping.sourceColumnName === 'customer_name'
    ).targetColumnName
  ).toBe('customer_name_std');
});
