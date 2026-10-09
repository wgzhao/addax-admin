// 首页图表接口返回类型（与后端 /dashboard/* 的 snake_case 别名一致）

export interface TableStatusDistRow {
  status: string;
  cnt: number;
}

export interface FailTrendRow {
  day: string;
  kind: string | null;
  cnt: number;
}

export interface FailTopRow {
  tid: number;
  source_db: string;
  source_table: string;
  target_db: string;
  target_table: string;
  source_code: string;
  source_name: string;
  retry_cnt: number;
  fail_cnt: number;
  last_kind: string | null;
  last_fail_at: string | null;
  last_error: string | null;
}

export interface ErrorRateRow {
  biz_date: string;
  total_recs: number;
  total_errors: number;
  error_pct: number | null;
}

export interface MissingCalendarRow {
  biz_date: string;
  expected: number;
  collected: number;
  missing: number;
}

export interface RecsBytesRow {
  month: string;
  total_recs: number;
  total_gb: number;
}

export interface SourceContributionRow {
  biz_date: string;
  code: string | null;
  name: string | null;
  gib: number;
}

export interface SlowTableRow {
  tid: number;
  source_db: string;
  source_table: string;
  target_db: string;
  target_table: string;
  source_code: string;
  source_name: string;
  days: number;
  avg_secs: number;
  max_secs: number;
  total_secs: number;
}

export interface TakeSecsBucket {
  idx: number;
  label: string;
  cnt: number;
}

export interface TakeSecsHistogram {
  buckets: TakeSecsBucket[];
  p50: number | null;
  p95: number | null;
  max_secs: number | null;
  total: number | null;
}

// 通用图表数据形状
export interface ChartSeries {
  key: string;
  label: string;
  data: (number | null)[];
  color?: string;
  fill?: boolean;
  yAxisID?: 'y' | 'y1';
  type?: 'line' | 'bar';
}

// 既有接口：近 5 天按源的对比（耗时接口只返回 total_secs，数据量接口只返回 total_bytes）
export interface Last5dRow {
  biz_date: string;
  sources: string[];
  total_secs?: number[];
  total_bytes?: number[];
}

// 既有接口：最近 12 个月累计采集量
export interface LastMonthsRow {
  month: string;
  total_gb: number;
}
