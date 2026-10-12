import type { EtlTargetOptions } from '@/types/database';

// Defaults must match com.wgzhao.addax.admin.common.TargetOptions on the backend.
const DEFAULT_HDFS_OPTIONS = {
  partKind: 'D',
  partName: 'logdate',
  partFormat: 'yyyyMMdd',
  storageFormat: 'orc',
  compressFormat: 'lz4',
};

const DEFAULT_RDBMS_OPTIONS = {
  businessDateColumn: '',
  createPrimaryKey: true,
};

export type TargetFamily = 'hdfs' | 'rdbms';

/**
 * Hive/HDFS is the default target, so an unknown or blank target type keeps the historical behaviour.
 */
export function targetFamily(targetType?: string | null): TargetFamily {
  const type = (targetType || '').trim().toUpperCase();
  if (type === 'HDFS' || type === 'HIVE' || type === '') return 'hdfs';
  return 'rdbms';
}

export function isHdfsTarget(targetType?: string | null): boolean {
  return targetFamily(targetType) === 'hdfs';
}

/**
 * The API can return a null bag for rows written before the target_options migration and a partial bag
 * for older clients, so the UI always works on a fully populated object.
 */
export function normalizeTargetOptions(
  options?: Partial<EtlTargetOptions> | null
): EtlTargetOptions {
  return {
    hdfs: { ...DEFAULT_HDFS_OPTIONS, ...(options?.hdfs || {}) },
    rdbms: { ...DEFAULT_RDBMS_OPTIONS, ...(options?.rdbms || {}) },
  };
}

/**
 * Only keep the section the target family actually uses. Writing the Hive section for an RDBMS target
 * left a misleading `partName` on the table, which the backend then mistook for a real date dimension.
 */
export function pickTargetOptions(
  options?: Partial<EtlTargetOptions> | null,
  targetType?: string | null
): Partial<EtlTargetOptions> {
  const normalized = normalizeTargetOptions(options);
  return targetFamily(targetType) === 'hdfs'
    ? { hdfs: normalized.hdfs }
    : { rdbms: normalized.rdbms };
}
