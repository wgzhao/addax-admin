<template>
  <DashboardChartCard
    title="近 14 天任务失败趋势"
    subtitle="Top 2 类型 + 其他"
    :height="320"
    :state="chartState"
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
  import { JOUR_KIND_META } from '@/utils/chart-colors';
  import { dashboardService } from '@/service/dashboard-service';

  const { raw, state, error } = useChartData(() => dashboardService.fetchFailTrend14d());
  const { palette, muted } = useChartTheme();

  const days = computed(() => [...new Set((raw.value ?? []).map(row => row.day))].sort());

  const topKinds = computed(() => {
    const totals = new Map<string, number>();
    for (const row of raw.value ?? []) {
      const kind = row.kind || 'UNKNOWN';
      totals.set(kind, (totals.get(kind) ?? 0) + Number(row.cnt ?? 0));
    }
    return [...totals.entries()]
      .filter(([, total]) => total > 0)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 2)
      .map(([kind]) => kind);
  });

  const countFor = (day: string, kinds: string[]) =>
    (raw.value ?? [])
      .filter(row => row.day === day && kinds.includes(row.kind || 'UNKNOWN'))
      .reduce((total, row) => total + Number(row.cnt ?? 0), 0);

  const series = computed<ChartSeries[]>(() => {
    const result: ChartSeries[] = topKinds.value.map((kind, index) => ({
      key: kind,
      label: JOUR_KIND_META[kind]?.label ?? kind,
      data: days.value.map(day => countFor(day, [kind])),
      color: palette.value[index],
    }));

    const otherData = days.value.map(day => {
      const remaining = (raw.value ?? []).filter(
        row => row.day === day && !topKinds.value.includes(row.kind || 'UNKNOWN')
      );
      return remaining.reduce((total, row) => total + Number(row.cnt ?? 0), 0);
    });

    if (otherData.some(value => value > 0)) {
      result.push({
        key: 'OTHER',
        label: '其他',
        data: otherData,
        color: muted.value,
      });
    }

    return result;
  });

  const shortLabels = computed(() => days.value.map(day => day.slice(5)));

  // A ready payload with all-zero buckets should read as "no failures", not
  // as an empty chart with a misleading 0–1 axis.
  const chartState = computed(() =>
    state.value === 'ready' && series.value.length === 0 ? 'empty' : state.value
  );
</script>
