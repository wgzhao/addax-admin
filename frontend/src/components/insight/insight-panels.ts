import type { DataTableHeader } from 'vuetify';
import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort.mjs';

export interface InsightFilters {
  days: number;
  lowRate: number;
  highRate: number;
  timeRate: number;
  keyword: string;
}

export interface InsightPanel {
  key: 'noChange' | 'lowChange' | 'highChange' | 'timeChange' | 'missingCollect';
  title: string;
  sortBy: SortItem[];
  headers: DataTableHeader[];
  exportName: string;
}

const normalizeKeyword = (value: string) => value.trim().toLowerCase();

export const formatTargetTable = (item: any) => {
  const targetDb = String(item?.target_db ?? '').trim();
  const targetTable = String(item?.target_table ?? '').trim();
  if (!targetDb && !targetTable) return '';
  if (!targetDb) return targetTable;
  if (!targetTable) return targetDb;
  return `${targetDb}.${targetTable}`;
};

export const filterByKeyword = (items: Array<Map<string, any>>, keyword: string) => {
  const normalized = normalizeKeyword(keyword || '');
  if (!normalized) return items;
  return items.filter((item: any) => {
    const sourceDb = String(item?.source_db ?? '').toLowerCase();
    const sourceTable = String(item?.source_table ?? '').toLowerCase();
    return sourceDb.includes(normalized) || sourceTable.includes(normalized);
  });
};

export const parseMissingDates = (value: string) =>
  String(value ?? '')
    .split('|')
    .map(d => d.trim())
    .filter(Boolean);

export const compactMissingDates = (item: any) => {
  const dates = parseMissingDates(item?.missing_dates);
  if (!dates.length) return '-';
  if (dates.length <= 2) return dates.join('、');
  return `${dates[0]} ~ ${dates[dates.length - 1]}（共 ${dates.length} 天）`;
};

export const hasMissingDates = (item: any) => parseMissingDates(item?.missing_dates).length > 0;

// Build the five insight segment definitions; titles follow the live filters.
export function createInsightPanels(filters: InsightFilters): InsightPanel[] {
  return [
    {
      key: 'noChange',
      title: `近 ${filters.days} 天内，数据量无变化的表`,
      sortBy: <SortItem[]>[{ key: 'total_recs', order: 'desc' }],
      exportName: 'insight-no-change',
      headers: <DataTableHeader[]>[
        { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
        { title: '源库', key: 'source_db' },
        { title: '表名', key: 'source_table' },
        { title: '目标库表', key: 'target_table_full', value: item => formatTargetTable(item) },
        { title: '记录数', key: 'total_recs', align: 'end' },
        { title: '开始日期', key: 'start_date' },
        { title: '结束日期', key: 'end_date' },
        { title: '天数', key: 'day_count', align: 'end' },
        { title: '操作', key: 'actions', sortable: false, align: 'center' },
      ],
    },
    {
      key: 'lowChange',
      title: `近 ${filters.days} 天内，数据变化率小于 ${filters.lowRate}% 的表`,
      sortBy: <SortItem[]>[{ key: 'change_rate_pct', order: 'asc' }],
      exportName: 'insight-low-change',
      headers: <DataTableHeader[]>[
        { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
        { title: '源库', key: 'source_db' },
        { title: '表名', key: 'source_table' },
        { title: '目标库表', key: 'target_table_full', value: item => formatTargetTable(item) },
        { title: '最小记录数', key: 'min_recs', align: 'end' },
        { title: '最大记录数', key: 'max_recs', align: 'end' },
        {
          title: '变化率',
          key: 'change_rate_pct',
          align: 'end',
          value: item => `${item.change_rate_pct ?? 0}%`,
        },
        { title: '开始日期', key: 'start_date' },
        { title: '结束日期', key: 'end_date' },
        { title: '天数', key: 'day_count', align: 'end' },
      ],
    },
    {
      key: 'highChange',
      title: `近 ${filters.days} 天内，数据变化率超过 ${filters.highRate}% 的表`,
      sortBy: <SortItem[]>[{ key: 'change_rate_pct', order: 'desc' }],
      exportName: 'insight-high-change',
      headers: <DataTableHeader[]>[
        { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
        { title: '源库', key: 'source_db' },
        { title: '表名', key: 'source_table' },
        { title: '目标库表', key: 'target_table_full', value: item => formatTargetTable(item) },
        { title: '最小记录数', key: 'min_recs', align: 'end' },
        { title: '最大记录数', key: 'max_recs', align: 'end' },
        {
          title: '变化率',
          key: 'change_rate_pct',
          align: 'end',
          value: item => `${item.change_rate_pct ?? 0}%`,
        },
        { title: '开始日期', key: 'start_date' },
        { title: '结束日期', key: 'end_date' },
        { title: '天数', key: 'day_count', align: 'end' },
      ],
    },
    {
      key: 'timeChange',
      title: `近 ${filters.days} 天内，采集耗时变动率超过 ${filters.timeRate}% 的表`,
      sortBy: <SortItem[]>[{ key: 'change_rate_pct', order: 'desc' }],
      exportName: 'insight-time-change',
      headers: <DataTableHeader[]>[
        { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
        { title: '源库', key: 'source_db' },
        { title: '表名', key: 'source_table' },
        { title: '目标库表', key: 'target_table_full', value: item => formatTargetTable(item) },
        { title: '最小耗时(秒)', key: 'min_secs', align: 'end' },
        { title: '最大耗时(秒)', key: 'max_secs', align: 'end' },
        {
          title: '变动率',
          key: 'change_rate_pct',
          align: 'end',
          value: item => `${item.change_rate_pct ?? 0}%`,
        },
        { title: '开始日期', key: 'start_date' },
        { title: '结束日期', key: 'end_date' },
        { title: '天数', key: 'day_count', align: 'end' },
      ],
    },
    {
      key: 'missingCollect',
      title: `近 ${filters.days} 天内，缺失采集记录的有效表`,
      sortBy: <SortItem[]>[{ key: 'missing_days', order: 'desc' }],
      exportName: 'insight-missing-collect',
      headers: <DataTableHeader[]>[
        { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
        { title: '源库', key: 'source_db' },
        { title: '表名', key: 'source_table' },
        { title: '目标库表', key: 'target_table_full', value: item => formatTargetTable(item) },
        { title: '应采集天数', key: 'expected_days', align: 'end' },
        { title: '实际采集天数', key: 'actual_days', align: 'end' },
        { title: '缺失天数', key: 'missing_days', align: 'end' },
        { title: '首次缺失日期', key: 'first_missing_date' },
        { title: '最近缺失日期', key: 'last_missing_date' },
        { title: '最近采集日期', key: 'last_collect_date' },
        { title: '缺失日期', key: 'missing_dates', sortable: false, width: '280px' },
      ],
    },
  ];
}
