// 首页仪表盘接口
import Requests from '@/utils/requests';
import type {
  ErrorRateRow,
  FailTopRow,
  FailTrendRow,
  Last5dRow,
  LastMonthsRow,
  MissingCalendarRow,
  RecsBytesRow,
  SlowTableRow,
  SourceContributionRow,
  TableStatusDistRow,
  TakeSecsHistogram,
} from '@/types/dashboard';

class DashboardService {
  prefix = '/dashboard';

  // 当前采集表状态分布（实时）
  fetchTableStatusDist(): Promise<TableStatusDistRow[]> {
    return Requests.get(`${this.prefix}/table-status-dist`);
  }

  // 近 14 天按操作类型的失败趋势
  fetchFailTrend14d(): Promise<FailTrendRow[]> {
    return Requests.get(`${this.prefix}/last-14d-fail-trend`);
  }

  // 近 N 天失败次数最多的采集任务
  fetchFailTopTables(params?: { days?: number; limit?: number }): Promise<FailTopRow[]> {
    return Requests.get(`${this.prefix}/fail-top-tables`, params);
  }

  // 近 30 天每日错误行占比
  fetchErrorRate30d(): Promise<ErrorRateRow[]> {
    return Requests.get(`${this.prefix}/last-30d-error-rate`);
  }

  // 近 N 天采集缺失日历
  fetchMissingCalendar(params?: { days?: number }): Promise<MissingCalendarRow[]> {
    return Requests.get(`${this.prefix}/missing-collect-calendar`, params);
  }

  // 近 12 个月每月记录数与数据量（GiB）
  fetchRecsBytes12m(): Promise<RecsBytesRow[]> {
    return Requests.get(`${this.prefix}/last-12m-recs-bytes`);
  }

  // 近 30 天各数据源采集量贡献
  fetchSourceContribution30d(): Promise<SourceContributionRow[]> {
    return Requests.get(`${this.prefix}/last-30d-source-contribution`);
  }

  // 近 N 天平均耗时最长的采集任务
  fetchSlowTopTables(params?: { days?: number; limit?: number }): Promise<SlowTableRow[]> {
    return Requests.get(`${this.prefix}/slow-top-tables`, params);
  }

  // 近 N 天采集耗时分布直方图
  fetchTakeSecsHistogram(params?: { days?: number }): Promise<TakeSecsHistogram> {
    return Requests.get(`${this.prefix}/take-secs-histogram`, params);
  }

  // 既有图表：最近 12 个月累计采集量（GiB）
  fetchLast12Months(): Promise<LastMonthsRow[]> {
    return Requests.get(`${this.prefix}/last-12m-collect-data`);
  }

  // 既有图表：近 5 天按源的耗时
  fetchLast5dTime(): Promise<Last5dRow[]> {
    return Requests.get(`${this.prefix}/last-5d-collect-time`);
  }

  // 既有图表：近 5 天按源的数据量（MB）
  fetchLast5dData(): Promise<Last5dRow[]> {
    return Requests.get(`${this.prefix}/last-5d-collect-data`);
  }
}

export const dashboardService = new DashboardService();
