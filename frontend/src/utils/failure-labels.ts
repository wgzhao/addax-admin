import type { FailureCategory, FailureStage } from '@/types/failure';

export const FAILURE_STAGE_LABELS: Record<FailureStage, string> = {
  PREPARE: '准备阶段',
  SOURCE_SCHEMA: '源表结构',
  TARGET_SCHEMA: '目标表结构',
  PARTITION: '目标分区',
  JOB_TEMPLATE: '任务模板',
  COLLECT: '采集进程',
  QUEUE: '任务队列',
  RECOVERY: '执行恢复',
  RECONCILE: '采集对账',
  DATA_QUALITY: '数据质量',
};

export const FAILURE_CATEGORY_LABELS: Record<FailureCategory, string> = {
  OOM: '内存不足',
  NETWORK: '网络连接',
  DISK: '磁盘空间',
  AUTH: '权限认证',
  TABLE_NOT_FOUND: '表不存在',
  CONTRACT: '目标契约',
  DATA: '数据问题',
  TIMEOUT: '超时',
  PROCESS: '进程退出',
  UNKNOWN: '未分类',
};

export const FAILURE_CATEGORY_COLORS: Record<FailureCategory, string> = {
  OOM: 'deep-purple',
  NETWORK: 'blue-grey',
  DISK: 'brown',
  AUTH: 'orange',
  TABLE_NOT_FOUND: 'red',
  CONTRACT: 'teal',
  DATA: 'indigo',
  TIMEOUT: 'amber',
  PROCESS: 'blue',
  UNKNOWN: 'grey',
};

export const FAILURE_STAGE_OPTIONS = (Object.keys(FAILURE_STAGE_LABELS) as FailureStage[]).map(
  value => ({ value, title: FAILURE_STAGE_LABELS[value] })
);

export const FAILURE_CATEGORY_OPTIONS = (
  Object.keys(FAILURE_CATEGORY_LABELS) as FailureCategory[]
).map(value => ({ value, title: FAILURE_CATEGORY_LABELS[value] }));

export function failureStageLabel(stage: string | null | undefined) {
  return FAILURE_STAGE_LABELS[stage as FailureStage] || stage || '-';
}

export function failureCategoryLabel(category: string | null | undefined) {
  return FAILURE_CATEGORY_LABELS[category as FailureCategory] || category || '-';
}

export function failureCategoryColor(category: string | null | undefined) {
  return FAILURE_CATEGORY_COLORS[category as FailureCategory] || 'grey';
}
