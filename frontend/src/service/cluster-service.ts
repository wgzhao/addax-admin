import Requests from '@/utils/requests';

export interface WorkerNode {
  instanceId: string;
  host: string;
  role: 'MASTER/WORKER' | 'WORKER';
  running: number;
  availableSlots: number;
  concurrentLimit: number;
  weight: number;
  sourceRunning: Record<string, number>;
  lastSeen: string | null;
  online: boolean;
}

export interface ClusterStatus {
  nodes: WorkerNode[];
  masterInstanceId: string;
  totalNodes: number;
  timestamp: string;
}

class ClusterService {
  prefix = '/cluster';

  getNodes(): Promise<ClusterStatus> {
    return Requests.get(`${this.prefix}/nodes`) as unknown as Promise<ClusterStatus>;
  }
}

export const clusterService = new ClusterService();
