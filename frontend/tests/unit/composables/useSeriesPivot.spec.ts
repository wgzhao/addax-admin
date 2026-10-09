import { describe, expect, it } from 'vitest';
import { pivotSeries } from '@/composables/useSeriesPivot';

describe('pivotSeries', () => {
  it('pivots long rows into labels and zero-filled series', () => {
    const rows = [
      { day: '2026-10-01', kind: 'COLLECT', cnt: 3 },
      { day: '2026-10-01', kind: 'SCHEMA', cnt: 1 },
      { day: '2026-10-02', kind: 'COLLECT', cnt: 2 },
    ];
    const result = pivotSeries(rows, {
      labelField: 'day',
      seriesField: 'kind',
      valueField: 'cnt',
    });
    expect(result.labels).toEqual(['2026-10-01', '2026-10-02']);
    expect(result.series).toEqual([
      { key: 'COLLECT', label: 'COLLECT', data: [3, 2] },
      { key: 'SCHEMA', label: 'SCHEMA', data: [1, 0] },
    ]);
  });

  it('honours explicit series order and labels', () => {
    const rows = [
      { day: 'd1', code: 'B', gib: 1 },
      { day: 'd1', code: 'A', gib: 2 },
      { day: 'd1', code: 'Z', gib: 5 },
    ];
    const result = pivotSeries(rows, {
      labelField: 'day',
      seriesField: 'code',
      valueField: 'gib',
      seriesOrder: ['A', 'B'],
      seriesLabels: { A: '源A', B: '源B' },
    });
    expect(result.series.map(s => s.key)).toEqual(['A', 'B', 'Z']);
    expect(result.series[0].label).toBe('源A');
    expect(result.series[0].data).toEqual([2]);
  });

  it('aggregates duplicate label/series combinations', () => {
    const rows = [
      { day: 'd1', code: 'A', gib: 1.5 },
      { day: 'd1', code: 'A', gib: 2.5 },
    ];
    const result = pivotSeries(rows, {
      labelField: 'day',
      seriesField: 'code',
      valueField: 'gib',
    });
    expect(result.series[0].data).toEqual([4]);
  });

  it('treats null series keys as absent rows', () => {
    const rows = [
      { day: 'd1', code: null, gib: 9 },
      { day: 'd2', code: 'A', gib: 1 },
    ];
    const result = pivotSeries(rows, {
      labelField: 'day',
      seriesField: 'code',
      valueField: 'gib',
    });
    expect(result.labels).toEqual(['d1', 'd2']);
    expect(result.series).toEqual([{ key: 'A', label: 'A', data: [0, 1] }]);
  });

  it('returns empty structures for empty input', () => {
    const result = pivotSeries([] as Record<string, unknown>[], {
      labelField: 'day',
      seriesField: 'kind',
      valueField: 'cnt',
    });
    expect(result.labels).toEqual([]);
    expect(result.series).toEqual([]);
  });
});
