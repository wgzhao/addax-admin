<template>
  <div class="p-4 logs-page">
    <v-card class="logs-card" elevation="0">
      <v-card-title class="logs-title">
        <v-icon color="primary" size="20">mdi-clipboard-text-outline</v-icon>
        <span>日志管理</span>
      </v-card-title>
      <v-divider />
      <v-card-text class="logs-body">
        <v-tabs v-model="tab" color="primary" class="logs-tabs">
          <v-tab value="addax_log">采集日志表</v-tab>
          <v-tab value="etl_jour">采集流水表</v-tab>
          <v-tab value="etl_statistic">统计表</v-tab>
        </v-tabs>

        <v-tabs-window v-model="tab" class="logs-window">
          <v-tabs-window-item value="addax_log">
            <v-sheet class="tab-panel">
              <AddaxLogList table="addax_log" />
            </v-sheet>
          </v-tabs-window-item>
          <v-tabs-window-item value="etl_jour">
            <v-sheet class="tab-panel">
              <div class="empty-placeholder">功能建设中</div>
            </v-sheet>
          </v-tabs-window-item>
          <v-tabs-window-item value="etl_statistic">
            <v-sheet class="tab-panel">
              <div class="empty-placeholder">功能建设中</div>
            </v-sheet>
          </v-tabs-window-item>
        </v-tabs-window>
      </v-card-text>
    </v-card>

  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue'
  import AddaxLogList from '@/components/logs/AddaxLogList.vue'
  const tab = ref('addax_log')
</script>
<style scoped>
.logs-page {
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
