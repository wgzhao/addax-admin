import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';
import { routes as autoRoutes } from 'vue-router/auto-routes';
import { useAuthStore } from '@/stores/auth';
import { APP_NAME, APP_SUBTITLE } from '@/config/branding';

// The generated routes from unplugin-vue-router carry their own vue-router type
// instances. Cast to the workspace's RouteRecordRaw to avoid cross-package
// type mismatch after the dependency upgrade.
const routes = autoRoutes as unknown as RouteRecordRaw[];

// 为特定路由添加 meta 信息
routes.forEach(route => {
  // 如果是登录页面，设置单独的布局
  if (route.path === '/login') {
    route.meta = { ...route.meta, layout: 'login' };
  } else {
    // 其他页面使用默认布局
    route.meta = { ...route.meta, layout: 'default' };
  }
});

routes.push({
  path: '/change-password',
  name: 'ChangePassword',
  component: () => import('@/views/change-password.vue'),
  meta: { layout: 'default', navHidden: true },
});

routes.push({
  path: '/login',
  name: 'Login',
  component: () => import('@/views/login.vue'),
  meta: { layout: 'login', navHidden: true },
});

routes.push({
  path: '/forbidden',
  name: 'Forbidden',
  component: () => import('@/views/forbidden.vue'),
  meta: { layout: 'default', navHidden: true, title: '无权访问' },
});

// The former cluster page is now part of system status; keep old bookmarks working.
routes.push({
  path: '/cluster',
  redirect: '/status',
});

routes.push({
  path: '/:pathMatch(.*)*',
  name: 'NotFound',
  component: () => import('@/views/not-found.vue'),
  meta: { layout: 'default', navHidden: true, title: '页面不存在' },
});

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// Vue Router 5 deprecates the next() callback in guards; return the outcome instead.
router.beforeEach(async to => {
  // 如果访问登录页面，直接放行
  if (to.path === '/login') {
    return true;
  }

  // 显式声明为公开的页面才允许免登录访问
  if (to.meta.requiresAuth === false) {
    return true;
  }

  const authStore = useAuthStore();

  if (!authStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  // 可选：检查 JWT token 是否过期（客户端预检查）
  if (authStore.isTokenExpired && authStore.isTokenExpired()) {
    authStore.logout();
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  // The role lives on the server. Wait for it before deciding whether the route is visible; the
  // default is guest, so a slow response can never expose an admin page.
  if (!authStore.roleLoaded) {
    await authStore.loadCurrentUser();
  }
  const requiredRoles = Array.isArray(to.meta.roles) ? (to.meta.roles as string[]) : null;
  if (requiredRoles && !requiredRoles.includes(authStore.role)) {
    return { path: '/forbidden' };
  }

  return true;
});

router.afterEach(to => {
  const title = typeof to.meta.title === 'string' ? to.meta.title.trim() : '';
  document.title = title ? `${title} · ${APP_NAME}` : `${APP_NAME} · ${APP_SUBTITLE}`;
});

export default router;
