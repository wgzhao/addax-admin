import Requests from '@/utils/requests';

export interface ReconcileRecord {
  id: number;
  tid: number;
  executionId: number | null;
  bizDate: string;
  sourceRows: number | null;
  targetRows: number | null;
  diff: number | null;
  tolerance: number;
  status: 'PASS' | 'FAIL';
  message: string | null;
  createdAt: string;
}

class ReconcileService {
  prefix = '/reconciles';

  list(tid: number): Promise<ReconcileRecord[]> {
    return Requests.get(this.prefix, { tid }) as unknown as Promise<ReconcileRecord[]>;
  }

  run(tid: number, bizDate: string): Promise<ReconcileRecord> {
    return Requests.post(`${this.prefix}/run`, { tid, bizDate }) as unknown as Promise<ReconcileRecord>;
  }
}

export default new ReconcileService();
