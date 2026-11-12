<template>
  <v-app-bar app color="surface" class="topbar-bar" elevation="0">
    <template #default>
      <v-btn
        v-if="compactNav"
        class="mr-1"
        icon="mdi-menu"
        variant="text"
        aria-label="打开导航菜单"
        @click="drawerOpen = true"
      />
      <router-link to="/" class="brand-lockup" :aria-label="`${APP_NAME} 首页`">
        <img :src="APP_LOGO" class="brand-mark" alt="" width="28" height="28" />
        <span class="brand-copy">
          <span class="brand-name">{{ APP_NAME }}</span>
          <span v-if="!compactNav" class="brand-sub">{{ APP_SUBTITLE }}</span>
        </span>
        <span v-if="showEnv" class="brand-env">{{ APP_ENV_LABEL }}</span>
      </router-link>
      <NavCluster v-if="!compactNav" :items="urls" />
      <v-menu
        v-if="authStore.canWrite"
        :min-width="0"
        offset-y
        open-on-hover
        open-on-click
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
      <NotificationMenu v-if="authStore.isLoggedIn" />
      <UserMenu v-if="authStore.currentUserName" @logout="logout" />
      <v-btn v-if="!authStore.isLoggedIn" @click="goLogin">登录</v-btn>
      <v-btn icon title="全局搜索 (Ctrl+K)" aria-label="全局搜索" @click="toggleCommandPalette">
        <v-icon>mdi-magnify</v-icon>
      </v-btn>
      <!-- 深色/浅色模式切换按钮 -->
      <v-btn
        icon
        :title="isDarkTheme ? '切换为浅色模式' : '切换为深色模式'"
        :aria-label="isDarkTheme ? '切换为浅色模式' : '切换为深色模式'"
        @click="toggleThemeWithLog"
      >
        <v-icon>mdi-theme-light-dark</v-icon>
      </v-btn>
    </template>
  </v-app-bar>
  <NavDrawer v-model="drawerOpen" :items="urls" />
  <!-- End of Topbar -->
  <SchemaUpdateAllDialog v-model="confirmUpdateAllDialog" />
</template>
<script setup lang="ts">
  import { ref, computed, watch } from 'vue';
  import { useDisplay } from 'vuetify';
  import { useRoute } from 'vue-router';
  import { useAuthStore } from '@/stores/auth';
  import { useAppTheme } from '@/composables/useAppTheme';
  import { useRouter } from 'vue-router';
  import tableService from '@/service/table-service';
  import { notify } from '@/stores/notifier';
  import { buildNavMenu } from './build-nav';
  import type { NavItem } from './build-nav';
  import { toggleCommandPalette } from '@/stores/command-palette';
  import NavCluster from './NavCluster.vue';
  import NavDrawer from './NavDrawer.vue';
  import UserMenu from './UserMenu.vue';
  import SchemaUpdateAllDialog from './SchemaUpdateAllDialog.vue';
  import NotificationMenu from '@/components/NotificationMenu.vue';
  import { APP_LOGO, APP_NAME, APP_SUBTITLE } from '@/config/branding';
  import { APP_ENV, APP_ENV_LABEL } from '@/config/version';

  const router = useRouter();
  const route = useRoute();
  const { width } = useDisplay();

  // Why: below ~1000px the topbar cannot fit every menu, so the same tree moves into a drawer
  const compactNav = computed(() => width.value < 1000);
  const showEnv = computed(() => APP_ENV !== 'production');
  const drawerOpen = ref(false);

  watch(
    () => route.fullPath,
    () => {
      drawerOpen.value = false;
    }
  );

  const authStore = useAuthStore();
  const { isDarkTheme, toggleTheme } = useAppTheme();

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

  // Why: prevent accidental destructive/costly operation by asking user to confirm before running the full update
  const confirmUpdateAllDialog = ref(false);

  const openConfirmUpdateAll = () => {
    confirmUpdateAllDialog.value = true;
  };

  const urls = computed<NavItem[]>(() =>
    buildNavMenu(
      router.getRoutes(),
      {
        updateSchemaNeed,
        openConfirmUpdateAll,
      },
      authStore.role
    )
  );

  const toggleThemeWithLog = () => {
    toggleTheme();
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
</script>

<style lang="scss" scoped>
  @use './topbar-menu-shared' as *;

  .topbar-bar {
    position: fixed !important;
    top: 0 !important;
    left: 0;
    right: 0;
    z-index: 1005;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.16);
  }

  /* Why: Vuetify's app bar default slot starts at the viewport edge; the
     brand mark needs the same optical inset as the page content. */
  .topbar-bar :deep(.v-toolbar__content) {
    padding-inline: 20px;
  }

  @media (max-width: 760px) {
    .topbar-bar :deep(.v-toolbar__content) {
      padding-inline: 12px;
    }
  }

  .brand-lockup {
    display: inline-flex;
    align-items: center;
    gap: 9px;
    min-width: 0;
    margin-inline-end: 18px;
    color: inherit;
    text-decoration: none;
  }

  .brand-mark {
    width: 28px;
    height: 28px;
    border-radius: 8px;
    flex: none;
  }

  .brand-copy {
    display: inline-flex;
    flex-direction: column;
    min-width: 0;
    line-height: 1.1;
  }

  .brand-name {
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.1px;
    color: rgb(var(--v-theme-on-surface));
    white-space: nowrap;
  }

  .brand-sub {
    margin-top: 2px;
    font-size: 11px;
    color: var(--ds-text-3);
    white-space: nowrap;
  }

  .brand-env {
    margin-inline-start: 2px;
    padding: 1px 6px;
    border-radius: 999px;
    font-size: 10px;
    font-weight: 600;
    color: rgb(var(--v-theme-warning));
    background: rgba(var(--v-theme-warning), 0.12);
    white-space: nowrap;
  }

  @media (max-width: 1280px) {
    .brand-sub {
      display: none;
    }

    .brand-lockup {
      margin-inline-end: 10px;
    }
  }

  .topbar-bar :deep(.top-nav-btn) {
    border-radius: 8px;
    min-height: 34px;
    font-weight: 500;
  }

  .topbar-bar :deep(.top-nav-btn--active) {
    background: rgb(var(--v-theme-primary)) !important;
    color: rgb(var(--v-theme-on-primary)) !important;
  }

  .topbar-bar :deep(.top-nav-btn--active .v-btn__overlay) {
    opacity: 0 !important;
  }

  @media (max-width: 1280px) {
    .topbar-bar :deep(.top-nav-btn) {
      min-width: auto;
      padding-inline: 10px;
    }
  }
</style>
