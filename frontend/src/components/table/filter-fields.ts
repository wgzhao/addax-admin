import { statusMeta } from '@/utils/chart-colors';

export type ScopeValue = 'CURRENT' | 'DORMANT' | 'RETIRED' | 'ALL';
export type Tristate = 'ANY' | 'true' | 'false';

/** Everything the collection-table list can be filtered by, except keyword/status. */
export interface TableFilters {
  q: string;
  statuses: string[];
  scope: ScopeValue;
  sourceIds: number[];
  dbTypes: string[];
  sourceEnabled: Tristate;
  targetIds: number[];
  targetTypes: string[];
  targetDb: string;
  targetEnabled: Tristate;
  writeModes: string[];
  schemaModes: string[];
  comment: string;
  endFrom: string;
  endTo: string;
  createdFrom: string;
  createdTo: string;
  updatedFrom: string;
  updatedTo: string;
}

export function defaultFilters(): TableFilters {
  return {
    q: '',
    statuses: [],
    scope: 'CURRENT',
    sourceIds: [],
    dbTypes: [],
    sourceEnabled: 'ANY',
    targetIds: [],
    targetTypes: [],
    targetDb: '',
    targetEnabled: 'ANY',
    writeModes: [],
    schemaModes: [],
    comment: '',
    endFrom: '',
    endTo: '',
    createdFrom: '',
    createdTo: '',
    updatedFrom: '',
    updatedTo: '',
  };
}

/**
 * Families are UI sugar over concrete target_type values: the backend filters on target_type only,
 * because target_type already carries the family (ADR-0012 validates the pair at save time).
 */
export const TARGET_FAMILIES: { value: string; label: string; types: string[] }[] = [
  { value: 'HDFS', label: 'HDFS', types: ['HDFS', 'HIVE'] },
  { value: 'RDBMS', label: 'RDBMS', types: ['RDBMS', 'MYSQL', 'POSTGRESQL', 'SQLSERVER'] },
  { value: 'DORIS', label: 'Doris', types: ['DORIS'] },
];

export const TARGET_TYPE_OPTIONS = [
  { value: 'HDFS', label: 'HDFS' },
  { value: 'HIVE', label: 'Hive' },
  { value: 'RDBMS', label: 'RDBMS' },
  { value: 'MYSQL', label: 'MySQL' },
  { value: 'POSTGRESQL', label: 'PostgreSQL' },
  { value: 'SQLSERVER', label: 'SQL Server' },
  { value: 'DORIS', label: 'Doris' },
];

export const SOURCE_DB_TYPE_OPTIONS = [
  { value: 'mysql', label: 'MySQL' },
  { value: 'postgresql', label: 'PostgreSQL' },
  { value: 'oracle', label: 'Oracle' },
  { value: 'sqlserver', label: 'SQL Server' },
  { value: 'hive', label: 'Hive' },
  { value: 'db2', label: 'DB2' },
  { value: 'sqlite', label: 'SQLite' },
  { value: 'sybase', label: 'Sybase' },
  { value: 'clickhouse', label: 'ClickHouse' },
  { value: 'rdbms', label: '其他 RDBMS' },
];

export const WRITE_MODE_OPTIONS = [
  { value: 'overwrite', label: '覆盖 overwrite' },
  { value: 'append', label: '追加 append' },
  { value: 'nonConflict', label: '不冲突 nonConflict' },
  // RDBMS targets default to plain INSERT when writeMode is blank (RdbmsTargetAdapter), so this
  // value exists in real data even though the UI never writes it.
  { value: 'insert', label: '插入 insert' },
];

export const SCHEMA_MODE_OPTIONS = [
  { value: 'MANAGED', label: '平台托管 MANAGED' },
  { value: 'EXTERNAL', label: '外部管理 EXTERNAL' },
];

export const TRISTATE_OPTIONS: { value: Tristate; label: string }[] = [
  { value: 'ANY', label: '全部' },
  { value: 'true', label: '是' },
  { value: 'false', label: '否' },
];

/** Display family of a target_type value; unknown targets must not pretend to be RDBMS. */
export function targetFamilyLabel(type?: string | null): string {
  const value = (type || '').trim().toUpperCase();
  if (value === 'HDFS' || value === 'HIVE') return 'HDFS';
  if (value === 'DORIS') return 'Doris';
  if (value) return 'RDBMS';
  return '未指定';
}

export function targetFamilyColor(type?: string | null): string {
  const label = targetFamilyLabel(type);
  if (label === 'HDFS') return 'blue';
  if (label === 'Doris') return 'deep-orange';
  if (label === 'RDBMS') return 'green';
  return 'grey';
}

