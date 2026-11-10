<template>
  <DashboardChartCard
    title="月度记录数与数据量"
    subtitle="近 12 个月 · 单轴切换"
    :height="320"
    :state="state"
    :error="error"
  >
    <template #actions>
      <v-btn-toggle v-model="metric" density="compact" variant="outlined" mandatory divided>
        <v-btn value="recs" size="x-small">记录数</v-btn>
        <v-btn value="gb" size="x-small">数据量</v-btn>
      </v-btn-toggle>
    </template>

    <DashboardLineChart
      :labels="labels"
      :series="series"
      :y-title="metric === 'recs' ? '记录数' : 'GiB'"
      :value-formatter="metric === 'recs' ? fmtRecs : fmtGb"
      :show-points="false"
    />
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardLineChart from './charts/DashboardLineChart.vue';
  import type { ChartSeries } from '@/types/dashboard';
  import { useChartData } from '@/composables/useChartData';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { dashboardService } from '@/service/dashboard-service';

  const { raw, state, error } = useChartData(() => dashboardService.fetchRecsBytes12m());
  const { emphasis } = useChartTheme();
  const metric = ref<'recs' | 'gb'>('recs');

  const labels = computed(() => (raw.value ?? []).map(row => row.month));

  const series = computed<ChartSeries[]>(() => {
    const isRecs = metric.value === 'recs';
    return [
      {
        key: metric.value,
        label: isRecs ? '记录数' : '数据量 (GiB)',
        data: (raw.value ?? []).map(row => (isRecs ? row.total_recs : row.total_gb)),
        color: emphasis.value,
        type: isRecs ? 'bar' : 'line',
        fill: !isRecs,
      },
    ];
  });

  const fmtRecs = (value: number) => {
    if (Math.abs(value) >= 1e8) return `${(value / 1e8).toFixed(1)}亿`;
    if (Math.abs(value) >= 1e4) return `${(value / 1e4).toFixed(0)}万`;
    return String(value);
  };

  const fmtGb = (value: number) => `${value}`;
</script>
