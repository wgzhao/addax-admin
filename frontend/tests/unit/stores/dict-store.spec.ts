import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useDictStore } from '@/stores/dict-store';

vi.mock('@/service/enum-service', () => ({
  default: {
    getJourKind: vi.fn(),
    getTableStatus: vi.fn(),
    getTaskStatus: vi.fn(),
  },
}));

import EnumService from '@/service/enum-service';

const getJourKind = vi.mocked(EnumService.getJourKind);
const getTableStatus = vi.mocked(EnumService.getTableStatus);
const getTaskStatus = vi.mocked(EnumService.getTaskStatus);

describe('dict store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    // reset implementations too: config restoreMocks only resets spies, and
    // module-mock implementations would otherwise leak between tests here
    vi.resetAllMocks();
  });

  it('loads all three enums in parallel', async () => {
    getJourKind.mockResolvedValue([{ code: 1, desc: '日' }]);
    getTableStatus.mockResolvedValue([{ code: 'N', desc: '未采集' }]);
    getTaskStatus.mockResolvedValue([{ code: 'R', desc: '运行中' }]);

    const store = useDictStore();
    await store.loadAll();

    expect(store.jourKind).toEqual([{ code: 1, desc: '日' }]);
    expect(store.tableStatus).toEqual([{ code: 'N', desc: '未采集' }]);
    expect(store.taskStatus).toEqual([{ code: 'R', desc: '运行中' }]);
  });

  it('keeps empty lists and logs when loading fails', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    getJourKind.mockRejectedValue(new Error('boom'));

    const store = useDictStore();
    await store.loadAll();

    expect(store.jourKind).toEqual([]);
    expect(store.tableStatus).toEqual([]);
    expect(store.taskStatus).toEqual([]);
    expect(errorSpy).toHaveBeenCalled();
  });

  it('finds entries with string/number normalized comparison', async () => {
    getJourKind.mockResolvedValue([{ code: 1, desc: '日' }]);
    getTableStatus.mockResolvedValue([{ code: 3, desc: '已上线' }]);
    getTaskStatus.mockResolvedValue([{ code: 'R', desc: '运行中' }]);
    const store = useDictStore();
    await store.loadAll();

    expect(store.findTableStatus('3')?.desc).toBe('已上线');
    expect(store.findTableStatus(3)?.desc).toBe('已上线');
    expect(store.findTableStatus(99)).toBeUndefined();
    expect(store.findTaskStatus('R')?.desc).toBe('运行中');
  });
});
