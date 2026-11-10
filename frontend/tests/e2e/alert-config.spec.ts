import { test, expect, type APIRequestContext } from '@playwright/test';
import http from 'node:http';
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin } from './helpers/auth';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const runId = `${Date.now()}`.slice(-8);
const channelName = `E2E Alert ${runId}`;
const ruleName = `E2E Rule SYSTEM ${runId}`;
const securityRuleName = `E2E Rule SECURITY ${runId}`;
const mockPort = Number(process.env.E2E_ALERT_MOCK_PORT || 18888);
const smtpPort = Number(process.env.E2E_ALERT_SMTP_PORT || 18889);
// The backend under test must be able to reach this host. Local runs use
// loopback; remote test environments set E2E_ALERT_MOCK_HOST to the test
// runner's LAN address and the mock binds all interfaces.
const mockHost = process.env.E2E_ALERT_MOCK_HOST || '127.0.0.1';
const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

function decodeMimeHeader(value: string): string {
  return value.replace(
    /=\?([^?]+)\?([BbQq])\?([^?]*)\?=/g,
    (match: string, _charset: string, encoding: string, text: string) => {
      try {
        if (encoding.toUpperCase() === 'B') {
          return Buffer.from(text, 'base64').toString('utf8');
        }
        const bytes = text
          .replace(/_/g, ' ')
          .replace(/=([0-9A-Fa-f]{2})/g, (_item: string, hex: string) =>
            String.fromCharCode(parseInt(hex, 16))
          );
        return Buffer.from(bytes, 'binary').toString('utf8');
      } catch {
        return match;
      }
    }
  );
}

function parseSmtpHeaders(raw: string): Record<string, string> {
  const headerBlock = raw.split('\r\n\r\n')[0] || '';
  const headers: Record<string, string> = {};
  let current = '';
  for (const line of headerBlock.split('\r\n')) {
    if ((line.startsWith(' ') || line.startsWith('\t')) && current) {
      headers[current] = `${headers[current]} ${line.trim()}`;
      continue;
    }
    const index = line.indexOf(':');
    if (index > 0) {
      current = line.slice(0, index).toLowerCase();
      headers[current] = line.slice(index + 1).trim();
    } else {
      current = '';
    }
  }
  return headers;
}

function decodePlainAuth(payload: string): string {
  const raw = Buffer.from(payload, 'base64').toString('utf8');
  const parts = raw.split('\0');
  return parts.length >= 3 ? parts[1] : parts[0] || '';
}

function startSmtpMock(): Promise<void> {
  smtpServer = net.createServer(socket => {
    smtpSockets.add(socket);
    socket.on('close', () => smtpSockets.delete(socket));
    let buffer = '';
    let inData = false;
    let dataLines: string[] = [];
    let from = '';
    let to: string[] = [];
    let authUser: string | null = null;
    let authState: 'none' | 'plain' | 'login-user' | 'login-password' = 'none';
    let pendingUser = '';
    const write = (line: string) => socket.write(`${line}\r\n`);
    write('220 e2e-smtp ready');

    socket.on('data', chunk => {
      buffer += chunk.toString('utf8');
      let index = buffer.indexOf('\r\n');
      while (index >= 0) {
        const line = buffer.slice(0, index);
        buffer = buffer.slice(index + 2);
        index = buffer.indexOf('\r\n');

        if (inData) {
          if (line === '.') {
            inData = false;
            const raw = dataLines.join('\r\n');
            const headers = parseSmtpHeaders(raw);
            smtpReceived.push({
              at: new Date().toISOString(),
              from,
              to: [...to],
              subject: decodeMimeHeader(headers.subject || ''),
              headers,
              authUser,
              bytes: Buffer.byteLength(raw),
            });
            dataLines = [];
            write('250 OK: queued');
          } else {
            dataLines.push(line.startsWith('..') ? line.slice(1) : line);
          }
          continue;
        }

        if (authState === 'plain') {
          authUser = decodePlainAuth(line);
          authState = 'none';
          write('235 2.7.0 Authentication successful');
          continue;
        }
        if (authState === 'login-user') {
          pendingUser = Buffer.from(line, 'base64').toString('utf8');
          authState = 'login-password';
          write('334 UGFzc3dvcmQ6');
          continue;
        }
        if (authState === 'login-password') {
          authUser = pendingUser;
          authState = 'none';
          write('235 2.7.0 Authentication successful');
          continue;
        }

        const upper = line.toUpperCase();
        if (upper.startsWith('EHLO') || upper.startsWith('HELO')) {
          write('250-e2e-smtp');
          write('250-AUTH PLAIN LOGIN');
          write('250-SIZE 10485760');
          write('250 8BITMIME');
        } else if (upper.startsWith('AUTH PLAIN')) {
          const payload = line.slice(line.toUpperCase().indexOf('AUTH PLAIN') + 10).trim();
          if (payload) {
            authUser = decodePlainAuth(payload);
            write('235 2.7.0 Authentication successful');
          } else {
            authState = 'plain';
            write('334 ');
          }
        } else if (upper.startsWith('AUTH LOGIN')) {
          authState = 'login-user';
          write('334 VXNlcm5hbWU6');
        } else if (upper.startsWith('MAIL FROM')) {
          from = line.slice(line.indexOf(':') + 1).trim();
          to = [];
          write('250 OK');
        } else if (upper.startsWith('RCPT TO')) {
          to.push(line.slice(line.indexOf(':') + 1).trim());
          write('250 OK');
        } else if (upper === 'DATA') {
          inData = true;
          dataLines = [];
          write('354 End data with <CR><LF>.<CR><LF>');
        } else if (upper === 'QUIT') {
          write('221 Bye');
          socket.end();
        } else if (upper === 'RSET') {
          from = '';
          to = [];
          write('250 OK');
        } else {
          write('250 OK');
        }
      }
    });
  });
  return new Promise<void>((resolve, reject) => {
    smtpServer.once('error', reject);
    smtpServer.listen(smtpPort, '0.0.0.0', () => resolve());
  });
}

