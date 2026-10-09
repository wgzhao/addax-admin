<template>
  <DashboardChartCard
    title="记录数 vs 数据量"
    subtitle="近 12 个月（月值）"
    :height="320"
    :state="state"
    :error="error"
  >
    <DashboardLineChart
      :labels="labels"
      :series="series"
      y-title="记录数"
      y1-title="数据量 (GiB)"
      :value-formatter="fmtRecs"
      :y1-formatter="fmtGb"
    />
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardLineChart from './charts/DashboardLineChart.vue';
  import { useChartData } from '@/composables/useChartData';
  import { dashboardService } from '@/service/dashboard-service';

  const { raw, state, error } = useChartData(() => dashboardService.fetchRecsBytes12m());

  const labels = computed(() => (raw.value ?? []).map(r => r.month));

  const series = computed(() => [
    {
      key: 'total_recs',
      label: '记录数',
      data: (raw.value ?? []).map(r => r.total_recs),
      type: 'bar' as const,
      yAxisID: 'y' as const,
    },
    {
      key: 'total_gb',
      label: '数据量 (GiB)',
      data: (raw.value ?? []).map(r => r.total_gb),
      type: 'line' as const,
      yAxisID: 'y1' as const,
      fill: true,
    },
  ]);

  const fmtRecs = (value: number) => {
    if (Math.abs(value) >= 1e8) return `${(value / 1e8).toFixed(1)}亿`;
    if (Math.abs(value) >= 1e4) return `${(value / 1e4).toFixed(0)}万`;
    return String(value);
  };

  const fmtGb = (value: number) => `${value}`;
</script>
