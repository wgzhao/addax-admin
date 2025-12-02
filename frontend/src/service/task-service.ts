interface TaskStatusResponse {
  status: string
  progress: string
  result?: string
}
import Requests from '@/utils/requests'

interface ApiResponse {
  success: boolean
  message: string
}

interface ExecuteTaskResponse {
  taskId: number
  message: string
}

class TaskService {
  prefix: string = '/tasks'

  // 获取队列状态
  getQueueStatus(): Promise<Map<string, any>> {
    return Requests.get(`${this.prefix}/queue`) as unknown as Promise<Map<string, any>>
  }

  // 更改队列监控器状态
  updateQueueStatus(state: 'running' | 'stopped'): Promise<ApiResponse> {
    return Requests.patch(`${this.prefix}/queue`, { state }) as unknown as Promise<ApiResponse>
  }

  // 重置采集任务队列
  resetQueue(): Promise<ApiResponse> {
    return Requests.post(
      `${this.prefix}/queue/actions/reset`,
      {}
    ) as unknown as Promise<ApiResponse>
  }

  // 立即更新所有采集任务配置
  updateAllJobs(): Promise<ApiResponse> {
    return Requests.post(`${this.prefix}/jobs`, {}) as unknown as Promise<ApiResponse>
  }

  // 立即更新单个采集任务配置
  updateJob(taskId: number): Promise<ApiResponse> {
    return Requests.put(`${this.prefix}/${taskId}/job`, {}) as unknown as Promise<ApiResponse>
  }

  // 执行采集任务，支持异步参数
  executeTask(taskId: number, timeout = 60000, type?: 'async'): Promise<ExecuteTaskResponse> {
    const params = type === 'async' ? { type: 'async' } : undefined
    return Requests.post(
      `${this.prefix}/${taskId}/executions`,
      {},
      { timeout, params }
    ) as unknown as Promise<ExecuteTaskResponse>
  }

  executeTasksBatch(taskIds: Array<number>): Promise<ExecuteTaskResponse> {
    return Requests.post(
      `${this.prefix}/executions/batch`,
      taskIds
    ) as unknown as Promise<ExecuteTaskResponse>
  }

  getAllTaskStatus(): Promise<Array<Map<string, any>>> {
    return Requests.get(`${this.prefix}/status`) as unknown as Promise<Array<Map<string, any>>>
  }
  getLastError(taskId: number): Promise<string> {
    return Requests.get(`${this.prefix}/${taskId}/last-error`) as unknown as Promise<string>
  }
}

export default new TaskService()
