import { describe, expect, it } from 'vitest';
import {
  inheritedStartAt,
  latestRunLabel,
  scheduleLabel,
  sourceIdentity,
  sourceSystemLabel,
  statusColor,
  statusLabel,
  targetIdentity,
  targetLabel,
} from '@/components/table/detail/table-labels';
import type { VEtlWithSource } from '@/types/database';

const table = (overrides: Partial<VEtlWithSource> = {}) =>
  ({
    sourceDb: 'src',
    sourceTable: 'orders',
    targetDb: 'ods',
    targetTable: 'orders',
    ...overrides,
  }) as VEtlWithSource;

describe('table-labels', () => {
  it('builds source and target identities with fallbacks', () => {
    expect(sourceIdentity(table())).toBe('src.orders');
    expect(sourceIdentity(table({ sourceDb: '' }))).toBe('未配置库.orders');
    expect(targetIdentity(table())).toBe('ods.orders');
  });

  it('renders the source system label', () => {
    expect(sourceSystemLabel(table({ name: 'MySQL', code: 'MY' }))).toBe('MySQL (MY)');
    expect(sourceSystemLabel(table({ name: 'MySQL' }))).toBe('MySQL');
    expect(sourceSystemLabel(table())).toBe('未配置');
  });

  it('resolves target labels from options first, then the table', () => {
    const options = [{ label: 'Hive (hive)', value: 3 }];
    expect(targetLabel(table({ targetId: 3 }), options)).toBe('Hive (hive)');
    expect(targetLabel(table({ targetName: 'HDFS', targetType: 'hdfs' }), [])).toBe(
      'HDFS (hdfs)'
    );
    expect(targetLabel(table(), [])).toBe('未选择');
  });

  it('truncates the inherited schedule time to HH:mm', () => {
    expect(inheritedStartAt(table({ sourceStartAt: '03:30:00' }))).toBe('03:30');
    expect(inheritedStartAt(table())).toBe('');
    expect(scheduleLabel(table({ startAt: '05:00' }))).toBe('05:00');
    expect(scheduleLabel(table({ sourceStartAt: '03:30' }))).toBe('03:30');
    expect(scheduleLabel(table())).toBe('未设置');
  });

  it('picks the latest run label', () => {
    expect(latestRunLabel(table({ endTime: 't2' as any, startTime: 't1' as any }))).toBe('t2');
    expect(latestRunLabel(table({ startTime: 't1' as any }))).toBe('t1');
    expect(latestRunLabel(table())).toBe('-');
  });

  it('maps status to label and color', () => {
    expect(statusLabel(table({ status: 'R' }))).toBe('R - 正在采集');
    expect(statusColor(table({ status: 'R' }))).toBe('blue');
    expect(statusColor(table({ status: 'unknown' }))).toBe('grey');
  });
});
