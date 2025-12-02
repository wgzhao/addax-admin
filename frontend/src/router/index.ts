import { createRouter, createWebHistory } from 'vue-router/auto'
import { routes } from 'vue-router/auto-routes'
import { useAuthStore } from '@/stores/auth'

// 为特定路由添加 meta 信息
routes.forEach((route) => {
  // 如果是登录页面，设置单独的布局
  if (route.path === '/login') {
    route.meta = { ...route.meta, layout: 'login' }
  } else {
    // 其他页面使用默认布局
    route.meta = { ...route.meta, layout: 'default' }
  }
})

routes.push({
  path: '/personal-settings',
  name: 'PersonalSettings',
  component: () => import('@/views/personal-settings.vue')
})

routes.push({
  path: '/login',
  name: 'Login',
  component: () => import('@/views/login.vue'),
  meta: { layout: 'login' }
})

// 添加 settings 相关路由
routes.push({
  path: '/settings/sys-settings',
  name: 'SysSettings',
  component: () => import('@/views/settings/sys-settings.vue')
})

routes.push({
  path: '/settings/job-settings',
  name: 'JobSettings',
  component: () => import('@/views/settings/job-settings.vue')
})

// 为了向后兼容，重定向旧的 settings 路径到新的 sys-settings
routes.push({
  path: '/settings',
  redirect: '/settings/sys-settings'
})

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  // 如果访问登录页面，直接放行
  if (to.path === '/login') {
    next()
    return
  }

  // 检查是否有 token
  if (!authStore.token) {
    next('/login')
    return
  }

  // 可选：检查 JWT token 是否过期（客户端预检查）
  if (authStore.isTokenExpired && authStore.isTokenExpired()) {
    authStore.logout()
    next('/login')
    return
  }

  next()
})

export default router
