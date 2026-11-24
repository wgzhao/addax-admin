import { test, expect, type APIRequestContext } from '@playwright/test';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 切日刷新从失败恢复后，必须通知当初收到失败告警的通道。
 *
 * 这是破坏性用例：它真的触发切日刷新（重置全部表状态、停/启队列监控、刷新表结构），
 * 所以只能指向专用后端 + 专用库 + 独立 Redis DB：
 *
 *   # 1. 从当前开发库克隆（源/目标改到克隆库自身，并补建 7 张源表，见 README）
 *   # 2. 启动专用后端
 *   DB_NAME=addax_admin_daycut_e2e REDIS_DB=5 SERVER_PORT=50602 \
 *     java -jar backend/target/addax-admin-*.jar
 *
 *   # 3. 运行
 *   E2E_DAYCUT_REFRESH=1 E2E_DB_NAME=addax_admin_daycut_e2e \
 *   E2E_API_HOST=http://localhost:50602 VITE_API_HOST=http://localhost:50602 \
 *   yarn e2e tests/e2e/schedule-recovery-notice.spec.ts
 *
 * 场景（成功率不高的成功路径才算覆盖）：先把超时压到 0 秒制造 P1 失败，通道 A 收到；
 * 失败后再新建并启用通道 B；恢复超时后重新切日成功，恢复通知只能投递给 A，不能把 B
 * 拉进受众；再跑一次成功切日，没有打开的故障事件时不产生任何恢复通知。
 */

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const apiBase = (process.env.E2E_API_HOST || 'http://localhost:50602').replace(/\/$/, '');
const username = process.env.E2E_USERNAME || 'admin';
const password = process.env.E2E_PASSWORD || 'admin123';
const mockPort = Number(process.env.E2E_DAYCUT_MOCK_PORT || 18890);
const mockHost = process.env.E2E_DAYCUT_MOCK_HOST || '127.0.0.1';
const dbName = process.env.E2E_DB_NAME || '';
const runId = `${Date.now()}`.slice(-8);
const channelAName = `E2E DayCut A ${runId}`;
const channelBName = `E2E DayCut B ${runId}`;

const enabled = process.env.E2E_DAYCUT_REFRESH === '1' && dbName.includes('e2e');

type Received = { at: string; body: any };
const received: Received[] = [];
let mockServer: http.Server;

test.beforeAll(async () => {
  mockServer = http.createServer((req, res) => {
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
      received.push({ at: new Date().toISOString(), body });
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{"ok":true}');
    });
  });
  await new Promise<void>((resolve, reject) => {
    mockServer.once('error', reject);
    mockServer.listen(mockPort, '0.0.0.0', () => resolve());
  });
});

test.afterAll(async () => {
  if (mockServer) {
    await new Promise<void>(resolve => mockServer.close(() => resolve()));
  }
});

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

