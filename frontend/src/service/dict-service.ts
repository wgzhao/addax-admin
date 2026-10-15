// 参数管理接口
import { ref } from 'vue';
import Requests from '@/utils/requests';
import { SysDict, SysItem } from '@/types/database';

const apiPrefix = ref('/dicts');

const listDicts = (): Promise<SysDict[]> => {
  return Requests.get(apiPrefix.value) as unknown as Promise<SysDict[]>;
};

const createOrUpdateDict = (dict: SysDict): Promise<SysDict> => {
  return Requests.post(`${apiPrefix.value}/dict`, dict) as unknown as Promise<SysDict>;
};

const deleteDict = (code: number): Promise<void> => {
  return Requests.delete(`${apiPrefix.value}/${code}`) as unknown as Promise<void>;
};

// items means the dictionary table

const listSysItems = (dictCode: number): Promise<SysItem[]> => {
  return Requests.get(`${apiPrefix.value}/${dictCode}/items`) as unknown as Promise<SysItem[]>;
};

const createDictItem = (dictItem: SysItem): Promise<SysItem> => {
  return Requests.post(
    `${apiPrefix.value}/${dictItem.dictCode}/items`,
    dictItem
  ) as unknown as Promise<SysItem>;
};

const updateDictItem = (dictItem: SysItem): Promise<SysItem> => {
  return Requests.put(
    `${apiPrefix.value}/${dictItem.dictCode}/items/${dictItem.itemKey}`,
    dictItem
  ) as unknown as Promise<SysItem>;
};

const deleteDictItem = (dictCode: number, itemKey: string): Promise<void> => {
  return Requests.delete(
    `${apiPrefix.value}/${dictCode}/items/${itemKey}`
  ) as unknown as Promise<void>;
};

const getHdfsStorageDefaults = (): Promise<{ storageFormat: string; compressFormat: string }> => {
  return Requests.get(`${apiPrefix.value}/hdfs-storage-defaults`) as unknown as Promise<{
    storageFormat: string;
    compressFormat: string;
  }>;
};

// 生成日历字典（后端批量生成）。
// dictCode: 1021 交易日历、1022 工作日历；year: 四位年份；includeWeekend: true/false
const generateCalendar = (
  dictCode: number,
  year: number,
  includeWeekend: boolean
): Promise<void> => {
  const include = includeWeekend ? 'true' : 'false';
  return Requests.post(
    `${apiPrefix.value}/${dictCode}/generate-calendar/${year}/${include}`
  ) as unknown as Promise<void>;
};

export interface CalendarCoverage {
  code: number;
  calendar: string;
  lastDate: string | null;
  warning: string | null;
}

// 日历覆盖范围，用于提醒维护者导入后续年份
const getCalendarCoverage = (): Promise<CalendarCoverage[]> => {
  return Requests.get(`${apiPrefix.value}/calendar-coverage`) as unknown as Promise<
    CalendarCoverage[]
  >;
};

export default {
  listDicts,
  createOrUpdateDict,
  deleteDict,
  listSysItems,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  getHdfsStorageDefaults,
  generateCalendar,
  getCalendarCoverage,
};
