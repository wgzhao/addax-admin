import type { EtlTargetOptions } from '@/types/database';

// Fallbacks for a table whose bag does not carry the attribute yet. They must match the backend's
// documented defaults (the Java defaults / the legacy column defaults): the effective defaults for a new
// table come from the HDFS settings (HDFS_STORAGE_FORMAT / HDFS_COMPRESS_FORMAT), which BatchAdd loads.
const DEFAULT_HDFS_OPTIONS = {
  partKind: 'D',
  partName: 'logdate',
  partFormat: 'yyyyMMdd',
  storageFormat: 'orc',
  compressFormat: 'snappy',
  partitionCreationMode: 'PLATFORM' as const,
  partitionBindings: [
    {
      name: 'logdate',
      valueType: 'BIZ_DATE' as const,
      format: 'yyyyMMdd',
    },
  ],
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

/**
 * The API can return a null bag for rows written before the target_options migration and a partial bag
 * for older clients, so the UI always works on a fully populated object.
 */
export function normalizeTargetOptions(
  options?: Partial<EtlTargetOptions> | null
): EtlTargetOptions {
  const hdfs = { ...DEFAULT_HDFS_OPTIONS, ...(options?.hdfs || {}) };
  if (!hdfs.partitionBindings?.length && hdfs.partName) {
    hdfs.partitionBindings = [
      {
        name: hdfs.partName,
        valueType: 'BIZ_DATE',
        format: hdfs.partFormat || 'yyyyMMdd',
      },
    ];
  }
  return {
    hdfs,
    rdbms: { ...DEFAULT_RDBMS_OPTIONS, ...(options?.rdbms || {}) },
  };
}

/**
 * The section to persist for the picked target family. Writing the Hive section for an RDBMS target left a
 * misleading `partName` on the table, so callers must merge this over (not replace) the options already
 * stored on the row: the bag is namespaced so a table can switch target families and keep both.
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
