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

router.beforeEach((to, from, next) => {
  // 如果访问登录页面，直接放行
  if (to.path === '/login') {
    next();
    return;
  }

  // 显式声明为公开的页面才允许免登录访问
  if (to.meta.requiresAuth === false) {
    next();
    return;
  }

  const authStore = useAuthStore();

  if (!authStore.token) {
    next({ path: '/login', query: { redirect: to.fullPath } });
    return;
  }

  // 可选：检查 JWT token 是否过期（客户端预检查）
  if (authStore.isTokenExpired && authStore.isTokenExpired()) {
    authStore.logout();
    next({ path: '/login', query: { redirect: to.fullPath } });
    return;
  }

  next();
});

router.afterEach(to => {
  const title = typeof to.meta.title === 'string' ? to.meta.title.trim() : '';
  document.title = title ? `${title} · ${APP_NAME}` : `${APP_NAME} · ${APP_SUBTITLE}`;
});

export default router;
