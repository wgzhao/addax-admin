<template>
  <v-menu offset-y :min-width="0">
    <template #activator="{ props }">
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
        @click="router.push('/change-password')"
      >
        <v-list-item-title>修改密码</v-list-item-title>
      </v-list-item>
      <v-list-item class="top-nav-menu-item" prepend-icon="mdi-logout" slim @click="$emit('logout')">
        <v-list-item-title>注销</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-menu>

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

  <VersionDialog v-model="versionDialog" />
</template>

<script setup lang="ts">
  import { ref, watch, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { useAuthStore } from '@/stores/auth';
  import { useAppTheme } from '@/composables/useAppTheme';
  import userService from '@/service/user-service';
  import VersionDialog from './VersionDialog.vue';

  defineEmits(['logout']);

  const router = useRouter();
  const authStore = useAuthStore();
  const { themeStore, resetToSystemTheme } = useAppTheme();

  const profileDialog = ref(false);
  const versionDialog = ref(false);
  const profileRole = ref('');
  const isAdmin = ref(false);
  const feedbackUrl = 'https://github.com/wgzhao/addax-admin/issues/new';

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

  watch(
    () => authStore.isLoggedIn,
    loggedIn => {
      if (loggedIn) {
        refreshCurrentUserRole();
      }
    }
  );

  onMounted(() => {
    if (authStore.isLoggedIn) {
      refreshCurrentUserRole();
    }
  });

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
</script>

<style lang="scss" scoped>
  @import './_topbar-menu-shared.scss';
</style>
