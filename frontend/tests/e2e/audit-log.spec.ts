import { test, expect, type APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin, username as adminUsername } from './helpers/auth';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const dbName = process.env.E2E_DB_NAME || 'addax_admin_history_e2e';
const runId = `${Date.now()}`.slice(-8);
const auditUser = `audit_e2e_${runId}`;
const auditPassword = 'audit-user-password';
const sourceCode = `AU${runId}`;
const sourceSecret = `audit-source-secret-${runId}`;

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

interface AuditEvent {
  id: number;
  requestId: string | null;
  actorUsername: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  outcome: string;
  beforeData: unknown;
  afterData: unknown;
  metadata: unknown;
}

async function queryAudit(
  request: APIRequestContext,
  token: string,
  params: Record<string, string | number>
): Promise<{ content: AuditEvent[]; totalElements: number }> {
  const response = await request.get('/api/v1/audit-events', {
    headers: authHeaders(token),
    params: { page: 0, pageSize: 100, ...params },
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

test('security audit records attribution, denial and redacted changes end to end', async ({
  page,
  request,
}) => {
  test.setTimeout(90_000);
  const startedAt = new Date(Date.now() - 60_000).toISOString();
  const adminToken = await apiLogin(request);

  const failedLogin = await request.post('/api/v1/auth/login', {
    data: { username: auditUser, password: 'definitely-wrong' },
  });
  expect(failedLogin.status()).toBe(401);
  const failedRequestId = failedLogin.headers()['x-request-id'];
  expect(failedRequestId).toBeTruthy();

  const createUser = await request.post('/api/v1/users', {
    headers: authHeaders(adminToken),
    data: { username: auditUser, password: auditPassword, enabled: true, authority: 'user' },
  });
  expect(createUser.ok()).toBeTruthy();

  const userLogin = await request.post('/api/v1/auth/login', {
    data: { username: auditUser, password: auditPassword },
  });
  expect(userLogin.ok()).toBeTruthy();
  const userToken = (await userLogin.json()).data as string;

  const denied = await request.post('/api/v1/tables', {
    headers: authHeaders(userToken),
    data: {},
  });
  expect(denied.status()).toBe(403);
  const deniedRequestId = denied.headers()['x-request-id'];
  expect(deniedRequestId).toBeTruthy();

  const deniedRead = await request.get('/api/v1/audit-events', {
    headers: authHeaders(userToken),
  });
  expect(deniedRead.status()).toBe(403);
  const deniedReadRequestId = deniedRead.headers()['x-request-id'];
  expect(deniedReadRequestId).toBeTruthy();

  const createSource = await request.post('/api/v1/sources', {
    headers: authHeaders(adminToken),
    data: {
      code: sourceCode,
      name: `Audit E2E ${runId}`,
      url: `jdbc:postgresql://localhost:5432/audit_${runId}`,
      username: 'audit_e2e',
      pass: sourceSecret,
      startAt: '23:59:00',
      calendar: 'NATURAL_DAY',
      enabled: false,
      maxConcurrency: 1,
      dbType: 'POSTGRESQL',
    },
  });
  expect(createSource.status()).toBe(201);
  const source = await createSource.json();

  try {
    const updateSource = await request.put(`/api/v1/sources/${source.id}`, {
      headers: authHeaders(adminToken),
      data: {
        ...source,
        name: `Audit E2E Updated ${runId}`,
        pass: `audit-source-secret-updated-${runId}`,
      },
    });
    expect(updateSource.ok()).toBeTruthy();
  } finally {
    const cleanupSource = await request.delete(`/api/v1/sources/${source.id}`, {
      headers: authHeaders(adminToken),
    });
    expect(cleanupSource.status()).toBe(204);
  }

  const failedEvents = await queryAudit(request, adminToken, {
    from: startedAt,
    requestId: failedRequestId,
  });
  expect(failedEvents.content).toHaveLength(1);
  expect(failedEvents.content[0]).toMatchObject({
    action: 'AUTH_LOGIN_FAILED',
    outcome: 'FAILED',
    requestId: failedRequestId,
    actorUsername: auditUser,
  });

  const deniedEvents = await queryAudit(request, adminToken, {
    from: startedAt,
    requestId: deniedRequestId,
  });
  expect(deniedEvents.content).toHaveLength(1);
  expect(deniedEvents.content[0]).toMatchObject({
    action: 'WRITE_ACCESS_DENIED',
    outcome: 'DENIED',
    requestId: deniedRequestId,
    actorUsername: auditUser,
  });

  const deniedReadEvents = await queryAudit(request, adminToken, {
    from: startedAt,
    requestId: deniedReadRequestId,
  });
  expect(deniedReadEvents.content).toHaveLength(1);
  expect(deniedReadEvents.content[0]).toMatchObject({
    action: 'WRITE_ACCESS_DENIED',
    outcome: 'DENIED',
    actorUsername: auditUser,
  });

  const sourceEvents = await queryAudit(request, adminToken, {
    from: startedAt,
    resourceType: 'ETL_SOURCE',
    resourceId: String(source.id),
  });
  expect(sourceEvents.content.some(event => event.action === 'SOURCE_CREATED')).toBeTruthy();
  const updated = sourceEvents.content.find(event => event.action === 'SOURCE_UPDATED');
  expect(updated).toBeTruthy();
  expect(updated?.actorUsername).toBe(adminUsername);

  const serializedEvents = JSON.stringify(sourceEvents.content);
  expect(serializedEvents).not.toContain(sourceSecret);
  expect(serializedEvents).not.toContain(`audit-source-secret-updated-${runId}`);
  expect(serializedEvents).toContain('[REDACTED]');

  let appendOnlyRejected = false;
  try {
    psql(`update audit_event set action = 'TAMPERED' where id = ${updated?.id};`);
  } catch {
    appendOnlyRejected = true;
  }
  expect(appendOnlyRejected).toBeTruthy();

  const retentionProbe = psql(`
    begin;
    set local addax.audit_purge = 'on';
    delete from audit_event where id = ${updated?.id} returning id;
    rollback;
  `);
  expect(retentionProbe).toContain(String(updated?.id));

  await page.goto('/audit');
  await expect(page.getByRole('heading', { name: '安全审计' })).toBeVisible();
  await page.getByLabel('操作者').fill(adminUsername);
  await page.getByLabel('动作').fill('SOURCE_UPDATED');
  await page.getByRole('button', { name: '查询' }).click();
  const auditRow = page.getByRole('row', { name: /SOURCE_UPDATED/ }).first();
  await expect(auditRow).toBeVisible();
  await auditRow.click();
  await expect(page.getByText('脱敏后的变更')).toBeVisible();

  const artifact = path.join(artifactDir, 'audit-log.json');
  const payload = {
    checkedAt: new Date().toISOString(),
    runId,
    failedLogin: failedEvents.content,
    deniedWrite: deniedEvents.content,
    deniedAuditRead: deniedReadEvents.content,
    sourceChanges: sourceEvents.content,
    redaction: {
      checkedValues: ['source-create-secret', 'source-update-secret'],
      containsRedactionMarker: serializedEvents.includes('[REDACTED]'),
      passed: true,
    },
    appendOnly: {
      directUpdateRejected: appendOnlyRejected,
    },
    retention: {
      authorizedDeleteInsideTransaction: retentionProbe.includes(String(updated?.id)),
    },
  };
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(artifact, JSON.stringify(payload, null, 2));
  await test.info().attach('audit-log', { path: artifact, contentType: 'application/json' });

  const cleanupUser = await request.delete(`/api/v1/users/${auditUser}`, {
    headers: authHeaders(adminToken),
  });
  expect(cleanupUser.ok()).toBeTruthy();
});
