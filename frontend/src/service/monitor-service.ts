// 采集聚合数据：首页「今日运行」与数据治理「数据洞察」共用。
// 面向前端页面的 运行监控 已下线，接口路径暂时保留以兼容现有调用方。
import Requests from '@/utils/requests';

class MonitorService {
  prefix = '/monitor';

  // 数据源完成情况：首页今日运行时间轴
  fetchAccomplishList(): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/accomplish') as unknown as Promise<Array<Map<string, any>>>;
  }

  // 数据洞察：近 15 天数据量无变化
  insightNoChange(params?: { days?: number }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/no-change', params) as unknown as Promise<
      Array<Map<string, any>>
    >;
  }

  // 数据洞察：近 15 天数据变化率 < 2%
  insightLowChange(params?: {
    days?: number;
    threshold?: number;
  }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/low-change', params) as unknown as Promise<
      Array<Map<string, any>>
    >;
  }

  // 数据洞察：近 15 天数据变化率 > 40%
  insightHighChange(params?: {
    days?: number;
    threshold?: number;
  }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/high-change', params) as unknown as Promise<
      Array<Map<string, any>>
    >;
  }

  // 数据洞察：近 15 天采集耗时变动率 > 40%
  insightTimeChange(params?: {
    days?: number;
    threshold?: number;
  }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/time-change', params) as unknown as Promise<
      Array<Map<string, any>>
    >;
  }

  // 数据洞察：近 N 天内缺失采集的有效表
  insightMissingCollect(params?: { days?: number }): Promise<Array<Map<string, any>>> {
    return Requests.get(this.prefix + '/insight/missing-collect', params) as unknown as Promise<
      Array<Map<string, any>>
    >;
  }
}
export const monitorService = new MonitorService();
