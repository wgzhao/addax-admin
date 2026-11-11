import type { EtlTargetOptions } from '@/types/database';

// Fallbacks for a table whose bag does not carry the attribute yet. They are Hive-family product
// defaults; the effective defaults for a new table come from the selected target endpoint.
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

export type TargetFamily = 'hdfs' | 'rdbms' | 'unknown';

/**
 * The writer template key is authoritative. target_type is a display label that may lag behind a
 * connection change, and a blank/unknown target must not silently inherit the Hive form.
 */
export function targetFamily(
  writerTemplateKey?: string | null,
  targetType?: string | null
): TargetFamily {
  const key = (writerTemplateKey || '').trim().toUpperCase();
  if (key === 'WH') return 'hdfs';
  if (key === 'WR') return 'rdbms';
  const type = (targetType || '').trim().toUpperCase();
  if (type === 'HDFS' || type === 'HIVE') return 'hdfs';
  if (['RDBMS', 'MYSQL', 'POSTGRESQL', 'SQLSERVER'].includes(type)) return 'rdbms';
  return 'unknown';
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
  writerTemplateKey?: string | null,
  targetType?: string | null
): Partial<EtlTargetOptions> {
  const family = targetFamily(writerTemplateKey, targetType);
  if (family === 'unknown') return {};
  const normalized = normalizeTargetOptions(options);
  return family === 'hdfs' ? { hdfs: normalized.hdfs } : { rdbms: normalized.rdbms };
}
