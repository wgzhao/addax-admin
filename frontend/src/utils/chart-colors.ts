// 图表调色板与语义色映射（token 名由 useChartTheme 解析为实际主题色）
//
// Why: the previous palette failed the dataviz checks (light CVD ΔE 5.7,
// dark lightness band out of range). These are the validated reference steps;
// re-run scripts/validate_palette.js before changing them.
export const CATEGORICAL_LIGHT = [
  '#2a78d6',
  '#eb6834',
  '#1baf7a',
  '#eda100',
  '#e87ba4',
  '#008300',
  '#6250d6',
  '#e34948',
];

export const CATEGORICAL_DARK = [
  '#3987e5',
  '#d95926',
  '#199e70',
  '#c98500',
  '#d55181',
  '#008300',
  '#9085e9',
  '#e66767',
];

// Most dashboard charts use one accent plus a muted context series.
export const EMPHASIS_LIGHT = '#2a78d6';
export const EMPHASIS_DARK = '#3987e5';

// 采集表状态码到中文标签、图标与主题 token 的单一映射。
export const STATUS_META: Record<
  string,
  { label: string; token: string; fallback: string; icon: string }
> = {
  Y: {
    label: '已采集',
    token: 'success',
    fallback: '#217A38',
    icon: 'mdi-check-circle-outline',
  },
  R: {
    label: '采集中',
    token: 'info',
    fallback: '#1E67B3',
    icon: 'mdi-progress-clock',
  },
  W: {
    label: '等待采集',
    token: 'warning',
    fallback: '#A86717',
    icon: 'mdi-clock-outline',
  },
  E: {
    label: '采集失败',
    token: 'error',
    fallback: '#C92A2A',
    icon: 'mdi-alert-circle-outline',
  },
  N: {
    label: '未采集',
    token: 'grey-500',
    fallback: '#8C99A8',
    icon: 'mdi-minus-circle-outline',
  },
  U: {
    label: '待同步',
    token: 'grey-600',
    fallback: '#4E5968',
    icon: 'mdi-cloud-sync-outline',
  },
  X: {
    label: '已排除',
    token: 'grey-400',
    fallback: '#B7C2CE',
    icon: 'mdi-cancel',
  },
};

export const STATUS_ORDER = ['Y', 'R', 'W', 'E', 'N', 'U', 'X'];

export const statusMeta = (status: string | null | undefined) => {
  const key = (status || '').toUpperCase();
  return (
    STATUS_META[key] || {
      label: key || '未知',
      token: 'grey-500',
      fallback: '#8C99A8',
      icon: 'mdi-help-circle-outline',
    }
  );
};

// 流水操作类型到中文标签与调色板索引的映射（与后端 JourKind 对齐）
export const JOUR_KIND_META: Record<string, { label: string; paletteIndex: number }> = {
  COLLECT: { label: '表采集', paletteIndex: 0 },
  ADDAX_JOB: { label: '生成采集模板', paletteIndex: 1 },
  SCHEMA: { label: '表结构同步', paletteIndex: 2 },
  PARTITION: { label: '创建表分区', paletteIndex: 3 },
  UPDATE_TABLE: { label: '创建目标表', paletteIndex: 4 },
  UPDATE_COLUMN: { label: '表字段更新', paletteIndex: 5 },
  CREATE_COLUMN: { label: '首次同步表结构', paletteIndex: 6 },
  FILLBACK: { label: '补数采集', paletteIndex: 7 },
};

// 按小时/分钟格式化耗时秒数，用于图表刻度与 tooltip
export function formatSeconds(secs: number | null | undefined): string {
  if (secs === null || secs === undefined || Number.isNaN(secs)) return '-';
  if (secs < 60) return `${Math.round(secs)}s`;
  if (secs < 3600) return `${Math.round(secs / 60)}m`;
  const hours = Math.floor(secs / 3600);
  const minutes = Math.round((secs % 3600) / 60);
  return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
}