async function waitFor<T>(
  fetch: () => Promise<T>,
  predicate: (value: T) => boolean,
  label: string,
  timeoutMs = 45_000
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  let last: T;
  while (Date.now() < deadline) {
    last = await fetch();
    if (predicate(last)) {
      return last;
    }
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error(`${label} did not reach the expected state: ${JSON.stringify(last)}`);
}

async function listEvents(request: APIRequestContext, token: string, from: string) {
  const response = await request.get(`${apiBase}/api/v1/alert-events`, {
    headers: authHeaders(token),
    params: { eventType: 'SCHEDULE', page: 0, pageSize: 100, from },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).content as Array<any>;
}

async function listDeliveries(request: APIRequestContext, token: string, eventId: number) {
  const response = await request.get(`${apiBase}/api/v1/alert-deliveries`, {
    headers: authHeaders(token),
    params: { eventId, page: 0, pageSize: 20 },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).content as Array<any>;
}

async function deleteByPrefix(
  request: APIRequestContext,
  token: string,
  collection: 'alert-channels' | 'alert-rules',
  prefix: string
) {
  const response = await request.get(`${apiBase}/api/v1/${collection}`, {
    headers: authHeaders(token),
  });
  if (!response.ok()) return;
  const items = (await response.json()) as Array<{ id: number; name: string }>;
  for (const item of items) {
    if (item.name?.startsWith(prefix)) {
      await request.delete(`${apiBase}/api/v1/${collection}/${item.id}`, {
        headers: authHeaders(token),
      });
    }
  }
}

type CreatedChannel = { id: number; name: string };

async function createEnabledWebhookChannel(
  request: APIRequestContext,
  token: string,
  name: string
): Promise<CreatedChannel> {
  const config = {
    url: `http://${mockHost}:${mockPort}/hook`,
    allowPrivateNetwork: true,
  };
  const created = await request.post(`${apiBase}/api/v1/alert-channels`, {
    headers: authHeaders(token),
    data: { name, type: 'WEBHOOK', config, rateLimitPerMinute: 120 },
  });
  expect(created.ok(), `create channel ${name}: ${created.status()}`).toBeTruthy();
  const channel = (await created.json()) as CreatedChannel;

  const tested = await request.post(`${apiBase}/api/v1/alert-channels/${channel.id}/test`, {
    headers: authHeaders(token),
    timeout: 20_000,
  });
  expect(tested.ok(), `test channel ${name}: ${tested.status()}`).toBeTruthy();
  expect((await tested.json()).ok, `channel ${name} test send must succeed`).toBe(true);

  const enabledResponse = await request.put(`${apiBase}/api/v1/alert-channels/${channel.id}`, {
    headers: authHeaders(token),
    data: { name, type: 'WEBHOOK', config, rateLimitPerMinute: 120, enabled: true },
  });
  expect(enabledResponse.ok(), `enable channel ${name}: ${enabledResponse.status()}`).toBeTruthy();
  const enabledChannel = (await enabledResponse.json()) as any;
  expect(enabledChannel.enabled).toBe(true);
  return channel;
}

test('day-cut recovery notifies only the channels that saw the failure', async ({ request }) => {
  test.skip(
    !enabled,
    'destructive day-cut scenario: set E2E_DAYCUT_REFRESH=1 and point E2E_DB_NAME at a dedicated e2e database'
  );
  test.setTimeout(420_000);

  const login = await request.post(`${apiBase}/api/v1/auth/login`, {
    data: { username, password },
  });
  expect(login.ok(), `login failed: ${login.status()}`).toBeTruthy();
  const token = (await login.json()).data as string;

  const artifact: Record<string, any> = {
    runId,
    apiBase,
    dbName,
    mockUrl: `http://${mockHost}:${mockPort}/hook`,
    startedAt: new Date().toISOString(),
  };
  const createdChannels: number[] = [];
  let originalTimeout: unknown = null;
  let timeoutRestored = false;

  try {
    await deleteByPrefix(request, token, 'alert-channels', 'E2E DayCut ');
    await deleteByPrefix(request, token, 'alert-rules', 'E2E DayCut ');

    const configResponse = await request.get(`${apiBase}/api/v1/settings/sys-config`, {
      headers: authHeaders(token),
    });
    expect(configResponse.ok()).toBeTruthy();
    const sysConfig = await configResponse.json();
    originalTimeout = sysConfig.SCHEMA_REFRESH_TIMEOUT;
    artifact.originalTimeout = originalTimeout;
    // Fail deterministically: 0 makes Future.get return a TimeoutException at once,
    // while 1s can still be enough for a one-table refresh on a fast machine.
    const shorten = await request.post(`${apiBase}/api/v1/settings/sys-config`, {
      headers: authHeaders(token),
      data: { SCHEMA_REFRESH_TIMEOUT: 0 },
    });
    expect(shorten.ok()).toBeTruthy();

    const channelA = await createEnabledWebhookChannel(request, token, channelAName);
    createdChannels.push(channelA.id);

    // ── run 1: hard failure, P1 delivered to channel A only ────────────────
    const failureStartedAt = new Date(Date.now() - 1000).toISOString();
    const failedRun = await request.post(`${apiBase}/api/v1/system/refresh/start`, {
      headers: authHeaders(token),
      timeout: 180_000,
    });
    expect(failedRun.status(), 'a 0s timeout must fail the run').toBe(500);
    const failedBody = await failedRun.text();
    expect(failedBody).toContain('切日刷新失败');

    const failureEvent = await waitFor(
      () => listEvents(request, token, failureStartedAt),
      events => events.some(event => event.eventKey.startsWith('SCHEDULE:DAYCUT:')),
      'day-cut failure event'
    ).then(events => events.find(event => event.eventKey.startsWith('SCHEDULE:DAYCUT:')));
    expect(failureEvent.status).toBe('FIRING');
    expect(failureEvent.severity).toBe('P1');

    const failureDeliveries = await waitFor(
      () => listDeliveries(request, token, failureEvent.id),
      deliveries => deliveries.some(delivery => delivery.status === 'SENT'),
      'failure delivery'
    );
    expect(failureDeliveries).toHaveLength(1);
    expect(failureDeliveries[0].channelId).toBe(channelA.id);
    artifact.failure = {
      eventKey: failureEvent.eventKey,
      status: failureEvent.status,
      severity: failureEvent.severity,
      delivery: {
        channelId: failureDeliveries[0].channelId,
        status: failureDeliveries[0].status,
      },
    };

    // ── a second channel joins after the failure ───────────────────────────
    // It is enabled before recovery but never saw the failure; the recovery
    // must not introduce it as a new audience.
    const channelB = await createEnabledWebhookChannel(request, token, channelBName);
    createdChannels.push(channelB.id);

    if (originalTimeout != null) {
      const restore = await request.post(`${apiBase}/api/v1/settings/sys-config`, {
        headers: authHeaders(token),
        data: { SCHEMA_REFRESH_TIMEOUT: Number(originalTimeout) },
      });
      expect(restore.ok()).toBeTruthy();
      timeoutRestored = true;
    }

    // ── run 2: success after the failure ───────────────────────────────────
    const recoveryStartedAt = new Date(Date.now() - 1000).toISOString();
    const successfulRun = await request.post(`${apiBase}/api/v1/system/refresh/start`, {
      headers: authHeaders(token),
      timeout: 180_000,
    });
    const successfulBody = await successfulRun.text();
    expect(successfulRun.status(), successfulBody).toBe(200);
    expect(successfulBody).toContain('切日刷新完成');

    const recoveryEvent = await waitFor(
      () => listEvents(request, token, recoveryStartedAt),
      events => events.some(event => event.eventKey.startsWith('SCHEDULE:DAYCUT_RECOVERY:')),
      'day-cut recovery event'
    ).then(events => events.find(event => event.eventKey.startsWith('SCHEDULE:DAYCUT_RECOVERY:')));
    expect(recoveryEvent.eventType).toBe('SCHEDULE');
    expect(recoveryEvent.status).toBe('RESOLVED');

    const recoveryDeliveries = await waitFor(
      () => listDeliveries(request, token, recoveryEvent.id),
      deliveries => deliveries.length > 0 && deliveries.every(delivery => delivery.status === 'SENT'),
      'recovery delivery'
    );
    expect(recoveryDeliveries).toHaveLength(1);
    expect(recoveryDeliveries[0].channelId).toBe(channelA.id);
    const recoveredFailure = (await listEvents(request, token, failureStartedAt)).find(
      event => event.id === failureEvent.id
    );
    expect(recoveredFailure.status).toBe('RESOLVED');

    const recoveryPayload = received.find(item => item.body?.eventKey === recoveryEvent.eventKey);
    expect(recoveryPayload, 'webhook must receive the recovery message').toBeTruthy();
    expect(String(recoveryPayload?.body?.text || '')).toContain('恢复');

    artifact.recovery = {
      eventKey: recoveryEvent.eventKey,
      status: recoveryEvent.status,
      deliveredTo: recoveryDeliveries.map(delivery => delivery.channelId),
      failureResolved: recoveredFailure.status,
      webhookText: recoveryPayload?.body?.text,
    };

    // ── run 3: a clean success with nothing open stays silent ──────────────
    const silentRun = await request.post(`${apiBase}/api/v1/system/refresh/start`, {
      headers: authHeaders(token),
      timeout: 180_000,
    });
    expect(silentRun.status()).toBe(200);
    await new Promise(resolve => setTimeout(resolve, 5_000));
    const recoveryCountAfter = (await listEvents(request, token, recoveryStartedAt)).filter(event =>
      event.eventKey.startsWith('SCHEDULE:DAYCUT_RECOVERY:')
    ).length;
    expect(recoveryCountAfter, 'a later clean run must not repeat the recovery').toBe(1);
    artifact.silentRun = { recoveryEvents: recoveryCountAfter };
  } finally {
    if (!timeoutRestored && originalTimeout != null) {
      await request
        .post(`${apiBase}/api/v1/settings/sys-config`, {
          headers: authHeaders(token),
          data: { SCHEMA_REFRESH_TIMEOUT: Number(originalTimeout) },
        })
        .catch(() => undefined);
    }
    for (const id of createdChannels) {
      await request
        .delete(`${apiBase}/api/v1/alert-channels/${id}`, { headers: authHeaders(token) })
        .catch(() => undefined);
    }
    artifact.finishedAt = new Date().toISOString();
    fs.mkdirSync(artifactDir, { recursive: true });
    fs.writeFileSync(
      path.join(artifactDir, 'schedule-recovery-notice.json'),
      JSON.stringify(artifact, null, 2)
    );
  }
});
