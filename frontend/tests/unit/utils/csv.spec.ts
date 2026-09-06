import { describe, expect, it } from 'vitest';
import type { DataTableHeader } from 'vuetify';
import { buildCsv } from '@/utils/csv';

describe('buildCsv', () => {
  it('builds a title row and evaluates value functions per row', () => {
    const headers: DataTableHeader[] = [
      { title: '表 ID', key: 'tid' },
      {
        title: '目标库表',
        key: 'target_table_full',
        value: (item: any) => `${item.db}.${item.tbl}`,
      },
      { title: '操作', key: 'actions' },
    ];
    const items = [
      { tid: 1, db: 'ods', tbl: 'user' },
      { tid: 2, db: 'dw', tbl: 'order' },
    ];
    expect(buildCsv(headers, items)).toBe('表 ID,目标库表\n1,ods.user\n2,dw.order');
  });

  it('falls back to empty strings for missing keys', () => {
    const headers: DataTableHeader[] = [
      { title: 'a', key: 'x' },
      { title: 'b', key: 'y' },
    ];
    expect(buildCsv(headers, [{ x: 1 }])).toBe('a,b\n1,');
  });

  it('returns the title row only for empty items', () => {
    expect(buildCsv([{ title: 'a', key: 'x' }], [])).toBe('a');
  });
});
