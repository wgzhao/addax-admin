import Requests from '@/utils/requests';
import type { Page } from '@/types';
import type {
  Failure,
  FailureCleanupResult,
  FailureDetail,
  FailureGroup,
  FailureStats,
  LatestFailure,
} from '@/types/failure';

export interface FailureQuery {
  page?: number;
  pageSize?: number;
  tid?: number;
  sourceId?: number;
  stage?: string;
  category?: string;
  fingerprint?: string;
  from?: string;
  to?: string;
  q?: string;
  finalOnly?: boolean;
}

export interface FailureGroupQuery extends Omit<FailureQuery, 'page' | 'pageSize'> {
  limit?: number;
}

export interface FailureCleanupRequest {
  before: string;
  tid?: number;
  sourceId?: number;
  stage?: string;
  dryRun: boolean;
}

class FailureService {
  prefix = '/failures';

  getFailures(params: FailureQuery): Promise<Page<Failure>> {
    return Requests.get(this.prefix, params) as unknown as Promise<Page<Failure>>;
  }

  getLatest(tid: number): Promise<LatestFailure> {
    return Requests.get(`${this.prefix}/tables/${tid}/latest`) as unknown as Promise<LatestFailure>;
  }

  getLatestBatch(tids: number[]): Promise<Record<string, LatestFailure>> {
    return Requests.get(`${this.prefix}/latest`, {
      tids: tids.join(','),
    }) as unknown as Promise<Record<string, LatestFailure>>;
  }

  getDetail(id: number): Promise<FailureDetail> {
    return Requests.get(`${this.prefix}/${id}`) as unknown as Promise<FailureDetail>;
  }

  getGroups(params: FailureGroupQuery): Promise<FailureGroup[]> {
    return Requests.get(`${this.prefix}/groups`, params) as unknown as Promise<FailureGroup[]>;
  }

  getStats(params: FailureGroupQuery): Promise<FailureStats> {
    return Requests.get(`${this.prefix}/stats`, params) as unknown as Promise<FailureStats>;
  }

  cleanup(payload: FailureCleanupRequest): Promise<FailureCleanupResult> {
    return Requests.post(`${this.prefix}/cleanup`, payload) as unknown as Promise<FailureCleanupResult>;
  }
}

export default new FailureService();
