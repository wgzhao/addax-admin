import Requests from '@/utils/requests';

interface ApiResponse {
  success: boolean;
  message: string;
}

interface ApiResponseWrapper<T> {
  code: number;
  message: string;
  data: T;
}

interface ExecuteTaskResponse {
  taskId: number;
  message: string;
}

interface FillbackRequest {
  tids: number[];
  startDate?: string;
  endDate?: string;
  dates?: string[];
}

export interface FillbackDetail {
  tid: number;
  table: string;
  dates: string[];
  enqueued: number;
  skipped: boolean;
  reason: string;
}

export interface FillbackResult {
  batchId: string;
  submittedAt: string;
  totalEnqueued: number;
  skipped: number;
  details: FillbackDetail[];
}

export type FillbackProgressState =
  | 'UNKNOWN'
  | 'SKIPPED'
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'PARTIAL_SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'HISTORY_PRUNED';

export interface FillbackProgressCounts {
  total: number;
  pending: number;
  running: number;
  completed: number;
  failed: number;
  cancelled: number;
  unresolved: number;
}

export interface FillbackProgressItem {
  date: string;
  status: string;
  attempts: number;
  maxAttempts: number;
  lastError: string | null;
  queueId: number | null;
  createdAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface FillbackProgress {
  found: boolean;
  batchId: string | null;
  tid: number;
  table: string;
  supported: boolean;
  reason: string | null;
  retired: boolean;
  sourceEnabled: boolean;
  tableStatus: string | null;
  maxDate: string | null;
  startDate: string | null;
  endDate: string | null;
  submitter: string | null;
  submittedAt: string | null;
  requestedCount: number;
  state: FillbackProgressState;
  counts: FillbackProgressCounts;
  items: FillbackProgressItem[];
}

class TaskService {
  prefix: string = '/tasks';

  // 获取队列状态
  getQueueStatus(): Promise<Map<string, any>> {
    return Requests.get(`${this.prefix}/queue`) as unknown as Promise<Map<string, any>>;
  }

  // 更改队列监控器状态
  updateQueueStatus(state: 'running' | 'stopped'): Promise<ApiResponse> {
    return Requests.patch(`${this.prefix}/queue`, { state }) as unknown as Promise<ApiResponse>;
  }

  // 重置采集任务队列
  resetQueue(): Promise<ApiResponse> {
    return Requests.post(
      `${this.prefix}/queue/actions/reset`,
      {}
    ) as unknown as Promise<ApiResponse>;
  }

  // 立即更新所有采集任务配置
  updateAllJobs(): Promise<ApiResponse> {
    return Requests.post(`${this.prefix}/jobs`, {}) as unknown as Promise<ApiResponse>;
  }

  // 立即更新单个采集任务配置
  updateJob(taskId: number): Promise<ApiResponse> {
    return Requests.put(`${this.prefix}/${taskId}/job`, {}) as unknown as Promise<ApiResponse>;
  }

  // 执行采集任务，支持异步参数
  executeTask(taskId: number, timeout = 60000, type?: 'async'): Promise<ExecuteTaskResponse> {
    const params = type === 'async' ? { type: 'async' } : undefined;
    return Requests.post(
      `${this.prefix}/${taskId}/executions`,
      {},
      { timeout, params }
    ) as unknown as Promise<ExecuteTaskResponse>;
  }

  executeTasksBatch(taskIds: Array<number>): Promise<ExecuteTaskResponse> {
    return Requests.post(
      `${this.prefix}/executions/batch`,
      taskIds
    ) as unknown as Promise<ExecuteTaskResponse>;
  }

  getAllTaskStatus(): Promise<Array<Map<string, any>>> {
    return Requests.get(`${this.prefix}/status`) as unknown as Promise<Array<Map<string, any>>>;
  }
  getLastError(taskId: number): Promise<string> {
    return Requests.get(`${this.prefix}/${taskId}/last-error`) as unknown as Promise<string>;
  }

  // Kill a running task
  killTask(taskId: number): Promise<ApiResponse> {
    return Requests.post(`${this.prefix}/${taskId}/kill`, {}) as unknown as Promise<ApiResponse>;
  }

  // 补数采集：对指定表在给定日期集合或日期范围内重新采集
  fillback(request: FillbackRequest): Promise<ApiResponseWrapper<FillbackResult>> {
    return Requests.post(`${this.prefix}/fillback`, request) as unknown as Promise<
      ApiResponseWrapper<FillbackResult>
    >;
  }

  // 补数进度：不传 batchId 时返回该表最近一次补数
  getFillbackProgress(
    tid: number,
    batchId?: string
  ): Promise<ApiResponseWrapper<FillbackProgress>> {
    return Requests.get(
      `${this.prefix}/${tid}/fillback/progress`,
      batchId ? { batchId } : undefined
    ) as unknown as Promise<ApiResponseWrapper<FillbackProgress>>;
  }
}

export default new TaskService();
