<template>
  <DashboardChartCard
    title="各数据源采集量贡献"
    subtitle="近 30 天 Top8 + 其他（GiB）"
    :height="340"
    :state="state"
    :error="error"
  >
    <DashboardLineChart
      :labels="shortLabels"
      :series="series"
      :stacked="true"
      y-title="采集量 (GiB)"
      :value-formatter="fmtGb"
    />
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardLineChart from './charts/DashboardLineChart.vue';
  import type { ChartSeries } from '@/types/dashboard';
  import { useChartData } from '@/composables/useChartData';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { pivotSeries } from '@/composables/useSeriesPivot';
  import { dashboardService } from '@/service/dashboard-service';

  const { raw, state, error } = useChartData(() => dashboardService.fetchSourceContribution30d());
  const { palette } = useChartTheme();

  // API 已按总量融合为 Top8 + '其他'（code 为 OTHER 时展示名称为“其他”）
  const nameByCode = computed(() => {
    const map: Record<string, string> = {};
    for (const row of raw.value ?? []) {
      if (row.code && row.name) map[row.code] = row.name;
    }
    return map;
  });

  const pivoted = computed(() =>
    pivotSeries((raw.value ?? []) as unknown as Record<string, unknown>[], {
      labelField: 'biz_date',
      seriesField: 'code',
      valueField: 'gib',
      seriesLabels: nameByCode.value,
    })
  );

  const series = computed<ChartSeries[]>(() =>
    pivoted.value.series.map((s, index) => ({
      ...s,
      fill: true,
      color: palette.value[index % palette.value.length],
    }))
  );

  const shortLabels = computed(() => pivoted.value.labels.map(d => d.slice(5)));
  const fmtGb = (value: number) => `${value} GiB`;
</script>
