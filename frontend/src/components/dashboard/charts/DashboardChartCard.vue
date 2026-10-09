<template>
  <v-card class="chart-card section-card pa-3" elevation="0" rounded="lg">
    <v-card-title class="chart-title d-flex align-center">
      <span class="text-truncate">{{ title }}</span>
      <v-spacer />
      <span v-if="subtitle" class="chart-subtitle">{{ subtitle }}</span>
      <slot name="actions" />
    </v-card-title>
    <v-card-text class="pa-0">
      <div class="chart-body" :data-chart-state="state" :style="{ height: `${height}px` }">
        <div v-if="state === 'loading'" class="chart-placeholder">加载中…</div>
        <div v-else-if="state === 'error'" class="chart-placeholder text-error">
          加载失败：{{ error || '未知错误' }}
        </div>
        <div v-else-if="state === 'empty'" class="chart-placeholder">暂无数据</div>
        <slot v-else />
      </div>
    </v-card-text>
    <slot name="footer" />
  </v-card>
</template>

<script setup lang="ts">
  import type { ChartDataState } from '@/composables/useChartData';

  withDefaults(
    defineProps<{
      title: string;
      subtitle?: string;
      height?: number;
      state: ChartDataState;
      error?: string | null;
    }>(),
    { height: 320, error: null }
  );
</script>

<style scoped>
  .chart-title {
    font-size: 15px;
    font-weight: 600;
    padding: 4px 4px 8px;
    min-height: unset;
  }

  .chart-subtitle {
    font-size: 12px;
    font-weight: 400;
    color: rgb(var(--v-theme-on-surface-variant));
    margin-left: 12px;
    white-space: nowrap;
  }

  .chart-body {
    position: relative;
    width: 100%;
  }

  .chart-placeholder {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    color: rgb(var(--v-theme-on-surface-muted));
  }
</style>
