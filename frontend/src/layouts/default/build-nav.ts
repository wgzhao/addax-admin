import type { Router } from 'vue-router';

export interface NavChildItem {
  path?: string;
  title: string;
  icon?: string;
  onClick?: () => void;
}

export interface NavItem {
  path?: string;
  name?: string;
  title: string;
  children?: NavChildItem[];
}

export interface NavActions {
  updateSchemaNeed: () => void;
  openConfirmUpdateAll: () => void;
}

type NavGroup = 'source' | 'target' | 'collect' | 'data' | 'systemManage';

const NAV_GROUP_ORDER: NavGroup[] = ['source', 'target', 'collect', 'data', 'systemManage'];

const NAV_GROUP_TITLE: Record<NavGroup, string> = {
  source: '源端管理',
  target: '目标端管理',
  collect: '采集管理',
  data: '数据管理',
  systemManage: '系统管理',
};

// Aggregate router meta into the topbar navigation tree. Actions are injected
// for the collect group so this stays a pure function over route records.
export function buildNavMenu(
  routes: ReturnType<Router['getRoutes']>,
  actions: NavActions
): NavItem[] {
  const routeMap = new Map<
    string,
    {
      path: string;
      title: string;
      icon?: string;
      navGroup?: NavGroup;
      navOrder: number;
      isHome: boolean;
    }
  >();

  routes.forEach(item => {
    const path = item.path;
    const meta = (item.meta || {}) as Record<string, any>;

    if (!path || path.includes('/:') || path === '/:pathMatch(.*)*') return;
    if (meta.layout === 'login' || meta.navHidden) return;

    const title = typeof meta.title === 'string' ? meta.title.trim() : '';
    const navTitle = typeof meta.navTitle === 'string' ? meta.navTitle.trim() : '';
    const displayTitle = navTitle || title;
    if (!displayTitle) return;

    const navGroup = NAV_GROUP_ORDER.includes(meta.navGroup as NavGroup)
      ? (meta.navGroup as NavGroup)
      : undefined;

    if (path !== '/' && !navGroup) return;

    const icon = typeof meta.icon === 'string' ? meta.icon : undefined;
    const navOrder = Number.isFinite(Number(meta.navOrder)) ? Number(meta.navOrder) : 999;

    const existing = routeMap.get(path);
    if (!existing) {
      routeMap.set(path, {
        path,
        title: displayTitle,
        icon,
        navGroup,
        navOrder,
        isHome: path === '/',
      });
      return;
    }

    existing.title = existing.title || displayTitle;
    existing.icon = existing.icon || icon;
    existing.navGroup = existing.navGroup || navGroup;
    existing.navOrder = Math.min(existing.navOrder, navOrder);
    existing.isHome = existing.isHome || path === '/';
  });

  const flatRoutes = Array.from(routeMap.values());
  const homeRoute = flatRoutes.find(item => item.isHome);

  const result: NavItem[] = [];
  if (homeRoute) {
    result.push({ path: homeRoute.path, title: homeRoute.title, name: 'Home' });
  }

  NAV_GROUP_ORDER.forEach(groupKey => {
    const children: NavChildItem[] = flatRoutes
      .filter(item => !item.isHome && item.navGroup === groupKey)
      .sort((a, b) => a.navOrder - b.navOrder || a.title.localeCompare(b.title, 'zh-CN'))
      .map(item => ({
        path: item.path,
        title: item.title,
        icon: item.icon,
      }));

    // Attach standalone actions (not routes) to the collect group
    if (groupKey === 'collect') {
      children.push(
        { title: '更新表信息 (按需)', icon: 'mdi-update', onClick: actions.updateSchemaNeed },
        { title: '强制更新全部表信息', icon: 'mdi-alert', onClick: actions.openConfirmUpdateAll }
      );
    }

    if (children.length > 0) {
      result.push({
        title: NAV_GROUP_TITLE[groupKey],
        children,
      });
    }
  });

  return result;
}

export function isPathActive(currentPath: string, path?: string): boolean {
  if (!path) return false;
  if (path === '/') return currentPath === '/';
  return currentPath === path || currentPath.startsWith(`${path}/`);
}

export function isMenuActive(currentPath: string, item: NavItem): boolean {
  if (item.path && isPathActive(currentPath, item.path)) return true;
  if (!item.children?.length) return false;
  return item.children.some(child => isPathActive(currentPath, child.path));
}
