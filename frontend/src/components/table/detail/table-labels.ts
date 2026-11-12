import { TABLE_STATUS_OPTIONS } from '@/utils';
import { statusMeta } from '@/utils/chart-colors';
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

export const sourceSystemLabel = (table: VEtlWithSource) => table.name || '未配置';

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

// createdAt is serialized by the API as "yyyy-MM-dd HH:mm:ss"; it is only a Date when built locally.
export const createdAtLabel = (table: VEtlWithSource) => {
  const v = (table as any)?.createdAt;
  if (!v) return '-';
  return v instanceof Date ? v.toLocaleString() : String(v);
};

export const statusLabel = (table: VEtlWithSource) => {
  const matched = TABLE_STATUS_OPTIONS.find(item => item.value === table.status);
  return matched?.label || table.status || '未设置';
};

export const statusColor = (table: VEtlWithSource) => statusMeta(table.status).token;
