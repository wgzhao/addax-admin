<template>
  <DashboardChartCard
    title="当前采集状态分布"
    subtitle="实时"
    :height="300"
    :state="state"
    :error="error"
  >
    <div class="status-figure">
      <div class="status-total">
        <span class="status-total-value">{{ total }}</span>
        <span class="status-total-label">采集表</span>
      </div>
      <div class="status-track" role="img" :aria-label="ariaLabel">
        <span
          v-for="group in groups"
          :key="group.key"
          class="status-seg"
          :style="{ flexGrow: group.value, background: group.color }"
        />
      </div>
      <ul class="status-legend">
        <li v-for="group in groups" :key="group.key">
          <span class="legend-dot" :style="{ background: group.color }" />
          <span class="legend-label">{{ group.label }}</span>
          <span class="legend-value">{{ group.value }}</span>
          <span class="legend-pct">{{ percent(group.value) }}%</span>
        </li>
      </ul>
    </div>
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import { useChartData } from '@/composables/useChartData';
  import { dashboardService } from '@/service/dashboard-service';

  const { raw, state, error } = useChartData(() => dashboardService.fetchTableStatusDist());

  const countOf = (statuses: string[]) => {
    const rows = raw.value ?? [];
    return statuses.reduce(
      (total, status) => total + Number(rows.find(row => row.status === status)?.cnt ?? 0),
      0
    );
  };

  const groups = computed(() => [
    {
      key: 'healthy',
      label: '已采集',
      value: countOf(['Y']),
      color: 'rgb(var(--v-theme-success))',
    },
    {
      key: 'running',
      label: '采集中',
      value: countOf(['R']),
      color: 'rgb(var(--v-theme-info))',
    },
    {
      key: 'attention',
      label: '需关注',
      value: countOf(['E', 'W', 'U']),
      color: 'rgb(var(--v-theme-error))',
    },
    {
      key: 'inactive',
      label: '未采集 / 已排除',
      value: countOf(['N', 'X']),
      color: 'rgba(var(--v-theme-on-surface), 0.28)',
    },
  ]);

  const total = computed(() => groups.value.reduce((sum, group) => sum + group.value, 0));
  const percent = (value: number) => (total.value > 0 ? (value / total.value) * 100 : 0).toFixed(1);
  const ariaLabel = computed(() =>
    groups.value.map(group => `${group.label} ${group.value}`).join('，')
  );
</script>

<style scoped>
  .status-figure {
    display: flex;
    flex-direction: column;
    gap: 14px;
    height: 100%;
    justify-content: center;
  }

  .status-total {
    display: flex;
    align-items: baseline;
    gap: 6px;
  }

  .status-total-value {
    font-size: 34px;
    line-height: 1;
    font-weight: 700;
    letter-spacing: -0.03em;
    color: rgb(var(--v-theme-on-surface));
  }

  .status-total-label {
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .status-track {
    display: flex;
    gap: 2px;
    height: 14px;
    border-radius: 999px;
    overflow: hidden;
    background: rgba(var(--v-theme-on-surface), 0.06);
  }

  .status-seg {
    flex-basis: 0;
    min-width: 0;
  }

  .status-legend {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px 18px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .status-legend li {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .legend-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex: none;
  }

  .legend-label {
    flex: 1 1 auto;
    min-width: 0;
    white-space: nowrap;
  }

  .legend-value {
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .legend-pct {
    width: 44px;
    text-align: right;
    color: var(--ds-text-3);
    font-variant-numeric: tabular-nums;
  }
</style>
