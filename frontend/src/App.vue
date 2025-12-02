<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import DefaultLayout from './layouts/DefaultLayout.vue';
import LoginLayout from './layouts/LoginLayout.vue';
import Notifier from './components/Notifier.vue';

const route = useRoute();
const layout = computed(() => {
  const layoutName = route.meta.layout || 'default'; // 默认使用 DefaultLayout
  return layoutName === 'default' ? DefaultLayout : LoginLayout;
});
</script>

<template>
  <component :is="layout">
    <router-view />
  </component>
  <!-- 全局通知条 - 移到外层确保始终显示 -->
  <Notifier />
</template>

<style></style>
