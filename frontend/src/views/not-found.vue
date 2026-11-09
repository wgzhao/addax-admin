<template>
  <div class="state-page">
    <div class="state-card">
      <div class="state-code">404</div>
      <h1>页面不存在</h1>
      <p>你访问的页面可能已被移动、删除，或者地址有误。</p>
      <div class="state-actions">
        <v-btn color="primary" variant="flat" prepend-icon="mdi-home" @click="goHome">
          返回首页
        </v-btn>
        <v-btn variant="text" prepend-icon="mdi-arrow-left" @click="goBack">返回上一页</v-btn>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useRouter } from 'vue-router';

  const router = useRouter();
  const goHome = () => router.push('/');
  const goBack = () => (window.history.length > 1 ? router.back() : router.push('/'));
</script>

<style scoped>
  .state-page {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: calc(100vh - 160px);
    padding: 32px 16px;
  }

  .state-card {
    width: min(520px, 100%);
    text-align: center;
  }

  .state-code {
    font-size: 72px;
    line-height: 1;
    font-weight: 800;
    letter-spacing: -0.04em;
    color: rgba(var(--v-theme-primary), 0.22);
  }

  h1 {
    margin: 12px 0 0;
    font-size: 22px;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  p {
    margin: 10px auto 0;
    max-width: 420px;
    font-size: 14px;
    line-height: 1.7;
    color: var(--ds-text-2);
  }

  .state-actions {
    display: flex;
    justify-content: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 24px;
  }
</style>
