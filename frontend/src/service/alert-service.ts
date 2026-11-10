import Requests from '@/utils/requests';

export type AlertChannelType = 'WECOM' | 'DINGTALK' | 'FEISHU' | 'WEBHOOK' | 'EMAIL';
export type AlertEventType = 'COLLECT_FAILURE' | 'COLLECT_RECOVERY' | 'SECURITY' | 'SYSTEM';
export type AlertSeverity = 'P1' | 'P2' | 'P3';
export type AlertScopeType = 'ALL' | 'SOURCE' | 'TABLE';

export interface AlertChannel {
  id: number;
  name: string;
  type: AlertChannelType;
  typeLabel: string;
  enabled: boolean;
  config: Record<string, any>;
  rateLimitPerMinute: number;
  lastTestAt: string | null;
  lastTestOk: boolean | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AlertChannelRequest {
  name: string;
  type: AlertChannelType;
  config: Record<string, any>;
  enabled?: boolean;
  rateLimitPerMinute?: number;
}

export interface AlertChannelTestResult {
  ok: boolean;
  message: string;
  responseCode: number | null;
  responseBody: string | null;
  latencyMs: number;
  testedAt: string;
}

export interface AlertRule {
  id: number;
  name: string;
  eventType: AlertEventType;
  severity: AlertSeverity;
  scopeType: AlertScopeType;
  scopeValue: string | null;
  channelIds: number[];
  cooldownSecs: number;
  groupWindowSecs: number;
  ackRequired: boolean;
  escalateAfterSecs: number | null;
  escalationChannelIds: number[] | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AlertRuleRequest {
  name: string;
  eventType: AlertEventType;
  severity: AlertSeverity;
  scopeType: AlertScopeType;
  scopeValue: string | null;
  channelIds: number[];
  cooldownSecs: number;
  groupWindowSecs: number;
  enabled?: boolean;
}

export interface AlertEvent {
  id: number;
  eventKey: string;
  eventType: string;
  severity: string;
  scopeType: string;
  scopeValue: string | null;
  status: string;
  firstSeen: string;
  lastSeen: string;
  fireCount: number;
  resolvedAt: string | null;
  title: string;
  text: string;
}

export interface AlertDelivery {
  id: number;
  eventId: number;
  eventTitle: string | null;
  eventType: string | null;
  severity: string | null;
  channelId: number | null;
  channelName: string | null;
  status: string;
  attempts: number;
  nextRetryAt: string | null;
  responseCode: number | null;
  responseBody: string | null;
  lastError: string | null;
  sentAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AlertSilence {
  id: number;
  scopeType: string;
  scopeValue: string | null;
  startsAt: string;
  endsAt: string;
  reason: string | null;
  createdBy: string | null;
  createdAt: string;
  active: boolean;
}

export interface AlertSummary {
  channelCount: number;
  enabledChannelCount: number;
  paused: boolean;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  empty: boolean;
}

class AlertService {
  baseUrl = 'alert-channels';

  listChannels(): Promise<AlertChannel[]> {
    return Requests.get(this.baseUrl);
  }

  getSummary(): Promise<AlertSummary> {
    return Requests.get(`${this.baseUrl}/summary`);
  }

  createChannel(payload: AlertChannelRequest): Promise<AlertChannel> {
    return Requests.post(this.baseUrl, payload);
  }

  updateChannel(id: number, payload: AlertChannelRequest): Promise<AlertChannel> {
    return Requests.put(`${this.baseUrl}/${id}`, payload);
  }

  deleteChannel(id: number): Promise<void> {
    return Requests.delete(`${this.baseUrl}/${id}`);
  }

  testChannel(id: number): Promise<AlertChannelTestResult> {
    return Requests.post(`${this.baseUrl}/${id}/test`, undefined, { timeout: 20000 });
  }

  listRules(): Promise<AlertRule[]> {
    return Requests.get('alert-rules');
  }

  createRule(payload: AlertRuleRequest): Promise<AlertRule> {
    return Requests.post('alert-rules', payload);
  }

  updateRule(id: number, payload: AlertRuleRequest): Promise<AlertRule> {
    return Requests.put(`alert-rules/${id}`, payload);
  }

  deleteRule(id: number): Promise<void> {
    return Requests.delete(`alert-rules/${id}`);
  }

  listEvents(params: Record<string, any>): Promise<PageResponse<AlertEvent>> {
    return Requests.get('alert-events', params);
  }

  sendTestEvent(payload: {
    eventType?: string;
    message?: string;
    sourceDb?: string;
    sourceTable?: string;
    tid?: number;
  }): Promise<AlertEvent> {
    return Requests.post('alert-events/test', payload);
  }

  listDeliveries(params: Record<string, any>): Promise<PageResponse<AlertDelivery>> {
    return Requests.get('alert-deliveries', params);
  }

  retryDelivery(id: number): Promise<AlertDelivery> {
    return Requests.post(`alert-deliveries/${id}/retry`);
  }

  listSilences(): Promise<AlertSilence[]> {
    return Requests.get('alert-silences');
  }

  createSilence(payload: {
    scopeType: string;
    scopeValue: string | null;
    startsAt: string;
    endsAt: string;
    reason?: string;
  }): Promise<AlertSilence> {
    return Requests.post('alert-silences', payload);
  }

  deleteSilence(id: number): Promise<void> {
    return Requests.delete(`alert-silences/${id}`);
  }
}

export default new AlertService();
