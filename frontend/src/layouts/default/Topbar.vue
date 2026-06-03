<template>
  <v-app-bar app color="surface" class="topbar-bar" elevation="0">
    <template v-slot:default>
      <v-app-bar-title>统一采集管理系统</v-app-bar-title>
      <div class="top-nav-cluster">
        <template v-for="item in urls" :key="item.title">
          <v-menu
            v-if="item.children"
            :min-width="0"
            open-on-hover
            :open-on-click="false"
            :open-delay="80"
            :close-delay="120"
          >
            <template v-slot:activator="{ props }">
              <v-btn
                v-bind="props"
                class="top-nav-btn"
                :class="{ 'top-nav-btn--active': isMenuActive(item) }"
                :variant="isMenuActive(item) ? 'flat' : 'text'"
                :color="isMenuActive(item) ? 'primary' : undefined"
                append-icon="mdi-chevron-down"
              >
                {{ item.title }}
              </v-btn>
            </template>
            <v-list density="compact" nav class="top-nav-menu-list">
              <v-list-item
                v-for="(child, index) in item.children"
                :key="`${item.title}-${index}`"
                @click="e => handleNavChildClick(child, e)"
                :class="['top-nav-menu-item']"
                :prepend-icon="child.icon || 'mdi-chevron-right'"
                slim
              >
                <v-list-item-title>{{ child.title }}</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
          <v-btn
            v-else
            class="top-nav-btn"
            :class="{ 'top-nav-btn--active': isPathActive(item.path) }"
            :variant="isPathActive(item.path) ? 'flat' : 'text'"
            :color="isPathActive(item.path) ? 'primary' : undefined"
            :to="{ path: item.path }"
          >
            {{ item.title }}
          </v-btn>
        </template>
      </div>
      <v-menu
        :min-width="0"
        offset-y
        open-on-hover
        :open-on-click="false"
        :open-delay="80"
        :close-delay="120"
      >
        <template #activator="{ props }">
          <v-btn
            v-bind="props"
            class="top-nav-btn"
            variant="text"
            prepend-icon="mdi-plus-circle-outline"
          >
            新增
          </v-btn>
        </template>
        <v-list density="compact" nav class="top-nav-menu-list user-nav-menu-list">
          <v-list-item
            class="top-nav-menu-item"
            prepend-icon="mdi-database-cog"
            slim
            @click="goQuickCreate('/source')"
          >
            <v-list-item-title>新增采集源</v-list-item-title>
          </v-list-item>
          <v-list-item
            class="top-nav-menu-item"
            prepend-icon="mdi-database-export"
            slim
            @click="goQuickCreate('/target')"
          >
            <v-list-item-title>新增目标端</v-list-item-title>
          </v-list-item>
          <v-list-item
            class="top-nav-menu-item"
            prepend-icon="mdi-table-plus"
            slim
            :to="'/table/batch-add'"
          >
            <v-list-item-title>新增采集表</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>

      <!--
      <v-menu v-if="authStore.isLoggedIn" v-model="notifyMenu" offset-y>
        <template v-slot:activator="{ props }">
          <v-badge
            :content="unreadCountValue"
            :model-value="unreadCountValue > 0"
            color="error"
            overlap
          >
            <v-btn v-bind="props" icon :title="'未读消息: ' + unreadCountValue">
              <v-icon>mdi-bell-outline</v-icon>
            </v-btn>
          </v-badge>
        </template>
        <v-card width="380" class="pa-2">
          <v-row align="center" class="px-2">
            <div class="text-subtitle-1 font-weight-medium">消息中心</div>
            <v-spacer />
            <v-btn
              size="small"
              variant="text"
              :disabled="unreadCountValue === 0"
              @click="markAllRead"
            >
              全部已读
            </v-btn>
          </v-row>
          <v-divider class="my-2" />
          <div v-if="visibleNotifications.length === 0" class="px-2 py-3 text-caption">
            暂无消息
          </div>
          <div v-else class="notification-list">
            <div
              v-for="item in visibleNotifications"
              :key="item.id"
              class="notification-bubble"
              :class="{ unread: item.status === 'UNREAD' }"
              @click="markRead(item)"
            >
              <div class="bubble-title">
                <span>{{ item.title }}</span>
                <span class="bubble-time">{{ formatTime(item.createdAt) }}</span>
              </div>
              <div class="bubble-content">{{ item.content || item.title }}</div>
            </div>
          </div>
        </v-card>
      </v-menu>
    -->
      <v-menu v-if="authStore.currentUserName" offset-y :min-width="0">
        <template v-slot:activator="{ props }">
          <v-btn v-bind="props" flat>{{ authStore.currentUserName }}</v-btn>
        </template>
        <v-list density="compact" nav class="top-nav-menu-list user-nav-menu-list">
          <v-list-item
            class="top-nav-menu-item"
            prepend-icon="mdi-account-circle-outline"
            slim
            @click="profileDialog = true"
          >
            <v-list-item-title>账号信息</v-list-item-title>
          </v-list-item>
          <v-list-item
            v-if="themeStore.hasUserPreference"
            class="top-nav-menu-item"
            prepend-icon="mdi-theme-light-dark"
            slim
            @click="resetThemeToSystem"
          >
            <v-list-item-title>跟随系统主题</v-list-item-title>
          </v-list-item>
          <v-divider class="my-1" />
          <v-list-item
            class="top-nav-menu-item"
            prepend-icon="mdi-information-outline"
            slim
            @click="versionDialog = true"
          >
            <v-list-item-title>系统版本</v-list-item-title>
          </v-list-item>
          <v-list-item
            v-if="isAdmin"
            class="top-nav-menu-item"
            prepend-icon="mdi-account-group-outline"
            slim
            @click="goAccounts"
          >
            <v-list-item-title>账号管理</v-list-item-title>
          </v-list-item>
          <v-list-item
            class="top-nav-menu-item"
            prepend-icon="mdi-book-open-variant"
            slim
            @click="goHelp"
          >
            <v-list-item-title>帮助文档</v-list-item-title>
          </v-list-item>
          <v-list-item
            class="top-nav-menu-item"
            prepend-icon="mdi-message-text-outline"
            slim
            @click="openFeedback"
          >
            <v-list-item-title>我要反馈</v-list-item-title>
          </v-list-item>
          <v-list-item
            class="top-nav-menu-item"
            prepend-icon="mdi-lock-reset"
            slim
            @click="$router.push('/change-password')"
          >
            <v-list-item-title>修改密码</v-list-item-title>
          </v-list-item>
          <v-list-item class="top-nav-menu-item" prepend-icon="mdi-logout" slim @click="logout">
            <v-list-item-title>注销</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
      <v-btn v-if="!authStore.isLoggedIn" @click="goLogin">Login</v-btn>
      <!-- 深色/浅色模式切换按钮 -->
      <v-btn
        icon
        @click="toggleThemeWithLog"
        :title="isDarkTheme ? '切换为浅色模式' : '切换为深色模式'"
      >
        <v-icon>mdi-theme-light-dark</v-icon>
      </v-btn>
    </template>
  </v-app-bar>
  <!-- End of Topbar -->
  <v-dialog v-model="profileDialog" max-width="520">
    <v-card>
      <v-card-title class="text-h6">账号信息</v-card-title>
      <v-card-text>
        <v-list density="compact">
          <v-list-item>
            <v-list-item-title>用户名</v-list-item-title>
            <v-list-item-subtitle>{{ authStore.currentUserName || '-' }}</v-list-item-subtitle>
          </v-list-item>
          <v-list-item>
            <v-list-item-title>角色</v-list-item-title>
            <v-list-item-subtitle>{{ profileRole || '-' }}</v-list-item-subtitle>
          </v-list-item>
          <v-list-item>
            <v-list-item-title>邮箱</v-list-item-title>
            <v-list-item-subtitle>未配置</v-list-item-subtitle>
          </v-list-item>
        </v-list>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="profileDialog = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
  <v-dialog v-model="versionDialog" max-width="520">
    <v-card class="version-dialog-card">
      <v-card-text>
        <div class="version-dialog-body">
          <div class="version-logo-wrap">
            <img class="version-logo" src="/logo.svg" alt="Addax Admin Logo" />
          </div>
          <div class="version-app-name">Addax Admin</div>
          <div class="version-app-meta">Version {{ APP_VERSION }}</div>
          <div class="version-app-copyright">&copy; 2023-{{ new Date().getFullYear() }} wgzhao</div>
        </div>
      </v-card-text>
      <v-card-actions class="pb-4">
        <v-spacer />
        <v-btn variant="text" @click="versionDialog = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
  <!-- Confirm dialog for destructive full schema update -->
  <v-dialog v-model="confirmUpdateAllDialog" max-width="640">
    <v-card>
      <v-card-title class="text-h6">请确认：强制更新全部表信息</v-card-title>
      <v-card-text>
        <div>
          警告：该操作会对所有采集表执行强制结构更新，可能耗时很久并产生破坏性变更（例如覆盖现有表结构）。请确保您已备份数据并明确需要执行此操作。
        </div>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          variant="text"
          @click="confirmUpdateAllDialog = false"
          :disabled="confirmUpdateAllLoading"
          >取消</v-btn
        >
        <v-btn color="error" :loading="confirmUpdateAllLoading" @click="confirmUpdateSchemaAll"
          >确认并执行</v-btn
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
<script setup lang="ts">
  import { ref, watch, onMounted, onUnmounted, computed } from 'vue';
  import { useAuthStore } from '@/stores/auth';
  import { useAppTheme } from '@/composables/useAppTheme';
  import { useRoute, useRouter } from 'vue-router';
  import { APP_VERSION } from '@/config/version';
  import userService from '@/service/user-service';
  import tableService from '@/service/table-service';
  import { notify } from '@/stores/notifier';

  const router = useRouter();
  const route = useRoute();

  // const {global} = useTheme();

  const authStore = useAuthStore();
  const { isDarkTheme, toggleTheme, themeStore, resetToSystemTheme } = useAppTheme();
  const profileDialog = ref(false);
  const versionDialog = ref(false);
  const profileRole = ref('');
  const isAdmin = ref(false);
  const feedbackUrl = 'https://github.com/wgzhao/addax-admin/issues/new';
  // const notifyMenu = ref(false)
  let notifyTimer: number | null = null;

  // 定义菜单项类型
  interface MenuChildItem {
    path?: string;
    title: string;
    icon?: string;
    onClick?: () => void;
  }

  interface MenuItem {
    path?: string;
    name?: string;
    title: string;
    children?: MenuChildItem[];
  }

  type NavGroup = 'source' | 'target' | 'collect' | 'data' | 'systemManage';

  const navGroupOrder: NavGroup[] = ['source', 'target', 'collect', 'data', 'systemManage'];

  const navGroupTitle: Record<NavGroup, string> = {
    source: '源端管理',
    target: '目标端管理',
    collect: '采集管理',
    data: '数据管理',
    systemManage: '系统管理',
  };

  // 点击菜单项的通用处理器（支持 action 回调或路由 path）

  const handleNavChildClick = (child: any, ev?: Event) => {
    if (child?.onClick) {
      ev?.stopPropagation();
      try {
        child.onClick();
      } catch (e) {
        // swallow
      }
      return;
    }
    if (child?.path) {
      router.push({ path: child.path });
    }
  };

  // 表结构更新操作（按需）
  const updateSchemaNeed = async () => {
    try {
      const res = await tableService.updateSchema({ mode: 'need' });
      notify(res || '表结构更新任务已启动', 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      notify('更新失败: ' + msg, 'error');
    }
  };

  // 表结构更新操作（全部）
  const updateSchemaAll = async () => {
    try {
      const res = await tableService.updateSchema({ mode: 'all' });
      notify(res || '强制更新全部表信息任务已启动', 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      notify('强制更新失败: ' + msg, 'error');
    }
  };

  // Why: prevent accidental destructive/costly operation by asking user to confirm before running the full update
  const confirmUpdateAllDialog = ref(false);
  const confirmUpdateAllLoading = ref(false);

  const openConfirmUpdateAll = () => {
    confirmUpdateAllDialog.value = true;
  };

  const confirmUpdateSchemaAll = async () => {
    confirmUpdateAllLoading.value = true;
    try {
      await updateSchemaAll();
      confirmUpdateAllDialog.value = false;
    } finally {
      confirmUpdateAllLoading.value = false;
    }
  };

  const urls = computed<MenuItem[]>(() => {
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

    router.getRoutes().forEach(item => {
      const path = item.path;
      const meta = (item.meta || {}) as Record<string, any>;

      if (!path || path.includes('/:') || path === '/:pathMatch(.*)*') return;
      if (meta.layout === 'login' || meta.navHidden) return;

      const title = typeof meta.title === 'string' ? meta.title.trim() : '';
      const navTitle = typeof meta.navTitle === 'string' ? meta.navTitle.trim() : '';
      const displayTitle = navTitle || title;
      if (!displayTitle) return;

      const navGroup = navGroupOrder.includes(meta.navGroup as NavGroup)
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

    const result: MenuItem[] = [];
    if (homeRoute) {
      result.push({ path: homeRoute.path, title: homeRoute.title, name: 'Home' });
    }

    navGroupOrder.forEach(groupKey => {
      const children: MenuChildItem[] = flatRoutes
        .filter(item => !item.isHome && item.navGroup === groupKey)
        .sort((a, b) => a.navOrder - b.navOrder || a.title.localeCompare(b.title, 'zh-CN'))
        .map(item => ({
          path: item.path,
          title: item.title,
          icon: item.icon,
        }));

      // 在采集管理分组中附加独立操作（不是路由）
      if (groupKey === 'collect') {
        children.push(
          { title: '更新表信息 (按需)', icon: 'mdi-update', onClick: updateSchemaNeed },
          { title: '强制更新全部表信息', icon: 'mdi-alert', onClick: openConfirmUpdateAll }
        );
      }

      if (children.length > 0) {
        result.push({
          title: navGroupTitle[groupKey],
          children,
        });
      }
    });

    return result;
  });

  const isPathActive = (path?: string) => {
    if (!path) return false;
    if (path === '/') return route.path === '/';
    return route.path === path || route.path.startsWith(`${path}/`);
  };

  const isMenuActive = (item: MenuItem) => {
    if (item.path && isPathActive(item.path)) return true;
    if (!item.children?.length) return false;
    return item.children.some(child => isPathActive(child.path));
  };

  // 切换主题函数
  const toggleThemeWithLog = () => {
    toggleTheme();
    console.log('当前主题切换为：', themeStore.theme);
  };

  const resetThemeToSystem = () => {
    resetToSystemTheme();
    console.log('当前主题切换为：', themeStore.theme);
  };

  const goHelp = () => {
    router.push('/help');
  };

  const goAccounts = () => {
    router.push('/accounts');
  };

  const openFeedback = () => {
    window.open(feedbackUrl, '_blank', 'noopener');
  };

  const goQuickCreate = (path: '/source' | '/target' | '/table') => {
    router.push({
      path,
      query: {
        action: 'create',
      },
    });
  };

  // Logout function
  const logout = () => {
    authStore.logout(); // Assuming authStore has a logout method
    router.replace('/login');
  };

  const goLogin = () => {
    router.replace('/login');
  };

  // const refreshNotifications = async () => {
  //   if (!authStore.isLoggedIn) return
  //   await notificationCenter.refreshUnreadCount()
  // }

  const normalizeAuthority = (authority?: string) => {
    const value = (authority || '').trim().toLowerCase();
    if (value.startsWith('role_')) {
      return value.slice(5);
    }
    return value;
  };

  const refreshCurrentUserRole = async () => {
    if (!authStore.isLoggedIn) {
      profileRole.value = '';
      isAdmin.value = false;
      return;
    }

    try {
      const me = await userService.me();
      const roles = (me?.authorities || []).map(item => normalizeAuthority(item));
      profileRole.value = roles[0] || '';
      isAdmin.value = roles.includes('admin');
    } catch {
      profileRole.value = '';
      isAdmin.value = false;
    }
  };
  /*
const loadNotificationList = async () => {
  if (!authStore.isLoggedIn) return
  await notificationCenter.refreshList('ALL', 20)
}

const markRead = async (item: any) => {
  if (item?.status === 'READ') return
  await notificationCenter.markRead(item.id)
}

const markAllRead = async () => {
  await notificationCenter.markAllRead()
}

const formatTime = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.toLocaleDateString()} ${date.toLocaleTimeString()}`
}

const unreadCountValue = computed(() => notificationCenter.unreadCount.value ?? 0)

const visibleNotifications = computed(() => {
  return notificationCenter.notifications.value.filter((item: any) => {
    const title = typeof item.title === 'string' ? item.title.trim() : ''
    const content = typeof item.content === 'string' ? item.content.trim() : ''
    return title.length > 0 || content.length > 0
  })
})

watch(
  () => notifyMenu.value,
  (open) => {
    if (open) {
      loadNotificationList()
    }
  }
)
*/
  watch(
    () => authStore.isLoggedIn,
    loggedIn => {
      if (loggedIn) {
        // refreshNotifications()
        refreshCurrentUserRole();
        // if (!notifyTimer) {
        //   notifyTimer = window.setInterval(() => {
        //     refreshNotifications()
        //   }, 10000)
        // }
      }
      // else if (notifyTimer) {
      //   window.clearInterval(notifyTimer)
      //   notifyTimer = null
      //   profileRole.value = ''
      //   isAdmin.value = false
      // }
    }
  );

  onMounted(() => {
    if (authStore.isLoggedIn) {
      // refreshNotifications()
      refreshCurrentUserRole();
      // notifyTimer = window.setInterval(() => {
      //   refreshNotifications()
      // }, 10000)
    }
  });

  onUnmounted(() => {
    if (notifyTimer) {
      window.clearInterval(notifyTimer);
      notifyTimer = null;
    }
  });
</script>

<style scoped>
  .topbar-bar {
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.16);
  }

  .top-nav-cluster {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .topbar-bar :deep(.top-nav-btn) {
    border-radius: 8px;
    min-height: 34px;
    font-weight: 500;
  }

  :deep(.top-nav-menu-list) {
    min-width: 0;
    width: max-content;
    max-width: 240px;
  }

  :deep(.top-nav-menu-item) {
    min-height: 34px;
    --v-list-prepend-gap: 18px;
  }

  :deep(.top-nav-menu-item .v-list-item__prepend) {
    flex: 0 0 18px;
    width: 18px;
    min-width: 18px;
    max-width: 18px;
    margin-inline-end: 0;
  }

  :deep(.top-nav-menu-item .v-list-item__prepend > .v-list-item__spacer) {
    width: 6px;
  }

  :deep(.top-nav-menu-item .v-list-item__prepend > .v-icon) {
    font-size: 16px;
    opacity: 0.92;
  }

  :deep(.top-nav-menu-item .v-list-item-title) {
    font-size: 14px;
    line-height: 1.35;
    font-weight: 500;
  }

  :deep(.user-nav-menu-list) {
    min-width: 0;
    width: max-content;
    max-width: 260px;
  }

  .topbar-bar :deep(.top-nav-btn--active) {
    background: rgb(var(--v-theme-primary)) !important;
    color: rgb(var(--v-theme-on-primary)) !important;
  }

  .topbar-bar :deep(.top-nav-btn--active .v-btn__overlay) {
    opacity: 0 !important;
  }

  @media (max-width: 1280px) {
    .top-nav-cluster {
      gap: 4px;
    }

    .topbar-bar :deep(.top-nav-btn) {
      min-width: auto;
      padding-inline: 10px;
    }
  }

  .notification-list {
    max-height: 360px;
    overflow-y: auto;
    padding: 4px 8px 8px;
  }

  .notification-bubble {
    background: rgba(var(--v-theme-primary), 0.08);
    border: 1px solid rgba(var(--v-theme-primary), 0.24);
    border-radius: 12px;
    padding: 10px 12px;
    margin-bottom: 8px;
    cursor: pointer;
    transition:
      transform 0.1s ease,
      box-shadow 0.1s ease;
  }

  .notification-bubble:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
  }

  .notification-bubble.unread {
    background: rgba(var(--v-theme-warning), 0.14);
    border-color: rgba(var(--v-theme-warning), 0.42);
  }

  .bubble-title {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 4px;
  }

  .bubble-time {
    font-size: 11px;
    color: rgba(var(--v-theme-on-surface), 0.7);
    margin-left: 8px;
  }

  .bubble-content {
    font-size: 12px;
    line-height: 1.5;
    color: rgba(var(--v-theme-on-surface), 0.9);
  }

  .version-dialog-card {
    background: rgb(var(--v-theme-surface));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.2);
  }

  .version-dialog-body {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 12px 8px 6px;
  }

  .version-logo-wrap {
    width: 72px;
    height: 72px;
    border-radius: 20px;
    background: linear-gradient(
      145deg,
      rgba(var(--v-theme-primary), 0.24),
      rgba(var(--v-theme-on-surface), 0.08)
    );
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.24);
  }

  .version-logo {
    width: 56px;
    height: 56px;
    object-fit: contain;
  }

  .version-app-name {
    font-size: 20px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .version-app-meta {
    font-size: 14px;
    color: rgba(var(--v-theme-on-surface), 0.7);
  }

  .version-app-copyright {
    margin-top: 12px;
    font-size: 12px;
    color: rgba(var(--v-theme-on-surface), 0.55);
  }
</style>
