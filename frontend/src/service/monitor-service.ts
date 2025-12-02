// 采集监控接口
import Requests from '@/utils/requests'
import { ref } from 'process'

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

  smsDetail(): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/sms-detail') as unknown as Promise<Array<Map<string, any>>>
  }
}
export const monitorService = new MonitorService()