// The spec owns a mock HTTP server and prefix-based cleanup, so its tests must
// not interleave with each other.
test.describe.configure({ mode: 'serial' });

type MockMode = 'success' | 'fail';
type MockReceive = {
  at: string;
  url: string;
  headers: Record<string, string | string[] | undefined>;
  body: any;
};
type SmtpReceive = {
  at: string;
  from: string;
  to: string[];
  subject: string;
  headers: Record<string, string>;
  authUser: string | null;
  bytes: number;
};

let mockMode: MockMode = 'success';
const received: MockReceive[] = [];
const smtpReceived: SmtpReceive[] = [];
const smtpSockets = new Set<net.Socket>();
let mockServer: http.Server;
let smtpServer: net.Server;

test.beforeAll(async () => {
  mockMode = 'success';
  received.length = 0;
  smtpReceived.length = 0;
  mockServer = http.createServer((req, res) => {
    if (req.url === '/control/fail') {
      mockMode = 'fail';
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{"mode":"fail"}');
      return;
    }
    if (req.url === '/control/success') {
      mockMode = 'success';
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{"mode":"success"}');
      return;
    }
    let raw = '';
    req.on('data', chunk => {
      raw += chunk;
    });
    req.on('end', () => {
      let body: any = null;
      try {
        body = JSON.parse(raw);
      } catch {
        body = { raw };
      }
      // Only the synchronous channel test send uses eventKey "test"; alert test
      // events carry a real event key and must obey the forced-failure mode.
      const isChannelTest = body?.eventKey === 'test';
      const fail = mockMode === 'fail' && !isChannelTest;
      received.push({
        at: new Date().toISOString(),
        url: req.url || '',
        headers: req.headers,
        body,
      });
      res.writeHead(fail ? 500 : 200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(fail ? { ok: false, error: 'forced failure' } : { ok: true }));
    });
  });
  await new Promise<void>((resolve, reject) => {
    mockServer.once('error', reject);
    mockServer.listen(mockPort, '0.0.0.0', () => resolve());
  });
  await startSmtpMock();
});

test.afterAll(async () => {
  await new Promise<void>(resolve => mockServer.close(() => resolve()));
  for (const socket of smtpSockets) {
    socket.destroy();
  }
  await new Promise<void>(resolve => smtpServer.close(() => resolve()));
});

type PausedChannel = {
  id: number;
  name: string;
  type: string;
  config: Record<string, any>;
  rateLimitPerMinute: number;
};

// The default-route assertion sends to every enabled channel. On a shared test
// environment that includes real destinations, so pause them for the run and
// restore them afterwards.
async function pauseEnabledChannels(
  request: APIRequestContext,
  token: string,
  paused: PausedChannel[]
): Promise<void> {
  const response = await request.get('/api/v1/alert-channels', {
    headers: authHeaders(token),
  });
  expect(response.ok()).toBeTruthy();
  const channels = (await response.json()) as Array<any>;
  for (const channel of channels) {
    if (!channel.enabled || channel.name?.startsWith('E2E Alert ')) {
      continue;
    }
    const update = await request.put(`/api/v1/alert-channels/${channel.id}`, {
      headers: authHeaders(token),
      data: {
        name: channel.name,
        type: channel.type,
        config: channel.config,
        rateLimitPerMinute: channel.rateLimitPerMinute,
        enabled: false,
      },
    });
    expect(update.ok()).toBeTruthy();
    paused.push({
      id: channel.id,
      name: channel.name,
      type: channel.type,
      config: channel.config,
      rateLimitPerMinute: channel.rateLimitPerMinute,
    });
  }
}

async function restorePausedChannels(
  request: APIRequestContext,
  token: string,
  paused: PausedChannel[]
) {
  if (paused.length === 0) return;
  const response = await request.get('/api/v1/alert-channels', {
    headers: authHeaders(token),
  });
  if (!response.ok()) return;
  const channels = (await response.json()) as Array<any>;
  for (const saved of paused) {
    const current = channels.find(channel => channel.id === saved.id);
    if (!current) continue;
    const update = await request.put(`/api/v1/alert-channels/${saved.id}`, {
      headers: authHeaders(token),
      data: {
        name: current.name,
        type: current.type,
        config: current.config,
        rateLimitPerMinute: current.rateLimitPerMinute,
        enabled: true,
      },
    });
    if (!update.ok()) {
      console.warn(`failed to restore alert channel ${saved.name}: ${update.status()}`);
    }
  }
}

