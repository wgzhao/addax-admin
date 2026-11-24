import { reactive } from 'vue';
import type { LocationQuery } from 'vue-router';
import {
  defaultFilters,
  type ScopeValue,
  type TableFilters,
  type Tristate,
} from '@/components/table/filter-fields';

// Module-level state: the panel, the chips and the list page must see the same filters, and there
// is only ever one collection-table list on screen.
const filters = reactive<TableFilters>(defaultFilters());

function firstText(value: LocationQuery[string]): string {
  if (typeof value === 'string') return value;
  if (Array.isArray(value) && typeof value[0] === 'string') return value[0];
  return '';
}

function splitCsv(value: LocationQuery[string]): string[] {
  return firstText(value)
    .split(',')
    .map(item => item.trim())
    .filter(item => item.length > 0);
}

function parseIds(value: LocationQuery[string]): number[] {
  return splitCsv(value)
    .map(Number)
    .filter(item => Number.isFinite(item));
}

function parseTristate(value: LocationQuery[string]): Tristate {
  const raw = firstText(value);
  return raw === 'true' || raw === 'false' ? raw : 'ANY';
}

export function queryToFilters(query: LocationQuery): TableFilters {
  const result = defaultFilters();
  result.q = firstText(query.q);
  result.statuses = splitCsv(query.status).map(item => item.toUpperCase());
  const scopeRaw = (firstText(query.scope) || firstText(query.lifecycle)).toUpperCase();
  result.scope =
    scopeRaw === 'DORMANT' || scopeRaw === 'RETIRED' || scopeRaw === 'ALL'
      ? (scopeRaw as ScopeValue)
      : 'CURRENT';
  // `source` is the historical single-value key; it now accepts a comma-separated list.
  result.sourceIds = parseIds(query.source);
  result.sourceEnabled = parseTristate(query.sourceEnabled);
  result.targetIds = parseIds(query.targets);
  result.targetDb = firstText(query.targetDb);
  result.targetEnabled = parseTristate(query.targetEnabled);
  result.writeModes = splitCsv(query.writeModes);
  result.schemaModes = splitCsv(query.schemaModes).map(item => item.toUpperCase());
  result.durationFrom = firstText(query.durationFrom);
  result.durationTo = firstText(query.durationTo);
  result.endFrom = firstText(query.endFrom);
  result.endTo = firstText(query.endTo);
  result.createdFrom = firstText(query.createdFrom);
  result.createdTo = firstText(query.createdTo);
  result.updatedFrom = firstText(query.updatedFrom);
  result.updatedTo = firstText(query.updatedTo);
  return result;
}

/**
 * Canonical, sorted output so the same filter set always produces the same query (and the
 * route watcher can compare it against the live state without looping).
 */
export function filtersToQuery(input: TableFilters): Record<string, string> {
  const query: Record<string, string> = {};
  const q = input.q.trim();
  if (q) query.q = q;
  if (input.statuses.length) query.status = [...input.statuses].sort().join(',');
  if (input.scope !== 'CURRENT') query.scope = input.scope;
  if (input.sourceIds.length) {
    query.source = [...input.sourceIds].sort((a, b) => a - b).join(',');
  }
  if (input.sourceEnabled !== 'ANY') query.sourceEnabled = input.sourceEnabled;
  if (input.targetIds.length) {
    query.targets = [...input.targetIds].sort((a, b) => a - b).join(',');
  }
  const targetDb = input.targetDb.trim();
  if (targetDb) query.targetDb = targetDb;
  if (input.targetEnabled !== 'ANY') query.targetEnabled = input.targetEnabled;
  if (input.writeModes.length) query.writeModes = [...input.writeModes].sort().join(',');
  if (input.schemaModes.length) query.schemaModes = [...input.schemaModes].sort().join(',');
  if (input.durationFrom) query.durationFrom = input.durationFrom;
  if (input.durationTo) query.durationTo = input.durationTo;
  if (input.endFrom) query.endFrom = input.endFrom;
  if (input.endTo) query.endTo = input.endTo;
  if (input.createdFrom) query.createdFrom = input.createdFrom;
  if (input.createdTo) query.createdTo = input.createdTo;
  if (input.updatedFrom) query.updatedFrom = input.updatedFrom;
  if (input.updatedTo) query.updatedTo = input.updatedTo;
  return query;
}

export function useTableFilters() {
  const reset = () => {
    Object.assign(filters, defaultFilters());
  };
  const applyFromQuery = (query: LocationQuery) => {
    Object.assign(filters, queryToFilters(query));
  };
  return { filters, reset, applyFromQuery };
}
