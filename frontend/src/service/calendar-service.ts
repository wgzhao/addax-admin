// 采集日历（交易日历 1021 / 工作日历 1022）维护接口
import Requests from '@/utils/requests';
import { SysItem } from '@/types/database';

export interface CalendarCoverage {
  code: number;
  calendar: string;
  lastDate: string | null;
  warning: string | null;
}

export interface TradeCalendarRefreshResult {
  year: number;
  source: string;
  tradingDays: number;
  added: number;
  removed: number;
  removedDates: string[];
  warning: string | null;
}

const base = '/calendars';

const getCoverage = (): Promise<CalendarCoverage[]> => {
  return Requests.get(`${base}`) as unknown as Promise<CalendarCoverage[]>;
};

const listDates = (dictCode: number, year?: number): Promise<SysItem[]> => {
  const query = year ? `?year=${year}` : '';
  return Requests.get(`${base}/${dictCode}/dates${query}`) as unknown as Promise<SysItem[]>;
};

const addDate = (dictCode: number, date: string): Promise<void> => {
  return Requests.post(`${base}/${dictCode}/dates`, { date }) as unknown as Promise<void>;
};

const removeDate = (dictCode: number, date: string): Promise<void> => {
  return Requests.delete(`${base}/${dictCode}/dates/${date}`) as unknown as Promise<void>;
};

const generate = (dictCode: number, year: number, includeWeekend: boolean): Promise<void> => {
  return Requests.post(
    `${base}/${dictCode}/generate/${year}/${includeWeekend}`
  ) as unknown as Promise<void>;
};

const refreshTrade = (year: number): Promise<TradeCalendarRefreshResult> => {
  return Requests.post(`${base}/1021/refresh-trade/${year}`) as unknown as Promise<
    TradeCalendarRefreshResult
  >;
};

export default {
  getCoverage,
  listDates,
  addDate,
  removeDate,
  generate,
  refreshTrade,
};
