<template>
  <DashboardChartCard
    title="各采集源采集量贡献"
    subtitle="近 30 天 · Top 8 + 其他"
    :height="cardHeight"
    :state="state"
    :error="error"
  >
    <div v-if="items.length" class="contribution-list">
      <div v-for="(item, index) in items" :key="item.key" class="contribution-row">
        <div class="contribution-meta">
          <span class="contribution-name" :title="item.name">{{ item.name }}</span>
          <span class="contribution-value">{{ format(item.gib) }}</span>
        </div>
        <div class="contribution-track">
          <span
            class="contribution-bar"
            :class="{
              'contribution-bar--top': index === 0,
              'contribution-bar--other': item.key === 'OTHER',
            }"
            :style="{ width: `${widthFor(item.gib)}%` }"
          />
        </div>
      </div>
    </div>
    <div v-else class="contribution-empty">暂无采集量数据</div>
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import { useChartData } from '@/composables/useChartData';
  import { dashboardService } from '@/service/dashboard-service';

  const { raw, state, error } = useChartData(() =>
    dashboardService.fetchSourceContribution30d()
  );

  const items = computed(() => {
    const map = new Map<string, { key: string; name: string; gib: number }>();
    for (const row of raw.value ?? []) {
      // Why: empty calendar days arrive as null placeholders and must not
      // become a 未知 source in the contribution list
      if (row.source_id == null && !row.name) continue;
      const key = row.source_id != null ? String(row.source_id) : row.name || 'UNKNOWN';
      const current = map.get(key) ?? { key, name: row.name || '未知', gib: 0 };
      current.gib += Number(row.gib ?? 0);
      map.set(key, current);
    }
    const sorted = [...map.values()].sort((a, b) => b.gib - a.gib);
    const top = sorted.slice(0, 8);
    const rest = sorted.slice(8).reduce((total, item) => total + item.gib, 0);
    return rest > 0 ? [...top, { key: 'OTHER', name: '其他', gib: rest }] : top;
  });

  // Why: a ninth row (Top 8 + 其他) is taller than the fixed 340px body and
  // would overflow the card, so grow the card with the row count
  const cardHeight = computed(() => Math.max(340, items.value.length * 39 + 12));

  const max = computed(() => Math.max(...items.value.map(item => item.gib), 1));
  const widthFor = (value: number) => Math.max((value / max.value) * 100, value > 0 ? 1.5 : 0);

  const format = (value: number) => {
    if (value >= 1024) return `${(value / 1024).toFixed(2)} TiB`;
    return `${value.toFixed(2)} GiB`;
  };
</script>

<style scoped>
  .contribution-list {
    display: flex;
    flex-direction: column;
    gap: 11px;
    padding: 6px 2px;
  }

  .contribution-row {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  .contribution-meta {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
    font-size: 12px;
  }

  .contribution-name {
    color: var(--ds-text-2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .contribution-value {
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  .contribution-track {
    height: 8px;
    border-radius: 999px;
    background: rgba(var(--v-theme-on-surface), 0.06);
    overflow: hidden;
  }

  .contribution-bar {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: rgba(var(--v-theme-primary), 0.62);
  }

  .contribution-bar--top {
    background: rgb(var(--v-theme-primary));
  }

  .contribution-bar--other {
    background: rgba(var(--v-theme-on-surface), 0.22);
  }

  .contribution-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    font-size: 13px;
    color: var(--ds-text-3);
  }
</style>
