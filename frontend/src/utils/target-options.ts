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

const DEFAULT_DORIS_OPTIONS = {
  keyModel: 'AUTO' as const,
  keyColumns: [] as string[],
  distributionType: 'HASH' as const,
  distributionColumns: [] as string[],
  buckets: 'AUTO',
  replicationNum: null as number | null,
  partitionMode: 'NONE' as const,
  partitionColumn: '',
  partitionStart: -30,
  partitionEnd: 3,
  partitionPrefix: 'p',
};

export type TargetFamily = 'hdfs' | 'rdbms' | 'unknown';

/**
 * Doris is an adapter type inside the RDBMS family, not a dialect: its JDBC URL can never tell it
 * apart from MySQL. The writer key is authoritative; the display type is the fallback.
 */
export function isDorisTarget(
  writerTemplateKey?: string | null,
  targetType?: string | null
): boolean {
  const key = (writerTemplateKey || '').trim().toUpperCase();
  if (key === 'WD') return true;
  return (targetType || '').trim().toUpperCase() === 'DORIS';
}

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
  if (key === 'WR' || key === 'WD') return 'rdbms';
  const type = (targetType || '').trim().toUpperCase();
  if (type === 'HDFS' || type === 'HIVE') return 'hdfs';
  if (['RDBMS', 'MYSQL', 'POSTGRESQL', 'SQLSERVER', 'DORIS'].includes(type)) return 'rdbms';
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
  const doris = { ...DEFAULT_DORIS_OPTIONS, ...(options?.doris || {}) };
  return {
    hdfs,
    rdbms: { ...DEFAULT_RDBMS_OPTIONS, ...(options?.rdbms || {}) },
    doris: {
      ...doris,
      keyColumns: [...(doris.keyColumns || [])],
      distributionColumns: [...(doris.distributionColumns || [])],
    },
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
  // Doris needs both sections: rdbms carries the shared snapshot business-date column, doris carries
  // the key/distribution/partition policy that only this adapter understands.
  if (isDorisTarget(writerTemplateKey, targetType)) {
    return { rdbms: normalized.rdbms, doris: normalized.doris };
  }
  return family === 'hdfs' ? { hdfs: normalized.hdfs } : { rdbms: normalized.rdbms };
}
