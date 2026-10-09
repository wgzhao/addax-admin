// 图表调色板与语义色映射（token 名由 useChartTheme 解析为实际主题色）

// 浅色沿用现有柱状图的硬编码色，避免已有图表视觉突变
export const CATEGORICAL_LIGHT = [
  '#2563EB',
  '#22C55E',
  '#F59E0B',
  '#EF4444',
  '#A855F7',
  '#14B8A6',
  '#F97316',
  '#0EA5E9',
];

// 深色为提亮变体，保证暗背景对比度
export const CATEGORICAL_DARK = [
  '#60A5FA',
  '#4ADE80',
  '#FBBF24',
  '#F87171',
  '#C084FC',
  '#2DD4BF',
  '#FB923C',
  '#38BDF8',
];

// 采集表状态码到中文标签与主题 token 的映射
export const STATUS_META: Record<string, { label: string; token: string; fallback: string }> = {
  Y: { label: '已完成', token: 'success', fallback: '#217A38' },
  R: { label: '运行中', token: 'info', fallback: '#1E67B3' },
  W: { label: '等待中', token: 'warning', fallback: '#A86717' },
  E: { label: '失败', token: 'error', fallback: '#C92A2A' },
  N: { label: '未采集', token: 'grey-500', fallback: '#8C99A8' },
  U: { label: '待同步', token: 'grey-700', fallback: '#4E5968' },
  X: { label: '已排除', token: 'grey-400', fallback: '#B7C2CE' },
};

export const STATUS_ORDER = ['Y', 'R', 'W', 'E', 'N', 'U', 'X'];

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
