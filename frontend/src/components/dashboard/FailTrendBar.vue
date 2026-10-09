<template>
  <DashboardChartCard
    title="近 14 天任务失败趋势"
    subtitle="按操作类型"
    :height="320"
    :state="state"
    :error="error"
  >
    <DashboardBarChart :labels="shortLabels" :series="series" stacked y-title="失败次数" />
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardBarChart from './charts/DashboardBarChart.vue';
  import type { ChartSeries } from '@/types/dashboard';
  import { useChartData } from '@/composables/useChartData';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { pivotSeries } from '@/composables/useSeriesPivot';
  import { dashboardService } from '@/service/dashboard-service';
  import { JOUR_KIND_META } from '@/utils/chart-colors';

  const { raw, state, error } = useChartData(() => dashboardService.fetchFailTrend14d());
  const { palette } = useChartTheme();

  const kindLabels = Object.fromEntries(
    Object.entries(JOUR_KIND_META).map(([key, meta]) => [key, meta.label])
  );
  const kindOrder = Object.keys(JOUR_KIND_META);

  const pivoted = computed(() =>
    pivotSeries(raw.value ?? [], {
      labelField: 'day',
      seriesField: 'kind',
      valueField: 'cnt',
      seriesOrder: kindOrder,
      seriesLabels: kindLabels,
    })
  );

  const series = computed<ChartSeries[]>(() =>
    pivoted.value.series.map(s => ({
      ...s,
      color: palette.value[JOUR_KIND_META[s.key]?.paletteIndex ?? 0],
    }))
  );

  // 轴标签缩短为 MM-DD，完整日期保留在 tooltip
  const shortLabels = computed(() => pivoted.value.labels.map(d => d.slice(5)));
</script>
