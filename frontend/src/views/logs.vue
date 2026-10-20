<template>
  <div class="page-shell logs-page">
    <PageHeader title="日志管理" subtitle="查看采集执行日志，并按需清理历史记录。" />
    <v-card class="logs-card ds-card" elevation="0">
      <v-card-text class="logs-body">
        <AddaxLogList table="addax_log" />
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
  import AddaxLogList from '@/components/logs/AddaxLogList.vue';
  import PageHeader from '@/components/PageHeader.vue';
</script>
<style scoped>
  .logs-page {
    --ds-table-max: calc(100vh - 395px);
    --ds-table-max-md: calc(100vh - 600px);
    background: rgb(var(--v-theme-surface));
  }

  .logs-card {
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .logs-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .logs-body {
    background: transparent;
  }

  .logs-tabs :deep(.v-slide-group__content) {
    gap: 8px;
  }

  .logs-window {
    margin-top: 12px;
  }

  .tab-panel {
    background: rgb(var(--v-theme-surface));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.06);
    border-radius: 12px;
    padding: 16px;
  }

  .empty-placeholder {
    color: rgba(var(--v-theme-on-surface), 0.6);
    font-size: 14px;
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "日志管理",
    "icon": "mdi-file-document-outline",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 30
  }
}
</route>
