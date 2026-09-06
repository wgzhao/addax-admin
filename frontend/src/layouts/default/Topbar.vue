<template>
  <v-app-bar app color="surface" class="topbar-bar" elevation="0">
    <template #default>
      <v-app-bar-title>统一采集管理系统</v-app-bar-title>
      <NavCluster :items="urls" />
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
      <UserMenu v-if="authStore.currentUserName" @logout="logout" />
      <v-btn v-if="!authStore.isLoggedIn" @click="goLogin">Login</v-btn>
      <!-- 深色/浅色模式切换按钮 -->
      <v-btn
        icon
        :title="isDarkTheme ? '切换为浅色模式' : '切换为深色模式'"
        @click="toggleThemeWithLog"
      >
        <v-icon>mdi-theme-light-dark</v-icon>
      </v-btn>
    </template>
  </v-app-bar>
  <!-- End of Topbar -->
  <SchemaUpdateAllDialog v-model="confirmUpdateAllDialog" />
</template>
<script setup lang="ts">
  import { ref, computed } from 'vue';
  import { useAuthStore } from '@/stores/auth';
  import { useAppTheme } from '@/composables/useAppTheme';
  import { useRouter } from 'vue-router';
  import tableService from '@/service/table-service';
  import { notify } from '@/stores/notifier';
  import { buildNavMenu } from './build-nav';
  import type { NavItem } from './build-nav';
  import NavCluster from './NavCluster.vue';
  import UserMenu from './UserMenu.vue';
  import SchemaUpdateAllDialog from './SchemaUpdateAllDialog.vue';

  const router = useRouter();

  const authStore = useAuthStore();
  const { isDarkTheme, toggleTheme, themeStore } = useAppTheme();

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
    buildNavMenu(router.getRoutes(), {
      updateSchemaNeed,
      openConfirmUpdateAll,
    })
  );

  // 切换主题函数
  const toggleThemeWithLog = () => {
    toggleTheme();
    console.log('当前主题切换为：', themeStore.theme);
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
  @import './_topbar-menu-shared.scss';

  .topbar-bar {
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.16);
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
