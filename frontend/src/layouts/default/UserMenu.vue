<template>
  <v-menu offset-y :min-width="0">
    <template #activator="{ props }">
      <!-- Why: share the topbar nav button metrics; without top-nav-btn the
           global button reset inherits body weight (400) instead of 500 -->
      <v-btn v-bind="props" class="top-nav-btn" variant="text" aria-label="账号菜单">{{
        authStore.displayName || authStore.currentUserName
      }}</v-btn>
    </template>
    <v-list density="compact" nav class="top-nav-menu-list user-nav-menu-list">
      <v-list-item
        class="top-nav-menu-item"
        prepend-icon="mdi-account-circle-outline"
        slim
        @click="router.push('/account')"
      >
        <v-list-item-title>我的账号</v-list-item-title>
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
        class="top-nav-menu-item"
        prepend-icon="mdi-help-circle-outline"
        slim
        @click="router.push('/help')"
      >
        <v-list-item-title>帮助中心</v-list-item-title>
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
        prepend-icon="mdi-logout"
        slim
        @click="$emit('logout')"
      >
        <v-list-item-title>注销</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-menu>

  <VersionDialog v-model="versionDialog" />
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { useAuthStore } from '@/stores/auth';
  import VersionDialog from './VersionDialog.vue';

  defineEmits(['logout']);

  const router = useRouter();
  const authStore = useAuthStore();

  const versionDialog = ref(false);
  const feedbackUrl = 'https://github.com/wgzhao/addax-admin/issues/new';

  const openFeedback = () => {
    window.open(feedbackUrl, '_blank', 'noopener');
  };
</script>

<style lang="scss" scoped>
  @use './topbar-menu-shared' as *;
</style>
