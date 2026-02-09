// 采集监控接口
import Requests from '@/utils/requests'

class MonitorService {
  prefix = '/monitor'
  d = []
  // 数据源完成情况
  fetchAccomplishList(): Promise<Array<Map<string, any>>> {
    // fetch data via api and return
    return Requests.get(this.prefix + '/accomplish') as unknown as Promise<Array<Map<string, any>>>
  }

  // 特殊任务提醒：错误、耗时过长、有重试、有拒绝行
  fetchSpecialTask(): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/special-task') as unknown as Promise<
      Array<Map<string, any>>
    >
  }

  // 采集拒绝行信息
  fetchRejectTask(): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/reject-task') as unknown as Promise<Array<Map<string, any>>>
  }

  sysRisks(): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/sys-risk') as unknown as Promise<Array<Map<string, any>>>
  }

  fieldsChanges(): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/field-change') as unknown as Promise<
      Array<Map<string, any>>
    >
  }

  // 字段变更 - 分页接口
  fieldsChangesPaged(
    page: number,
    pageSize: number,
    sortBy?: { sortField?: string | null; sortOrder?: string | null }
  ): Promise<{ content: Array<Map<string, any>>; totalElements: number }> {
    const params = {
      page,
      pageSize,
      sortField: sortBy?.sortField,
      sortOrder: sortBy?.sortOrder
    }
    // 复用同一路径，通过查询参数启用后端分页
    return Requests.get(this.prefix + '/field-change', params) as unknown as Promise<{
      content: Array<Map<string, any>>
      totalElements: number
    }>
  }

  smsDetail(): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/sms-detail') as unknown as Promise<Array<Map<string, any>>>
  }

  // 数据洞察：近 15 天数据量无变化
  insightNoChange(params?: { days?: number }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/no-change', params) as unknown as Promise<
      Array<Map<string, any>>
    >
  }

  // 数据洞察：近 15 天数据变化率 < 2%
  insightLowChange(params?: { days?: number; threshold?: number }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/low-change', params) as unknown as Promise<
      Array<Map<string, any>>
    >
  }

  // 数据洞察：近 15 天数据变化率 > 40%
  insightHighChange(params?: { days?: number; threshold?: number }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/high-change', params) as unknown as Promise<
      Array<Map<string, any>>
    >
  }

  // 数据洞察：近 15 天采集耗时变动率 > 40%
  insightTimeChange(params?: { days?: number; threshold?: number }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/time-change', params) as unknown as Promise<
      Array<Map<string, any>>
    >
  }
}
export const monitorService = new MonitorService()
