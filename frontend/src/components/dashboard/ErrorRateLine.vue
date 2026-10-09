<template>
  <DashboardChartCard
    title="错误行占比"
    subtitle="近 30 天"
    :height="320"
    :state="state"
    :error="error"
  >
    <DashboardLineChart
      :labels="labels"
      :series="series"
      y-title="错误行占比 (%)"
      :value-formatter="fmtPct"
      :extra-tooltip="extraTooltip"
    />
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardLineChart from './charts/DashboardLineChart.vue';
  import type { ErrorRateRow } from '@/types/dashboard';
  import { useChartData } from '@/composables/useChartData';
  import { dashboardService } from '@/service/dashboard-service';

  const { raw, state, error } = useChartData(() => dashboardService.fetchErrorRate30d());

  const labels = computed(() => (raw.value ?? []).map(r => r.biz_date));
  const series = computed(() => [
    {
      key: 'error_pct',
      label: '错误行占比 (%)',
      // 当日无采集记录时占比无意义，置 null 让曲线断点而不是落到 0
      data: (raw.value ?? []).map(r => (r.total_recs > 0 ? r.error_pct ?? 0 : null)),
      fill: true,
    },
  ]);

  const fmtPct = (value: number) => `${value}%`;
  const fmtCount = (value: number) => value.toLocaleString('zh-CN');

  const extraTooltip = (index: number): string[] => {
    const row: ErrorRateRow | undefined = raw.value?.[index];
    if (!row) return [];
    return [`采集行数：${fmtCount(row.total_recs)}`, `错误行数：${fmtCount(row.total_errors)}`];
  };
</script>
