import Requests from '@/utils/requests';

export interface AuditEvent {
  id: number;
  eventId: string;
  occurredAt: string;
  requestId: string | null;
  actorType: 'USER' | 'SYSTEM';
  actorUsername: string | null;
  actorRoles: string[];
  sessionId: string | null;
  action: string;
  category: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  resourceType: string | null;
  resourceId: string | null;
  resourceName: string | null;
  outcome: 'SUCCESS' | 'FAILED' | 'DENIED' | 'ACCEPTED';
  httpMethod: string | null;
  requestPath: string | null;
  clientIp: string | null;
  userAgent: string | null;
  nodeId: string | null;
  beforeData: unknown;
  afterData: unknown;
  changedFields: string[];
  errorCode: string | null;
  errorMessage: string | null;
  metadata: Record<string, unknown>;
}

export interface AuditQuery {
  from?: string;
  to?: string;
  actor?: string;
  action?: string;
  outcome?: string;
  severity?: string;
  resourceType?: string;
  resourceId?: string;
  requestId?: string;
  q?: string;
  page?: number;
  pageSize?: number;
}

export interface AuditPage {
  content: AuditEvent[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  empty: boolean;
}

class AuditService {
  search(params: AuditQuery): Promise<AuditPage> {
    return Requests.get('/audit-events', params) as Promise<AuditPage>;
  }

  get(id: number): Promise<AuditEvent> {
    return Requests.get(`/audit-events/${id}`) as Promise<AuditEvent>;
  }

  exportCsv(params: AuditQuery): Promise<Blob> {
    return Requests.get('/audit-events/export.csv', params, {
      responseType: 'blob',
    }) as Promise<Blob>;
  }
}

export default new AuditService();
