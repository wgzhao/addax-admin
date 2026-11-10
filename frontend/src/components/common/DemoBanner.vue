<template>
  <div class="demo-banner" role="status">
    <v-icon size="16">mdi-flask-outline</v-icon>
    <span>演示环境 · 数据为模拟数据，可安全重置</span>
    <v-spacer />
    <slot name="actions" />
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
  .demo-banner {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 12px;
    border: 1px solid rgba(var(--v-theme-warning), 0.28);
    border-radius: 9px;
    background: rgba(var(--v-theme-warning), 0.08);
    color: rgb(var(--v-theme-warning));
    font-size: 12px;
    font-weight: 500;
  }
</style>