async function deleteByPrefix(
  request: APIRequestContext,
  token: string,
  collection: 'alert-channels' | 'alert-rules',
  prefix: string
) {
  const response = await request.get(`/api/v1/${collection}`, { headers: authHeaders(token) });
  if (!response.ok()) return;
  const items = (await response.json()) as Array<{ id: number; name: string }>;
  for (const item of items) {
    if (item.name?.startsWith(prefix)) {
      await request.delete(`/api/v1/${collection}/${item.id}`, { headers: authHeaders(token) });
    }
  }
}

async function findDelivery(request: APIRequestContext, token: string, eventId: number) {
  const response = await request.get('/api/v1/alert-deliveries', {
    headers: authHeaders(token),
    params: { eventId, page: 0, pageSize: 10 },
  });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  return body.content?.[0] as
    | {
        id: number;
        status: string;
        attempts: number;
        responseCode: number | null;
        lastError: string | null;
        channelName: string | null;
      }
    | undefined;
}

async function waitForDelivery(
  request: APIRequestContext,
  token: string,
  eventId: number,
  predicate: (delivery: any) => boolean,
  timeoutMs = 25_000
) {
  const deadline = Date.now() + timeoutMs;
  let last: any;
  while (Date.now() < deadline) {
    last = await findDelivery(request, token, eventId);
    if (last && predicate(last)) {
      return last;
    }
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error(
    `delivery for event ${eventId} did not reach the expected state: ${JSON.stringify(last)}`
  );
}

async function listDeliveries(request: APIRequestContext, token: string, eventId: number) {
  const response = await request.get('/api/v1/alert-deliveries', {
    headers: authHeaders(token),
    params: { eventId, page: 0, pageSize: 20 },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).content as Array<any>;
}

async function waitForDeliveries(
  request: APIRequestContext,
  token: string,
  eventId: number,
  predicate: (deliveries: Array<any>) => boolean,
  timeoutMs = 25_000
) {
  const deadline = Date.now() + timeoutMs;
  let last: Array<any> = [];
  while (Date.now() < deadline) {
    last = await listDeliveries(request, token, eventId);
    if (predicate(last)) {
      return last;
    }
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error(
    `deliveries for event ${eventId} did not reach the expected state: ${JSON.stringify(last)}`
  );
}

test('alert channel, rule, delivery retry and paused-channel semantics end to end', async ({
  page,
  request,
}) => {
  test.setTimeout(150_000);
  const adminToken = await apiLogin(request);
  const headers = authHeaders(adminToken);
  // The switch visibility regression only exists in the light theme, which is
  // the product default but could be overridden by a saved browser preference.
  await page.addInitScript(() => localStorage.setItem('theme', 'light'));
  const artifact: Record<string, any> = {
    runId,
    channelName,
    ruleName,
    securityRuleName,
    mockPort,
    startedAt: new Date().toISOString(),
  };
  let channelId: number | null = null;
  const createdRules: number[] = [];
  const createdChannels: number[] = [];
  const createdSilences: number[] = [];
  let pausedChannels: PausedChannel[] = [];

  try {
    await deleteByPrefix(request, adminToken, 'alert-rules', 'E2E Rule ');
    await deleteByPrefix(request, adminToken, 'alert-channels', 'E2E Alert ');
    await pauseEnabledChannels(request, adminToken, pausedChannels);

    await page.goto('/alert-config');
    await expect(page.getByRole('heading', { name: '告警配置' })).toBeVisible();

    // ── channel CRUD through the UI ───────────────────────────────────────
    await page.getByTestId('channel-create').click();
    const channelDialog = page.getByTestId('channel-dialog');
    await expect(channelDialog).toBeVisible();
    await channelDialog.getByTestId('channel-name').locator('input').fill(channelName);
    await channelDialog.getByTestId('channel-type').click();
    await page.getByRole('option', { name: 'Webhook' }).click();
    // The off track must not be the near-white surface-variant at 60% opacity;
    // that combination is invisible on a white card in the light theme.
    const privateSwitch = channelDialog.getByTestId('channel-allow-private');
    await expect(privateSwitch).toBeVisible();
    const privateTrackStyle = await privateSwitch.locator('.v-switch__track').evaluate(el => ({
      background: getComputedStyle(el).backgroundColor,
      opacity: getComputedStyle(el).opacity,
    }));
    expect(privateTrackStyle.opacity).toBe('1');
    const trackRgb =
      privateTrackStyle.background
        .match(/\d+(\.\d+)?/g)
        ?.slice(0, 3)
        .map(Number) ?? [];
    expect(trackRgb).toHaveLength(3);
    // The old surface-variant track summed to ~733 (near white); a visible
    // neutral track must be meaningfully darker than that.
    expect(trackRgb[0] + trackRgb[1] + trackRgb[2]).toBeLessThan(600);
    await privateSwitch.locator('input[type="checkbox"]').check({ force: true });
    await channelDialog
      .getByTestId('channel-url')
      .locator('input')
      .fill(`http://${mockHost}:${mockPort}/hook`);
    await channelDialog.getByTestId('channel-save').click();
    await expect(channelDialog).toBeHidden();

    const card = page.getByTestId('channel-card').filter({ hasText: channelName }).first();
    await expect(card).toBeVisible();
    await expect(card).toContainText('已停用');

    // The four card actions must stay on one row at the grid's minimum width;
    // the old spacer added an extra gap and pushed 启用/停用 onto its own line.
    await page.evaluate(() => {
      const grid = document.querySelector('.channel-grid');
      if (grid instanceof HTMLElement) grid.style.gridTemplateColumns = '340px';
    });
    const actionTops = await card
      .locator('.channel-actions .v-btn')
      .evaluateAll(buttons =>
        buttons.map(button => Math.round(button.getBoundingClientRect().top))
      );
    expect(actionTops).toHaveLength(4);
    expect(new Set(actionTops).size).toBe(1);
    await page.evaluate(() => {
      const grid = document.querySelector('.channel-grid');
      if (grid instanceof HTMLElement) grid.style.gridTemplateColumns = '';
    });

    // A channel cannot be enabled before a successful test send.
    await expect(card.getByTestId('channel-enable')).toBeDisabled();
    await expect(card.getByTestId('channel-enable')).toContainText('启用');

    await card.getByTestId('channel-test').click();
    await expect(card.getByText('成功', { exact: true })).toBeVisible({ timeout: 15_000 });
    const testReceive = received.find(item => item.at && item.body?.eventKey === 'test');
    expect(testReceive).toBeTruthy();
    expect(String(testReceive?.headers['x-addax-signature'] || '')).toHaveLength(0);

    await card.getByTestId('channel-enable').click();
    await expect(card).toContainText('已启用', { timeout: 10_000 });

    const channelList = await (await request.get('/api/v1/alert-channels', { headers })).json();
    const channel = (channelList as Array<any>).find(item => item.name === channelName);
    expect(channel).toBeTruthy();
    channelId = channel.id;
    artifact.channel = {
      id: channel.id,
      name: channel.name,
      enabled: channel.enabled,
      maskedUrl: channel.config?.url,
      lastTestOk: channel.lastTestOk,
    };
    expect(channel.config.url).toBe('******');

    // ── route a SYSTEM event through a rule ───────────────────────────────
    await page.getByTestId('tab-rules').click();
    await page.getByTestId('rule-create').click();
    const ruleDialog = page.getByTestId('rule-dialog');
    await expect(ruleDialog).toBeVisible();
    await ruleDialog.getByTestId('rule-name').locator('input').fill(ruleName);
    await ruleDialog.getByTestId('rule-event-type').click();
    await page.getByRole('option', { name: '系统告警' }).click();
    await ruleDialog.getByTestId('rule-channels').click();
    await page.getByRole('option').filter({ hasText: channelName }).first().click();
    await page.keyboard.press('Escape');
    await ruleDialog.getByTestId('rule-save').click();
    await expect(ruleDialog).toBeHidden();
    await expect(page.getByRole('row').filter({ hasText: ruleName })).toBeVisible();

    const ruleList = await (await request.get('/api/v1/alert-rules', { headers })).json();
    const rule = (ruleList as Array<any>).find(item => item.name === ruleName);
    expect(rule).toBeTruthy();
    createdRules.push(rule.id);

    await page.getByTestId('send-test-event').click();
    const sentRow = page
      .getByRole('row')
      .filter({ hasText: '测试告警 · SYSTEM' })
      .filter({ hasText: channelName })
      .first();
    await expect(sentRow).toBeVisible({ timeout: 30_000 });
    await expect(sentRow).toContainText('已发送', { timeout: 20_000 });
    artifact.systemDelivery = { row: await sentRow.innerText() };

    // ── default route, SOURCE scope and silence ───────────────────────────
    const channelBName = `${channelName} B`;
    const createB = await request.post('/api/v1/alert-channels', {
      headers,
      data: {
        name: channelBName,
        type: 'WEBHOOK',
        config: {
          url: `http://${mockHost}:${mockPort}/hook`,
          method: 'POST',
          headers: {},
          hmacSecret: '',
          payloadTemplate: '',
          successStatus: [200],
          timeoutMs: 5000,
          allowPrivateNetwork: true,
        },
        rateLimitPerMinute: 60,
      },
    });
    expect(createB.ok()).toBeTruthy();
    const channelB = await createB.json();
    createdChannels.push(channelB.id);
    const testB = await request.post(`/api/v1/alert-channels/${channelB.id}/test`, { headers });
    expect((await testB.json()).ok).toBe(true);
    const enableB = await request.put(`/api/v1/alert-channels/${channelB.id}`, {
      headers,
      data: {
        name: channelBName,
        type: 'WEBHOOK',
        config: channelB.config,
        rateLimitPerMinute: 60,
        enabled: true,
      },
    });
    expect(enableB.ok()).toBeTruthy();

    const sourceRule = await request.post('/api/v1/alert-rules', {
      headers,
      data: {
        name: `E2E Rule SOURCE ${runId}`,
        eventType: 'COLLECT_FAILURE',
        severity: 'P2',
        scopeType: 'SOURCE',
        scopeValue: 'sales',
        channelIds: [channel.id],
        cooldownSecs: 0,
        groupWindowSecs: 0,
        enabled: true,
      },
    });
    expect(sourceRule.ok()).toBeTruthy();
    createdRules.push((await sourceRule.json()).id);

    const sourceMatchEvent = await (
      await request.post('/api/v1/alert-events/test', {
        headers,
        data: {
          eventType: 'COLLECT_FAILURE',
          message: 'E2E source scope match',
          sourceDb: 'sales',
          sourceTable: 'orders',
        },
      })
    ).json();
    const sourceMatchDelivery = await waitForDelivery(
      request,
      adminToken,
      sourceMatchEvent.id,
      delivery => delivery.status === 'SENT'
    );
    expect(sourceMatchDelivery.channelId).toBe(channel.id);
    const sourceMatchDeliveries = await listDeliveries(request, adminToken, sourceMatchEvent.id);
    expect(sourceMatchDeliveries).toHaveLength(1);

    const defaultRouteEvent = await (
      await request.post('/api/v1/alert-events/test', {
        headers,
        data: {
          eventType: 'COLLECT_FAILURE',
          message: 'E2E default route',
          sourceDb: 'other_source',
          sourceTable: 'orders',
        },
      })
    ).json();
    const defaultRouteDeliveries = await waitForDeliveries(
      request,
      adminToken,
      defaultRouteEvent.id,
      deliveries =>
        deliveries.length === 2 && deliveries.every(delivery => delivery.status === 'SENT')
    );
    expect(
      defaultRouteDeliveries.map(delivery => delivery.channelId).sort((a, b) => a - b)
    ).toEqual([channel.id, channelB.id].sort((a, b) => a - b));
    artifact.routing = {
      sourceMatch: sourceMatchDeliveries.map(delivery => ({
        channelId: delivery.channelId,
        status: delivery.status,
      })),
      defaultRoute: defaultRouteDeliveries.map(delivery => ({
        channelId: delivery.channelId,
        status: delivery.status,
      })),
    };

    // ── failure, manual retry, and the paused-channel outcome ────────────
    await fetch(`http://127.0.0.1:${mockPort}/control/fail`);

    const securityRule = await request.post('/api/v1/alert-rules', {
      headers,
      data: {
        name: securityRuleName,
        eventType: 'SECURITY',
        severity: 'P1',
        scopeType: 'ALL',
        scopeValue: null,
        channelIds: [channel.id],
        cooldownSecs: 0,
        groupWindowSecs: 0,
        enabled: true,
      },
    });
    expect(securityRule.ok()).toBeTruthy();
    createdRules.push((await securityRule.json()).id);

    const failedEventResponse = await request.post('/api/v1/alert-events/test', {
      headers,
      data: { eventType: 'SECURITY', message: `E2E forced failure ${runId}` },
    });
    expect(failedEventResponse.ok()).toBeTruthy();
    const failedEvent = await failedEventResponse.json();
    const failedDelivery = await waitForDelivery(
      request,
      adminToken,
      failedEvent.id,
      delivery => delivery.status === 'FAILED'
    );
    expect(failedDelivery.attempts).toBe(1);
    expect(failedDelivery.responseCode).toBe(500);
    expect(failedDelivery.lastError || '').toContain('HTTP 500');
    artifact.failedDelivery = failedDelivery;

    await fetch(`http://127.0.0.1:${mockPort}/control/success`);
    const retry = await request.post(`/api/v1/alert-deliveries/${failedDelivery.id}/retry`, {
      headers,
    });
    expect(retry.ok()).toBeTruthy();
    const retriedDelivery = await waitForDelivery(
      request,
      adminToken,
      failedEvent.id,
      delivery => delivery.status === 'SENT',
      20_000
    );
    artifact.retriedDelivery = retriedDelivery;

    const silence = await request.post('/api/v1/alert-silences', {
      headers,
      data: {
        scopeType: 'ALL',
        scopeValue: null,
        startsAt: new Date(Date.now() - 60_000).toISOString(),
        endsAt: new Date(Date.now() + 3_600_000).toISOString(),
        reason: `E2E silence ${runId}`,
      },
    });
    expect(silence.ok()).toBeTruthy();
    const silenceBody = await silence.json();
    createdSilences.push(silenceBody.id);
    const silencedEvent = await (
      await request.post('/api/v1/alert-events/test', {
        headers,
        data: { eventType: 'SECURITY', message: `E2E silenced ${runId}` },
      })
    ).json();
    const silencedDelivery = await waitForDelivery(
      request,
      adminToken,
      silencedEvent.id,
      delivery => delivery.status === 'SILENCED'
    );
    expect(silencedDelivery.channelId).toBe(channel.id);
    const eventList = await request.get('/api/v1/alert-events', {
      headers,
      params: { page: 0, pageSize: 100, eventType: 'SECURITY', status: 'SILENCED' },
    });
    expect(eventList.ok()).toBeTruthy();
    const eventBody = await eventList.json();
    const silencedEventRow = (eventBody.content as Array<any>).find(
      item => item.id === silencedEvent.id
    );
    expect(silencedEventRow?.status).toBe('SILENCED');
    artifact.silence = {
      deliveryStatus: silencedDelivery.status,
      channelId: silencedDelivery.channelId,
      eventStatus: silencedEventRow?.status,
    };
    await request.delete(`/api/v1/alert-silences/${silenceBody.id}`, { headers });
    createdSilences.splice(createdSilences.indexOf(silenceBody.id), 1);

    // Disabling the only channel that the rule selects must record the event
    // as SKIPPED_NO_CHANNEL, not silently drop it and not fall back to another
    // destination.
    const disabled = await request.put(`/api/v1/alert-channels/${channel.id}`, {
      headers,
      data: {
        name: channel.name,
        type: channel.type,
        config: channel.config,
        rateLimitPerMinute: channel.rateLimitPerMinute,
        enabled: false,
      },
    });
    const disabledBody = await disabled.text();
    artifact.disableResponse = {
      status: disabled.status(),
      body: disabledBody,
    };
    expect(disabled.ok(), `disable failed: ${disabled.status()} ${disabledBody}`).toBeTruthy();
    expect(JSON.parse(disabledBody).enabled).toBe(false);

    const skippedEventResponse = await request.post('/api/v1/alert-events/test', {
      headers,
      data: { eventType: 'SECURITY', message: `E2E paused channel ${runId}` },
    });
    expect(skippedEventResponse.ok()).toBeTruthy();
    const skippedEvent = await skippedEventResponse.json();
    const skippedDelivery = await waitForDelivery(
      request,
      adminToken,
      skippedEvent.id,
      delivery => delivery.status === 'SKIPPED_NO_CHANNEL',
      10_000
    );
    artifact.skippedDelivery = skippedDelivery;

    await page.getByTestId('tab-deliveries').click();
    await page.getByRole('button', { name: '刷新' }).click();
    await expect(
      page
        .getByRole('row')
        .filter({ hasText: '测试告警 · SECURITY' })
        .filter({ hasText: '无可用通道' })
        .first()
    ).toBeVisible({ timeout: 15_000 });

    // Masked configuration survives an update: the browser only ever saw
    // ****** for the credential, yet the stored URL is still usable.
    const renamed = `${channelName} renamed`;
    const update = await request.put(`/api/v1/alert-channels/${channel.id}`, {
      headers,
      data: {
        name: renamed,
        type: channel.type,
        config: channel.config,
        rateLimitPerMinute: channel.rateLimitPerMinute,
      },
    });
    expect(update.ok()).toBeTruthy();
    const updated = await update.json();
    expect(updated.config.url).toBe('******');
    const reTest = await request.post(`/api/v1/alert-channels/${channel.id}/test`, { headers });
    expect(reTest.ok()).toBeTruthy();
    const reTestBody = await reTest.json();
    expect(reTestBody.ok).toBe(true);
    artifact.maskPreserved = {
      renamed,
      maskedUrl: updated.config.url,
      retestOk: reTestBody.ok,
    };

    // Changing an enabled channel's configuration must take it offline until the
    // new endpoint passes a test; otherwise the gate only applies at creation.
    const channelBList = await (await request.get('/api/v1/alert-channels', { headers })).json();
    const channelBRow = (channelBList as Array<any>).find(item => item.id === channelB.id);
    expect(channelBRow.enabled).toBe(true);
    const reconfigure = await request.put(`/api/v1/alert-channels/${channelB.id}`, {
      headers,
      data: {
        name: channelBRow.name,
        type: channelBRow.type,
        config: { ...channelBRow.config, timeoutMs: 6000 },
        rateLimitPerMinute: channelBRow.rateLimitPerMinute,
      },
    });
    expect(reconfigure.ok()).toBeTruthy();
    const reconfigured = await reconfigure.json();
    expect(reconfigured.enabled).toBe(false);
    expect(reconfigured.lastTestOk).toBe(false);
    artifact.reconfigureGate = {
      enabled: reconfigured.enabled,
      lastTestOk: reconfigured.lastTestOk,
    };

    artifact.mockReceives = received.map(item => ({
      url: item.url,
      eventKey: item.body?.eventKey,
      eventType: item.body?.eventType,
      severity: item.body?.severity,
      hasSignature: Boolean(item.headers['x-addax-signature']),
      isChannelTest: item.body?.eventKey === 'test',
    }));
    artifact.finishedAt = new Date().toISOString();
  } finally {
    fs.mkdirSync(artifactDir, { recursive: true });
    fs.writeFileSync(
      path.join(artifactDir, 'alert-config-e2e.json'),
      `${JSON.stringify(artifact, null, 2)}\n`
    );
    for (const id of createdSilences) {
      await request.delete(`/api/v1/alert-silences/${id}`, { headers });
    }
    for (const id of createdRules) {
      await request.delete(`/api/v1/alert-rules/${id}`, { headers });
    }
    for (const id of createdChannels) {
      await request.delete(`/api/v1/alert-channels/${id}`, { headers });
    }
    if (channelId) {
      await request.delete(`/api/v1/alert-channels/${channelId}`, { headers });
    }
    // Also sweep anything a failed run left behind before cleanup ran.
    await deleteByPrefix(request, adminToken, 'alert-rules', 'E2E Rule ');
    await deleteByPrefix(request, adminToken, 'alert-channels', 'E2E Alert ');
    await restorePausedChannels(request, adminToken, pausedChannels);
    pausedChannels = [];
  }
});

test('IM channel form shows the fixed prefix and only asks for the key', async ({
  page,
  request,
}) => {
  test.setTimeout(60_000);
  const adminToken = await apiLogin(request);
  const headers = authHeaders(adminToken);
  const name = `E2E Alert IM ${runId}`;
  const defaultPrefix = 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=';

  try {
    await deleteByPrefix(request, adminToken, 'alert-channels', name);
    await page.goto('/alert-config');

    await page.getByTestId('channel-create').click();
    const dialog = page.getByTestId('channel-dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByTestId('channel-name').locator('input').fill(name);
    // The WECOM form is the default and already carries the fixed prefix.
    await expect(dialog.getByTestId('channel-webhook-base').locator('input')).toHaveValue(
      defaultPrefix
    );
    await expect(dialog.getByTestId('channel-webhook-key').locator('input')).toHaveValue('');
    await dialog.getByTestId('channel-webhook-key').locator('input').fill('e2e-key-123');
    await dialog.getByTestId('channel-save').click();
    await expect(dialog).toBeHidden();

    const createdList = await (await request.get('/api/v1/alert-channels', { headers })).json();
    const created = (createdList as Array<any>).find(item => item.name === name);
    expect(created).toBeTruthy();
    expect(created.config.webhookUrl).toBe(`${defaultPrefix}******`);

    const card = page.getByTestId('channel-card').filter({ hasText: name }).first();
    await expect(card).toBeVisible();
    await card.getByRole('button', { name: '编辑' }).click();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByTestId('channel-webhook-base').locator('input')).toHaveValue(
      defaultPrefix
    );
    await expect(dialog.getByTestId('channel-webhook-key').locator('input')).toHaveValue('******');
    // Keeping the masked key round-trips the stored URL without re-entry.
    await dialog.getByTestId('channel-save').click();
    await expect(dialog).toBeHidden();

    const updatedList = await (await request.get('/api/v1/alert-channels', { headers })).json();
    const updated = (updatedList as Array<any>).find(item => item.id === created.id);
    expect(updated.config.webhookUrl).toBe(`${defaultPrefix}******`);
  } finally {
    await deleteByPrefix(request, adminToken, 'alert-channels', name);
  }
});

test('email channel delivers a routed alert through SMTP', async ({ page, request }) => {
  test.setTimeout(150_000);
  const adminToken = await apiLogin(request);
  const headers = authHeaders(adminToken);
  const emailChannelName = `E2E Alert Email ${runId}`;
  const emailRuleName = `E2E Rule Email ${runId}`;
  const artifact: Record<string, any> = {
    runId,
    emailChannelName,
    emailRuleName,
    smtpPort,
    startedAt: new Date().toISOString(),
  };
  let emailChannelId: number | null = null;
  let emailRuleId: number | null = null;
  const pausedChannels: PausedChannel[] = [];

  try {
    await deleteByPrefix(request, adminToken, 'alert-rules', emailRuleName);
    await deleteByPrefix(request, adminToken, 'alert-channels', emailChannelName);
    await pauseEnabledChannels(request, adminToken, pausedChannels);

    await page.goto('/alert-config');
    await expect(page.getByRole('heading', { name: '告警配置' })).toBeVisible();

    await page.getByTestId('channel-create').click();
    const dialog = page.getByTestId('channel-dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByTestId('channel-name').locator('input').fill(emailChannelName);
    await dialog.getByTestId('channel-type').click();
    await page.getByRole('option', { name: '邮件' }).click();
    await dialog.getByTestId('channel-email-host').locator('input').fill(mockHost);
    await dialog.getByTestId('channel-email-security').click();
    await page.getByRole('option', { name: '不加密（25）' }).click();
    // Selecting the security mode moves the port to its default; set the test
    // port afterwards so the mock listener is used.
    await dialog.getByTestId('channel-email-port').locator('input').fill(String(smtpPort));
    await dialog.getByTestId('channel-email-username').locator('input').fill('e2e-mailer');
    await dialog.getByTestId('channel-email-password').locator('input').fill('e2e-mail-password');
    await dialog.getByTestId('channel-email-from').locator('input').fill('alerts@example.com');
    await dialog
      .getByTestId('channel-email-to')
      .locator('input')
      .fill('ops@example.com, audit@example.com');
    await dialog.getByTestId('channel-email-cc').locator('input').fill('cc@example.com');
    await dialog
      .getByTestId('channel-email-private')
      .locator('input[type="checkbox"]')
      .check({ force: true });
    await dialog.getByTestId('channel-save').click();
    await expect(dialog).toBeHidden();

    const card = page.getByTestId('channel-card').filter({ hasText: emailChannelName }).first();
    await expect(card).toBeVisible();
    await expect(card.getByTestId('channel-enable')).toBeDisabled();

    await card.getByTestId('channel-test').click();
    await expect(card.getByText('成功', { exact: true })).toBeVisible({ timeout: 15_000 });
    await card.getByTestId('channel-enable').click();
    await expect(card).toContainText('已启用', { timeout: 10_000 });

    const channelList = await (await request.get('/api/v1/alert-channels', { headers })).json();
    const channel = (channelList as Array<any>).find(item => item.name === emailChannelName);
    expect(channel).toBeTruthy();
    emailChannelId = channel.id;
    expect(channel.config.password).toBe('******');
    expect(channel.config.host).toBe(mockHost);
    expect(channel.config.to).toBe('ops@example.com, audit@example.com');
    expect(channel.config.allowPrivateNetwork).toBe(true);

    const testMessage = smtpReceived.find(item => item.headers['x-addax-event-key'] === 'test');
    expect(testMessage).toBeTruthy();
    expect(testMessage?.authUser).toBe('e2e-mailer');
    expect(testMessage?.from).toContain('alerts@example.com');
    expect(testMessage?.to.join(',')).toContain('ops@example.com');
    expect(testMessage?.to.join(',')).toContain('audit@example.com');
    expect(testMessage?.to.join(',')).toContain('cc@example.com');
    expect(testMessage?.subject).toContain('测试告警');
    expect(testMessage?.headers['x-addax-event-type']).toBe('SYSTEM');

    // A masked password must survive an ordinary edit without re-entry.
    await card.getByRole('button', { name: '编辑' }).click();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByTestId('channel-email-password').locator('input')).toHaveValue(
      '******'
    );
    await dialog.getByTestId('channel-save').click();
    await expect(dialog).toBeHidden();
    await card.getByTestId('channel-test').click();
    await expect(card.getByText('成功', { exact: true })).toBeVisible({ timeout: 15_000 });
    const afterEditList = await (await request.get('/api/v1/alert-channels', { headers })).json();
    const afterEdit = (afterEditList as Array<any>).find(item => item.id === emailChannelId);
    expect(afterEdit.config.password).toBe('******');
    expect(afterEdit.enabled).toBe(true);

    await page.getByTestId('tab-rules').click();
    await page.getByTestId('rule-create').click();
    const ruleDialog = page.getByTestId('rule-dialog');
    await expect(ruleDialog).toBeVisible();
    await ruleDialog.getByTestId('rule-name').locator('input').fill(emailRuleName);
    await ruleDialog.getByTestId('rule-event-type').click();
    await page.getByRole('option', { name: '系统告警' }).click();
    await ruleDialog.getByTestId('rule-channels').click();
    await page.getByRole('option').filter({ hasText: emailChannelName }).first().click();
    await page.keyboard.press('Escape');
    await ruleDialog.getByTestId('rule-save').click();
    await expect(ruleDialog).toBeHidden();
    await expect(page.getByRole('row').filter({ hasText: emailRuleName })).toBeVisible();

    const ruleList = await (await request.get('/api/v1/alert-rules', { headers })).json();
    const rule = (ruleList as Array<any>).find(item => item.name === emailRuleName);
    expect(rule).toBeTruthy();
    emailRuleId = rule.id;

    const event = await (
      await request.post('/api/v1/alert-events/test', {
        headers,
        data: { eventType: 'SYSTEM', message: `E2E email delivery ${runId}` },
      })
    ).json();
    const deliveries = await waitForDeliveries(request, adminToken, event.id, items =>
      items.some(item => item.channelId === emailChannelId && item.status === 'SENT')
    );
    const delivery = deliveries.find(item => item.channelId === emailChannelId);
    expect(delivery).toBeTruthy();
    expect(delivery?.responseCode).toBe(250);

    const alertMessage = smtpReceived.find(
      item => item.headers['x-addax-event-key'] === event.eventKey
    );
    expect(alertMessage).toBeTruthy();
    expect(alertMessage?.subject).toContain('测试告警 · SYSTEM');
    expect(alertMessage?.headers['x-addax-event-type']).toBe('SYSTEM');
    // The rule form defaults to P1, and the router uses the most severe level
    // among matching rules rather than the event type's default severity.
    expect(alertMessage?.headers['x-addax-severity']).toBe('P1');
    expect(alertMessage?.to.join(',')).toContain('ops@example.com');

    artifact.emailChannel = {
      id: channel.id,
      maskedPassword: channel.config.password,
      host: channel.config.host,
      from: channel.config.from,
      to: channel.config.to,
      cc: channel.config.cc,
      lastTestOk: afterEdit.lastTestOk,
      enabled: afterEdit.enabled,
    };
    artifact.smtpTestMessage = {
      subject: testMessage?.subject,
      from: testMessage?.from,
      to: testMessage?.to,
      authUser: testMessage?.authUser,
      eventKey: testMessage?.headers['x-addax-event-key'],
    };
    artifact.smtpAlertMessage = {
      subject: alertMessage?.subject,
      to: alertMessage?.to,
      eventKey: alertMessage?.headers['x-addax-event-key'],
      severity: alertMessage?.headers['x-addax-severity'],
    };
    artifact.delivery = {
      id: delivery?.id,
      status: delivery?.status,
      channelId: delivery?.channelId,
      responseCode: delivery?.responseCode,
    };
    artifact.finishedAt = new Date().toISOString();
  } finally {
    fs.mkdirSync(artifactDir, { recursive: true });
    fs.writeFileSync(
      path.join(artifactDir, 'alert-email-e2e.json'),
      `${JSON.stringify(artifact, null, 2)}\n`
    );
    if (emailRuleId) {
      await request.delete(`/api/v1/alert-rules/${emailRuleId}`, { headers });
    }
    if (emailChannelId) {
      await request.delete(`/api/v1/alert-channels/${emailChannelId}`, { headers });
    }
    await deleteByPrefix(request, adminToken, 'alert-rules', emailRuleName);
    await deleteByPrefix(request, adminToken, 'alert-channels', emailChannelName);
    await restorePausedChannels(request, adminToken, pausedChannels);
  }
});