export type ChipKey =
  | 'statuses'
  | 'sourceIds'
  | 'dbTypes'
  | 'sourceEnabled'
  | 'targetIds'
  | 'targetTypes'
  | 'targetDb'
  | 'targetEnabled'
  | 'writeModes'
  | 'schemaModes'
  | 'comment'
  | 'end'
  | 'created'
  | 'updated'
  | 'q';

export interface FilterChip {
  key: ChipKey;
  label: string;
  text: string;
}

export interface ChipLookups {
  sourceNames: Map<number, string>;
  targetNames: Map<number, string>;
}

/**
 * One chip per user-visible condition, so a single click can drop exactly that condition. A date
 * range is one chip because its two inputs are only meaningful together.
 */
export function buildFilterChips(filters: TableFilters, lookups: ChipLookups): FilterChip[] {
  const chips: FilterChip[] = [];
  if (filters.q.trim()) {
    chips.push({ key: 'q', label: '关键字', text: filters.q.trim() });
  }
  if (filters.statuses.length) {
    chips.push({
      key: 'statuses',
      label: '状态',
      text: filters.statuses.map(status => statusMeta(status).label).join('、'),
    });
  }
  if (filters.sourceIds.length) {
    chips.push({
      key: 'sourceIds',
      label: '采集源',
      text: filters.sourceIds.map(id => lookups.sourceNames.get(id) ?? `#${id}`).join('、'),
    });
  }
  if (filters.dbTypes.length) {
    chips.push({ key: 'dbTypes', label: '源库类型', text: filters.dbTypes.join('、') });
  }
  if (filters.sourceEnabled !== 'ANY') {
    chips.push({
      key: 'sourceEnabled',
      label: '源状态',
      text: filters.sourceEnabled === 'true' ? '启用' : '已停用',
    });
  }
  if (filters.targetIds.length) {
    chips.push({
      key: 'targetIds',
      label: '目标端',
      text: filters.targetIds.map(id => lookups.targetNames.get(id) ?? `#${id}`).join('、'),
    });
  }
  if (filters.targetTypes.length) {
    chips.push({ key: 'targetTypes', label: '目标类型', text: filters.targetTypes.join('、') });
  }
  if (filters.targetDb.trim()) {
    chips.push({ key: 'targetDb', label: '目标库', text: filters.targetDb.trim() });
  }
  if (filters.targetEnabled !== 'ANY') {
    chips.push({
      key: 'targetEnabled',
      label: '目标端状态',
      text: filters.targetEnabled === 'true' ? '启用' : '已停用',
    });
  }
  if (filters.writeModes.length) {
    chips.push({ key: 'writeModes', label: '写入模式', text: filters.writeModes.join('、') });
  }
  if (filters.schemaModes.length) {
    chips.push({ key: 'schemaModes', label: '结构管理', text: filters.schemaModes.join('、') });
  }
  if (filters.comment.trim()) {
    chips.push({ key: 'comment', label: '表注释', text: filters.comment.trim() });
  }
  if (filters.endFrom || filters.endTo) {
    chips.push({
      key: 'end',
      label: '完成时间',
      text: `${filters.endFrom || '不限'} ~ ${filters.endTo || '不限'}`,
    });
  }
  if (filters.createdFrom || filters.createdTo) {
    chips.push({
      key: 'created',
      label: '创建时间',
      text: `${filters.createdFrom || '不限'} ~ ${filters.createdTo || '不限'}`,
    });
  }
  if (filters.updatedFrom || filters.updatedTo) {
    chips.push({
      key: 'updated',
      label: '更新时间',
      text: `${filters.updatedFrom || '不限'} ~ ${filters.updatedTo || '不限'}`,
    });
  }
  return chips;
}

export function clearFilterChip(filters: TableFilters, key: ChipKey): void {
  switch (key) {
    case 'q':
      filters.q = '';
      break;
    case 'statuses':
      filters.statuses = [];
      break;
    case 'sourceIds':
      filters.sourceIds = [];
      break;
    case 'dbTypes':
      filters.dbTypes = [];
      break;
    case 'sourceEnabled':
      filters.sourceEnabled = 'ANY';
      break;
    case 'targetIds':
      filters.targetIds = [];
      break;
    case 'targetTypes':
      filters.targetTypes = [];
      break;
    case 'targetDb':
      filters.targetDb = '';
      break;
    case 'targetEnabled':
      filters.targetEnabled = 'ANY';
      break;
    case 'writeModes':
      filters.writeModes = [];
      break;
    case 'schemaModes':
      filters.schemaModes = [];
      break;
    case 'comment':
      filters.comment = '';
      break;
    case 'end':
      filters.endFrom = '';
      filters.endTo = '';
      break;
    case 'created':
      filters.createdFrom = '';
      filters.createdTo = '';
      break;
    case 'updated':
      filters.updatedFrom = '';
      filters.updatedTo = '';
      break;
  }
}
