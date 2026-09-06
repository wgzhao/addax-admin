import { TABLE_STATUS_OPTIONS } from '@/utils';
import type { VEtlWithSource } from '@/types/database';

// Pure display-label helpers for the detail cards; kept in one module so the
// hero, schedule and snapshot cards agree on wording.

export const inheritedStartAt = (table: VEtlWithSource): string => {
  const v = (table as any)?.sourceStartAt;
  if (!v) return '';
  return typeof v === 'string' && v.length >= 5 ? v.slice(0, 5) : String(v);
};

export const sourceIdentity = (table: VEtlWithSource) =>
  `${table.sourceDb || '未配置库'}.${table.sourceTable || '未配置表'}`;

export const targetIdentity = (table: VEtlWithSource) =>
  `${table.targetDb || '未配置库'}.${table.targetTable || '未配置表'}`;

export const sourceSystemLabel = (table: VEtlWithSource) => {
  if (!table.name && !table.code) return '未配置';
  return table.code ? `${table.name || '-'} (${table.code})` : table.name;
};

export const targetLabel = (
  table: VEtlWithSource,
  targetOptions: { label: string; value: number }[]
) => {
  const matched = targetOptions.find(item => item.value === table.targetId);
  if (matched) return matched.label;
  if (table.targetName) return `${table.targetName} (${table.targetType || '-'})`;
  return '未选择';
};

export const scheduleLabel = (table: VEtlWithSource) =>
  table.startAt || inheritedStartAt(table) || '未设置';

export const latestRunLabel = (table: VEtlWithSource) => table.endTime || table.startTime || '-';

export const statusLabel = (table: VEtlWithSource) => {
  const matched = TABLE_STATUS_OPTIONS.find(item => item.value === table.status);
  return matched?.label || table.status || '未设置';
};

export const statusColor = (table: VEtlWithSource) => {
  const statusColorMap: Record<string, string> = {
    N: 'grey-lighten-1',
    R: 'blue',
    Y: 'success',
    E: 'error',
    X: 'warning',
    W: 'amber',
    U: 'grey-darken-1',
  };
  return statusColorMap[table.status] || 'grey';
};
