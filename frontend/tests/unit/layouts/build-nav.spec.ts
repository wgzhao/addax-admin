import { describe, expect, it, vi } from 'vitest';
import { buildNavMenu, isMenuActive, isPathActive } from '@/layouts/default/build-nav';
import type { NavActions } from '@/layouts/default/build-nav';

// Route-shaped stubs matching what router.getRoutes() returns
const route = (path: string, meta: Record<string, any> = {}) => ({ path, meta }) as any;

const actions: NavActions = { updateSchemaNeed: () => {}, openConfirmUpdateAll: () => {} };

describe('buildNavMenu', () => {
  it('puts home first and groups routes by navGroup in declared order', () => {
    const routes = [
      route('/source', { title: '采集源', icon: 'mdi-db', navGroup: 'source', navOrder: 2 }),
      route('/target', { title: '目标端', navGroup: 'target', navOrder: 1 }),
      route('/cluster', { title: '集群', navGroup: 'source', navOrder: 1 }),
      route('/', { title: '首页' }),
    ];
    const menu = buildNavMenu(routes, actions);
    // the collect group always appears because its action items are appended unconditionally
    expect(menu.map(i => i.title)).toEqual(['首页', '源端管理', '目标端管理', '采集管理']);
    const sourceGroup = menu.find(i => i.title === '源端管理');
    expect(sourceGroup?.children?.map(c => c.title)).toEqual(['集群', '采集源']);
  });

  it('skips login layout, hidden, param and group-less routes', () => {
    const routes = [
      route('/login', { title: '登录', layout: 'login' }),
      route('/secret', { title: '隐藏', navHidden: true }),
      route('/table/:tid', { title: '详情' }),
      route('/orphan', { title: '孤儿页' }),
      route('/', { title: '首页' }),
    ];
    const menu = buildNavMenu(routes, actions);
    expect(menu.map(i => i.title)).toEqual(['首页', '采集管理']);
  });

  it('injects collect-group actions', () => {
    const need = vi.fn();
    const all = vi.fn();
    const routes = [
      route('/', { title: '首页' }),
      route('/table', { title: '采集表', navGroup: 'collect', navOrder: 1 }),
    ];
    const menu = buildNavMenu(routes, { updateSchemaNeed: need, openConfirmUpdateAll: all });
    const collect = menu.find(i => i.title === '采集管理');
    const actionItems = collect?.children?.filter(c => c.onClick) ?? [];
    expect(actionItems).toHaveLength(2);
    actionItems[0].onClick!();
    actionItems[1].onClick!();
    expect(need).toHaveBeenCalledTimes(1);
    expect(all).toHaveBeenCalledTimes(1);
  });
});

describe('isPathActive', () => {
  it('matches the home path exactly', () => {
    expect(isPathActive('/', '/')).toBe(true);
    expect(isPathActive('/table', '/')).toBe(false);
  });

  it('matches exact and prefixed paths only', () => {
    expect(isPathActive('/table', '/table')).toBe(true);
    expect(isPathActive('/table/detail/1', '/table')).toBe(true);
    expect(isPathActive('/tablex', '/table')).toBe(false);
  });
});

describe('isMenuActive', () => {
  it('is active when the item path or any child path is active', () => {
    const item = {
      title: 'g',
      children: [
        { title: 'a', path: '/a' },
        { title: 'b', path: '/b' },
      ],
    };
    expect(isMenuActive('/b', item)).toBe(true);
    expect(isMenuActive('/c', item)).toBe(false);
  });

  it('is inactive without children or paths', () => {
    expect(isMenuActive('/x', { title: 'solo' })).toBe(false);
  });
});
