import { describe, expect, it } from 'vitest';
import { buildNavMenu, isMenuActive, isPathActive } from '@/config/navigation';

// Route-shaped stubs matching what router.getRoutes() returns
const route = (path: string, meta: Record<string, any> = {}) => ({ path, meta }) as any;

describe('buildNavMenu', () => {
  it('puts home first and groups routes by navGroup in declared order', () => {
    const routes = [
      route('/source', { title: '采集源', icon: 'mdi-db', navGroup: 'collectConfig', navOrder: 2 }),
      route('/target', { title: '目标端', navGroup: 'collectConfig', navOrder: 1 }),
      route('/table', { title: '采集表', navGroup: 'collectConfig', navOrder: 3 }),
      route('/task', { title: '采集任务', navGroup: 'collectRun', navOrder: 1 }),
      route('/', { title: '首页' }),
    ];
    const menu = buildNavMenu(routes);
    expect(menu.map(i => i.title)).toEqual(['首页', '采集配置', '采集运行']);
    const configGroup = menu.find(i => i.title === '采集配置');
    expect(configGroup?.children?.map(c => c.title)).toEqual(['目标端', '采集源', '采集表']);
  });

  it('skips login layout, hidden, param and group-less routes', () => {
    const routes = [
      route('/login', { title: '登录', layout: 'login' }),
      route('/secret', { title: '隐藏', navHidden: true }),
      route('/table/:tid', { title: '详情' }),
      route('/orphan', { title: '孤儿页' }),
      route('/', { title: '首页' }),
    ];
    const menu = buildNavMenu(routes);
    expect(menu.map(i => i.title)).toEqual(['首页']);
  });

  it('drops groups a role cannot see any child of', () => {
    const routes = [
      route('/', { title: '首页' }),
      route('/sys-settings', {
        title: '系统配置',
        navGroup: 'systemManage',
        roles: ['admin'],
      }),
    ];
    expect(buildNavMenu(routes, 'user').map(i => i.title)).toEqual(['首页']);
    expect(buildNavMenu(routes, 'admin').map(i => i.title)).toEqual(['首页', '系统管理']);
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
