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
 *
 * The partition defaults are for a new table. An existing row whose bag says "no partition name and
 * no bindings" is a non-partitioned table; merging the product default into it would have turned a
 * filter-only edit into a partitioned definition whose Hive table can never accept a partition.
 */
export function normalizeTargetOptions(
  options?: Partial<EtlTargetOptions> | null
): EtlTargetOptions {
  const storedHdfs = options?.hdfs;
  const hdfs = { ...DEFAULT_HDFS_OPTIONS, ...(storedHdfs || {}) };
  if (Array.isArray(storedHdfs?.partitionBindings)) {
    // An explicit list (including an empty one) is authoritative.
    hdfs.partitionBindings = storedHdfs.partitionBindings;
  } else if (storedHdfs && typeof storedHdfs.partName === 'string' && storedHdfs.partName.trim()) {
    // Legacy rows carried the partition only as partName/partFormat.
    hdfs.partitionBindings = [
      {
        name: storedHdfs.partName.trim(),
        valueType: 'BIZ_DATE',
        format: storedHdfs.partFormat || 'yyyyMMdd',
      },
    ];
  } else if (storedHdfs) {
    hdfs.partitionBindings = [];
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
  if (family === 'hdfs') {
    const hdfs = { ...normalized.hdfs };
    if (!hdfs.partitionBindings?.length) {
      // The backend derives a partition from a legacy partName when the list is empty, so a
      // non-partitioned table must persist a blank name and no creation mode.
      hdfs.partName = '';
      delete hdfs.partitionCreationMode;
    }
    return { hdfs };
  }
  return { rdbms: normalized.rdbms };
}
